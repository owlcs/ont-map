package ru.avicomp.map.spin;

import org.apache.jena.enhanced.BuiltinPersonalities;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.GraphEventManager;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.arq.SPINFunctionDrivers;
import org.topbraid.spin.util.CommandWrapper;
import org.topbraid.spin.util.QueryWrapper;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.MapJenaException;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.MapModel;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.utils.GraphLogListener;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.impl.UnionModel;
import ru.avicomp.ontapi.jena.impl.conf.OntModelConfig;
import ru.avicomp.ontapi.jena.model.OntCE;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntIndividual;
import ru.avicomp.ontapi.jena.model.OntStatement;
import ru.avicomp.ontapi.jena.utils.Graphs;
import ru.avicomp.ontapi.jena.utils.Iter;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by @szuev on 19.05.2018.
 */
@SuppressWarnings("WeakerAccess")
public class InferenceEngineImpl implements MapManager.InferenceEngine {
    static {
        // Warning: Jena stupidly allows to modify global personality (org.apache.jena.enhanced.BuiltinPersonalities#model),
        // what does SPIN API, which, also, implicitly requires that patched version everywhere.
        // It may be dangerous, increases the system load and may impact other jena-based tools.
        // but I don't think there is an easy good workaround, so it's better to put up with that modifying.
        SpinModelConfig.init(BuiltinPersonalities.model);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(InferenceEngineImpl.class);

    protected final MapManagerImpl manager;
    protected final SPINInferenceHelper helper;
    protected static final Comparator<CommandWrapper> MAP_COMPARATOR = createMapComparator();

    public InferenceEngineImpl(MapManagerImpl manager) {
        this.manager = manager;
        this.helper = new MapInferenceHelper(manager);
    }

    /**
     * Reassemblies an union graph.
     * It is just in case, the input mapping should already contain everything needed.
     *
     * @param mapping {@link MapModel} mapping model
     * @param source  {@link Graph} graph with data to inference
     * @param target  {@link Graph} graph to put inference results
     * @return {@link UnionModel} with SPIN personalities
     * @see SpinModelConfig#LIB_PERSONALITY
     */
    public UnionModel assembleQueryModel(MapModel mapping, Graph source, Graph target) {
        // spin mapping should be fully described inside base graph:
        UnionGraph union = new UnionGraph(mapping.asOntModel().getBaseGraph());
        // pass prefixes:
        union.getPrefixMapping().setNsPrefixes(mapping.asOntModel());
        // add everything from mapping:
        Graphs.flat(((UnionGraph) mapping.asOntModel().getGraph()).getUnderlying()).forEach(union::addGraph);
        // add everything from source:
        Graphs.flat(source).forEach(union::addGraph);
        // add everything from targer:
        Graphs.flat(target).forEach(union::addGraph);
        // all from library with except of avc (also, just in case):
        Graphs.flat(manager.getMapLibraryGraph()).forEach(union::addGraph);
        return new UnionModel(union, SpinModelConfig.LIB_PERSONALITY);
    }

    /**
     * Lists all valid spin map rules ({@code spinmap:rule}).
     *
     * @param model {@link UnionModel} a query model
     * @return List of {@link QueryWrapper}s
     */
    protected List<QueryWrapper> getSpinMapRules(UnionModel model) {
        return Iter.asStream(model.getBaseGraph().find(Node.ANY, SPINMAP.rule.asNode(), Node.ANY))
                .flatMap(t -> helper.listCommands(t, model, true, false))
                .filter(QueryWrapper.class::isInstance)
                .map(QueryWrapper.class::cast)
                .collect(Collectors.toList());
    }

    @Override
    public void run(MapModel mapping, Graph source, Graph target) {
        UnionModel query = assembleQueryModel(mapping, source, target);
        // preparing for runtime functions
        Set<Resource> runtimeFunctions = Iter.asStream(query.getBaseModel().listSubjectsWithProperty(AVC.runtime))
                .map(r -> r.inModel(query))
                .collect(Collectors.toSet());
        if (!runtimeFunctions.isEmpty()) {
            manager.spinARQFactory.clearCaches();
            runtimeFunctions.forEach(r -> manager.functionRegistry.put(r.getURI(), SPINFunctionDrivers.get().create(r)));
        }

        List<QueryWrapper> commands = getSpinMapRules(query);
        if (LOGGER.isDebugEnabled())
            commands.forEach(c -> LOGGER.debug("Rule for {}: '{}'", c.getStatement().getSubject(), SpinModels.toString(c)));

        OntGraphModel m = OntModelFactory.createModel(source, OntModelConfig.ONT_PERSONALITY_LAX);
        GraphEventManager events = target.getEventManager();
        GraphLogListener logs = new GraphLogListener(LOGGER::debug);
        Model dst = ModelFactory.createModelForGraph(target);
        try {
            if (LOGGER.isDebugEnabled())
                events.register(logs);
            listTypedIndividuals(m).forEach(i -> run(commands, i, dst));
        } finally {
            events.unregister(logs);
        }
    }

    /**
     * Lists all typed individuals from a model.
     * The expression {@code model.ontObject(OntIndividual.class)} will return all individuals from a model,
     * while we need only class-assertion individuals.
     * TODO: move to ONT-API?
     *
     * @param m {@link OntGraphModel} ontology model
     * @return Stream of {@link OntIndividual}s.
     * @see ru.avicomp.ontapi.internal.ClassAssertionTranslator
     */
    public static Stream<OntIndividual> listTypedIndividuals(OntGraphModel m) {
        return m.statements(null, RDF.type, null)
                .filter(s -> s.getObject().canAs(OntCE.class))
                .map(OntStatement::getSubject)
                .map(s -> s.as(OntIndividual.class));
    }

    /**
     * Runs a query collection on specified individual and stores result to the specified model
     *
     * @param queries    List of {@link QueryWrapper}s
     * @param individual source individual
     * @param dst        {@link Model} to store results
     */
    protected void run(List<QueryWrapper> queries, OntIndividual individual, Model dst) {
        List<QueryWrapper> selected = select(queries, individual.classes().collect(Collectors.toSet()));
        process(helper, queries, selected, new HashMap<>(), dst, individual);
    }

    private static void process(SPINInferenceHelper helper,
                                List<QueryWrapper> all,
                                List<QueryWrapper> selected,
                                Map<Resource, Set<QueryWrapper>> processed,
                                Model target,
                                Resource individual) {
        selected.forEach(c -> {
            if (processed.computeIfAbsent(individual, i -> new HashSet<>()).contains(c)) {
                LOGGER.warn("The query '{}' has already been processed for individual {}", c, individual);
                return;
            }
            LOGGER.debug("RUN: {} ::: '{}'", individual, SpinModels.toString(c));
            Model res = helper.runQueryOnInstance(c, individual);
            processed.get(individual).add(c);
            target.add(res);
            if (res.isEmpty()) return;
            res.listResourcesWithProperty(RDF.type)
                    .forEachRemaining(i -> {
                        Set<Resource> classes = i.listProperties(RDF.type)
                                .mapWith(Statement::getObject)
                                .filterKeep(RDFNode::isResource)
                                .mapWith(RDFNode::asResource).toSet();
                        List<QueryWrapper> next = select(all, classes);
                        process(helper, all, next, processed, target, i);
                    });
        });
    }

    /**
     * Selects those queries whose source class is in the the given list.
     * Auxiliary method.
     *
     * @param all     List of {@link QueryWrapper}s
     * @param classes List of class expressions
     * @return List of {@link QueryWrapper}
     */
    private static List<QueryWrapper> select(List<QueryWrapper> all, Set<? extends Resource> classes) {
        return all.stream()
                .filter(c -> classes.contains(c.getStatement().getSubject()))
                .sorted(MAP_COMPARATOR::compare)
                .collect(Collectors.toList());
    }

    /**
     * Creates a rule ({@code spin:rule}) comparator which puts declaration map rules
     * (i.e. {@code spinmap:rule} with {@code spinmap:targetPredicate1 rdf:type}) first.
     *
     * @return {@link Comparator} comparator for {@link CommandWrapper}s
     */
    public static Comparator<CommandWrapper> createMapComparator() {
        Comparator<Resource> mapRuleComparator = Comparator.comparing(SpinModels::isDeclarationMapping).reversed();
        Comparator<CommandWrapper> res = (left, right) -> {
            Optional<Resource> r1 = SpinModels.rule(left);
            Optional<Resource> r2 = SpinModels.rule(right);
            return r1.isPresent() && r2.isPresent() ? mapRuleComparator.compare(r1.get(), r2.get()) : 10;
        };
        return res.thenComparing(SpinModels::toString);
    }

    /**
     * Customized spin-inference helper.
     * Created by @szuev on 27.05.2018.
     */
    public static class MapInferenceHelper extends SPINInferenceHelper {

        public MapInferenceHelper(MapManagerImpl manager) {
            super(manager.spinARQFactory, manager.functionRegistry);
        }

        /**
         * Runs a given Jena Query on a given individual and returns the inferred triples as a Model.
         * <p>
         * There is a difference with SPIN-API Inferences implementation:
         * in additional to passing {@code ?this} to top-level query binding (mapping construct)
         * there is also a ONT-MAP workaround solution to place it deep in all sub-queries, which are called by specified construct.
         * Handling {@code ?this} only by top-level mapping is definitely leak of SPIN-API functionality,
         * which severely limits the space of usage opportunities.
         * But it seems that Topbraid Composer also (checked version 5.5.1) has some magic solution for that leak in its deeps,
         * maybe similar to ours:
         * testing shows that sub-queries which handled by {@code spin:eval} may accept {@code ?this} but only  in some limited conditions,
         * e.g. for original {@code spinmap:Mapping-1-1}, which has no been cloned with changing namespace to local mapping model.
         *
         * @param query    {@link QueryWrapper}
         * @param instance {@link Resource}
         * @return {@link Model} new triples
         * @see SPINInferenceHelper#runQueryOnInstance(QueryWrapper, Resource)
         * @see AVC#currentIndividual
         * @see AVC#MagicFunctions
         */
        @Override
        public Model runQueryOnInstance(QueryWrapper query, Resource instance) {
            Model model = MapJenaException.notNull(query.getSPINQuery().getModel(), "Unattached query: " + query);
            Resource get = AVC.currentIndividual.inModel(model);
            Map<Statement, Statement> vars = getThisVarReplacement(get, instance);
            try {
                vars.forEach((a, b) -> model.add(b).remove(a));
                if (!vars.isEmpty()) {
                    spinARQFactory.clearCaches();
                    jenaFunctionRegistry.put(get.getURI(), SPINFunctionDrivers.get().create(get));
                }
                return super.runQueryOnInstance(query, instance);
            } finally {
                vars.forEach((a, b) -> model.add(a).remove(b));
            }
        }

        private static Map<Statement, Statement> getThisVarReplacement(Resource function, Resource instance) {
            Model m = function.getModel();
            return SpinModels.getFunctionBody(m, function).stream()
                    .filter(s -> Objects.equals(s.getObject(), SPIN._this))
                    .collect(Collectors.toMap(x -> x, x -> m.createStatement(x.getSubject(), x.getPredicate(), instance)));
        }
    }
}

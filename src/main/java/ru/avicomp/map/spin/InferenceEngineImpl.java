package ru.avicomp.map.spin;

import org.apache.jena.enhanced.BuiltinPersonalities;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.GraphEventManager;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.util.CommandWrapper;
import org.topbraid.spin.util.QueryWrapper;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.MapModel;
import ru.avicomp.map.utils.GraphLogListener;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.impl.UnionModel;
import ru.avicomp.ontapi.jena.impl.conf.OntModelConfig;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntIndividual;
import ru.avicomp.ontapi.jena.utils.Graphs;
import ru.avicomp.ontapi.jena.utils.Iter;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.*;
import java.util.stream.Collectors;

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
        this.helper = new SPINInferenceHelper(manager.spinARQFactory, manager.functionRegistry);
    }

    public UnionModel assembleQueryModel(MapModel mapping, Graph source, Graph target) {
        // Reassembly a union graph (just in case, it should already contain everything needed):
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
            m.listNamedIndividuals().forEach(i -> run(commands, i, dst));
        } finally {
            events.unregister(logs);
        }
    }

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
            target.add(res);
            processed.get(individual).add(c);
            if (res.isEmpty()) return;
            res.listResourcesWithProperty(RDF.type).forEachRemaining(i -> {
                Set<Resource> classes = i.listProperties(RDF.type)
                        .mapWith(Statement::getObject)
                        .filterKeep(RDFNode::isResource)
                        .mapWith(RDFNode::asResource).toSet();
                List<QueryWrapper> next = select(all, classes);
                process(helper, all, next, processed, target, i);
            });
        });
    }

    private static List<QueryWrapper> select(List<QueryWrapper> all, Set<? extends Resource> classes) {
        return all.stream()
                .filter(c -> classes.contains(c.getStatement().getSubject()))
                .sorted(MAP_COMPARATOR::compare)
                .collect(Collectors.toList());
    }

    /**
     * Creates a rule ({@code spin:rule}) comparator which puts declaration map rules ({@code spinmap:rule}) first.
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

}

package ru.avicomp.map.spin;

import org.apache.jena.enhanced.BuiltinPersonalities;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.GraphEventManager;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.rdf.model.*;
import org.apache.jena.sparql.function.FunctionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.arq.ARQFactory;
import org.topbraid.spin.arq.SPINFunctionDrivers;
import org.topbraid.spin.model.*;
import org.topbraid.spin.model.update.Update;
import org.topbraid.spin.system.SPINLabels;
import org.topbraid.spin.util.*;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.MapModel;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.utils.GraphLogListener;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.impl.UnionModel;
import ru.avicomp.ontapi.jena.impl.conf.OntModelConfig;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntIndividual;
import ru.avicomp.ontapi.jena.utils.Graphs;
import ru.avicomp.ontapi.jena.utils.Iter;
import ru.avicomp.ontapi.jena.utils.Models;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.*;
import java.util.Optional;
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

    private final MapManagerImpl manager;
    protected final SPINInferenceHelper helper;

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
            commands.forEach(c -> LOGGER.debug("Rule for {}: '{}'", c.getStatement().getSubject(), SPINInferenceHelper.toString(c)));

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
                                Resource individual
    ) {
        selected.forEach(c -> {
            if (processed.computeIfAbsent(individual, i -> new HashSet<>()).contains(c)) {
                LOGGER.warn("The query '{}' has already been processed for individual {}", c, individual);
                return;
            }
            LOGGER.debug("RUN: {} ::: '{}'", individual, SPINInferenceHelper.toString(c));
            Model res = helper.runQueryOnInstance(c, individual);
            target.add(res);
            processed.get(individual).add(c);
            if (res.isEmpty()) return;
            // possible recursion?
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
        Comparator<Resource> declaration = Comparator.comparing(SpinModels::isDeclarationMapping).reversed();
        return all.stream()
                .filter(c -> classes.contains(c.getStatement().getSubject()))
                .sorted((o1, o2) -> declaration.compare(o1.getStatement().getObject().asResource(), o2.getStatement().getObject().asResource()))
                .collect(Collectors.toList());
    }

    /**
     * An ONT-MAP replacement for several Topbraid-SPIN common classes to conduct inference.
     *
     * @see org.topbraid.spin.util.SPINQueryFinder
     * @see org.topbraid.spin.inference.SPINInferences
     */
    @SuppressWarnings("UnusedReturnValue")
    public static class SPINInferenceHelper {

        protected final ARQFactory spinARQFactory;
        protected final FunctionRegistry jenaFunctionRegistry;

        public SPINInferenceHelper(ARQFactory spinARQFactory, FunctionRegistry jenaFunctionRegistry) {
            this.spinARQFactory = spinARQFactory;
            this.jenaFunctionRegistry = jenaFunctionRegistry;
        }

        /**
         * @param triple    root rule triple, e.g. {@code C spinmap:rule _:x}
         * @param model     query model, must contain spin stuff (functions, templates, rules, etc), built on top of {@link SpinModelConfig#LIB_PERSONALITY}
         * @param withClass if true {@code ?this a ?TYPE_CLASS} will be added to query
         * @param allowAsk  to allow ASK sparql
         * @return Stream of {@link CommandWrapper}s, possible empty
         * @see SPINQueryFinder#add(Map, Statement, Model, boolean, boolean)
         */
        public Stream<CommandWrapper> listCommands(Triple triple, Model model, boolean withClass, boolean allowAsk) {
            Statement statement = model.asStatement(triple);
            if (!statement.getObject().isResource()) return Stream.empty();
            TemplateCall templateCall = SPINFactory.asTemplateCall(statement.getResource());
            if (templateCall == null) {
                Command spinCommand = SPINFactory.asCommand(statement.getResource());
                if (spinCommand == null) return Stream.empty();
                CommandWrapper wrapper = createCommandWrapper(statement, withClass, allowAsk, null, spinCommand.getComment(), spinCommand, spinCommand);
                if (wrapper == null) {
                    return Stream.empty();
                }
                return Stream.of(wrapper);
            }
            Template baseTemplate = templateCall.getTemplate();
            if (baseTemplate == null) return Stream.empty();
            Map<String, RDFNode> bindings = templateCall.getArgumentsMapByVarNames();
            return JenaUtil.getAllSuperClassesStar(baseTemplate)
                    .stream()
                    .filter(r -> JenaUtil.hasIndirectType(r, SPIN.Template))
                    .map(r -> r.as(Template.class))
                    .filter(t -> hasAllNonOptionalArguments(t, bindings))
                    .map(Module::getBody)
                    .filter(body -> {
                        if (body instanceof Construct) return true;
                        if (body instanceof Update) return true;
                        return allowAsk && body instanceof Ask;
                    }).map(body -> {
                        String spinQueryText = SPINLabels.get().getLabel(templateCall);
                        return createCommandWrapper(statement, withClass, allowAsk, spinQueryText, spinQueryText,
                                body, templateCall);
                    })
                    .filter(Objects::nonNull)
                    .peek(c -> {
                        if (bindings.isEmpty()) return;
                        c.setTemplateBinding(bindings);
                    });
        }

        /**
         * @param statement     the root {@link Statement} attached to the query model
         * @param withClass     boolean
         * @param allowAsk      boolean
         * @param spinQueryText String
         * @param label         String
         * @param spinCommand   {@link Command}
         * @param source        {@link Resource}
         * @return new {@link CommandWrapper}
         * @see SPINQueryFinder#createCommandWrapper(Map, Statement, boolean, boolean, String, String, Command, Resource)
         */
        protected CommandWrapper createCommandWrapper(Statement statement,
                                                      boolean withClass,
                                                      boolean allowAsk,
                                                      String spinQueryText,
                                                      String label,
                                                      Command spinCommand,
                                                      Resource source) {
            String queryString = spinARQFactory.createCommandString(spinCommand);
            boolean thisUnbound = spinCommand.hasProperty(SPIN.thisUnbound, JenaDatatypes.TRUE);
            if (spinQueryText == null) {
                spinQueryText = queryString;
            }
            if (spinCommand instanceof org.topbraid.spin.model.Query) {
                org.apache.jena.query.Query arqQuery = spinARQFactory.createQuery(queryString);
                if (arqQuery.isConstructType() || (allowAsk && arqQuery.isAskType())) {
                    boolean thisDeep = NestedQueries.hasNestedBlocksUsingThis(arqQuery.getQueryPattern());
                    if (isAddThisTypeClause(thisUnbound, withClass, thisDeep, spinCommand)) {
                        queryString = SPINUtil.addThisTypeClause(queryString);
                        arqQuery = spinARQFactory.createQuery(queryString);
                    }
                    return new QueryWrapper(arqQuery, source, spinQueryText,
                            (org.topbraid.spin.model.Query) spinCommand, label, statement, thisUnbound, thisDeep);
                }
            }
            if (spinCommand instanceof org.topbraid.spin.model.update.Update) {
                org.apache.jena.update.UpdateRequest updateRequest = spinARQFactory.createUpdateRequest(queryString);
                org.apache.jena.update.Update operation = updateRequest.getOperations().get(0);
                boolean thisDeep = NestedQueries.hasNestedBlocksUsingThis(operation);
                if (isAddThisTypeClause(thisUnbound, withClass, thisDeep, spinCommand)) {
                    queryString = SPINUtil.addThisTypeClause(queryString);
                    updateRequest = spinARQFactory.createUpdateRequest(queryString);
                    operation = updateRequest.getOperations().get(0);
                }
                return new UpdateWrapper(operation, source, spinQueryText,
                        (org.topbraid.spin.model.update.Update) spinCommand, label, statement, thisUnbound, thisDeep);
            }
            return null;
        }

        public static String toString(CommandWrapper w) {
            if (w.getLabel() != null)
                return w.getLabel();
            if (w.getText() != null)
                return w.getText();
            return Optional.ofNullable(w.getStatement()).map(Object::toString).orElse("Null mapping");
        }

        private static boolean isAddThisTypeClause(boolean thisUnbound, boolean withClass, boolean thisDeep, Command command) {
            return !thisUnbound && withClass && !thisDeep && SPINUtil.containsThis((CommandWithWhere) command);
        }

        /**
         * @param template {@link Template}
         * @param bindings Map
         * @return true if template is good to use
         * @see org.topbraid.spin.util.SPINQueryFinder#hasAllNonOptionalArguments(Template, Map)
         */
        private static boolean hasAllNonOptionalArguments(Template template, Map<String, RDFNode> bindings) {
            return template.getArguments(false).stream().filter(a -> !a.isOptional())
                    .map(Argument::getVarName).allMatch(bindings::containsKey);
        }

        /**
         * Runs a given Jena Query on a given instance and adds the inferred triples to a given Model.
         *
         * @param queryWrapper {@link CommandWrapper} command to run
         * @param newTriples   {@link Model} to add result of inference
         * @param instance     {@link Resource} individual to infer
         * @return true if
         * @see org.topbraid.spin.inference.SPINInferences#runQueryOnInstance(QueryWrapper, Model, Model, Resource, boolean)
         */
        public boolean runQueryOnInstance(QueryWrapper queryWrapper, Model newTriples, Resource instance) {
            Model res = runQueryOnInstance(queryWrapper, instance);
            res.listStatements().forEachRemaining(newTriples::add);
            return !res.isEmpty();
        }

        /**
         * TODO: description with explanation
         *
         * @param query    {@link QueryWrapper}
         * @param instance {@link Resource}
         * @return {@link Model} new triples
         * @see org.topbraid.spin.inference.SPINInferences#runQueryOnInstance(QueryWrapper, Model, Model, Resource, boolean)
         */
        public Model runQueryOnInstance(QueryWrapper query, Resource instance) {
            Model model = query.getSPINQuery().getModel();
            //ARQFactory.LOG_QUERIES = true;
            Resource get = AVC.resource("get").inModel(model);
            Map<Statement, Statement> vars = getThisVarReplacement(get, instance);
            try {
                vars.forEach((a, b) -> model.add(b).remove(a));
                if (!vars.isEmpty()) {
                    spinARQFactory.clearCaches();
                    jenaFunctionRegistry.put(get.getURI(), SPINFunctionDrivers.get().create(get));
                }
                Map<String, RDFNode> initialBindings = query.getTemplateBinding();
                QuerySolutionMap bindings = new QuerySolutionMap();
                if (initialBindings != null) {
                    initialBindings.forEach(bindings::add);
                }
                bindings.add(SPIN.THIS_VAR_NAME, instance);
                QueryExecution qexec = spinARQFactory.createQueryExecution(query.getQuery(), model, bindings);
                return qexec.execConstruct();
            } finally {
                vars.forEach((a, b) -> model.add(a).remove(b));
            }
        }

        private static Map<Statement, Statement> getThisVarReplacement(Resource function, Resource instance) {
            Model m = function.getModel();
            return getFunctionBody(m, function).stream()
                    .filter(s -> Objects.equals(s.getObject(), SPIN._this))
                    .collect(Collectors.toMap(x -> x, x -> m.createStatement(x.getSubject(), x.getPredicate(), instance)));
        }

        public static Set<Statement> getFunctionBody(Model m, Resource function) {
            return Iter.asStream(m.listStatements(function, RDF.type, SPIN.Function))
                    .map(Statement::getSubject)
                    .filter(r -> r.hasProperty(SPIN.body))
                    .map(r -> r.getRequiredProperty(SPIN.body))
                    .map(Statement::getObject)
                    .filter(RDFNode::isAnon)
                    .map(RDFNode::asResource)
                    .map(Models::getAssociatedStatements)
                    .findFirst()
                    .orElse(Collections.emptySet());
        }
    }

}

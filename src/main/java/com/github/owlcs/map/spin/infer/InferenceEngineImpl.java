/*
 * This file is part of the ONT MAP.
 * The contents of this file are subject to the Apache License, Version 2.0.
 * Copyright (c) 2019, The University of Manchester, owl.cs group.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.owlcs.map.spin.infer;

import com.github.owlcs.map.MapJenaException;
import com.github.owlcs.map.MapManager;
import com.github.owlcs.map.MapModel;
import com.github.owlcs.map.spin.*;
import com.github.owlcs.map.spin.vocabulary.AVC;
import com.github.owlcs.map.utils.GraphLogListener;
import com.github.owlcs.map.utils.GraphUtils;
import com.github.owlcs.map.utils.ModelUtils;
import com.github.owlcs.ontapi.jena.OntModelFactory;
import com.github.owlcs.ontapi.jena.UnionGraph;
import com.github.owlcs.ontapi.jena.impl.UnionModel;
import com.github.owlcs.ontapi.jena.model.OntClass;
import com.github.owlcs.ontapi.jena.model.OntIndividual;
import com.github.owlcs.ontapi.jena.model.OntModel;
import com.github.owlcs.ontapi.jena.utils.Graphs;
import com.github.owlcs.ontapi.jena.utils.Iter;
import com.github.owlcs.ontapi.jena.vocabulary.OWL;
import com.github.owlcs.ontapi.jena.vocabulary.RDF;
import org.apache.jena.enhanced.BuiltinPersonalities;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.GraphEventManager;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.util.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.arq.ARQFactory;
import org.topbraid.spin.util.QueryWrapper;
import org.topbraid.spin.vocabulary.SPIN;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An implementation of {@link MapManager.InferenceEngine} adapted to the ontology (OWL2) data mapping paradigm.
 * <p>
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

    protected final MapModel mapping;
    protected final Graph library;
    protected final MapConfigImpl config;
    protected final MapARQFactory factory;

    // A threshold for internal nodes cache.
    // Assume there is Hotspot Java 6 VM (x32)
    // Then java6 (actually java8 much less, java9 even less) approximate String memory size would be: 8 * (int) ((((no chars) * 2) + 45) / 8)
    // org.apache.jena.graph.Node_Blank contains BlankNodeId which in turn contains a String (id) ~ size: 8 (header) + 8 + (string size)
    // org.apache.jena.graph.Node_URI contains label as String ~ size: 8 + (string size)
    // For uri or blank node with length 50 (owl:ObjectProperty = 46, jena blank node id = 36) chars
    // the average size of node would be ~ 160 bytes.
    // Let it be ten times more ~ 1024 byte, i.e. 1MB ~= 1000 nodes (Wow! It is very very understated. But whatever)
    // Then 50MB threshold:
    protected static final int INTERMEDIATE_NODES_STORE_THRESHOLD = 50_000;

    public InferenceEngineImpl(MapModel mapping, MapManagerImpl manager) {
        this(mapping, manager.getTopSpinGraph(), manager.getFactory(), manager.getMappingConfiguration());
    }

    public InferenceEngineImpl(MapModel mapping, Graph library, MapARQFactory factory, MapConfigImpl config) {
        this.mapping = Objects.requireNonNull(mapping);
        this.library = Objects.requireNonNull(library);
        this.factory = Objects.requireNonNull(factory);
        this.config = Objects.requireNonNull(config);
    }

    @Override
    public void run(Graph source, Graph target) throws MapJenaException {
        UnionModel query = assembleQueryModel();
        // re-register runtime functions
        query.getBaseModel().listResourcesWithProperty(AVC.runtime)
                .mapWith(r -> r.inModel(query))
                .forEachRemaining(factory::replace);
        // find rules:
        Set<ProcessedQuery> rules = selectMapRules(query);
        if (LOGGER.isDebugEnabled())
            rules.forEach(c -> LOGGER.debug("Rule for <{}>: '{}'", c.getSubject(), c));
        if (rules.isEmpty()) {
            throw Exceptions.INFERENCE_NO_RULES.create()
                    .add(Exceptions.Key.MAPPING, String.valueOf(mapping))
                    .build();
        }
        // run rules:
        GraphEventManager events = target.getEventManager();
        GraphLogListener logs = new GraphLogListener(LOGGER::debug);
        if (LOGGER.isDebugEnabled())
            events.register(logs);
        try {
            run(rules, source, target);
        } finally {
            events.unregister(logs);
        }
    }

    /**
     * Assemblies a query {@link UnionModel union model} from the given {@link MapModel mapping}.
     * The returned model has a flat graph structure without repetitions,
     * while the mapping graph has tree-like structure of dependency graphs with duplicated leaves.
     * Also notice that the result graph is not distinct,
     * since the mapping may contain also a source data (in additional to the schema, that is required for a mapping).
     * The nature of source is unknown, the distinct mode might unpredictable degrade performance and memory usage.
     * Therefore, it is expected that an iterator over the query model must be faster.
     *
     * @return {@link UnionModel} with SPIN personalities
     * @see SpinModelConfig#LIB_PERSONALITY
     */
    public UnionModel assembleQueryModel() {
        UnionGraph g = (UnionGraph) mapping.asGraphModel().getGraph();
        // no distinct:
        UnionGraph res = new UnionGraph(g.getBaseGraph(), null, null, false);
        // pass prefixes:
        res.getPrefixMapping().setNsPrefixes(mapping.asGraphModel());
        // add everything from the mapping:
        g.getUnderlying().graphs().flatMap(Graphs::baseGraphs).forEach(res::addGraph);
        // to ensure that all graphs from the library (with except of avc.*) are present (just in case) :
        Graphs.baseGraphs(library).forEach(res::addGraph);
        return new UnionModel(res, SpinModelConfig.LIB_PERSONALITY);
    }

    /**
     * Runs the given query collection on the {@code source} model and stores the result to the {@code target}.
     *
     * @param queries List of {@link ProcessedQuery}s, must not be empty
     * @param source  {@link Graph} containing source individuals
     * @param target  {@link Graph} to write resulting individuals
     */
    protected void run(Collection<ProcessedQuery> queries, Graph source, Graph target) {
        Context context = factory.getContext();
        UnionGraph queryGraph = (UnionGraph) (queries.iterator().next().getModel()).getGraph();
        OntModel src = assembleSourceDataModel(queryGraph, source, target);
        Model dst = ModelFactory.createModelForGraph(target);
        // insets the source data into the query model, if it is absent:
        if (!GraphUtils.containsAll(queryGraph, source)) {
            queryGraph.addGraph(source);
        }
        // don't quite understand why without target it doesn't always work
        if (!GraphUtils.containsAll(queryGraph, target)) {
            queryGraph.addGraph(target);
        }
        Set<Node> inMemory = new HashSet<>();

        Map<Node, NodeValue> factoryCache = context.get(MapARQFactory.NODE_TO_VALUE_CACHE);
        // first process all direct individuals from the source graph:
        listIndividuals(src, dst).forEach(i -> {
            Set<OntClass> classes = i.classes(false).collect(Collectors.toSet());
            Map<String, Set<QueryWrapper>> visited;
            processOne(queries, classes, visited = new HashMap<>(), inMemory, dst, i);
            // in case no enough memory to keep temporary objects, flush individuals set-store immediately:
            if (inMemory.size() > INTERMEDIATE_NODES_STORE_THRESHOLD) {
                processMany(queries, visited, dst, inMemory);
            }
            if (factoryCache.size() > INTERMEDIATE_NODES_STORE_THRESHOLD) {
                factoryCache.clear();
            }
        });
        // next iteration: flush temporarily stored individuals that are appeared on first pass,
        // this time it is for dependent queries:
        processMany(queries, new HashMap<>(), dst, inMemory);
        factoryCache.clear();
    }

    /**
     * Finds and lists all individuals from the given source ontology.
     * Warning: in case the source and the target match,
     * the method puts all found individuals to memory to avoid {@link ConcurrentModificationException}.
     *
     * @param src {@link OntModel}, the source, not {@code null}
     * @param dst {@link Model}, the target, not {@code null}
     * @return {@code Stream} of {@link OntIndividual}s
     */
    protected Stream<OntIndividual> listIndividuals(OntModel src, Model dst) {
        if (Graphs.isSameBase(src.getBaseGraph(), dst.getGraph())) {
            return src.individuals().collect(Collectors.toList()).stream();
        }
        return src.individuals();
    }

    /**
     * Assembles the source model from the given source graph, that may contain either raw data or data with schema.
     * The mapping must contain both the source and the target schemas,
     * but may also include source data in case it is in the same graph with the schema.
     * This method returns an OWL model both with the schema and data and without any other additions (spin library).
     *
     * @param query  {@link UnionGraph}, the query model, not {@code null}
     * @param source {@link Graph}, not {@code null}
     * @param target {@link Graph}, not {@code null}
     * @return {@link OntModel}, not {@code null}
     * @see #assembleQueryModel()
     */
    public OntModel assembleSourceDataModel(UnionGraph query, Graph source, Graph target) {
        if (GraphUtils.containsAll(query, source)) { // the source contains schema
            return OntModelFactory.createModel(source, SpinModelConfig.ONT_PERSONALITY);
        }
        // Otherwise the raw no-schema data is specified -> assembly source from the given parts:
        Set<Graph> exclude = Graphs.baseGraphs(library).collect(Collectors.toSet());
        if (target != null) {
            Graphs.baseGraphs(target).forEach(exclude::add);
        }
        List<Graph> schemas = Graphs.baseGraphs(query).filter(x -> !exclude.contains(x)).collect(Collectors.toList());
        List<Graph> sources = Graphs.baseGraphs(source).collect(Collectors.toList());
        // the result is not distinct, since the nature of source is unknown,
        // and the distinct mode might unpredictable degrade performance and memory usage:
        UnionGraph res = new UnionGraph(sources.remove(0), null, null, false);
        sources.forEach(res::addGraph);
        schemas.forEach(res::addGraph);
        return OntModelFactory.createModel(res, SpinModelConfig.ONT_PERSONALITY);
    }

    /**
     * Runs a query collection against a collection of individuals (in the form of regular resources),
     * writes the result into the specified {@code target} model.
     *
     * @param queries     Collection of all {@link ProcessedQuery}s found in the {@link #mapping}
     * @param processed   Map of already processed individual-queries to prevent recursion
     * @param target      {@link Model} to write
     * @param individuals List of {@link Resource}s
     */
    protected void processMany(Collection<ProcessedQuery> queries,
                               Map<String, Set<QueryWrapper>> processed,
                               Model target,
                               Set<Node> individuals) {
        Iterator<Node> iterator = individuals.iterator();
        while (iterator.hasNext()) {
            Resource i = target.asRDFNode(iterator.next()).asResource();
            Set<Resource> classes = ModelUtils.listDirectClasses(i).toSet();
            processOne(queries, classes, processed, individuals, target, i);
            iterator.remove();
        }
    }

    /**
     * Runs a query collection against the single individual.
     *
     * @param queries   Collection of all {@link ProcessedQuery}s found in the {@link #mapping}
     * @param classes   Set of class expressions, which the given individual is belonged to
     * @param processed Map of already processed individual-queries to prevent possible recursion,
     *                  it is not expected to be large
     * @param store     Set of {@link Node}s, the collection of result individuals to process in the next step
     * @param target    {@link Model} to write inference result (individuals and property assertions)
     * @param source    {@link Resource} the current individual to process
     */
    protected void processOne(Collection<ProcessedQuery> queries,
                              Set<? extends Resource> classes,
                              Map<String, Set<QueryWrapper>> processed,
                              Set<Node> store,
                              Model target,
                              Resource source) {
        queries.stream()
                .filter(q -> classes.contains(q.getSubject()))
                .forEach(q -> {
                    if (!processed.computeIfAbsent(ModelUtils.getResourceID(source), i -> new HashSet<>()).add(q)) {
                        LOGGER.warn("The query '{}' has been already processed for individual {}.", q, source);
                        return;
                    }
                    LOGGER.debug("RUN: {} ::: '{}'", source, q);
                    // use a fresh model, otherwise there is a danger of java.util.ConcurrentModificationException
                    // while graph iterating by some unclear reason if there are dependent rules in the mapping
                    Model res = q.run(source);
                    res.listStatements().forEachRemaining(s -> {
                        if (RDF.type.equals(s.getPredicate())) {
                            store.add(s.getSubject().asNode());
                        }
                        target.add(s);
                    });
                });
    }

    /**
     * Lists all valid spin map rules (i.e. {@code spinmap:rule}) from the given query model.
     *
     * @param model {@link UnionModel} a query model
     * @return List of {@link QueryWrapper}s
     */
    public Set<ProcessedQuery> selectMapRules(UnionModel model) {
        return selectMapRules(model, this::wrap);
    }

    /**
     * Creates a {@link ProcessedQuery} from {@link QueryWrapper}.
     *
     * @param qw {@link QueryWrapper}, not {@code null}
     * @return {@link ProcessedQuery}
     */
    protected ProcessedQuery wrap(QueryWrapper qw) {
        if (!config.optimizeQueries()) {
            return new ProcessedQuery(qw);
        }
        Resource type = SPINInferenceHelper.getTypeDeclaration(qw);
        if (type == null) {
            return new ProcessedQuery(qw);
        }
        if (OWL.NamedIndividual.equals(type)) {
            return new NamedIndividualQuery(qw);
        }
        return new TypeDeclarationQuery(qw, type);
    }

    public Set<ProcessedQuery> selectMapRules(UnionModel model, Function<QueryWrapper, ProcessedQuery> mapper) {
        return Iter.asStream(SPINInferenceHelper.listMappingRules(factory, model))
                .map(mapper)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * An {@link ExtendedQuery Extended SPIN Query} with possibility to process it for the given individual.
     * <p>
     * Created by @ssz on 14.11.2018.
     */
    public class ProcessedQuery extends ExtendedQuery {

        public ProcessedQuery(QueryWrapper qw) {
            super(qw);
        }

        /**
         * Runs the Jena Query encapsulating in this object
         * for a given individual and returns the inferred triples as a Model.
         * <p>
         * There is a difference with SPIN-API Inferences implementation:
         * in additional to passing {@code ?this} to top-level query binding (mapping construct)
         * there is also a ONT-MAP workaround solution to place it deep in all sub-queries,
         * which are called by specified construct.
         * Handling {@code ?this} only by the top-level mappings is definitely leak of SPIN-API functionality,
         * which severely limits the space of usage opportunities.
         * But, it seems, that Topbraid Composer also (checked version 5.5.1)
         * has some magic solution for that leak in its deeps, maybe similar to ours:
         * testing shows that sub-queries which handled by {@code spin:eval}
         * may accept {@code ?this} but only in some limited conditions,
         * for example (and at least) for the original {@code spinmap:Mapping-1-1},
         * that has no been cloned with changing namespace to local mapping model.
         *
         * @param instance {@link Resource}, an individual to process, not {@code null}
         * @return {@link Model}, new triples, not {@code null}
         * @throws MapJenaException in case exception occurred while inference
         * @see SPINInferenceHelper#runQueryOnInstance(ARQFactory, QueryWrapper, Resource, Model)
         * @see AVC#currentIndividual
         * @see AVC#MagicFunctions
         */
        public Model run(Resource instance) {
            Model mapping = getModel();
            Resource _this = AVC.currentIndividual.inModel(mapping);
            Map<Statement, Statement> vars = getThisVarReplacement(_this, instance);
            try {
                vars.forEach((a, b) -> mapping.add(b).remove(a));
                if (!vars.isEmpty()) {
                    factory.replace(_this);
                }
                try {
                    return SPINInferenceHelper.runQueryOnInstance(factory, this, instance, null);
                } catch (RuntimeException ex) {
                    throw Exceptions.INFERENCE_FAIL.create()
                            .add(Exceptions.Key.QUERY, String.valueOf(this))
                            .add(Exceptions.Key.INSTANCE, instance.toString())
                            .build(ex);
                }
            } finally {
                vars.forEach((a, b) -> mapping.add(a).remove(b));
            }
        }

        private Map<Statement, Statement> getThisVarReplacement(Resource function, Resource instance) {
            Model m = function.getModel();
            return SpinModels.getLocalFunctionBody(m, function).stream()
                    .filter(s -> Objects.equals(s.getObject(), SPIN._this))
                    .collect(Collectors.toMap(x -> x, x -> m.createStatement(x.getSubject(), x.getPredicate(), instance)));
        }
    }

    /**
     * A simplest {@link ProcessedQuery}
     * to generate {@code _:x rdf:type type} triple for a given individual.
     * <p>
     * Created by @ssz on 30.12.2018.
     */
    public class TypeDeclarationQuery extends ProcessedQuery {
        private final Resource type;

        public TypeDeclarationQuery(QueryWrapper qw, Resource type) {
            super(qw);
            this.type = Objects.requireNonNull(type);
        }

        @Override
        public Model run(Resource individual) {
            return individual.inModel(emptyModel()).addProperty(RDF.type, type).getModel();
        }

        protected Model emptyModel() {
            return ModelFactory.createDefaultModel();
        }
    }

    /**
     * A {@link ProcessedQuery} to generate {@link OWL#NamedIndividual owl:NamedIndividual} instances.
     * <p>
     * Created by @ssz on 14.11.2018.
     */
    public class NamedIndividualQuery extends TypeDeclarationQuery {

        public NamedIndividualQuery(QueryWrapper qw) {
            super(qw, OWL.NamedIndividual);
        }

        @Override
        public Model run(Resource individual) {
            if (individual.isAnon()) return emptyModel();
            return super.run(individual);
        }
    }
}

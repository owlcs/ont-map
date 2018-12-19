/*
 * This file is part of the ONT MAP.
 * The contents of this file are subject to the Apache License, Version 2.0.
 * Copyright (c) 2018, Avicomp Services, AO
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

package ru.avicomp.map.spin;

import org.apache.jena.enhanced.UnsupportedPolymorphismException;
import org.apache.jena.graph.Factory;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.*;
import org.apache.jena.shared.PrefixMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.model.Argument;
import org.topbraid.spin.system.ExtraPrefixes;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.*;
import ru.avicomp.map.spin.infer.InferenceEngineImpl;
import ru.avicomp.map.spin.system.Resources;
import ru.avicomp.map.spin.system.SystemModels;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;
import ru.avicomp.map.utils.AutoPrefixListener;
import ru.avicomp.map.utils.ClassPropertyMapListener;
import ru.avicomp.map.utils.LocalClassPropertyMapImpl;
import ru.avicomp.map.utils.ReadOnlyGraph;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.impl.OntGraphModelImpl;
import ru.avicomp.ontapi.jena.impl.UnionModel;
import ru.avicomp.ontapi.jena.impl.conf.OntPersonality;
import ru.avicomp.ontapi.jena.model.*;
import ru.avicomp.ontapi.jena.utils.Graphs;
import ru.avicomp.ontapi.jena.utils.Models;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A spin based implementation of {@link MapManager}.
 * <p>
 * Created by @szuev on 06.04.2018.
 *
 * @see ru.avicomp.map.Managers
 */
@SuppressWarnings("WeakerAccess")
public class MapManagerImpl implements MapManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(MapModelImpl.class);

    // prefix library:
    protected final PrefixMapping prefixes;
    // whole graph library (primary graph + avc complement graphs + original spin graphs):
    protected final UnionModel library;
    // cache; a part of #library: no avc and primary graphs (i.e. raw spin family lib):
    private Graph rawSpinLibrary;
    // cache; a part of #library: everything except the spin family:
    private List<Graph> additional;
    // map-functions:
    protected final Map<String, FunctionImpl> functions;
    // config:
    protected final MapConfigImpl config;
    // ARQ factory:
    protected final MapARQFactory arqFactory;
    // Graph factory:
    protected final Supplier<Graph> graphFactory;

    public MapManagerImpl(Graph primary) {
        this(primary, Factory::createGraphMem, new HashMap<>(), MapConfigImpl.INSTANCE);
    }

    /**
     * The main constructor.
     *
     * @param library {@link Graph} to use as primary in the library, not {@code null}
     * @param graphs  a factory to produce Graphs for mappings, not {@code null}
     * @param map     Map to store map-functions, not {@code null}
     * @param conf    {@link MapConfigImpl}, configuration, not {@code null}
     */
    protected MapManagerImpl(Graph library,
                             Supplier<Graph> graphs,
                             Map<String, FunctionImpl> map,
                             MapConfigImpl conf) {
        this.graphFactory = Objects.requireNonNull(graphs, "Null graph factory");
        this.functions = Objects.requireNonNull(map, "Null map");
        this.library = createLibraryModel(Objects.requireNonNull(library, "Null primary graph"));
        this.prefixes = Graphs.collectPrefixes(SystemModels.graphs().values());
        this.config = Objects.requireNonNull(conf, "Null config");
        this.arqFactory = MapARQFactory.createSPINARQFactory();
        SpinModels.listSpinFunctions(this.library).forEach(this::register);
    }

    /**
     * Creates a complete ONT-MAP library ("a query model" in terms of SPIN-API).
     * The result graph includes all turtle resources from {@code /etc} dir.
     * The top level graph is mutable and stands for user defined functions, while others are unmodifiable.
     * The result model supports OWL2 constructions also, it was done just to handle custom numeric datatype.
     *
     * @param graph {@link Graph} containing user-defined functions
     * @return {@link UnionModel}
     * @see SpinModelConfig#ONT_LIB_PERSONALITY
     */
    public static UnionModel createLibraryModel(Graph graph) {
        // root graph for user defined stuff (note: it is distinct)
        UnionGraph res = new UnionGraph(graph);
        // avc.spin, avc.lib, avc.fn, avc.xsd, avc.math, etc:
        SystemModels.graphs(false).forEach(res::addGraph);
        // topbraid spinmapl (the top graph of the spin family):
        res.addGraph(getSpinLibraryGraph());
        return new OntGraphModelImpl(res, SpinModelConfig.ONT_LIB_PERSONALITY);
    }

    /**
     * Gets the pin library union graph without ONT-MAP (avc.*.ttl) additions.
     *
     * @return {@link UnionGraph} with spin-family hierarchy of unmodifiable graphs
     */
    public static UnionGraph getSpinLibraryGraph() {
        return Graphs.toUnion(Resources.SPINMAPL.getGraph(),
                SystemModels.graphs(true).collect(Collectors.toSet()));
    }

    /**
     * Gets Map ARQ factory.
     *
     * @return {@link MapARQFactory}
     */
    public MapARQFactory getFactory() {
        return arqFactory;
    }

    /**
     * Gets Map Config.
     *
     * @return {@link MapConfigImpl}
     */
    public MapConfigImpl getConfig() {
        return config;
    }

    @Override
    public PrefixMapping prefixes() {
        return prefixes;
    }

    /**
     * Returns the library graph, that consists from:
     * <ul>
     * <li>a primary graph to store user-defined or manager-specific content</li>
     * <li>{@code avc.spin.ttl} - base definitions and customization</li>
     * <li>{@code avc.lib.ttl} - additional AVC functions</li>
     * <li>{@code avc.math.ttl} - functions from xquery/math</li>
     * <li>{@code avc.fn.ttl} - functions from xquery which were forgotten in {@code functions-fn.ttl}</li>
     * <li>{@code avc.xsd.ttl} - functions for xsd types which were forgotten in {@code spinmapl.spin.ttl}</li>
     * <li>{@code spinmapl.spin.ttl} - a top of the standard (composer's) spin-family graphs</li>
     * </ul>
     *
     * @return {@link UnionModel}
     */
    public UnionModel getLibrary() {
        return library;
    }

    /**
     * Gets a library graph without any inclusion (i.e. without avc additions and primary graph).
     * It is equivalent to the {@link #getSpinLibraryGraph()} method,
     * but it returns always the same reference from the {@link #getLibrary() library} {@link UnionGraph} graph.
     *
     * @return {@link UnionGraph}, each call the same instance
     * @throws ru.avicomp.map.MapJenaException.IllegalState wrong state of manager
     * @see #getLibrary()
     */
    public Graph getTopSpinGraph() throws MapJenaException.IllegalState {
        if (rawSpinLibrary != null) return rawSpinLibrary;
        return rawSpinLibrary = getLibrary().getGraph().getUnderlying().graphs()
                .filter(UnionGraph.class::isInstance)
                .map(UnionGraph.class::cast)
                .filter(g -> g.getUnderlying().graphs().count() != 0)
                .findFirst()
                .orElseThrow(() -> new MapJenaException.IllegalState("Unable to find SPINMAPL graph"));
    }

    /**
     * Lists all graphs that are additional to the spin-family graphs.
     * A primary graph is included to the returned stream in the first position.
     *
     * @return Stream of {@link Graph}s
     * @see #getLibrary()
     */
    public Stream<Graph> listAdditionalGraphs() {
        if (additional != null) return additional.stream();
        List<Graph> res = new ArrayList<>();
        res.add(getLibrary().getBaseGraph());
        getLibrary().getGraph().getUnderlying().graphs()
                .filter(x -> !(x instanceof UnionGraph))
                .forEach(res::add);
        return (additional = res).stream();
    }

    @Override
    public Graph getGraph() {
        return ReadOnlyGraph.wrap(getLibrary().getBaseGraph());
    }

    @Override
    public void addGraph(Graph g) {
        registerFunctions(ModelFactory.createModelForGraph(Objects.requireNonNull(g, "Null graph.")));
    }

    /**
     * Registers a spin-function in the manager in the from of {@link MapFunction}.
     *
     * @param inModel {@link Resource} with {@code rdf:type = spin:Function}
     * @throws MapJenaException wrong function resource
     * @see SpinModelConfig#LIB_PERSONALITY
     */
    protected void register(Resource inModel) throws MapJenaException {
        org.topbraid.spin.model.Function f;
        try {
            f = inModel.as(org.topbraid.spin.model.Function.class);
        } catch (UnsupportedPolymorphismException upe) {
            throw new MapJenaException("Wrong model attached - lack of personalities: " + inModel, upe);
        }
        ExtraPrefixes.add(f); // <- wtf?
        functions.put(f.getURI(), new FunctionImpl(f));
        if (f.isMagicProperty()) {
            arqFactory.registerProperty(f);
        } else {
            arqFactory.registerFunction(f);
        }
    }

    /**
     * Lists all common (i.e. no magic) executable spin functions that are not private, abstract, deprecated or hidden
     * (the last property is calculated using info provided by avc supplement graph).
     * Spin templates are not included also.
     *
     * @return Stream of {@link MapFunction}s.
     */
    @Override
    public Stream<MapFunction> functions() {
        return functions.values().stream().filter(this::filter).map(Function.identity());
    }

    /**
     * Gets all available functions as unmodifiable Map.
     *
     * @return {@link Map} with IRIs as keys and {@link FunctionImpl}s as values
     */
    public Map<String, FunctionImpl> getFunctionsMap() {
        return Collections.unmodifiableMap(functions);
    }

    /**
     * Answers {@code true} if the given map-function is good enough to be used to build and inference mappings.
     * Protected access to be able to override.
     *
     * @param f {@link FunctionImpl}
     * @return boolean
     */
    protected boolean filter(FunctionImpl f) {
        return !f.isPrivate() // skip private
                && !f.isAbstract() // skip abstract
                && !f.isDeprecated()  // skip deprecated
                && !f.isHidden()  // skip hidden
                && !f.isMagicProperty()  // skip properties
                && f.isExecutable(); // only registered
    }

    @Override
    public FunctionImpl getFunction(String name) throws MapJenaException {
        return MapJenaException.notNull(functions.get(name), "Can't find function " + name);
    }

    /**
     * Creates and configures a fresh (SPIN-) mapping model in form of rdf-ontology.
     * Uses {@link OntPersonality ont-personality} in order to reuse some owl2 resources
     * such as {@link ru.avicomp.ontapi.jena.model.OntID ontology id},
     * {@link ru.avicomp.ontapi.jena.model.OntCE ont class expression},
     * {@link ru.avicomp.ontapi.jena.model.OntPE ont property expression}.
     *
     * @return {@link MapModel mapping model}, which also ia anonymous owl ontology
     */
    @Override
    public MapModelImpl createMapModel() {
        return createMapModel(graphFactory.get(), null);
    }

    /**
     * Creates and configures a mapping model with the given graph and personalities
     *
     * @param graph       {@link Graph}, not null
     * @param personality {@link OntPersonality} or {@code null} for default
     * @return {@link MapModelImpl}
     */
    public MapModelImpl createMapModel(Graph graph, OntPersonality personality) {
        MapModelImpl m = newMapModelImpl(graph, personality);
        setupMapModel(m);
        return m;
    }

    /**
     * Wraps the given Ontology Model as Mapping model.
     *
     * @param m {@link OntGraphModel}, not null
     * @return {@link MapModelImpl}
     */
    public MapModelImpl newMapModelImpl(OntGraphModel m) {
        return newMapModelImpl(m.getGraph(), m instanceof OntGraphModelImpl ?
                ((OntGraphModelImpl) m).getPersonality() : null);
    }

    /**
     * Creates a new Mapping Model from the given Graph and personalities.
     *
     * @param graph       {@link Graph}, not {@code null}
     * @param personality {@link OntPersonality} or {@code null} for default
     * @return {@link MapModelImpl}
     */
    public MapModelImpl newMapModelImpl(Graph graph, OntPersonality personality) {
        // note: the mapping graph is distinct!
        Objects.requireNonNull(graph);
        return new MapModelImpl(graph instanceof UnionGraph ? (UnionGraph) graph : new UnionGraph(graph),
                personality == null ? SpinModelConfig.ONT_PERSONALITY : personality, this);
    }

    /**
     * Configures the given mapping model before it is used by the manager.
     * It is auxiliary method, not for public usage.
     * Note that the graph will be modified:
     * the method adds the statement {@code spinmap:rule spin:rulePropertyMaxIterationCount "2"^^xsd:int},
     * which indicates to Topbraid Composer Engine that inference is need to be run two times.
     * This is to be sure that all possible rule logical dependencies (e.g. in chained contexts) will be satisfied.
     * Notice that ONT-MAP Inference Engine does not use that setting and inference will be run only once.
     * Also this method ensures that the given graph contains http://topbraid.org/spin/spinmapl in import declarations.
     *
     * @param res {@link OntGraphModelImpl} model instance
     */
    public void setupMapModel(OntGraphModelImpl res) {
        // add prefix listener to the graph
        AutoPrefixListener.addAutoPrefixListener(res.getGraph(), prefixes());
        // Set spin:rulePropertyMaxIterationCount to 2
        // to be sure that all the rules have been processed through TopBraid Composer Inference as expected,
        // even if they depend on other rules.
        // Note: this parameter is unused in our ONT-Map InferenceEngine:
        // it provides own rules order, and each rule is processed only once.
        res.add(SPINMAP.rule, SPIN.rulePropertyMaxIterationCount, res.createTypedLiteral(2));
        // add spinmapl (a top of library,  do not add avc.*.ttl addition) to owl:imports:
        res.addImport(new OntGraphModelImpl(getTopSpinGraph(), res.getPersonality()));
    }

    /**
     * Tests weather the given OWL2 model is also a mapping model.
     * For the sake of simplicity assumes
     * that map-model must have &lt;http://topbraid.org/spin/spinmapl&gt; in the imports.
     * In general case it is not right, but both Topbraid Composer and ONT-MAP provides such mappings by default.
     *
     * @param m {@link OntGraphModel}
     * @return true if it is also {@link MapModelImpl}
     */
    @Override
    public boolean isMapModel(OntGraphModel m) {
        if (m instanceof MapModelImpl) return true;
        return m.getID().imports().anyMatch(this::isTopSpinURI);
    }

    /**
     * Answers {@code true} if the given uri is a name of the top-level spin graph,
     * that belongs to every mapping produced by the ONT-MAP.
     *
     * @param uri String
     * @return boolean
     */
    public boolean isTopSpinURI(String uri) {
        return SPINMAPL.BASE_URI.equals(uri);
    }

    @Override
    public MapModelImpl asMapModel(OntGraphModel m) throws MapJenaException {
        return asMapModel(m, SpinModelConfig.ONT_PERSONALITY);
    }

    /**
     * Wraps the given OWL2 model as a mapping model if it is possible.
     * Also puts any local defined functions to the manager registry.
     *
     * @param m           {@link OntGraphModel}
     * @param personality {@link OntPersonality}
     * @return {@link MapModelImpl}
     * @throws MapJenaException in case model can not be wrap as MapModelImpl
     */
    public MapModelImpl asMapModel(OntGraphModel m, OntPersonality personality) throws MapJenaException {
        if (!isMapModel(m)) {
            throw new MapJenaException("<" + m.getID() + "> is not a mapping model");
        }
        if (m instanceof MapModelImpl && this.equals(((MapModelImpl) m).getManager())) {
            return (MapModelImpl) m;
        }
        // register functions and add to the primary manager graph:
        registerFunctions(m.getBaseModel());
        // reassembly given model:
        return reassemble(m, personality);
    }

    /**
     * Reassembles the given model so that it has correct structure and references to the manager.
     *
     * @param m {@link OntGraphModel}, not {@code null}
     * @param p {@link OntPersonality}, not {@code null}
     * @return {@link MapModelImpl}
     */
    protected MapModelImpl reassemble(OntGraphModel m, OntPersonality p) {
        MapModelImpl res = newMapModelImpl(m.getBaseGraph(), p);
        m.imports()
                .filter(i -> !isTopSpinURI(i.getID().getURI())) // skip spin-top graph to be added then separately
                .map(ModelGraphInterface::getGraph)
                .forEach(res.getGraph()::addGraph);
        // add spin-top lib:
        res.getGraph().addGraph(getTopSpinGraph());
        return res;
    }

    /**
     * Registers all functions listed from the given model.
     *
     * @param m {@link Model}, not {@code null}
     * @throws MapJenaException unable to handle some of the listed functions
     */
    protected void registerFunctions(Model m) throws MapJenaException {
        SpinModels.listSpinFunctions(m)
                .forEach(f -> {
                    // if it is contained in the map and has the same content -> OK, continue
                    // if it is contained in the map and has different content, but it is not avc:runtime -> FAIL
                    // if it is contained in the map and has different content, but it is avc:runtime -> OK, re-register
                    // if it is no contained anywhere -> OK, register and add definition to the primary graph
                    if (functions.containsKey(f.getURI())) {
                        if (SpinModels.containsResource(library, f)) {
                            if (LOGGER.isDebugEnabled())
                                LOGGER.debug("Function <{}> is already within the manager {}.", f, MapManagerImpl.this);
                            return;
                        } else if (!f.hasProperty(AVC.runtime)) {
                            throw new MapJenaException("Attempt to re-register function <" + f + ">: " +
                                    "Within the manager " + MapManagerImpl.this + " there is already a function " +
                                    "with the same name, but with different content. " +
                                    "Please choose another uri-name or remove existing function duplicate.");
                        }
                        if (LOGGER.isDebugEnabled())
                            LOGGER.debug("Found avc:runtime function: <{}> .", f);
                    } else { // add content to the primary graph:
                        f = SpinModels.printSpinFunctionBody(library, f);
                    }
                    if (LOGGER.isDebugEnabled())
                        LOGGER.debug("Add function <{}> into the manager {}.", f, MapManagerImpl.this);
                    register(f);
                });
    }

    /**
     * Returns all numeric datatypes ({@code rdfs:Datatype}) defined in avc.spin.ttl.
     *
     * @return Stream of all number datatypes
     * @see AVC#numeric
     * @see <a href='https://www.w3.org/TR/sparql11-query/#operandDataTypes'>SPARQL Operand Data Types</a>
     * @deprecated no need any more
     */
    @Deprecated
    public Stream<OntDT> numberDatatypes() {
        OntDT res = AVC.numeric.inModel(getLibrary()).as(OntDT.class);
        OntDR dr = res.equivalentClass().findFirst()
                .orElseThrow(() -> new IllegalStateException("Can't find owl:equivalentClass for " + res));
        return dr.as(OntDR.UnionOf.class).dataRanges().map(d -> d.as(OntDT.class));
    }

    /**
     * Gets a a class-properties map object.
     * The resulting {@link ClassPropertyMap Class-Properties Mapping} is cached object and
     * it is placed directly within the specified model:
     * in a listener attached to the top-level {@link UnionGraph graph}.
     * <p>
     * Note: this method is used during validation of input arguments,
     * although SPIN-MAP API allows perform mapping even for properties which is not belonged to the context class.
     *
     * @param model {@link OntGraphModel OWL model}
     * @return {@link ClassPropertyMap mapping}
     * @see ClassPropertyMapListener
     */
    @Override
    public ClassPropertyMap getClassProperties(OntGraphModel model) {
        Stream<OntGraphModel> models = listRelatedModels(model);
        List<ClassPropertyMap> maps = models
                .map(m -> ClassPropertyMapListener.getCachedClassPropertyMap((UnionGraph) m.getGraph(),
                        () -> new LocalClassPropertyMapImpl(m, model)))
                .collect(Collectors.toList());
        return new ClassPropertyMap() {
            @Override
            public Stream<Property> properties(OntCE ce) {
                return maps.stream().flatMap(m -> m.properties(ce)).distinct();
            }

            @Override
            public Stream<OntCE> classes(OntPE pe) {
                return maps.stream().flatMap(m -> m.classes(pe)).distinct();
            }
        };
    }

    public Stream<OntGraphModel> listRelatedModels(OntGraphModel model) {
        return (model instanceof MapModel ? ((MapModel) model).ontologies() : Stream.of(model)).flatMap(Models::flat);
    }

    @Override
    public InferenceEngine getInferenceEngine(MapModel mapping) throws MapJenaException {
        if (MapJenaException.notNull(mapping, "Null mapping").contexts().noneMatch(MapContext::isValid)) {
            throw Exceptions.INFERENCE_NO_CONTEXTS.create()
                    .add(Exceptions.Key.MAPPING, String.valueOf(mapping))
                    .build();
        }
        return new InferenceEngineImpl(mapping, this);
    }

    /**
     * A {@link MapFunction MapFunction} attached to the manager.
     */
    public class FunctionImpl extends MapFunctionImpl {
        private Triple root;
        private Set<FunctionImpl> dependencies;

        public FunctionImpl(org.topbraid.spin.model.Function func) {
            super(func);
        }

        @Override
        public Triple getRootTriple() {
            return root == null ? root = super.getRootTriple() : root;
        }

        @Override
        public boolean isCustom() {
            Triple root = getRootTriple();
            return listAdditionalGraphs().anyMatch(g -> g.contains(root));
        }

        /**
         * Creates a builder.
         *
         * @return {@link FunctionBuilderImpl}
         */
        @Override
        public FunctionBuilderImpl create() {
            return new FunctionBuilderImpl(this) {

                @Override
                public Builder addLiteral(Property predicate, Object value) {
                    // primary graph may contain custom datatypes:
                    return addLiteral(predicate, library.createTypedLiteral(value));
                }
            };
        }

        @Override
        protected ArgImpl newArg(Argument arg, String name) {
            return new ArgImpl(arg, name) {

                @Override
                public String toString() {
                    return toString(prefixes);
                }
            };
        }

        @Override
        public boolean isUserDefined() {
            return getLibrary().getBaseGraph().contains(getRootTriple());
        }

        @Override
        public String toString() {
            return toString(prefixes);
        }

        /**
         * Answers {@code true} if the specified function is registered (as ARQ or SPARQL) and therefore is executable.
         * Note that it is a recursive method since a function is executable only if all nested functions are executable.
         *
         * @return boolean
         */
        public boolean isExecutable() {
            // SPARQL operators are always executable:
            if (isSparqlOperator()) return true;
            // unregistered functions are not executable:
            if (!arqFactory.getFunctionRegistry().isRegistered(name())) return false;
            // registered, but either no SPARQL body, which means that it has a java ARQ body and therefore executable,
            // or it has a SPARQL body, then it is executable iff all dependency functions are executable:
            return getDependencies().stream().allMatch(FunctionImpl::isExecutable);
        }

        @Override
        public Stream<MapFunction> dependencies() {
            return getDependencies().stream().map(Function.identity());
        }

        /**
         * Gets a Set of all dependencies.
         * It is assumed that list of dependencies cannot be changed.
         *
         * @return Set of {@link FunctionImpl}s, possible empty
         */
        public Set<FunctionImpl> getDependencies() {
            return dependencies != null ? dependencies : (dependencies = listDependencies()
                    .map(r -> MapJenaException.notNull(functions.get(r.getURI()), "Can't find function " + r))
                    .collect(Collectors.toSet()));
        }
    }
}

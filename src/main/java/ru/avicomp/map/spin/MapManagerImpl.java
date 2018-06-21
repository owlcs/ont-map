package ru.avicomp.map.spin;

import org.apache.jena.enhanced.UnsupportedPolymorphismException;
import org.apache.jena.graph.Factory;
import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.*;
import org.apache.jena.shared.PrefixMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.system.ExtraPrefixes;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.*;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;
import ru.avicomp.map.utils.AutoPrefixListener;
import ru.avicomp.map.utils.ClassPropertyMapListener;
import ru.avicomp.map.utils.LocalClassPropertyMapImpl;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.impl.OntGraphModelImpl;
import ru.avicomp.ontapi.jena.impl.UnionModel;
import ru.avicomp.ontapi.jena.impl.conf.OntPersonality;
import ru.avicomp.ontapi.jena.model.*;
import ru.avicomp.ontapi.jena.utils.Graphs;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.*;
import java.util.function.Function;
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

    private final PrefixMapping prefixes;
    private final UnionModel library;
    private final Map<String, FunctionImpl> functions;

    protected final MapARQFactory factory;

    public MapManagerImpl() {
        this.library = createLibraryModel(Factory.createGraphMem());
        this.prefixes = collectPrefixes(SystemModels.graphs().values());
        this.functions = new HashMap<>();
        this.factory = new MapARQFactory();
        SPINRegistry.putAll(factory.getFunctionRegistry(), factory.getPropertyFunctionRegistry());
        SpinModels.listSpinFunctions(library).forEach(this::register);
    }

    /**
     * Registers a spin-function in the manager.
     *
     * @param inModel {@link Resource} with {@code rdf:type = spin:Function}
     * @throws UnsupportedPolymorphismException - wrong resource or personality
     * @see SpinModelConfig#LIB_PERSONALITY
     */
    protected void register(Resource inModel) throws UnsupportedPolymorphismException {
        org.topbraid.spin.model.Function f = inModel.as(org.topbraid.spin.model.Function.class);
        ExtraPrefixes.add(f);
        functions.put(f.getURI(), new FunctionImpl(f));
        if (f.isMagicProperty()) {
            factory.registerProperty(f);
        } else {
            factory.registerFunction(f);
        }
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
        // root graph for user defined stuff
        UnionGraph res = new UnionGraph(graph);
        // avc.spin, avc.lib, avc.fn amd avc.math additions:
        additionLibraryGraphs().forEach(res::addGraph);
        // topbraid spinmapl (the top graph of the spin family):
        res.addGraph(getSpinLibraryGraph());
        return new OntGraphModelImpl(res, SpinModelConfig.ONT_LIB_PERSONALITY);
    }

    /**
     * Gets spin library union graph without ONT-MAP additions.
     *
     * @return {@link UnionGraph} with spin-family hierarchy of unmodifiable graphs
     */
    public static UnionGraph getSpinLibraryGraph() {
        return Graphs.toUnion(SystemModels.get(SystemModels.Resources.SPINMAPL), SystemModels.graphs().values());
    }

    /**
     * Returns a set of addition library graphs.
     *
     * @return Stream of unmodifiable {@link Graph}s
     * @see ru.avicomp.map.spin.vocabulary.AVC
     * @see ru.avicomp.map.spin.vocabulary.FN
     * @see ru.avicomp.map.spin.vocabulary.MATH
     */
    public static Stream<Graph> additionLibraryGraphs() {
        return Arrays.stream(SystemModels.Resources.values())
                .filter(s -> s.name().startsWith("AVC"))
                .map(SystemModels::get);
    }

    /**
     * Collects a prefixes library from a collection of graphs.
     * TODO: move to ONT-API ?
     *
     * @param graphs {@link Iterable} a collection of graphs
     * @return unmodifiable {@link PrefixMapping prefix mapping}
     */
    public static PrefixMapping collectPrefixes(Iterable<Graph> graphs) {
        PrefixMapping res = PrefixMapping.Factory.create();
        graphs.forEach(g -> res.setNsPrefixes(g.getPrefixMapping()));
        return res.lock();
    }

    /**
     * Answers iff target individuals must be {@code owl:NamedIndividuals} also.
     *
     * @return boolean
     */
    public boolean generateNamedIndividuals() {
        return true;
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
        return functions.values().stream()
                // skip private:
                .filter(f -> !f.isPrivate())
                // skip abstract:
                .filter(f -> !f.isAbstract())
                // skip deprecated:
                .filter(f -> !f.isDeprecated())
                // skip hidden:
                .filter(f -> !f.isHidden())
                // skip properties:
                .filter(f -> !f.isMagicProperty())
                // only registered:
                .filter(FunctionImpl::isExecutable)
                .map(Function.identity());
    }

    /**
     * Answers iff the specified function is registered (as ARQ or SPARQL) and therefore is executable.
     * Note that it is a recursive method.
     *
     * @param function {@link MapFunctionImpl}
     * @return boolean
     */
    protected boolean isExecutable(MapFunctionImpl function) {
        Resource func = function.asResource();
        // SPIN-indicator for SPARQL operator:
        if (func.hasProperty(SPIN.symbol)) return true;
        String uri = function.name();
        // not registered:
        if (!factory.getFunctionRegistry().isRegistered(uri)) return false;
        // registered, but no SPARQL body -> has a java ARQ body -> allow:
        if (!func.hasProperty(SPIN.body)) return true;
        // registered (has SPARQL body) but may depend on some other unregistered functions:
        Resource body = func.getRequiredProperty(SPIN.body).getObject().asResource();
        return MapModelImpl.listProperties(body)
                .filter(s -> RDF.type.equals(s.getPredicate()))
                .map(Statement::getObject)
                .filter(RDFNode::isURIResource)
                .map(RDFNode::asResource)
                .map(Resource::getURI)
                .distinct()
                .map(functions::get)
                .filter(Objects::nonNull)
                .allMatch(f -> !uri.equals(f.name()) && isExecutable(f));
    }

    /**
     * Gets all available functions as unmodifiable Map.
     *
     * @return {@link Map} with IRIs as keys and {@link FunctionImpl} as values
     */
    public Map<String, FunctionImpl> getFunctionsMap() {
        return Collections.unmodifiableMap(functions);
    }

    @Override
    public FunctionImpl getFunction(String name) throws MapJenaException {
        return MapJenaException.notNull(functions.get(name), "Can't find function " + name);
    }

    @Override
    public PrefixMapping prefixes() {
        return prefixes;
    }

    /**
     * Returns the library graph.
     * Consists from:
     * <ul>
     * <li>avc.spin.ttl - base definitions and customization</li>
     * <li>avc.lib.ttl - additional AVC functions</li>
     * <li>avc.math.ttl - functions from xquery/math</li>
     * <li>avc.fn.ttl - functions from xquery which were forgotten in http://topbraid.org/functions-afn</li>
     * <li>spinmapl.spin.ttl - a top of standard (composer's) spin-family</li>
     * </ul>
     *
     * @return {@link UnionModel}
     */
    public UnionModel getLibrary() {
        return library;
    }

    /**
     * Gets a library graph without any inclusion (i.e. without avc additions).
     *
     * @return {@link UnionGraph}
     * @throws IllegalStateException wrong state
     */
    public Graph getMapLibraryGraph() throws IllegalStateException {
        return getSpinLibraryGraph();
    }

    /**
     * Creates a fresh (SPIN-) mapping model in form of a rdf-ontology.
     * Uses {@link OntPersonality ont-personality} in order to reuse some owl2 resources
     * such as {@link ru.avicomp.ontapi.jena.model.OntID ontology id},
     * {@link ru.avicomp.ontapi.jena.model.OntCE ont class expression},
     * {@link ru.avicomp.ontapi.jena.model.OntPE ont property expression}.
     *
     * @return {@link MapModel mapping model}, which also ia anonymous owl ontology
     */
    @Override
    public MapModelImpl createMapModel() {
        return makeMapModel(Factory.createGraphMem(), SpinModelConfig.ONT_PERSONALITY);
    }

    /**
     * Creates a mapping model around the specified (fresh) graph.
     * It is auxiliary method, not for public usage.
     * Note that the graph will be modified:
     * the method adds the statement {@code spinmap:rule spin:rulePropertyMaxIterationCount "2"^^xsd:int},
     * which indicates to Topbraid Composer Engine that inference is need to be run two times.
     * This is to be sure that all possible rule logical dependencies (e.g. in chained contexts) will be satisfied.
     * Notice that ONT-MAP Inference Engine does not use that setting and inference will be run only once.
     * Also this method ensures that the given graph contains http://topbraid.org/spin/spinmapl in import declarations.
     *
     * @param base        {@link Graph} base graph
     * @param personality {@link OntPersonality}
     * @return {@link MapModelImpl}
     */
    public MapModelImpl makeMapModel(Graph base, OntPersonality personality) {
        UnionGraph union = new UnionGraph(Graphs.getBase(base));
        MapModelImpl res = new MapModelImpl(union, personality, this);
        // do not add avc.*.ttl addition to the final graph
        Graph lib = getMapLibraryGraph();
        union.addGraph(lib);
        AutoPrefixListener.addAutoPrefixListener(union, prefixes());
        // Set spin:rulePropertyMaxIterationCount to 2
        // to be sure that all the rules have been processed through TopBraid Composer Inference as expected,
        // even if they depend on other rules.
        // Note: this parameter is unused in our ONT-Map InferenceEngine:
        // it provides own rules order, and each rule is processed only once.
        res.add(SPINMAP.rule, SPIN.rulePropertyMaxIterationCount, res.createTypedLiteral(2));
        // add spinmapl (a top of library) to owl:imports:
        res.getID().addImport(Graphs.getURI(lib));
        return res;
    }

    /**
     * Tests weather the given OWL2 model is also a mapping model.
     * For the sake of simplicity assumes that map-model must have &lt;http://topbraid.org/spin/spinmapl&gt; in the imports.
     * In general case it is not right, but both Topbraid Composer and ONT-MAP provides such mappings by default.
     *
     * @param m {@link OntGraphModel}
     * @return true if it is also {@link MapModelImpl}
     */
    @Override
    public boolean isMapModel(OntGraphModel m) {
        if (m instanceof MapModelImpl) return true;
        return m.getID().imports().anyMatch(SPINMAPL.BASE_URI::equals);
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
        if (!isMapModel(m)) throw new MapJenaException("<" + m.getID() + "> is not a mapping model");
        if (m instanceof MapModelImpl && this.equals(((MapModelImpl) m).getManager())) return (MapModelImpl) m;
        UnionGraph union = new UnionGraph(m.getBaseGraph());
        m.imports()
                .filter(i -> !SPINMAPL.BASE_URI.equals(i.getID().getURI()))
                .map(ModelGraphInterface::getGraph)
                .forEach(union::addGraph);
        union.addGraph(getMapLibraryGraph());
        MapModelImpl res = new MapModelImpl(union, personality, this);
        Model full = SpinModelConfig.createSpinModel(res.getGraph());
        SpinModels.listSpinFunctions(m.getBaseModel())
                .map(r -> r.inModel(full))
                .forEach(r -> {
                    if (LOGGER.isDebugEnabled())
                        LOGGER.debug("Add function <{}>.", r);
                    register(r);
                });
        return res;
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
     * The result is cached and is placed in the specified model, in a listener attached to the top-level {@link UnionGraph graph}.
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
        Stream<OntGraphModel> models = (model instanceof MapModel ? ((MapModel) model).ontologies() : Stream.of(model))
                .flatMap(MapManagerImpl::flat);
        List<ClassPropertyMap> maps = models
                .map(m -> ClassPropertyMapListener.getCachedClassPropertyMap((UnionGraph) m.getGraph(), () -> new LocalClassPropertyMapImpl(m)))
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

    /**
     * Lists all associated (with {@code owl:imports}) models including specified as a flat stream.
     * TODO: move to ONT-API
     *
     * @param m {@link OntGraphModel}
     * @return Stream of models
     * @see Graphs#flat(Graph)
     */
    public static Stream<OntGraphModel> flat(OntGraphModel m) {
        return Stream.concat(Stream.of(m), m.imports().map(MapManagerImpl::flat).flatMap(Function.identity()));
    }

    @Override
    public InferenceEngine getInferenceEngine() {
        return new InferenceEngineImpl(this);
    }

    /**
     * A {@link MapFunction MapFunction} attached to the manager.
     */
    public class FunctionImpl extends MapFunctionImpl {
        private Boolean canExec;

        public FunctionImpl(org.topbraid.spin.model.Function func) {
            super(func);
        }

        @Override
        public String toString() {
            return toString(prefixes);
        }

        public boolean isExecutable() {
            if (canExec != null) return canExec;
            return canExec = MapManagerImpl.this.isExecutable(this);
        }
    }
}

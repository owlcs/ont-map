package ru.avicomp.map.spin;

import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.graph.Factory;
import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.function.FunctionFactory;
import org.apache.jena.sparql.function.FunctionRegistry;
import org.apache.jena.sparql.pfunction.PropertyFunctionFactory;
import org.apache.jena.sparql.pfunction.PropertyFunctionRegistry;
import org.topbraid.spin.arq.SPINARQPFunction;
import org.topbraid.spin.arq.SPINARQPFunctionFactory;
import org.topbraid.spin.arq.SPINFunctionDrivers;
import org.topbraid.spin.arq.SPINFunctionFactory;
import org.topbraid.spin.system.ExtraPrefixes;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.*;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.utils.AutoPrefixListener;
import ru.avicomp.map.utils.ClassPropertyMapListener;
import ru.avicomp.map.utils.LocalClassPropertyMapImpl;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.impl.OntGraphModelImpl;
import ru.avicomp.ontapi.jena.impl.UnionModel;
import ru.avicomp.ontapi.jena.impl.conf.OntModelConfig;
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

    // OWL2 personality (lax version)
    public static final OntPersonality ONT_PERSONALITY = OntModelConfig.ONT_PERSONALITY_LAX.copy();
    // OWL2 + SPIN personality
    public static final OntPersonality LIB_PERSONALITY = OntModelConfig.ONT_PERSONALITY_LAX.copy().add(SpinModelConfig.LIB_PERSONALITY);

    private final PrefixMapping prefixLibrary;
    private final UnionModel graphLibrary;
    private final Map<String, FunctionImpl> mapFunctions;

    protected final FunctionRegistry functionRegistry = FunctionRegistry.get();
    protected final PropertyFunctionRegistry propertyFunctionRegistry = PropertyFunctionRegistry.get();

    protected final TypeMapper types = TypeMapper.getInstance();

    public MapManagerImpl() {
        this.graphLibrary = createLibraryModel(Factory.createGraphMem());
        this.prefixLibrary = collectPrefixes(SystemModels.graphs().values());
        this.mapFunctions = new HashMap<>();
        this.prefixLibrary.getNsPrefixMap().forEach(ExtraPrefixes::add);
        SPINRegistry.init(this.functionRegistry, this.propertyFunctionRegistry);
        SpinModels.listSpinFunctions(graphLibrary).forEach(f -> {
            register(functionRegistry, f);
            if (f.isMagicProperty()) {
                register(propertyFunctionRegistry, f);
            }
            mapFunctions.put(f.getURI(), new FunctionImpl(f));
        });
    }

    /**
     * Registers an ARQ function.
     * If the provided Function has an executable body (spin:body), then registers an ARQ function for it with the current FunctionRegistry.
     * If there is an existing function with the same URI already registered, then it will only be replaced if it is also a SPINARQFunction.
     *
     * @param reg  {@link FunctionRegistry}
     * @param func the function to register
     * @see org.topbraid.spin.system.SPINModuleRegistry#registerARQFunction(org.topbraid.spin.model.Function)
     */
    protected static void register(FunctionRegistry reg, org.topbraid.spin.model.Function func) {
        FunctionFactory old = reg.get(func.getURI());
        // Never overwrite native Java functions
        if (old != null && !(old instanceof SPINFunctionFactory)) {
            return;
        }
        SPINFunctionFactory arq = SPINFunctionDrivers.get().create(func);
        if (arq != null) {
            reg.put(func.getURI(), arq);
        }
    }


    /**
     * Registers an ARQ property function.
     * If the provided Function has an executable body (spin:body), then registers an ARQ function for it with the current FunctionRegistry.
     * If there is an existing function with the same URI already registered, then it will only be replaced if it is also a SPINARQPFunction.
     *
     * @param reg  {@link PropertyFunctionRegistry}
     * @param func the function to register
     * @see org.topbraid.spin.system.SPINModuleRegistry#registerARQPFunction(org.topbraid.spin.model.Function)
     */
    protected static void register(PropertyFunctionRegistry reg, org.topbraid.spin.model.Function func) {
        if (!func.hasProperty(SPIN.body)) {
            return;
        }
        PropertyFunctionFactory old = reg.get(func.getURI());
        if (old == null || old instanceof SPINARQPFunction) {
            SPINARQPFunction arq = SPINARQPFunctionFactory.get().create(func);
            reg.put(func.getURI(), arq);
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
     * @see MapManagerImpl#LIB_PERSONALITY
     */
    public static UnionModel createLibraryModel(Graph graph) {
        // root graph for user defined stuff
        UnionGraph res = new UnionGraph(graph);
        // avc.spin, avc.lib, avc.fn amd avc.math additions:
        additionLibraryGraphs().forEach(res::addGraph);
        // topbraid spinmapl (the top graph of the spin family):
        res.addGraph(getSpinLibraryGraph());
        return new OntGraphModelImpl(res, LIB_PERSONALITY);
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
     * Lists all common registered spin functions which are not private, abstract, deprecated or hidden
     * (the last property is calculated using info provided by avc supplement graph).
     * Spin templates are not included also.
     *
     * @return Stream of {@link MapFunction}s.
     */
    @Override
    public Stream<MapFunction> functions() {
        return mapFunctions.values().stream()
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
                .filter(FunctionImpl::isRegistered)
                .map(Function.identity());
    }

    /**
     * Answers iff the specified function can be used by API.
     *
     * @param function {@link FunctionImpl}
     * @return boolean
     */
    protected boolean isRegistered(MapFunctionImpl function) {
        Resource func = function.asResource();
        // SPIN-indicator for SPARQL operator:
        if (func.hasProperty(SPIN.symbol)) return true;
        String uri = function.name();
        if (!functionRegistry.isRegistered(uri)) return false;
        // registered, but no body -> has a java ARQ body, allow
        if (!func.hasProperty(SPIN.body)) return true;
        // it can be registered but depend on some other unregistered function
        Resource body = func.getRequiredProperty(SPIN.body).getObject().asResource();
        return MapModelImpl.listProperties(body)
                .filter(s -> RDF.type.equals(s.getPredicate()))
                .map(Statement::getObject)
                .filter(RDFNode::isURIResource)
                .map(RDFNode::asResource)
                .map(Resource::getURI)
                .distinct()
                .map(mapFunctions::get)
                .filter(Objects::nonNull)
                .allMatch(f -> !uri.equals(f.name()) && MapManagerImpl.this.isRegistered(f));
    }

    /**
     * Gets all available functions as Map.
     *
     * @return {@link Map} with IRIs as keys and {@link FunctionImpl} as values
     */
    public Map<String, FunctionImpl> getFunctionsMap() {
        return Collections.unmodifiableMap(mapFunctions);
    }

    @Override
    public FunctionImpl getFunction(String name) throws MapJenaException {
        return MapJenaException.notNull(mapFunctions.get(name), "Can't find function " + name);
    }

    @Override
    public PrefixMapping prefixes() {
        return prefixLibrary;
    }

    /**
     * Returns the library graph.
     * Consists from:
     * <ul>
     * <li>avc.spin.ttl - base definitions and customization</li>
     * <li>avc.lib.ttl - additional AVC functions</li>
     * <li>avc.math.ttl - functions from xpath/math</li>
     * <li>avc.fn.ttl - functions from xpath which were forgotten in http://topbraid.org/functions-afn</li>
     * <li>spinmapl.spin.ttl - standard (composer) spin-family</li>
     * </ul>
     *
     * @return {@link UnionModel}
     */
    public UnionModel getLibrary() {
        return graphLibrary;
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
     * Creates a (SPIN-) mapping model in form of a rdf-ontology.
     * Uses {@link OntPersonality ont-personality} in order to reuse some owl2 resources
     * such as {@link ru.avicomp.ontapi.jena.model.OntID ontology id},
     * {@link ru.avicomp.ontapi.jena.model.OntCE ont class expression},
     * {@link ru.avicomp.ontapi.jena.model.OntPE ont property expression}.
     *
     * @return {@link MapModel mapping model}, which also ia anonymous owl ontology
     */
    @Override
    public MapModelImpl createMapModel() {
        return createMapModel(Factory.createGraphMem(), ONT_PERSONALITY);
    }

    /**
     * Creates a fresh mapping model for the specified graph.
     * Note: it adds {@code spinmap:rule spin:rulePropertyMaxIterationCount "2"^^xsd:int} statement to the model,
     * this is just in case only for Composer Inference Engine, ONT-Map Inference Engine does not use that setting.
     *
     * @param base           {@link Graph} base graph
     * @param owlPersonality {@link OntPersonality}
     * @return {@link MapModelImpl}
     */
    protected MapModelImpl createMapModel(Graph base, OntPersonality owlPersonality) {
        UnionGraph g = new UnionGraph(Graphs.getBase(base));
        MapModelImpl res = new MapModelImpl(g, owlPersonality, this);
        // do not add avc.*.ttl addition to the final graph
        Graph map = getMapLibraryGraph();
        g.addGraph(map);
        AutoPrefixListener.addAutoPrefixListener(g, prefixes());
        // Set spin:rulePropertyMaxIterationCount to 2
        // to be sure that all the rules have been processed through TopBraid Composer Inference as expected,
        // even if they depend on other rules.
        // Note: this parameter is unused in our ONT-Map InferenceEngine:
        // it provides own rules order, and each rule is processed only once.
        res.add(SPINMAP.rule, SPIN.rulePropertyMaxIterationCount, res.createTypedLiteral(2));
        // add spinmapl (a top of library) to owl:imports:
        res.getID().addImport(Graphs.getURI(map));
        return res;
    }

    /**
     * Returns all numeric datatypes ({@code rdfs:Datatype}) defined in avc.spin.ttl.
     *
     * @return Stream of all number datatypes
     * @see AVC#numeric
     * @see <a href='https://www.w3.org/TR/sparql11-query/#operandDataTypes'>SPARQL Operand Data Types</a>
     */
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
     * Lists all associated (from {@code owl:imports}) models including specified as a flat stream.
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
        private Boolean registered;

        public FunctionImpl(org.topbraid.spin.model.Function func) {
            super(func);
        }

        @Override
        public String toString() {
            return toString(prefixLibrary);
        }

        public boolean isRegistered() {
            if (registered != null) return registered;
            return registered = MapManagerImpl.this.isRegistered(this);
        }
    }
}

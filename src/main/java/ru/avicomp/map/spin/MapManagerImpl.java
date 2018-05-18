package ru.avicomp.map.spin;

import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.graph.Factory;
import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.shared.PrefixMapping;
import org.topbraid.spin.system.SPINModuleRegistry;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.*;
import ru.avicomp.map.utils.AutoPrefixListener;
import ru.avicomp.map.utils.ClassPropertyMapImpl;
import ru.avicomp.map.utils.ClassPropertyMapListener;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.impl.conf.OntModelConfig;
import ru.avicomp.ontapi.jena.impl.conf.OntPersonality;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.utils.Graphs;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.Map;
import java.util.Objects;
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

    private final PrefixMapping prefixLibrary;
    private final Model graphLibrary;
    private final Map<String, MapFunctionImpl> mapFunctions;
    public static final OntPersonality ONT_PERSONALITY = OntModelConfig.ONT_PERSONALITY_LAX.copy();

    public MapManagerImpl() {
        this.graphLibrary = createLibraryModel();
        this.prefixLibrary = collectPrefixes(SystemModels.graphs().values());
        registerALL(graphLibrary);
        this.mapFunctions = listSpinFunctions(graphLibrary)
                .map(f -> makeFunction(f, prefixLibrary))
                .collect(Collectors.toMap(MapFunction::name, Function.identity()));
    }

    public static void registerALL(Model library) {
        SPINRegistry.initSPIN();
        SPINRegistry.initSPIF();
        SPINModuleRegistry.get().registerAll(library, null);
    }

    private static Model createLibraryModel() {
        UnionGraph map = getSpinLibraryGraph();
        // note: this graph is not included to the owl:imports
        UnionGraph avc = new UnionGraph(getInclusionGraph());
        avc.addGraph(map);
        return SpinModelConfig.createSpinModel(avc);
    }

    /**
     * Gets graph for avc.spin.ttl
     *
     * @return unmodified {@link Graph}
     */
    public static Graph getInclusionGraph() {
        return SystemModels.graphs().get(SystemModels.Resources.AVC.getURI());
    }

    /**
     * Gets spin library union graph without addition
     *
     * @return {@link UnionGraph}
     */
    public static UnionGraph getSpinLibraryGraph() {
        return Graphs.toUnion(SystemModels.graphs().get(SystemModels.Resources.SPINMAPL.getURI()), SystemModels.graphs().values());
    }

    /**
     * Collects a prefixes library from a collection of graphs.
     * todo: move to ONT-API ?
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
     * Lists all spin-api functions.
     * Auxiliary method.
     *
     * @param model {@link Model} with spin-personalities
     * @return Stream of {@link org.topbraid.spin.model.Function topbraid spin function}s.
     */
    public static Stream<org.topbraid.spin.model.Function> listSpinFunctions(Model model) {
        return Iter.asStream(model.listSubjectsWithProperty(RDF.type))
                .filter(s -> s.canAs(org.topbraid.spin.model.Function.class) || s.hasProperty(RDF.type, SPINMAP.TargetFunction))
                .map(s -> s.as(org.topbraid.spin.model.Function.class));
    }

    private static MapFunctionImpl makeFunction(org.topbraid.spin.model.Function func, PrefixMapping pm) {
        return new MapFunctionImpl(func) {

            @Override
            public String toString() {
                return toString(pm);
            }
        };
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
     * Lists all spin functions with exclusion private, abstract, deprecated and hidden
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
                // only registered:
                .filter(this::isRegistered)
                .map(Function.identity());
    }

    /**
     * Answers iff the specified function can be used by API.
     *
     * @param function {@link MapFunctionImpl}
     * @return boolean
     */
    public boolean isRegistered(MapFunctionImpl function) {
        Resource func = function.asResource();
        // SPIN-indicator for SPARQL operator:
        if (func.hasProperty(SPIN.symbol)) return true;
        String uri = function.name();
        if (!SPINRegistry.functionRegistry.isRegistered(uri)) return false;
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

    @Override
    public MapFunction getFunction(String name) throws MapJenaException {
        return MapJenaException.notNull(mapFunctions.get(name), "Can't find function " + name);
    }

    @Override
    public PrefixMapping prefixes() {
        return prefixLibrary;
    }

    public Model library() {
        return graphLibrary;
    }

    /**
     * Gets a library graph without any inclusion (i.e. without avc addition).
     *
     * @return {@link UnionGraph}
     * @throws IllegalStateException wrong state
     */
    public Graph getMapLibraryGraph() throws IllegalStateException {
        return ((UnionGraph) graphLibrary.getGraph()).getUnderlying().graphs().findFirst().orElseThrow(IllegalStateException::new);
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
     * @param base           {@link Graph}
     * @param owlPersonality {@link OntPersonality}
     * @return {@link MapModelImpl}
     * @see InferenceEngineImpl#getMapComparator(Model)
     */
    public MapModelImpl createMapModel(Graph base, OntPersonality owlPersonality) {
        UnionGraph g = new UnionGraph(base);
        MapModelImpl res = new MapModelImpl(g, owlPersonality, this);
        // do not add avc.spin.ttl addition to the final graph
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
     * Note: this method is used during validation of input arguments,
     * although SPIN-MAP API allows perform mapping even for properties which is not belonged to the context class.
     *
     * @param model {@link OntGraphModel OWL model}
     * @return {@link ClassPropertyMap mapping}
     */
    @Override
    public ClassPropertyMap getClassProperties(OntGraphModel model) {
        return ClassPropertyMapListener.getCachedClassPropertyMap((UnionGraph) model.getGraph(), ClassPropertyMapImpl::new);
    }

    @Override
    public InferenceEngine getInferenceEngine() {
        return new InferenceEngineImpl(this);
    }

}

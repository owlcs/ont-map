package ru.avicomp.map.spin;

import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.enhanced.BuiltinPersonalities;
import org.apache.jena.graph.Factory;
import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.impl.ModelCom;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.RDF;
import org.topbraid.spin.inference.SPINInferences;
import org.topbraid.spin.system.SPINModuleRegistry;
import ru.avicomp.map.*;
import ru.avicomp.map.spin.model.SpinTargetFunction;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.utils.Graphs;
import ru.avicomp.ontapi.jena.vocabulary.OWL;

import java.util.Map;
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
    private final Map<String, MapFunction> mapFunctions;

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

    public static Graph getInclusionGraph() {
        return SystemModels.graphs().get(SystemModels.Resources.AVC.getURI());
    }

    public static UnionGraph getSpinLibraryGraph() {
        return Graphs.toUnion(SystemModels.graphs().get(SystemModels.Resources.SPINMAPL.getURI()), SystemModels.graphs().values());
    }

    /**
     * Collects a prefixes library from a collection of graphs.
     * todo: move to Graphs ont-api utils?
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
     * Lists all spin functions with exclusion private, abstract, deprecated and hidden
     * (the last one using info from avc supplement graph).
     * Spin templates are not included also.
     * Auxiliary method.
     *
     * @param model {@link Model} with spin-personalities
     * @return Stream of {@link org.topbraid.spin.model.Function topbraid spin function}s.
     */
    public static Stream<org.topbraid.spin.model.Function> listSpinFunctions(Model model) {
        return Iter.asStream(model.listSubjectsWithProperty(RDF.type))
                .filter(s -> s.canAs(org.topbraid.spin.model.Function.class) || s.canAs(SpinTargetFunction.class))
                .map(s -> s.as(org.topbraid.spin.model.Function.class))
                // skip private:
                .filter(f -> !f.isPrivate())
                // skip abstract:
                .filter(f -> !f.isAbstract())
                // skip deprecated:
                .filter(f -> !f.hasProperty(RDF.type, OWL.DeprecatedClass))
                // skip hidden:
                .filter(f -> !f.hasProperty(AVC.hidden));
    }

    private static MapFunction makeFunction(org.topbraid.spin.model.Function func, PrefixMapping pm) {
        return new MapFunctionImpl(func) {

            @Override
            public String toString() {
                return toString(pm);
            }
        };
    }

    @Override
    public Stream<MapFunction> functions() {
        return mapFunctions.values().stream();
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

    @Override
    public MapFunction getFunction(String name) throws MapJenaException {
        return MapJenaException.notNull(mapFunctions.get(name), "Can't find function " + name);
    }

    @Override
    public MapModelImpl createModel() {
        UnionGraph g = new UnionGraph(Factory.createGraphMem());
        MapModelImpl res = new MapModelImpl(g, SpinModelConfig.MAP_PERSONALITY);
        Graph map = getMapLibraryGraph();
        g.addGraph(map);
        configurePrefixes(g);
        // add spinmapl (a root of library) to owl:imports:
        res.setID(null).addImport(Graphs.getURI(map));
        return res;
    }

    /**
     * Configures prefixes for a graph.
     * Protected, to have a possibility to override
     *
     * @param g {@link Graph}
     */
    protected void configurePrefixes(Graph g) {
        g.getEventManager().register(new PrefixedGraphListener(g.getPrefixMapping(), getNodePrefixMapper()));
    }

    /**
     * Returns a graph prefix manager.
     *
     * @return {@link NodePrefixMapper}
     */
    public NodePrefixMapper getNodePrefixMapper() {
        return new NodePrefixMapper(prefixes());
    }

    /**
     * Note: this method is not used during validation of input arguments,
     * since SPIN-MAP API allows perform mapping even for properties which is not belonged to the context class.
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

    public static class InferenceEngineImpl implements InferenceEngine {
        static {
            // Warning: Jena stupidly allows to modify global personality (org.apache.jena.enhanced.BuiltinPersonalities#model),
            // what does SPIN API, which, also, implicitly requires that patched version everywhere.
            // It may be dangerous, increases the system load and may impact other jena-based tools.
            // but I don't think there is an easy good workaround, so it's better to put up with that modifying.
            SpinModelConfig.init(BuiltinPersonalities.model);
        }

        private final MapManagerImpl manager;

        public InferenceEngineImpl(MapManagerImpl manager) {
            this.manager = manager;
        }

        @Override
        public void run(MapModel mapping, Graph source, Graph target) throws MapJenaException {
            // todo: add logging

            // Reassembly a union graph (just in case, it should already contain everything needed):
            UnionGraph union = new UnionGraph(Factory.createGraphMem());
            // pass prefixes:
            union.getPrefixMapping().setNsPrefixes(mapping.getGraph().getPrefixMapping());
            // add everything from mapping:
            Graphs.flat(mapping.getGraph()).forEach(union::addGraph);
            // add everything from source:
            Graphs.flat(source).forEach(union::addGraph);
            // all from library with except of avc (also, just in case):
            Graphs.flat(manager.getMapLibraryGraph()).forEach(union::addGraph);

            Model s = SpinModelConfig.createSpinModel(union);
            Model t = new ModelCom(target);
            SPINInferences.run(s, t, null, null, false, null);
        }
    }

}

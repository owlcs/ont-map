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
import ru.avicomp.map.ClassPropertyMap;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapJenaException;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.spin.model.MapTargetFunction;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.utils.Graphs;
import ru.avicomp.ontapi.jena.vocabulary.OWL;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A spin based implementation of {@link MapManager}.
 * <p>
 * Created by @szuev on 06.04.2018.
 */
public class MapManagerImpl implements MapManager {

    private final Model library;
    private Map<String, MapFunction> mapFunctions;

    public MapManagerImpl() {
        this.library = createLibraryModel(true);
        registerALL(library);
        this.mapFunctions = loadFunctions(library);
    }

    public static void registerALL(Model library) {
        SPINRegistry.initSPIN();
        SPINRegistry.initSPIF();
        SPINModuleRegistry.get().registerAll(library, null);
    }

    public static Model createLibraryModel(boolean withInclusion) {
        UnionGraph map = Graphs.toUnion(SystemModels.graphs().get(SystemModels.Resources.SPINMAPL.getURI()), SystemModels.graphs().values());
        if (withInclusion) {
            // note: this graph is not included to the owl:imports
            UnionGraph avc = new UnionGraph(getInclusionGraph());
            avc.addGraph(map);
            map = avc;
        }
        return SpinModelConfig.createSpinModel(map);
    }

    private static Graph getInclusionGraph() {
        return SystemModels.graphs().get(SystemModels.Resources.AVC.getURI());
    }

    /**
     * Creates a class-properties mapping with cache which is attached to the specified graph.
     *
     * @param g         {@link UnionGraph} to attache listener
     * @param withCache boolean if false just return a base no-cache implementation, which will collect mapping every time on calling
     * @return {@link ClassPropertyMap} object, not null.
     */
    public static ClassPropertyMap createClassPropertyMap(UnionGraph g, boolean withCache) {
        ClassPropertyMap noCache = new ClassPropertyMapImpl();
        if (!withCache)
            return noCache;
        UnionGraph.OntEventManager manager = g.getEventManager();
        return manager.listeners()
                .filter(l -> ClassPropertyMapListener.class.equals(l.getClass()))
                .map(ClassPropertyMapListener.class::cast)
                .findFirst()
                .orElseGet(() -> {
                    ClassPropertyMapListener res = new ClassPropertyMapListener(noCache);
                    manager.register(res);
                    return res;
                }).get();
    }

    public static Map<String, MapFunction> loadFunctions(Model model) {
        return Iter.asStream(model.listSubjectsWithProperty(RDF.type))
                .filter(s -> s.canAs(org.topbraid.spin.model.Function.class) || s.canAs(MapTargetFunction.class))
                .map(s -> s.as(org.topbraid.spin.model.Function.class))
                // skip private:
                .filter(f -> !f.isPrivate())
                // skip abstract:
                .filter(f -> !f.isAbstract())
                // skip deprecated:
                .filter(f -> !f.hasProperty(RDF.type, OWL.DeprecatedClass))
                // skip hidden:
                .filter(f -> !f.hasProperty(AVC.hidden))
                .map(MapFunctionImpl::new)
                .collect(Collectors.toMap(MapFunctionImpl::name, Function.identity()));
    }

    public Graph getMapLibraryGraph() throws IllegalStateException {
        return ((UnionGraph) library.getGraph()).getUnderlying().graphs().findFirst().orElseThrow(IllegalStateException::new);
    }

    @Override
    public Stream<MapFunction> functions() {
        return mapFunctions.values().stream();
    }

    @Override
    public PrefixMapping prefixes() {
        return NodePrefixMapper.LIBRARY;
    }

    public Model library() {
        return library;
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
        res.setID(null).addImport(Graphs.getURI(map));
        return res;
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
        return createClassPropertyMap((UnionGraph) model.getGraph(), true);
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
        return new NodePrefixMapper();
    }

    @Override
    public InferenceEngine getInferenceEngine() {
        return (mapping, source, target) -> {
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
            Graphs.flat(library.getGraph())
                    .filter(g -> !Objects.equals(g, getInclusionGraph()))
                    .forEach(union::addGraph);
            // a hack.
            // Jena stupidly allows to modify global personality,
            // what does SPIN API, which, also, implicitly requires that patched version everywhere.
            // It may be dangerous and increases the load of the system,
            // so better to reset global personality to its original state after this procedure.
            Map<?, ?> init = SpinModelConfig.getPersonalityMap(BuiltinPersonalities.model);
            try {
                SpinModelConfig.init(BuiltinPersonalities.model);
                Model s = SpinModelConfig.createSpinModel(union);
                Model t = new ModelCom(target);
                SPINInferences.run(s, t, null, null, false, null);
            } finally {
                SpinModelConfig.setPersonalityMap(BuiltinPersonalities.model, init);
            }
        };
    }


}

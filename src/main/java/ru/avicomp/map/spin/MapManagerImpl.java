package ru.avicomp.map.spin;

import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.enhanced.BuiltinPersonalities;
import org.apache.jena.graph.Factory;
import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.impl.ModelCom;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.function.FunctionRegistry;
import org.apache.jena.sparql.pfunction.PropertyFunctionRegistry;
import org.apache.jena.vocabulary.RDF;
import org.topbraid.spin.arq.PropertyChainHelperPFunction;
import org.topbraid.spin.arq.functions.*;
import org.topbraid.spin.inference.SPINInferences;
import org.topbraid.spin.system.SPINModuleRegistry;
import org.topbraid.spin.vocabulary.SPIN;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapJenaException;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.spin.model.MapTargetFunction;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.ontapi.jena.UnionGraph;
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
        // todo: check it and replace with own impl
        FunctionRegistry.get().put(SPIN.ask.getURI(), new AskFunction());
        FunctionRegistry.get().put(SPIN.eval.getURI(), new EvalFunction());
        FunctionRegistry.get().put(SPIN.evalInGraph.getURI(), new EvalInGraphFunction());
        FunctionRegistry.get().put(SPIN.violatesConstraints.getURI(), new ViolatesConstraintsFunction());
        PropertyFunctionRegistry.get().put(SPIN.construct.getURI(), ConstructPFunction.class);
        PropertyFunctionRegistry.get().put(SPIN.constructViolations.getURI(), ConstructViolationsPFunction.class);
        PropertyFunctionRegistry.get().put(SPIN.select.getURI(), SelectPFunction.class);
        PropertyFunctionRegistry.get().put("http://topbraid.org/spin/owlrl#propertyChainHelper", PropertyChainHelperPFunction.class);
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

    public Graph getMapLibraryGraph() throws IllegalStateException {
        return ((UnionGraph) library.getGraph()).getUnderlying().graphs().findFirst().orElseThrow(IllegalStateException::new);
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

    @Override
    public Stream<MapFunction> functions() {
        return mapFunctions.values().stream();
    }

    @Override
    public PrefixMapping prefixes() {
        PrefixMapping res = PrefixMapping.Factory.create();
        SystemModels.graphs().values().forEach(g -> res.setNsPrefixes(g.getPrefixMapping()));
        return res.lock();
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
            // Reassembly a union graph (just in case, it should already contain everything needed):
            UnionGraph union = new UnionGraph(Factory.createGraphMem());
            // all from mapping:
            Graphs.flat(mapping.getGraph()).forEach(union::addGraph);
            // all from source:
            Graphs.flat(source).forEach(union::addGraph);
            // all from library with except of avc (also, just in case):
            Graphs.flat(library.getGraph())
                    .filter(g -> !Objects.equals(g, getInclusionGraph()))
                    .forEach(union::addGraph);
            // a hack.
            // Jena stupidly allows to modify global personality,
            // what does SPIN API, which, also, implicitly requires that patched version everywhere.
            // It may be dangerous and increases the load on the system,
            // so better to reset global personality to its original state after this procedure.
            Map<?, ?> init = SpinModelConfig.getPersonalityMap(BuiltinPersonalities.model);
            try {
                SpinModelConfig.init(BuiltinPersonalities.model);
                SPINInferences.run(SpinModelConfig.createSpinModel(union), new ModelCom(target), null, null, false, null);
            } finally {
                SpinModelConfig.setPersonalityMap(BuiltinPersonalities.model, init);
            }
        };
    }


}
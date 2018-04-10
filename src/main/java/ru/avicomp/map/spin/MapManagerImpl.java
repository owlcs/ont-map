package ru.avicomp.map.spin;

import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.graph.Factory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.impl.ModelCom;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.RDF;
import org.topbraid.spin.inference.SPINInferences;
import org.topbraid.spin.model.Function;
import org.topbraid.spin.system.SPINModuleRegistry;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapJenaException;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.ModelBuilder;
import ru.avicomp.map.spin.model.TargetFunction;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.utils.Graphs;
import ru.avicomp.ontapi.jena.vocabulary.OWL;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A spin based implementation of {@link MapManager}.
 * <p>
 * Created by @szuev on 06.04.2018.
 */
public class MapManagerImpl implements MapManager {

    private final Model library;
    private Map<String, MapFunction> spinFunctions;

    public MapManagerImpl() {
        this.library = createLibraryModel();
        this.spinFunctions = loadFunctions(library);
        registerALL(library);
    }

    private static void registerALL(Model library) {
        // todo: check it
        SPINModuleRegistry.get().init();
        SPINModuleRegistry.get().registerAll(library, null);
    }

    public static Model createLibraryModel() {
        UnionGraph g = Graphs.toUnion(SystemModels.graphs().get(SystemModels.Resources.SPINMAPL.getURI()), SystemModels.graphs().values());
        // note: this graph is not included to the owl:imports
        g.addGraph(SystemModels.graphs().get(SystemModels.Resources.AVC.getURI()));
        return new ModelCom(g, SpinModelConfig.SPIN_PERSONALITY);
    }

    public static Map<String, MapFunction> loadFunctions(Model model) {
        return Iter.asStream(model.listSubjectsWithProperty(RDF.type))
                .filter(s -> s.canAs(Function.class) || s.canAs(TargetFunction.class))
                .map(s -> s.as(Function.class))
                // skip private:
                .filter(f -> !f.isPrivate())
                // skip abstract:
                .filter(f -> !f.isAbstract())
                // skip deprecated:
                .filter(f -> !f.hasProperty(RDF.type, OWL.DeprecatedClass))
                // skip hidden:
                .filter(f -> !f.hasProperty(AVC.hidden))
                .map(MapFunctionImpl::new)
                .collect(Collectors.toMap(MapFunctionImpl::name, java.util.function.Function.identity()));
    }

    @Override
    public Stream<MapFunction> functions() {
        return spinFunctions.values().stream();
    }

    @Override
    public PrefixMapping prefixes() {
        return library;
    }

    public Model library() {
        return library;
    }

    @Override
    public MapFunction getFunction(String name) throws MapJenaException {
        return MapJenaException.notNull(spinFunctions.get(name), "Can't find function " + name);
    }

    @Override
    public ModelBuilder getModelBuilder() {
        return new ModelBuilderImpl(this);
    }

    @Override
    public InferenceEngine getInferenceEngine() {
        return (mapping, source, target) -> {
            UnionGraph g = new UnionGraph(Factory.createGraphMem());
            Graphs.flat(mapping.getGraph()).forEach(g::addGraph);
            g.addGraph(library.getGraph()); // todo: exclude avc?
            SPINInferences.run(new ModelCom(g, SpinModelConfig.SPIN_PERSONALITY), new ModelCom(target), null, null, false, null);
        };
    }

}

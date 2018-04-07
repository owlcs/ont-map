package ru.avicomp.map.spin;

import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.RDF;
import org.topbraid.spin.model.Argument;
import org.topbraid.spin.model.Function;
import org.topbraid.spin.model.Module;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapJenaException;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.spin.model.TargetFunction;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.utils.Graphs;
import ru.avicomp.ontapi.jena.vocabulary.OWL;

import java.util.Map;
import java.util.Objects;
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
    }

    public static Model createLibraryModel() {
        UnionGraph g = Graphs.toUnion(SystemModels.graphs().get(SystemModels.Resources.SPINMAPL.getURI()), SystemModels.graphs().values());
        // note: this graph is not included to the owl:imports
        g.addGraph(SystemModels.graphs().get(SystemModels.Resources.AVC.getURI()));
        return SpinModelConfig.createModel(g);
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

    @Override
    public MapFunction getFunction(String name) throws MapJenaException {
        return MapJenaException.notNull(spinFunctions.get(name), "Can't find function " + name);
    }

    /**
     * A spin based implementation of {@link MapFunction}.
     */
    public static class MapFunctionImpl implements MapFunction {

        public static final String VOID = "void";
        public static final String UNDEFINED = "?";
        private final Module func;

        public MapFunctionImpl(Function func) {
            this.func = Objects.requireNonNull(func, "Null " + Function.class.getName());
        }

        @Override
        public String name() {
            return func.getURI();
        }

        @Override
        public String returnType() {
            Resource r = func instanceof Function ? ((Function) func).getReturnType() : null;
            return r == null ? VOID : r.getURI();
        }

        @Override
        public Stream<Arg> args() {
            return func.getArguments(true).stream().map(ArgImpl::new);
        }

        @Override
        public boolean isTarget() {
            return func.canAs(TargetFunction.class);
        }

        public Model getModel() {
            return func.getModel();
        }

        public String toString(PrefixMapping pm) {
            return String.format("%s [%s](%s)",
                    pm.shortForm(returnType()),
                    pm.shortForm(name()), args()
                            .map(ArgImpl.class::cast)
                            .map(a -> a.toString(pm))
                            .collect(Collectors.joining(", ")));
        }

        @Override
        public String toString() {
            return toString(func.getModel());
        }

        public class ArgImpl implements Arg {
            private final Argument arg;

            public ArgImpl(Argument arg) {
                this.arg = arg;
            }

            @Override
            public String name() {
                return arg.getPredicate().getURI();
            }

            @Override
            public String type() {
                Resource t = arg.getValueType();
                return t == null ? UNDEFINED : t.getURI();
            }

            public String toString(PrefixMapping pm) {
                return String.format("%s=%s", pm.shortForm(name()), pm.shortForm(type()));
            }
        }
    }

}

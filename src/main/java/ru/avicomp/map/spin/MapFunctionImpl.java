package ru.avicomp.map.spin;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.RDFS;
import org.topbraid.spin.model.Argument;
import org.topbraid.spin.model.Function;
import org.topbraid.spin.model.Module;
import ru.avicomp.map.FunctionBuilder;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.spin.model.TargetFunction;
import ru.avicomp.map.utils.Models;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A spin based implementation of {@link MapFunction}.
 * Created by @szuev on 09.04.2018.
 */
public class MapFunctionImpl implements MapFunction {

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

    @Override
    public Builder createFunctionCall() {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public String getComment(String lang) {
        return Models.getLangValue(func, RDFS.comment, lang);
    }

    @Override
    public String getLabel(String lang) {
        return Models.getLangValue(func, RDFS.label, lang);
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

        @Override
        public boolean isOptional() {
            return arg.isOptional();
        }

        @Override
        public String getComment(String lang) {
            return Models.getLangValue(arg, RDFS.comment, lang);
        }

        @Override
        public String getLabel(String lang) {
            return Models.getLangValue(arg, RDFS.label, lang);
        }

        public String toString(PrefixMapping pm) {
            return String.format("%s%s=%s", pm.shortForm(name()), isOptional() ? "(*)" : "", pm.shortForm(type()));
        }
    }
}

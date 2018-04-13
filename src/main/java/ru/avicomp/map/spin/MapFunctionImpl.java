package ru.avicomp.map.spin;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.RDFS;
import org.topbraid.spin.model.Argument;
import org.topbraid.spin.model.Function;
import org.topbraid.spin.model.Module;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.FunctionBuilder;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapJenaException;
import ru.avicomp.map.spin.model.TargetFunction;
import ru.avicomp.map.utils.Models;

import java.util.*;
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
    private List<Arg> arguments;

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

    public List<Arg> getArguments() {
        return arguments == null ? arguments = func.getArguments(true).stream().map(ArgImpl::new).collect(Collectors.toList()) : arguments;
    }

    @Override
    public Stream<Arg> args() {
        return getArguments().stream();
    }

    public Optional<Arg> arg(String predicate) {
        return args().filter(a -> Objects.equals(a.name(), predicate)).findFirst();
    }

    @Override
    public boolean isTarget() {
        return func.canAs(TargetFunction.class);
    }

    @Override
    public FunctionBuilder createFunctionCall() {
        return new BuilderImpl();
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
        public boolean isAssignable() {
            return !isInherit();
        }

        /**
         * Checks if it is a direct argument or it goes from superclass.
         * Direct arguments can be used to make function call, inherited should be ignored.
         *
         * @return false if it is normal argument, i.e. it is ready to use.
         */
        public boolean isInherit() {
            return !func.hasProperty(SPIN.constraint, arg);
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
            return String.format("%s%s=%s", pm.shortForm(name()), info(), pm.shortForm(type()));
        }

        private String info() {
            List<String> res = new ArrayList<>(2);
            if (isOptional()) res.add("*");
            if (isInherit()) res.add("i");
            return res.isEmpty() ? "" : res.stream().collect(Collectors.joining(",", "(", ")"));
        }
    }

    public class BuilderImpl implements FunctionBuilder {
        private final Map<String, String> args = new HashMap<>();

        @Override
        public FunctionBuilder add(Arg arg, String value) {
            if (!arg.isAssignable()) // todo: add strict exception mechanism
                throw new MapJenaException();
            // todo: check type.
            args.put(arg.name(), value);
            return this;
        }

        @Override
        public FunctionBuilder add(Arg arg, FunctionBuilder other) {
            // todo: check type.
            throw new UnsupportedOperationException("TODO");
        }

        @Override
        public MapFunction getFunction() {
            return MapFunctionImpl.this;
        }

        @Override
        public Call build() {
            if (MapFunctionImpl.this.isTarget()) {
                // Most of spin-map target function calls should have spin:_source variable assigned on this argument,
                // although it does not seem it is really needed.
                MapFunctionImpl.this.arg(SPINMAP.source.getURI())
                        .ifPresent(a -> args.put(a.name(), SPINMAP.sourceVariable.getURI()));
            }
            return new CallImpl(this.args);
        }
    }

    public class CallImpl implements Call {
        private final Map<String, String> args;

        private CallImpl(Map<String, String> args) {
            this.args = args;
        }

        @Override
        public MapFunctionImpl getFunction() {
            return MapFunctionImpl.this;
        }

        @Override
        public String getValue(Arg arg) {
            return args.get(arg.name());
        }

        @Override
        public FunctionBuilder asUnmodifiableBuilder() {
            return new FunctionBuilder() {
                @Override
                public FunctionBuilder add(Arg arg, String value) {
                    throw new MapJenaException.Unsupported();
                }

                @Override
                public FunctionBuilder add(Arg arg, FunctionBuilder other) {
                    throw new MapJenaException.Unsupported();
                }

                @Override
                public MapFunction getFunction() {
                    return MapFunctionImpl.this;
                }

                @Override
                public Call build() throws MapJenaException {
                    return CallImpl.this;
                }
            };
        }

    }
}

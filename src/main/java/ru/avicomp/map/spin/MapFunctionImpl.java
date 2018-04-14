package ru.avicomp.map.spin;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.RDFS;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.FunctionBuilder;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapJenaException;
import ru.avicomp.map.spin.model.MapTargetFunction;
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
    private final org.topbraid.spin.model.Module func;
    private List<Arg> arguments;

    public MapFunctionImpl(org.topbraid.spin.model.Function func) {
        this.func = Objects.requireNonNull(func, "Null " + org.topbraid.spin.model.Function.class.getName());
    }

    @Override
    public String name() {
        return func.getURI();
    }

    @Override
    public String returnType() {
        Resource r = func instanceof org.topbraid.spin.model.Function ? ((org.topbraid.spin.model.Function) func).getReturnType() : null;
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
        return func.canAs(MapTargetFunction.class);
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
        return toString(NodePrefixMapper.LIBRARY);
    }

    public class ArgImpl implements Arg {
        private final org.topbraid.spin.model.Argument arg;

        public ArgImpl(org.topbraid.spin.model.Argument arg) {
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
        public String defaultValue() {
            RDFNode r = arg.getDefaultValue();
            return r == null ? null : r.toString();
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
        // either string or builder
        private final Map<String, Object> input = new HashMap<>();

        @Override
        public FunctionBuilder add(String arg, String value) {
            return put(arg, value);
        }

        @Override
        public FunctionBuilder add(String arg, FunctionBuilder other) {
            return put(arg, other);
        }

        private FunctionBuilder put(String predicate, Object val) {
            Arg arg = getFunction().getArg(predicate);
            if (!arg.isAssignable()) // todo: add strict exception mechanism
                throw new MapJenaException();
            if (val == null) {
                throw new MapJenaException("null val");
            }

            if (!(val instanceof String)) {
                if (val instanceof FunctionBuilder) {
                    if (VOID.equals(((FunctionBuilder) val).getFunction().returnType())) {
                        throw new MapJenaException("Void");
                    }
                } else {
                    throw new IllegalStateException("Wrong value: " + val);
                }
            }
            input.put(arg.name(), val);
            return this;
        }

        @Override
        public MapFunction getFunction() {
            return MapFunctionImpl.this;
        }

        @Override
        public Call build() throws MapJenaException {
            Map<String, Object> map = input.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey,
                            e -> e.getValue() instanceof FunctionBuilder ? ((FunctionBuilder) e.getValue()).build() : e.getValue()));
            if (MapFunctionImpl.this.isTarget()) {
                // Most of spin-map target function calls should have spin:_source variable assigned on this argument,
                // although it does not seem it is really needed.
                MapFunctionImpl.this.arg(SPINMAP.source.getURI())
                        .ifPresent(a -> map.put(a.name(), SPINMAP.sourceVariable.getURI()));
            }
            // check all required arguments are assigned
            getFunction().args().forEach(a -> {
                if (map.containsKey(a.name()) || a.isOptional())
                    return;
                String def = a.defaultValue();
                if (def == null) {
                    // todo: exception mechanism
                    throw new MapJenaException();
                }
                // set default:
                map.put(a.name(), def);

            });
            return new CallImpl(map);
        }
    }

    public class CallImpl implements Call {
        // either string or another function calls
        private final Map<String, Object> parameters;

        private CallImpl(Map<String, Object> args) {
            this.parameters = args;
        }

        @Override
        public Map<Arg, Object> asMap() {
            return parameters.entrySet().stream()
                    .collect(Collectors.toMap(e -> getFunction().getArg(e.getKey()), Map.Entry::getValue));
        }


        @Override
        public MapFunctionImpl getFunction() {
            return MapFunctionImpl.this;
        }

        @Override
        public FunctionBuilder asUnmodifiableBuilder() {
            return new FunctionBuilder() {
                @Override
                public FunctionBuilder add(String arg, String value) {
                    throw new MapJenaException.Unsupported();
                }

                @Override
                public FunctionBuilder add(String arg, FunctionBuilder other) {
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

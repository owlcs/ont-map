package ru.avicomp.map.spin;

import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.rdf.model.*;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import org.topbraid.spin.vocabulary.SPL;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapJenaException;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.spin.vocabulary.MATH;
import ru.avicomp.ontapi.jena.utils.Models;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ru.avicomp.map.spin.Exceptions.*;

/**
 * A spin based implementation of {@link MapFunction}.
 * Assumed to be immutable in program life-cycle.
 * Created by @szuev on 09.04.2018.
 */
@SuppressWarnings("WeakerAccess")
public class MapFunctionImpl implements MapFunction {
    public static final String STRING_VALUE_SEPARATOR = "\n";
    private final org.topbraid.spin.model.Module func;
    private List<ArgImpl> arguments;

    public MapFunctionImpl(org.topbraid.spin.model.Function func) {
        this.func = Objects.requireNonNull(func, "Null " + org.topbraid.spin.model.Function.class.getName());
    }

    @Override
    public String name() {
        return func.getURI();
    }

    @Override
    public String type() {
        Resource r = func instanceof org.topbraid.spin.model.Function ? ((org.topbraid.spin.model.Function) func).getReturnType() : null;
        return (r == null ? AVC.undefined : r).getURI();
    }

    public List<ArgImpl> getArguments() {
        return arguments == null ? arguments = func.getArguments(true).stream().map(ArgImpl::new).collect(Collectors.toList()) : arguments;
    }

    public Stream<ArgImpl> listArgs() {
        return getArguments().stream();
    }

    @Override
    public Stream<Arg> args() {
        return listArgs().map(Function.identity());
    }

    public Optional<ArgImpl> arg(String predicate) {
        return listArgs().filter(a -> Objects.equals(a.name(), predicate)).findFirst();
    }

    @Override
    public ArgImpl getArg(String predicate) throws MapJenaException {
        return arg(predicate)
                .orElseThrow(() -> exception(FUNCTION_NONEXISTENT_ARGUMENT).add(Key.ARG, predicate).build());
    }

    @Override
    public boolean isTarget() {
        return func.hasProperty(RDF.type, SPINMAP.TargetFunction);
    }

    @Override
    public boolean isBoolean() {
        return XSD.xboolean.getURI().equals(type());
    }

    /**
     * Answers iff this function is custom, i.e. does not belong to the original spin family.
     * Custom functions must be directly added to the final mapping graph for compatibility with Topbraid Composer.
     *
     * @return boolean
     */
    public boolean isCustom() {
        return Objects.equals(AVC.NS, func.getNameSpace()) || Objects.equals(MATH.NS, func.getNameSpace());
    }

    public boolean isPrivate() {
        return func instanceof org.topbraid.spin.model.Function && ((org.topbraid.spin.model.Function) func).isPrivate();
    }

    public boolean isAbstract() {
        return func.isAbstract();
    }

    public boolean isDeprecated() {
        return func.hasProperty(RDF.type, OWL.DeprecatedClass);
    }

    public boolean isHidden() {
        return func.hasProperty(AVC.hidden);
    }

    /**
     * Returns resource attached to the library model
     *
     * @return {@link Resource}
     */
    public Resource asResource() {
        return func;
    }

    @Override
    public Builder create() {
        return new BuilderImpl();
    }

    @Override
    public String getComment(String lang) {
        return Models.langValues(func, RDFS.comment, lang).collect(Collectors.joining(STRING_VALUE_SEPARATOR));
    }

    @Override
    public String getLabel(String lang) {
        return Models.langValues(func, RDFS.label, lang).collect(Collectors.joining(STRING_VALUE_SEPARATOR));
    }

    public Model getModel() {
        return func.getModel();
    }

    public String toString(PrefixMapping pm) {
        return String.format("%s [%s](%s)",
                pm.shortForm(type()),
                pm.shortForm(name()), listArgs()
                        .map(a -> a.toString(pm))
                        .collect(Collectors.joining(", ")));
    }

    private Exceptions.Builder exception(Exceptions code) {
        return code.create().add(Key.FUNCTION, name());
    }

    public static String argumentToName(org.topbraid.spin.model.Argument arg) {
        Property p = arg.getPredicate();
        return p == null ? arg.toString() : p.getURI();
    }

    public class ArgImpl implements Arg {
        private final org.topbraid.spin.model.Argument arg;
        private final String name;

        public ArgImpl(org.topbraid.spin.model.Argument arg) {
            this(arg, argumentToName(arg));
        }

        public ArgImpl(org.topbraid.spin.model.Argument arg, String name) {
            this.arg = Objects.requireNonNull(arg, "Null " + arg.getClass().getName());
            this.name = Objects.requireNonNull(name, "Null name");
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String type() {
            return getValueType().getURI();
        }

        public Resource getValueType() {
            Optional<Resource> r = refinedConstraints()
                    .filter(s -> Objects.equals(s.getPredicate(), SPL.valueType))
                    .map(Statement::getObject)
                    .filter(RDFNode::isURIResource)
                    .map(RDFNode::asResource)
                    .findFirst();
            if (r.isPresent()) return r.get();
            Resource res = arg.getValueType();
            return res == null ? AVC.undefined : res;
        }

        public Stream<Statement> refinedConstraints() {
            return Iter.asStream(func.listProperties(AVC.constraint))
                    .map(Statement::getObject)
                    .filter(RDFNode::isAnon)
                    .map(RDFNode::asResource)
                    .filter(r -> r.hasProperty(SPL.predicate, arg.getPredicate()))
                    .map(Resource::listProperties)
                    .flatMap(Iter::asStream);
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
        public boolean isVararg() {
            return AVC.vararg.getURI().equals(name);
        }

        @Override
        public boolean isAssignable() {
            return !isInherit();
        }

        @Override
        public MapFunction getFunction() {
            return MapFunctionImpl.this;
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
            return Models.langValues(arg, RDFS.comment, lang).collect(Collectors.joining(STRING_VALUE_SEPARATOR));
        }

        @Override
        public String getLabel(String lang) {
            return Models.langValues(arg, RDFS.label, lang).collect(Collectors.joining(STRING_VALUE_SEPARATOR));
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ArgImpl arg = (ArgImpl) o;
            return Objects.equals(name, arg.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }

    public class BuilderImpl implements Builder {
        // either string or builder
        private final Map<ArgImpl, Object> input = new HashMap<>();

        @Override
        public Builder add(String arg, String value) {
            return put(arg, value);
        }

        @Override
        public Builder add(String arg, Builder other) {
            return put(arg, other);
        }

        private Builder put(String predicate, Object val) {
            MapJenaException.notNull(val, "Null argument value");
            ArgImpl arg = getFunction().getArg(predicate);
            if (!arg.isAssignable())
                throw exception(FUNCTION_WRONG_ARGUMENT).add(Key.ARG, predicate).build();

            if (arg.isVararg()) {
                int index = nextIndex();
                arg = new ArgImpl(arg.arg, SP.arg(index).getURI());
            }

            if (!(val instanceof String)) {
                if (val instanceof Builder) {
                    if (this.equals(val)) {
                        throw exception(FUNCTION_SELF_CALL).add(Key.ARG, predicate).build();
                    }
                    // todo: if arg is rdf:Property no nested function must be allowed
                    /*if (AVC.undefined.getURI().equals(((Builder) val).getFunction().returnType())) {
                        // todo: undefined should be allowed
                        throw new MapJenaException("Void: " + ((Builder) val).getFunction());
                    }*/
                } else {
                    throw new IllegalArgumentException("Wrong argument type: " + val.getClass().getName() + ", " + val);
                }
            }
            input.put(arg, val);
            return this;
        }

        @Override
        public MapFunctionImpl getFunction() {
            return MapFunctionImpl.this;
        }

        @Override
        public Call build() throws MapJenaException {
            Map<ArgImpl, Object> map = new HashMap<>();
            input.forEach((key, value) -> {
                Object v;
                if (value instanceof Builder) {
                    v = ((Builder) value).build();
                } else if ((value instanceof Call) || (value instanceof String)) {
                    v = value;
                } else {
                    throw new IllegalArgumentException("Wrong value: " + value);
                }
                map.put(key, v);
            });
            if (MapFunctionImpl.this.isTarget()) {
                // All of the spin-map target function calls should have spin:_source variable assigned on this argument,
                // although it does not seem it is really needed.
                MapFunctionImpl.this.arg(SPINMAP.source.getURI())
                        .ifPresent(a -> map.put(a, SPINMAP.sourceVariable.getURI()));
            }
            // check all required arguments are assigned
            Exceptions.Builder error = exception(FUNCTION_NO_REQUIRED_ARG);
            getFunction().listArgs().forEach(a -> {
                if (a.isVararg()) return;
                if (map.containsKey(a) || a.isOptional())
                    return;
                String def = a.defaultValue();
                if (def == null) {
                    error.add(Key.ARG, a.name());
                } else {
                    map.put(a, def);
                }
            });
            if (error.has(Key.ARG))
                throw error.build();
            return new CallImpl(map);
        }

        public int nextIndex() {
            return Stream.concat(getFunction().args(), input.keySet().stream())
                    .map(Arg::name)
                    .filter(s -> s.matches("^.+#" + SP.ARG + "\\d+$"))
                    .map(s -> s.replaceFirst("^.+(\\d+)$", "$1"))
                    .mapToInt(Integer::parseInt)
                    .max()
                    .orElse(0) + 1;
        }
    }

    public class CallImpl implements Call {
        // values can be either string or another function calls
        protected final Map<ArgImpl, Object> parameters;

        public CallImpl(Map<ArgImpl, Object> args) {
            this.parameters = args;
        }

        @Override
        public Map<Arg, Object> asMap() {
            return Collections.unmodifiableMap(parameters);
        }

        @Override
        public Stream<Arg> args() {
            return listArgs().map(Function.identity());
        }

        @Override
        public Object get(Arg arg) throws MapJenaException {
            try {
                //noinspection SuspiciousMethodCalls
                return MapJenaException.notNull(parameters.get(arg), "No value for " + arg);
            } catch (ClassCastException c) {
                throw new MapJenaException("No value for " + arg, c);
            }
        }

        @Override
        public MapFunctionImpl getFunction() {
            return MapFunctionImpl.this;
        }

        public Stream<ArgImpl> listArgs() {
            return parameters.keySet().stream()
                    .filter(a -> !a.isInherit())
                    .sorted(Comparator.comparing(Arg::name));
        }

        public String toString(PrefixMapping pm) {
            return listArgs()
                    .map(a -> toString(pm, a))
                    .collect(Collectors.joining(", ", pm.shortForm(getFunction().name()) + "(", ")"));
        }

        protected String toString(PrefixMapping pm, ArgImpl a) {
            return getStringKey(pm, a) + "=" + getStringValue(pm, a);
        }

        protected String getStringValue(PrefixMapping pm, ArgImpl a) {
            Object v = parameters.get(a);
            if (v instanceof CallImpl) return ((CallImpl) v).toString(pm);
            return pm.shortForm(String.valueOf(v));
        }

        protected String getStringKey(PrefixMapping pm, ArgImpl a) {
            return pm.shortForm(a.name());
        }

        @Override
        public Builder asUnmodifiableBuilder() {
            return new Builder() {
                @Override
                public Builder add(String arg, String value) {
                    throw new MapJenaException.Unsupported();
                }

                @Override
                public Builder add(String arg, Builder other) {
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

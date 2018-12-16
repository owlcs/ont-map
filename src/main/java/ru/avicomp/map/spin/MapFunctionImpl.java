/*
 * This file is part of the ONT MAP.
 * The contents of this file are subject to the Apache License, Version 2.0.
 * Copyright (c) 2018, Avicomp Services, AO
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.avicomp.map.spin;

import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.*;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import org.topbraid.spin.vocabulary.SPL;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapJenaException;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.ontapi.jena.utils.Iter;
import ru.avicomp.ontapi.jena.utils.Models;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A spin based implementation of {@link MapFunction}.
 * Assumed to be immutable in program life-cycle.
 * Created by @szuev on 09.04.2018.
 */
@SuppressWarnings({"WeakerAccess"})
public abstract class MapFunctionImpl implements MapFunction {
    public static final String STRING_VALUE_SEPARATOR = "\n";

    protected final org.topbraid.spin.model.Module func;
    protected List<ArgImpl> arguments;

    public MapFunctionImpl(org.topbraid.spin.model.Function func) {
        this.func = Objects.requireNonNull(func, "Null " + org.topbraid.spin.model.Function.class.getName());
    }

    @Override
    public String name() {
        return func.getURI();
    }

    @Override
    public String type() {
        if (func.hasProperty(AVC.returnType)) {
            return func.getPropertyResourceValue(AVC.returnType).getURI();
        }
        Resource r = func instanceof org.topbraid.spin.model.Function ?
                ((org.topbraid.spin.model.Function) func).getReturnType() : null;
        return (r == null ? AVC.undefined : r).getURI();
    }

    public List<ArgImpl> getArguments() {
        return arguments == null ?
                arguments = func.getArguments(true).stream().map(this::newArg).collect(Collectors.toList()) : arguments;
    }

    public Stream<ArgImpl> listArgs() {
        return getArguments().stream();
    }

    @Override
    public Stream<Arg> args() {
        return listArgs().map(Function.identity());
    }

    @Override
    public ArgImpl getArg(String predicate) throws MapJenaException {
        return arg(predicate)
                .orElseThrow(() -> new MapJenaException.IllegalArgument("Unable to find argument " +
                        "with predicate <" + predicate + ">."));
    }

    public Optional<ArgImpl> arg(String predicate) {
        return listArgs().filter(a -> Objects.equals(a.name(), predicate)).findFirst();
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
     * Answers {@code true}
     * if this function has the object {@link AVC#PropertyFunctions} for a predicate {@code rdfs:subClassOf}.
     * That means it is used to manage mapping template call.
     *
     * @return boolean
     */
    protected boolean isMappingPropertyFunction() {
        return isInheritedOfClass(AVC.PropertyFunctions);
    }

    /**
     * Answers {@code true} if this function is inherited from the given super class.
     * Currently it is mostly for debug, not for public usage.
     * All possible in ONT-MAP function classes:
     * <ul>
     * <li>{@code spin:Functions} - a super class for all spin-functions</li>
     * <li>{@code spin:MagicProperties}</li>
     * <li>{@code spinmap:TargetFunctions}</li>
     * <li>{@code spl:BooleanFunctions}</li>
     * <li>{@code spl:DateFunctions}</li>
     * <li>{@code spl:MathematicalFunctions}</li>
     * <li>{@code spl:MiscFunctions}</li>
     * <li>{@code spl:OntologyFunctions}</li>
     * <li>{@code spl:StringFunctions}</li>
     * <li>{@code spl:URIFunctions}</li>
     * <li>{@link AVC#MagicFunctions avc:MagicFunctions}</li>
     * <li>{@link AVC#PropertyFunctions avc:PropertyFunctions}</li>
     * <li>{@link AVC#AggregateFunctions avc:AggregateFunctions}</li>
     * </ul>
     *
     * @param superClass {@link Resource}
     * @return if there is {@code _:this rdfs:subClassOf _:superClass} statement
     */
    public boolean isInheritedOfClass(Resource superClass) {
        return func.hasProperty(RDFS.subClassOf, superClass);
    }

    /**
     * Returns the definition main triple.
     *
     * @return {@link Triple}
     */
    public Triple getRootTriple() {
        return Triple.create(func.asNode(), RDF.Nodes.type,
                (isTarget() ? SPINMAP.TargetFunction : SPIN.Function).asNode());
    }

    /**
     * Answers {@code true} if this function is custom, i.e. does not belong to the original spin family.
     * Custom functions must be directly added to the final mapping graph for compatibility with Topbraid Composer.
     *
     * @return boolean
     */
    public abstract boolean isCustom();

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

    protected boolean isMagicProperty() {
        return func instanceof org.topbraid.spin.model.Function && ((org.topbraid.spin.model.Function) func).isMagicProperty();
    }

    /**
     * Returns resource attached to the library model.
     *
     * @return {@link Resource}
     */
    public Resource asResource() {
        return func;
    }

    @Override
    public FunctionBuilderImpl create() {
        return new FunctionBuilderImpl(this);
    }

    @Override
    public String getComment(String lang) {
        return Models.langValues(func, RDFS.comment, lang).collect(Collectors.joining(STRING_VALUE_SEPARATOR));
    }

    @Override
    public String getLabel(String lang) {
        return Models.langValues(func, RDFS.label, lang).collect(Collectors.joining(STRING_VALUE_SEPARATOR));
    }

    public String toString(PrefixMapping pm) {
        return String.format("%s [%s](%s)",
                pm.shortForm(type()),
                pm.shortForm(name()), listArgs()
                        .map(a -> a.toString(pm))
                        .collect(Collectors.joining(", ")));
    }

    /**
     * Answers {@code true} if this function has a SPARQL query (select) expression.
     *
     * @return boolean
     * @see #listDependencies()
     */
    public boolean isSparqlExpression() {
        return func.hasProperty(SPIN.body);
    }

    /**
     * Answers {@code true} if this function corresponds to some SPARQL operator.
     *
     * @return boolean
     */
    public boolean isSparqlOperator() {
        // SPIN-indicator for SPARQL operator:
        return func.hasProperty(SPIN.symbol);
    }

    /**
     * Lists all dependencies (functions, that participate within the SPARQL body).
     * Note: it is a recursive function.
     *
     * @return <b>not</b> distinct Stream of {@link Resource IRI Resource}s,
     * possible empty (if no SPARQL body or function has a java body)
     * @see #isSparqlExpression()
     */
    protected Stream<Resource> listDependencies() {
        if (!isSparqlExpression()) return Stream.empty();
        return Models.listProperties(func.getRequiredProperty(SPIN.body).getObject().asResource())
                .filter(s -> RDF.type.equals(s.getPredicate()))
                .map(Statement::getObject)
                .filter(RDFNode::isURIResource)
                .map(RDFNode::asResource)
                .filter(s -> s.hasProperty(RDF.type, SPIN.Function) || s.hasProperty(RDF.type, SPINMAP.TargetFunction));
    }

    /**
     * List all super classes of this function.
     *
     * @return Stream of {@link Resource IRI Resource}s
     */
    protected Stream<Resource> listSuperClasses() {
        return Iter.asStream(func.listProperties(RDFS.subClassOf).mapWith(Statement::getResource));
    }

    /**
     * Returns {@link AdjustFunctionBody} helper in the form of {@link Optional}.
     *
     * @return Optional of {@link AdjustFunctionBody}
     */
    protected Optional<AdjustFunctionBody> runtimeBody() {
        String classPath = Iter.findFirst(func.listProperties(AVC.runtime)
                .mapWith(Statement::getObject)
                .filterKeep(RDFNode::isLiteral)
                .mapWith(RDFNode::asLiteral)
                .mapWith(Literal::getString)).orElse(null);
        if (classPath == null) {
            return Optional.empty();
        }
        try {
            Class<?> impl = Class.forName(classPath);
            if (!AdjustFunctionBody.class.isAssignableFrom(impl)) {
                throw new MapJenaException.IllegalState(name() +
                        ": incompatible class type: " + classPath + " <> " + impl.getName());
            }
            return Optional.of((AdjustFunctionBody) impl.newInstance());
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
            throw new MapJenaException.IllegalState(name() + ": can't init " + classPath, e);
        }
    }

    /**
     * Writes the function (its body) to the given graph.
     *
     * @param graph {@link MapModelImpl}
     */
    protected void write(MapModelImpl graph) {
        SpinModels.printFunctionBody(graph, func);
    }

    @Override
    public boolean canBeNested() {
        return !isTarget();
    }

    @Override
    public boolean canHaveNested() {
        return !isMappingPropertyFunction();
    }

    /**
     * Creates a new {@link ArgImpl} wrapping the given {@link org.topbraid.spin.model.Argument}.
     *
     * @param arg {@link org.topbraid.spin.model.Argument}, not {@code null}
     * @return {@link ArgImpl}
     */
    protected ArgImpl newArg(org.topbraid.spin.model.Argument arg) {
        Property p = MapJenaException.notNull(arg, "Null argument.").getPredicate();
        if (p == null) {
            throw new MapJenaException.IllegalState("Null predicate for arg " + arg + ".");
        }
        return newArg(arg, p.getURI());
    }

    /**
     * Creates a new {@link ArgImpl} with the given name and {@link org.topbraid.spin.model.Argument}.
     *
     * @param arg  {@link org.topbraid.spin.model.Argument}, not {@code null}
     * @param name String, iri
     * @return {@link ArgImpl}
     */
    protected ArgImpl newArg(org.topbraid.spin.model.Argument arg, String name) {
        return new ArgImpl(arg, name);
    }

    /**
     * {@link Arg} impl.
     *
     * @see org.topbraid.spin.model.Argument
     */
    public class ArgImpl implements Arg {
        protected final org.topbraid.spin.model.Argument arg;
        protected final String name;

        protected ArgImpl(org.topbraid.spin.model.Argument arg, String name) {
            this.arg = Objects.requireNonNull(arg, "Null " + arg.getClass().getName());
            this.name = Objects.requireNonNull(name, "Null name");
        }

        /**
         * Returns resource attached to the library model.
         *
         * @return {@link Resource}
         */
        public Resource asResource() {
            return arg;
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
            Optional<Resource> r = Iter.findFirst(refinedConstraints()
                    .filterKeep(s -> Objects.equals(s.getPredicate(), SPL.valueType))
                    .mapWith(Statement::getObject)
                    .filterKeep(RDFNode::isURIResource)
                    .mapWith(RDFNode::asResource));
            if (r.isPresent()) return r.get();
            Resource res = arg.getValueType();
            return res == null ? AVC.undefined : res;
        }

        public ExtendedIterator<Statement> refinedConstraints() {
            return Iter.flatMap(func.listProperties(AVC.constraint)
                    .mapWith(Statement::getObject)
                    .filterKeep(RDFNode::isAnon)
                    .mapWith(RDFNode::asResource)
                    .filterKeep(r -> r.hasProperty(SPL.predicate, arg.getPredicate())), Resource::listProperties);
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

    /**
     * An implementation of {@link MapFunction.Call},
     * that is used as argument while building {@link MapModelImpl mapping model}.
     */
    public static class CallImpl implements Call {
        // values can be either string or another function calls
        protected final Map<ArgImpl, Object> parameters;
        protected final MapFunctionImpl function;

        public CallImpl(MapFunctionImpl function, Map<ArgImpl, Object> args) {
            this.function = Objects.requireNonNull(function);
            this.parameters = Objects.requireNonNull(args);
        }

        @Override
        public Map<Arg, Object> asMap() {
            return Collections.unmodifiableMap(parameters);
        }

        @Override
        public Stream<Arg> args() {
            return listSortedVisibleArgs().map(Function.identity());
        }

        public Stream<MapFunctionImpl.CallImpl> directFunctionCalls() {
            return parameters.values().stream()
                    .filter(MapFunctionImpl.CallImpl.class::isInstance)
                    .map(MapFunctionImpl.CallImpl.class::cast);
        }

        public Stream<MapFunctionImpl.CallImpl> listFunctions(boolean direct) {
            if (direct) return directFunctionCalls();
            return directFunctionCalls().flatMap(f -> Stream.concat(Stream.of(f), f.listFunctions(false)));
        }

        @Override
        public Stream<MapFunction.Call> functions(boolean direct) {
            return listFunctions(direct).map(Function.identity());
        }

        /**
         * Lists all functions related to this function call.
         * The returning string contains the function from this call in the first position.
         *
         * @return Stream of {@link MapFunctionImpl}s
         */
        public Stream<MapFunctionImpl> listAllFunctions() {
            return Stream.concat(Stream.of(getFunction()), listFunctions(false).map(CallImpl::getFunction));
        }

        @Override
        public Object get(Arg arg) throws MapJenaException {
            ArgImpl k;
            try {
                k = (ArgImpl) arg;
            } catch (ClassCastException c) {
                throw new MapJenaException("No value for " + arg, c);
            }
            return MapJenaException.notNull(parameters.get(k), "No value for " + k);
        }

        @Override
        public MapFunctionImpl getFunction() {
            return function;
        }

        /**
         * Lists all visible {@link ArgImpl} sorted by name.
         *
         * @return <b>sorted</b> stream of {@link ArgImpl}
         */
        public Stream<ArgImpl> listSortedVisibleArgs() {
            return listSortedArgs().filter(a -> !a.isInherit());
        }

        /**
         * Lists all {@link ArgImpl}, including hidden, sorted by name.
         *
         * @return <b>sorted</b> stream of {@link ArgImpl}
         */
        public Stream<ArgImpl> listSortedArgs() {
            return parameters.keySet().stream().sorted(Comparator.comparing(Arg::name));
        }

        /**
         * Gets a string representation of this {@link CallImpl} according to the {@link PrefixMapping}.
         *
         * @param pm {@link PrefixMapping}
         * @return String
         */
        public String toString(PrefixMapping pm) {
            return listSortedVisibleArgs()
                    .map(a -> toString(pm, a))
                    .collect(Collectors.joining(", ", pm.shortForm(getFunction().name()) + "(", ")"));
        }

        protected String toString(PrefixMapping pm, ArgImpl a) {
            return getStringKey(pm, a) + "=" + getStringValue(pm, a);
        }

        /**
         * Gets a string representation of argument value
         * for the given {@link ArgImpl} and according to the given {@link PrefixMapping}.
         *
         * @param pm {@link PrefixMapping}
         * @param a  {@link ArgImpl}, not {@code null}
         * @return String
         */
        protected String getStringValue(PrefixMapping pm, ArgImpl a) {
            Object v = parameters.get(a);
            if (v instanceof CallImpl) return ((CallImpl) v).toString(pm);
            return pm.shortForm(String.valueOf(v));
        }

        /**
         * Gets a string representation of {@link ArgImpl#name()} (i.e. argument prefix)
         * according to the given {@link PrefixMapping}.
         *
         * @param pm {@link PrefixMapping}
         * @param a  {@link ArgImpl}, not {@code null}
         * @return String
         */
        protected String getStringKey(PrefixMapping pm, ArgImpl a) {
            return pm.shortForm(a.name());
        }

        @Override
        public FunctionBuilderImpl asUnmodifiableBuilder() {
            return new FunctionBuilderImpl(function) {
                @Override
                public FunctionBuilderImpl put(String arg, Object value) {
                    throw new MapJenaException.Unsupported();
                }

                @Override
                public CallImpl build() throws MapJenaException {
                    return CallImpl.this;
                }
            };
        }
    }
}

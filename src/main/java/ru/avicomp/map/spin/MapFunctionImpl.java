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
import org.apache.jena.util.iterator.NullIterator;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import org.topbraid.spin.vocabulary.SPL;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapJenaException;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;
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
public abstract class MapFunctionImpl implements MapFunction, ToString {
    public static final String STRING_VALUE_SEPARATOR = "\n";
    public static final Comparator<Arg> ARG_COMPARATOR = Comparator.comparing(Arg::isVararg)
            .thenComparing(ArgURIComparator.comparing(Arg::name));

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
        if (arguments != null) {
            return arguments;
        }
        return arguments = func.getArguments(false).stream()
                .map(this::newArg)
                .sorted(ARG_COMPARATOR)
                .collect(Collectors.toList());
    }

    /**
     * Lists all argument impls.
     *
     * @return ordered {@code Stream}
     */
    public ExtendedIterator<ArgImpl> listArgImpls() {
        return Iter.create(getArguments());
    }

    @Override
    public Stream<Arg> args() {
        return Iter.asStream(listArgImpls());
    }

    @Override
    public ArgImpl getArg(String predicate) throws MapJenaException {
        return arg(predicate)
                .orElseThrow(() -> new MapJenaException.IllegalArgument("Unable to find argument " +
                        "with predicate <" + predicate + ">."));
    }

    public Optional<ArgImpl> arg(String predicate) {
        return Iter.findFirst(listArgImpls().filterKeep(a -> Objects.equals(a.name(), predicate)));
    }

    @Override
    public boolean isTarget() {
        return func.hasProperty(RDF.type, SPINMAP.TargetFunction);
    }

    /**
     * Answers {@code true} if the function, which is expected to be {@link #isTarget() target},
     * can produce anonymous resources.
     *
     * @return boolean
     * @see Resource#isAnon()
     */
    public boolean canProduceBNodes() {
        return SPINMAPL.self.equals(func);
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
        // todo: take into consideration class hierarchy
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
        return func instanceof org.topbraid.spin.model.Function
                && ((org.topbraid.spin.model.Function) func).isPrivate();
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
        return func instanceof org.topbraid.spin.model.Function
                && ((org.topbraid.spin.model.Function) func).isMagicProperty();
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

    @Override
    public String toString(PrefixMapping pm) {
        return String.format("%s [%s](%s)", ToString.getShortForm(pm, type()), ToString.getShortForm(pm, name()),
                Iter.asStream(listArgImpls()).map(a -> a.toString(pm)).collect(Collectors.joining(", ")));
    }

    /**
     * Answers {@code true} if this function has a SPARQL query (select) expression.
     *
     * @return boolean
     * @see #listDependencyResources()
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

    @Override
    public final Stream<MapFunction> dependencies() {
        return Iter.asStream(listDependencies());
    }

    /**
     * Lists all functions that this one depends on.
     * If a function is a SPARQL-based it may depend on other functions,
     * which in turn may also rely on some other SPARQL functions or operators or ARQ(java) based functions.
     *
     * @return {@link ExtendedIterator} over all nested functions
     */
    protected abstract ExtendedIterator<? extends MapFunctionImpl> listDependencies();

    /**
     * Lists all dependencies (functions, that participate within the SPARQL body).
     * Note: it is a recursive function.
     *
     * @return <b>not</b> distinct {@link ExtendedIterator} over {@link Resource URI Resource}s,
     * possible empty (if no SPARQL body or function has a java body)
     * @see #isSparqlExpression()
     */
    protected ExtendedIterator<Resource> listDependencyResources() {
        if (!isSparqlExpression()) return NullIterator.instance();
        return Models.listDescendingStatements(func.getRequiredProperty(SPIN.body).getResource())
                .filterKeep(s -> RDF.type.equals(s.getPredicate()))
                .mapWith(Statement::getObject)
                .filterKeep(RDFNode::isURIResource)
                .mapWith(RDFNode::asResource)
                .filterKeep(s -> s.hasProperty(RDF.type, SPIN.Function)
                        || s.hasProperty(RDF.type, SPINMAP.TargetFunction));
    }

    /**
     * List all super classes of this function.
     *
     * @return {@link ExtendedIterator} over {@link Resource IRI Resource}s
     */
    protected ExtendedIterator<Resource> listSuperClasses() {
        return func.listProperties(RDFS.subClassOf).mapWith(Statement::getResource);
    }

    /**
     * Returns {@link AdjustFunctionBody} helper wrapped as {@link Optional}.
     *
     * @return Optional around the {@link AdjustFunctionBody} impl
     * @see AVC#runtime
     */
    protected Optional<AdjustFunctionBody> runtimeBody() {
        return findClass(AdjustFunctionBody.class, AVC.runtime).map(type -> {
            try {
                return type.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                throw new MapJenaException.IllegalState(name() + ": can't init runtime body", e);
            }
        });
    }

    /**
     * Returns a class-type of ARQ function wrapped as {@link Optional}.
     * This is used for optimization.
     *
     * @return Optional around the class-type of {@link org.apache.jena.sparql.function.Function Jena ARQ function}
     * @see AVC#optimize
     */
    protected Optional<Class<org.apache.jena.sparql.function.Function>> optimizeClass() {
        return findClass(org.apache.jena.sparql.function.Function.class, AVC.optimize);
    }

    @SuppressWarnings("unchecked")
    protected <X> Optional<Class<X>> findClass(Class<X> type, Property property) {
        String classPath = Iter.findFirst(func.listProperties(property)
                .mapWith(Statement::getObject)
                .filterKeep(RDFNode::isLiteral)
                .mapWith(RDFNode::asLiteral)
                .mapWith(Literal::getString)).orElse(null);
        if (classPath == null) {
            return Optional.empty();
        }
        try {
            Class impl = Class.forName(classPath);
            if (!type.isAssignableFrom(impl)) {
                throw new MapJenaException.IllegalState(name() +
                        ": incompatible class type: " + classPath + " <> " + impl.getName());
            }
            return Optional.of(impl);
        } catch (ClassNotFoundException e) {
            throw new MapJenaException.IllegalState(name() + ": can't find " + classPath, e);
        }
    }

    /**
     * Writes the function (its body) to the given graph.
     *
     * @param graph {@link MapModelImpl}
     */
    protected void write(MapModelImpl graph) {
        SpinModels.printSpinFunctionBody(graph, func);
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
     * Converts a {@link RDFNode} to a {@code String}.
     * It is a reverse operation to the {@link MapModelImpl#toNode(String)},
     * and is used to pass arguments to a function and to get function parts from RDF.
     *
     * @param node {@link RDFNode}, not {@code null}
     * @return String
     * @see MapModelImpl#toNode(String)
     */
    protected String getAsString(RDFNode node) {
        if (node.isLiteral()) {
            return node.asNode().toString(false);
        }
        if (node.isURIResource()) {
            return node.asResource().getURI();
        }
        throw new MapJenaException.IllegalState();
    }

    /**
     * {@link Arg} impl.
     *
     * @see org.topbraid.spin.model.Argument
     */
    public class ArgImpl implements Arg, ToString {
        protected final org.topbraid.spin.model.Argument arg;
        protected final String name;
        // caches (currently it is not possible to change a function or its part):
        private Resource returnType;
        private Boolean optional;
        @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
        private Optional<RDFNode> defaultValue;
        private Set<String> oneOf;

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

        /**
         * Returns an argument value type.
         * If it is undefined, then {@link AVC#undefined avc:undefined} is returned,
         * which means the function may accept anything - any resource or literal.
         *
         * @return {@link Resource}, not {@code null}
         */
        public Resource getValueType() {
            if (returnType != null) return returnType;
            return returnType = Iter.findFirst(listStatements(SPL.valueType)
                    .mapWith(Statement::getObject)
                    .filterKeep(RDFNode::isURIResource)
                    .mapWith(RDFNode::asResource)).orElse(AVC.undefined);
        }

        @Override
        public String defaultValue() {
            return defaultValueNode().map(MapFunctionImpl.this::getAsString).orElse(null);
        }

        /**
         * Returns a default value as {@code Optional} around the {@link RDFNode}.
         *
         * @return {@code Optional}, never {@code null}
         * @see SPL#defaultValue
         */
        @SuppressWarnings("OptionalAssignedToNull")
        public Optional<RDFNode> defaultValueNode() {
            if (defaultValue != null) return defaultValue;
            return defaultValue = Iter.findFirst(listStatements(SPL.defaultValue).mapWith(Statement::getObject));
        }

        @Override
        public boolean isOptional() {
            if (optional != null) return optional;
            return optional = Iter.findFirst(listStatements(SPL.optional)
                    .filterKeep(x -> Models.TRUE.equals(x.getObject()))).isPresent();
        }

        @Override
        public Set<String> oneOf() {
            if (oneOf != null) return oneOf;
            Optional<RDFList> list = findOneOfList();
            if (!list.isPresent()) {
                return oneOf = Collections.emptySet();
            }
            return oneOf = Iter.asStream(list.get().iterator().mapWith(MapFunctionImpl.this::getAsString))
                    .collect(Iter.toUnmodifiableSet());
        }

        public Optional<RDFList> findOneOfList() {
            return Iter.findFirst(listStatements(AVC.oneOf)
                    .filterKeep(x -> x.getObject().canAs(RDFList.class))
                    .mapWith(x -> x.getObject().as(RDFList.class)));
        }

        /**
         * Lists all argument statements,
         * both from refined constraint and immutable core spin library, in this order.
         *
         * @param predicate {@link Property}, possible {@code null}
         * @return {@link ExtendedIterator} over {@link Statement}s
         */
        public ExtendedIterator<Statement> listStatements(Property predicate) {
            return Iter.concat(listRefinedConstraints()
                            .filterKeep(s -> predicate == null || s.getPredicate().equals(predicate)),
                    arg.listProperties(predicate));
        }

        /**
         * Lists all refined constraints for this argument.
         *
         * @return {@code ExtendedIterator} over {@link Statement}s
         * @see AVC#constraint
         */
        public ExtendedIterator<Statement> listRefinedConstraints() {
            return Iter.flatMap(func.listProperties(AVC.constraint)
                    .mapWith(Statement::getObject)
                    .filterKeep(RDFNode::isAnon)
                    .mapWith(RDFNode::asResource)
                    .filterKeep(r -> r.hasProperty(SPL.predicate, arg.getPredicate())), Resource::listProperties);
        }

        @Override
        public boolean isVararg() {
            return AVC.vararg.getURI().equals(name);
        }

        @Override
        public boolean isAssignable() {
            return !isInherit();
        }

        /**
         * Answers {@code true} if the argument is autogenerated, which means the function is vararg.
         *
         * @return boolean
         */
        protected boolean isAutoGenerated() {
            return !name().equals(arg.getPredicate().getURI());
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
            return langLiterals(RDFS.comment, lang).collect(Collectors.joining(STRING_VALUE_SEPARATOR));
        }

        @Override
        public String getLabel(String lang) {
            return langLiterals(RDFS.label, lang).collect(Collectors.joining(STRING_VALUE_SEPARATOR));
        }

        private Stream<String> langLiterals(Property predicate, String lang) {
            return Iter.asStream(listStatements(predicate)
                    .filterKeep(x -> x.getObject().isLiteral() && Models.filterByLangTag(x.getLiteral(), lang))
                    .mapWith(Statement::getString));
        }

        @Override
        public String toString(PrefixMapping pm) {
            return String.format("%s%s=%s", ToString.getShortForm(pm, name()), info(), ToString.getShortForm(pm, type()));
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
    public static class CallImpl implements Call, ToString {
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
            return sortedVisibleArgs().map(Function.identity());
        }

        public ExtendedIterator<CallImpl> listDirectFunctionCalls() {
            return Iter.create(parameters.values())
                    .filterKeep(MapFunctionImpl.CallImpl.class::isInstance)
                    .mapWith(MapFunctionImpl.CallImpl.class::cast);
        }

        public ExtendedIterator<CallImpl> listFunctions(boolean direct) {
            if (direct) return listDirectFunctionCalls();
            return Iter.flatMap(listDirectFunctionCalls(), f -> Iter.concat(Iter.of(f), f.listFunctions(false)));
        }

        @Override
        public Stream<MapFunction.Call> functions(boolean direct) {
            return Iter.asStream(listFunctions(direct));
        }

        /**
         * Lists all functions related to this function call.
         * The returned string contains the function from this call in the first position.
         *
         * @return {@link ExtendedIterator} of {@link MapFunctionImpl}s
         */
        public ExtendedIterator<MapFunctionImpl> listAllFunctions() {
            return Iter.concat(Iter.of(getFunction()), listFunctions(false).mapWith(CallImpl::getFunction));
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
        public Stream<ArgImpl> sortedVisibleArgs() {
            return sortedArgs().filter(ArgImpl::isAssignable);
        }

        /**
         * Lists all {@link ArgImpl}, including hidden, sorted by name.
         *
         * @return <b>sorted</b> stream of {@link ArgImpl}
         * @see ArgURIComparator
         */
        public Stream<ArgImpl> sortedArgs() {
            return parameters.keySet().stream().sorted(ARG_COMPARATOR);
        }

        /**
         * Gets a string representation of this {@link CallImpl} according to the {@link PrefixMapping}.
         *
         * @param pm {@link PrefixMapping}
         * @return String
         */
        @Override
        public String toString(PrefixMapping pm) {
            return sortedVisibleArgs()
                    .map(a -> toString(pm, a))
                    .collect(Collectors.joining(", ", ToString.getShortForm(pm, getFunction().name()) + "(", ")"));
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
            return ToString.getShortForm(pm, String.valueOf(v));
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
            return ToString.getShortForm(pm, a.name());
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

    /**
     * A helper to compare argument URIs.
     * Created by @szz on 18.12.2018.
     */
    @SuppressWarnings("WeakerAccess")
    public static class ArgURIComparator implements Comparator<String> {

        /**
         * Answers {@code true}
         * if the specified string corresponds index argument (e.g. {@code http://spinrdf.org/sp#arg1}).
         *
         * @param name String to test, not {@code null}
         * @return boolean
         */
        public static boolean isIndexedArg(String name) {
            return name.matches("^.+#" + SP.ARG + "\\d+$");
        }

        /**
         * Parses index from a index argument.
         *
         * @param name String, not {@code null}, e.g. {@code http://spinrdf.org/sp#arg1}
         * @return int, index (for the example above it is {@code 1})
         */
        static int parseIndex(String name) {
            return Integer.parseInt(name.replaceFirst("^.+[^\\d](\\d+)$", "$1"));
        }

        /**
         * Parses base from a index argument.
         *
         * @param name String, not {@code null}, e.g. {@code http://spinrdf.org/sp#arg1}
         * @return int, index (for the example above it is {@code http://spinrdf.org/sp#arg})
         */
        static String parseBase(String name) {
            return name.replaceFirst("^(.+[^\\d])\\d+$", "$1");
        }

        public static <T> Comparator<T> comparing(Function<? super T, String> keyExtractor) {
            Objects.requireNonNull(keyExtractor);
            ArgURIComparator res = new ArgURIComparator();
            return (a, b) -> res.compare(keyExtractor.apply(a), keyExtractor.apply(b));
        }

        @Override
        public int compare(String left, String right) {
            if (isIndexedArg(left) && isIndexedArg(right)) {
                String leftBase = parseBase(left);
                String rightBase = parseBase(right);
                if (leftBase.equals(rightBase)) {
                    Integer leftIndex = parseIndex(left);
                    Integer rightIndex = parseIndex(right);
                    return leftIndex.compareTo(rightIndex);
                }
            }
            return left.compareTo(right);
        }
    }
}

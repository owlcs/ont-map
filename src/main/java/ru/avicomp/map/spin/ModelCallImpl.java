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

import org.apache.jena.query.QueryException;
import org.apache.jena.rdf.model.*;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.RDFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.arq.ARQ2SPIN;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import org.topbraid.spin.vocabulary.SPL;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapJenaException;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.ontapi.jena.utils.Iter;
import ru.avicomp.ontapi.jena.utils.Models;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.*;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

/**
 * A {@link MapFunction.Call} implementation that is attached to a {@link MapModelImpl model}.
 * <p>
 * Created by @ssz on 23.11.2018.
 */
@SuppressWarnings("WeakerAccess")
public class ModelCallImpl extends MapFunctionImpl.CallImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModelCallImpl.class);

    private final MapModelImpl model;

    public ModelCallImpl(MapModelImpl model, MapFunctionImpl function, Map<MapFunctionImpl.ArgImpl, Object> args) {
        super(function, args);
        this.model = Objects.requireNonNull(model);
    }

    @Override
    public String toString(PrefixMapping pm) {
        String name = ToString.getShortForm(pm, getFunction().name());
        List<MapFunctionImpl.ArgImpl> args = sortedVisibleArgs().collect(Collectors.toList());
        if (args.size() == 1) { // print without predicate
            return name + "(" + getStringValue(pm, args.get(0)) + ")";
        }
        return args.stream().map(a -> toString(pm, a)).collect(Collectors.joining(", ", name + "(", ")"));
    }

    @Override
    protected String getStringValue(PrefixMapping pm, MapFunctionImpl.ArgImpl a) {
        Object v = get(a);
        if (!(v instanceof String)) {
            return super.getStringValue(pm, a);
        }
        RDFNode n = model.toNode((String) v);
        if (n.isLiteral()) {
            return ToString.getShortForm(pm, n.asLiteral());
        }
        return ToString.getShortForm(pm, n.asNode().toString());
    }

    @Override
    protected String getStringKey(PrefixMapping pm, MapFunctionImpl.ArgImpl a) {
        return "?" + model.toNode(a.name()).asResource().getLocalName();
    }

    /**
     * Overridden {@code #toString()}, to produce a good-looking output, which can be used as a label.
     * <p>
     * Actually, it is not a very good idea to override {@code toString()},
     * there should be a special mechanism to print anything in ONT-MAP api.
     * But as temporary solution it is okay: it is not dangerous here.
     *
     * @return String
     */
    @Override
    public String toString() {
        return toString(model);
    }

    @Override
    public MapFunctionImpl save(String name) throws MapJenaException {
        MapJenaException.notNull(name, "Null function name");
        MapManagerImpl manager = model.getManager();
        if (manager.getFunctionsMap().containsKey(name)) {
            throw new MapJenaException.IllegalArgument("A function with the same name (<" + name + ">)" +
                    " already exists. Please choose another name.");
        }
        MapFunctionImpl from = getFunction();
        if (!from.canHaveNested()) {
            throw new MapJenaException.IllegalArgument("The function <" + from.name() + "> is a special, " +
                    "it does not accept nested functions. Save operation is prohibited for such functions.");
        }
        Model lib = manager.getLibrary();
        PrefixMapping pm = manager.prefixes();
        MapFunctionImpl res = newFunction(lib, name);

        Expression expr = createExpression(res, i -> SpinModels.getSPArgProperty(lib, i));
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Create function <{}> based on query '{}'", name, expr.toString(pm));
        }
        String txt = String.format("SELECT ?res\nWHERE {\n\tBIND(%s AS ?res)\n}", expr.toString(lib));
        // make query:
        org.apache.jena.query.Query query;
        try {
            query = manager.getFactory().createQuery(txt, model);
        } catch (QueryException qe) {
            throw new MapJenaException.IllegalState("Unable to create a query from the expression '" + txt + "'", qe);
        }
        // print the function resource into the managers primary graph:
        Resource f = res.asResource()
                .addProperty(SPIN.returnType, lib.createResource(from.type()))
                .addProperty(RDF.type, from.isTarget() ? SPINMAP.TargetFunction : SPIN.Function);
        // inherit all super classes:
        Iter.flatMap(res.listDependencies(), MapFunctionImpl::listSuperClasses)
                .forEachRemaining(x -> f.addProperty(RDFS.subClassOf, x));
        // process arguments:
        expr.getArguments().forEach(arg -> {
            Resource constraint = lib.createResource()
                    .addProperty(RDF.type, SPL.Argument)
                    .addProperty(SPL.predicate, arg.getPredicate())
                    .addProperty(SPL.valueType, arg.getValueType());
            f.addProperty(SPIN.constraint, constraint);
            // copy labels:
            arg.getLabels().forEach(x -> constraint.addProperty(RDFS.label, x));
            // copy comments:
            arg.getComments(pm).forEach(x -> constraint.addProperty(RDFS.comment, x));
            arg.defaultValue().ifPresent(x -> constraint.addProperty(SPL.defaultValue, x));
            if (arg.isOptional()) {
                constraint.addProperty(SPL.optional, Models.TRUE);
            }
            // AVC#oneOf
            Set<String> oneOf = arg.oneOf();
            if (!oneOf.isEmpty()) {
                RDFList list = model.createList(oneOf.stream().map(model::toNode).iterator());
                constraint.addProperty(AVC.oneOf, list);
            }
        });
        f.addProperty(SPIN.body, new ARQ2SPIN(lib).createQuery(query, null));
        manager.register(f);
        return res;
    }

    /**
     * Creates a new {@link MapFunction#isUserDefined() user-defined} function.
     *
     * @param m    {@link Model} to which the returned function will be belonged
     * @param name String, uri of new function
     * @return {@link MapFunctionImpl}
     */
    protected MapFunctionImpl newFunction(Model m, String name) {
        return new MapFunctionImpl(m.createResource(name).as(org.topbraid.spin.model.Function.class)) {
            @Override
            public boolean isCustom() {
                return true;
            }

            @Override
            public boolean isUserDefined() {
                return true;
            }

            @Override
            public ExtendedIterator<MapFunctionImpl> listDependencies() {
                return ModelCallImpl.this.listAllFunctions().filterKeep(MapFunctionImpl::canHaveNested);
            }

            @Override
            public String toString() {
                return toString(model);
            }
        };
    }

    /**
     * Creates an expression using the given {@code IntFunction} to generate constraint predicates.
     *
     * @param func              {@link MapFunctionImpl} - new function, not {@code null}
     * @param predicateProvider {@link IntFunction}, not {@code null}
     * @return {@link Expression}
     */
    public Expression createExpression(MapFunctionImpl func, IntFunction<Property> predicateProvider) {
        Expression res = new Expression(func).write(this);
        List<FutureArg> list = new ArrayList<>(res.getArguments());
        // reorder so that optional arguments go last:
        list.sort(Comparator.comparing(FutureArg::isOptional));
        for (int i = 0; i < list.size(); i++) {
            // replace arg variable references with existing sp predicate
            list.get(i).key = predicateProvider.apply(i + 1);
        }
        return res;
    }

    /**
     * An expression representation of the function call.
     */
    public static class Expression implements ToString {
        private final MapFunctionImpl func;
        private Map<Resource, FutureArg> values = new LinkedHashMap<>();
        private List<Symbol> content = new ArrayList<>();

        protected Expression(MapFunctionImpl func) {
            this.func = Objects.requireNonNull(func);
        }

        public Collection<FutureArg> getArguments() {
            return values.values();
        }

        /**
         * Inserts a separator into the {@link #content} copy.
         *
         * @param separator {@link Symbol}
         * @return {@code List} of {@link Symbol}s
         */
        public List<Symbol> withSeparator(Symbol separator) {
            List<Symbol> res = new ArrayList<>();
            int size = content.size();
            for (int i = 0; i < size; i++) {
                Symbol s = content.get(i);
                res.add(s);
                if (Symbol.LEFT_BRACKET.equals(s)) {
                    continue;
                }
                if (i == size - 1) {
                    break;
                }
                Symbol next = content.get(i + 1);
                if (Symbol.isBracket(next)) {
                    continue;
                }
                res.add(separator);
            }
            return res;
        }

        @Override
        public String toString(PrefixMapping pm) {
            return withSeparator(Symbol.SEPARATOR).stream().map(x -> x.toString(pm)).collect(Collectors.joining());
        }

        /**
         * Writes an expression for the given function call.
         *
         * @param call {@link ModelCallImpl}
         * @return {@link Expression}
         */
        public Expression write(ModelCallImpl call) {
            content.add(pm -> ToString.getShortForm(pm, call.getFunction().name()));
            content.add(Symbol.LEFT_BRACKET);
            call.sortedArgs().forEach(c -> write(call, c));
            content.add(Symbol.RIGHT_BRACKET);
            return this;
        }

        /**
         * Writes an expression for the given function call argument.
         *
         * @param call {@link ModelCallImpl} a base call
         * @param arg  {@link MapFunctionImpl.ArgImpl} to parse
         * @see ru.avicomp.map.spin.vocabulary.AVC#PropertyFunctions
         */
        protected void write(ModelCallImpl call, MapFunctionImpl.ArgImpl arg) {
            Object value = call.get(arg);
            if (!(value instanceof String)) {
                ModelCallImpl other = ((ModelCallImpl) value);
                if (!other.getFunction().canHaveNested()) {
                    // skip avc:PropertyFunctions -> add as resource value
                    addEntity(arg, call.model.createResource(other.toString()));
                    return;
                }
                write(other);
                return;
            }
            RDFNode node = call.model.toNode((String) value);
            // literal:
            if (node.isLiteral()) {
                content.add(pm -> ToString.getShortForm(pm, node.asLiteral()));
                return;
            }
            // resource belonging to the model:
            Resource res = node.asResource();
            // a property, class-expression, datatype or spinmap-context (what else ?)
            // they must be discarded from expression as ontology specific.
            if (call.model.isEntity(res)) {
                addEntity(arg, res);
                return;
            }
            // just standalone IRI (variable or constant)
            if (res.isURIResource()) {
                // TODO: what if the dt belongs to the mapping (i.e. source?)
                content.add(pm -> ToString.getShortForm(pm, res.getURI()));
                return;
            }
            throw new MapJenaException.IllegalState("Fail to create the function <" + func.name() + ">: " +
                    "don't know what to do with anonymous value for the argument <" + arg.name() + ">.");
        }

        private void addEntity(MapFunctionImpl.ArgImpl arg, Resource value) {
            content.add(values.computeIfAbsent(value, r -> new FutureArg()).add(arg));
        }
    }

    /**
     * A container for {@link MapFunctionImpl.ArgImpl}s,
     * that is used while building new function query and spin constraints.
     */
    protected static class FutureArg implements Symbol {
        protected final Set<MapFunctionImpl.ArgImpl> argList = new HashSet<>();
        protected Property key;

        FutureArg add(MapFunctionImpl.ArgImpl arg) {
            argList.add(arg);
            return this;
        }

        @Override
        public String toString(PrefixMapping pm) {
            return "?" + getPredicate().getLocalName();
        }

        public Property getPredicate() {
            if (key == null)
                throw new MapJenaException.IllegalState();
            return key;
        }

        /**
         * Gets a future argument type.
         *
         * @return {@link Resource}
         * @see MapFunction.Arg#type()
         */
        public Resource getValueType() {
            // todo: must return the most specific type, not the first:
            return argList.stream()
                    .map(MapFunctionImpl.ArgImpl::getValueType)
                    .findFirst()
                    .orElseThrow(MapJenaException.IllegalState::new);
        }

        /**
         * Finds a future argument default value.
         * @return {@code Optional} around {@link RDFNode}
         * @see MapFunction.Arg#defaultValue()
         */
        public Optional<RDFNode> defaultValue() {
            // todo: must return the most specific value, not the first:
            return argList.stream()
                    .map(MapFunctionImpl.ArgImpl::defaultValueNode)
                    .filter(Optional::isPresent).map(Optional::get)
                    .findFirst();
        }

        public Set<String> oneOf() {
            return argList.stream().map(MapFunctionImpl.ArgImpl::oneOf)
                    .flatMap(Collection::stream).collect(Iter.toUnmodifiableSet());
        }

        /**
         * Answers if the variable argument is option, which is only possible if all source arguments are optional.
         * @return {@code true} if it is future optional argument
         */
        public boolean isOptional() {
            return argList.stream().allMatch(MapFunctionImpl.ArgImpl::isOptional);
        }

        private Literal createLiteral(String txt) {
            return ResourceFactory.createPlainLiteral(txt);
        }

        /**
         * Gets all {@code rdfs:label}s (from all source arguments, including its refined constraints).
         * @return {@code Set} of {@link Literal}s
         */
        public Set<Literal> getLabels() {
            return Iter.flatMap(Iter.create(argList), x -> x.listStatements(RDFS.label))
                    .mapWith(Statement::getLiteral)
                    .toSet();
        }

        /**
         * Gets all auto-generated and inherited {@code rdfs:comment}s
         * (from all source arguments, including its refined constraints).
         * @param pm {@link PrefixMapping}
         * @return {@code Set} of {@link Literal}s
         */
        public Set<Literal> getComments(PrefixMapping pm) {
            return Iter.flatMap(Iter.create(argList),
                    x -> Iter.concat(Iter.of(generateComment(pm, x)),
                            x.listStatements(RDFS.comment).mapWith(Statement::getLiteral)))
                    .toSet();
        }

        private Literal generateComment(PrefixMapping pm, MapFunctionImpl.ArgImpl arg) {
            if (arg.isAutoGenerated()) {
                return createLiteral(String.format("Copied from %s vararg",
                        ToString.getShortForm(pm, arg.getFunction().name())));
            }
            return createLiteral(String.format("Copied from %s/%s",
                    ToString.getShortForm(pm, arg.getFunction().name()), ToString.getShortForm(pm, arg.name())));
        }
    }

    /**
     * A part of {@link Expression}.
     */
    protected interface Symbol extends ToString {
        Symbol SEPARATOR = pm -> ", ";
        Symbol LEFT_BRACKET = pm -> "(";
        Symbol RIGHT_BRACKET = pm -> ")";

        static boolean isBracket(Symbol s) {
            return LEFT_BRACKET.equals(s) || RIGHT_BRACKET.equals(s);
        }
    }

}

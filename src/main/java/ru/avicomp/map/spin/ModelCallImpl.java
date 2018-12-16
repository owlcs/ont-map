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
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.arq.ARQ2SPIN;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import org.topbraid.spin.vocabulary.SPL;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapJenaException;
import ru.avicomp.ontapi.jena.utils.Models;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        String name = pm.shortForm(getFunction().name());
        List<MapFunctionImpl.ArgImpl> args = listSortedVisibleArgs().collect(Collectors.toList());
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
            Literal l = n.asLiteral();
            String u = l.getDatatypeURI();
            if (XSD.xstring.getURI().equals(u)) {
                return l.getLexicalForm();
            }
            return String.format("%s^^%s", l.getLexicalForm(), pm.shortForm(u));
        }
        return pm.shortForm(n.asNode().toString());
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
        MapFunctionImpl res = newFunction(lib, name);

        Map<Resource, FutureArg> map = new LinkedHashMap<>();
        String expr = writeExpression(res, map);
        List<FutureArg> list = new ArrayList<>(map.values());
        // reorder so that optional arguments go last:
        list.sort(Comparator.comparing(x -> x.arg.isOptional()));
        for (int i = 0; i < list.size(); i++) {
            FutureArg fa = list.get(i);
            String tmp = fa.key;
            Resource arg = createArgKey(i + 1);
            expr = expr.replace(tmp, arg.getLocalName());
            fa.key = arg.getURI();
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Create function <{}> based on query '{}'", name, expr);
        }
        expr = String.format("SELECT ?res\nWHERE {\n\tBIND(%s AS ?res)\n}", expr);
        // make query:
        org.apache.jena.query.Query query;
        try {
            query = manager.getFactory().createQuery(expr, model);
        } catch (QueryException qe) {
            throw new MapJenaException.IllegalState("Unable to create a query from expression '" + expr + "'", qe);
        }
        // print the function resource into the managers primary graph:
        Resource f = res.asResource()
                .addProperty(SPIN.returnType, lib.createResource(from.type()))
                .addProperty(RDF.type, from.isTarget() ? SPINMAP.TargetFunction : SPIN.Function);
        // inherit all super classes:
        res.dependencies().map(x -> (MapFunctionImpl) x)
                .flatMap(MapFunctionImpl::listSuperClasses)
                .forEach(x -> f.addProperty(RDFS.subClassOf, x));
        // process arguments:
        PrefixMapping pm = manager.prefixes();
        list.forEach(arg -> {
            MapFunctionImpl.ArgImpl argFrom = arg.arg;
            Resource constraint = lib.createResource()
                    .addProperty(RDF.type, SPL.Argument)
                    .addProperty(SPL.predicate, lib.createResource(arg.key))
                    .addProperty(SPL.valueType, argFrom.getValueType());
            f.addProperty(SPIN.constraint, constraint);
            // copy labels:
            argFrom.asResource().listProperties(RDFS.label)
                    .forEachRemaining(s -> constraint.addProperty(s.getPredicate(), s.getObject()));
            // autogenerated comment:
            constraint.addProperty(RDFS.comment, String.format("Copied from %s/%s",
                    pm.shortForm(argFrom.getFunction().name()),
                    pm.shortForm(argFrom.name())));
            String dv = argFrom.defaultValue();
            if (dv != null) {
                constraint.addProperty(SPL.defaultValue, dv);
            }
            if (argFrom.isOptional()) {
                constraint.addProperty(SPL.optional, Models.TRUE);
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
            public Stream<MapFunction> dependencies() {
                return ModelCallImpl.this.listAllFunctions().filter(MapFunctionImpl::canHaveNested).map(Function.identity());
            }

            @Override
            public String toString() {
                return toString(model);
            }
        };
    }

    /**
     * Makes a string representation of an expression for specified function (first input),
     * storing information about arguments in the map (second input).
     *
     * @param func a function to create, here it is used as a factory to create new {@link MapFunction.Arg}
     * @param args mutable map to store new arguments
     * @return full expression
     */
    protected String writeExpression(MapFunctionImpl func,
                                     Map<Resource, FutureArg> args) {
        return String.format("<%s>(%s)", getFunction().name(),
                listSortedArgs()
                        .map(a -> writeExpression(a, func, args)).collect(Collectors.joining(", ")));
    }

    /**
     * Makes a string representation of an expression argument-part.
     *
     * @param arg  {@link MapFunctionImpl.ArgImpl} to parse
     * @param func new function to create
     * @param args mutable map to store new arguments
     * @return a part of expression that corresponds the given argument
     * @see ru.avicomp.map.spin.vocabulary.AVC#PropertyFunctions
     */
    protected String writeExpression(MapFunctionImpl.ArgImpl arg,
                                     MapFunctionImpl func,
                                     Map<Resource, FutureArg> args) {
        Object value = get(arg);
        if (!(value instanceof String)) {
            ModelCallImpl call = ((ModelCallImpl) value);
            if (!call.getFunction().canHaveNested()) {
                // skip avc:PropertyFunctions
                return "?" + args.computeIfAbsent(model.createResource(call.toString()), r -> new FutureArg(arg)).key;
            }
            return call.writeExpression(func, args);
        }
        RDFNode n = model.toNode((String) value);
        // literal:
        if (n.isLiteral()) {
            Literal literal = n.asLiteral();
            String txt = literal.getLexicalForm();
            String dtURI = literal.getDatatypeURI();
            // TODO: what if the dt belongs to the mapping (i.e. source?)
            if (XSD.xstring.getURI().equals(dtURI)) {
                return txt;
            }
            return String.format("%s^^<%s>", txt, dtURI);
        }
        // resource belonging to the model:
        Resource res = n.asResource();
        // a property, class-expression, datatype or spinmap-context (what else ?)
        // they must be discarded from expression as ontology specific.
        if (model.isEntity(res)) {
            return "?" + args.computeIfAbsent(res, r -> new FutureArg(arg)).key;
        }
        // just standalone IRI (variable or constant)
        if (res.isURIResource()) {
            return "<" + res.asNode().toString() + ">";
        }
        throw new MapJenaException.IllegalState("Fail to create function <" + func.name() + ">: " +
                "don't know what to do with anonymous value for the argument <" + arg.name() + ">.");
    }

    protected Resource createArgKey(int index) {
        return SP.getArgProperty(index);
    }

    /**
     * A container for {@link MapFunctionImpl.ArgImpl}, that is used while building new function query.
     */
    protected class FutureArg {
        protected final MapFunctionImpl.ArgImpl arg;
        protected String key;

        public FutureArg(MapFunctionImpl.ArgImpl arg) {
            this(UUID.randomUUID().toString(), arg);
        }

        private FutureArg(String key, MapFunctionImpl.ArgImpl arg) {
            this.key = key;
            this.arg = arg;
        }

        @Override
        public String toString() {
            return String.format("%s(%s)", key, arg.name());
        }
    }

}

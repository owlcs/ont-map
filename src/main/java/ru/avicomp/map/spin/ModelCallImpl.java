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
import org.topbraid.spin.arq.ARQ2SPIN;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import org.topbraid.spin.vocabulary.SPL;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapJenaException;
import ru.avicomp.ontapi.jena.utils.Models;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A {@link MapFunction.Call} implementation that is attached to a {@link MapModelImpl model}.
 * <p>
 * Created by @ssz on 23.11.2018.
 */
@SuppressWarnings("WeakerAccess")
public class ModelCallImpl extends MapFunctionImpl.CallImpl {
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
            // todo: meaningful exception
            throw new MapJenaException("TODO");
        }
        Model lib = manager.getLibrary();
        MapFunctionImpl from = getFunction();
        MapFunctionImpl res = newFunction(lib, name);
        Map<Resource, MapFunctionImpl.ArgImpl> args = new LinkedHashMap<>();
        String expr = String.format("SELECT ?res\nWHERE {\n\tBIND(%s AS ?res)\n}", writeExpression(res, args));
        org.apache.jena.query.Query query;
        try {
            query = manager.getFactory().createQuery(expr, model);
        } catch (QueryException qe) {
            // todo: meaningful exception
            throw new MapJenaException("TODO", qe);
        }
        Resource f = res.asResource()
                .addProperty(SPIN.returnType, lib.createResource(from.type()));
        if (from.isTarget()) {
            f.addProperty(RDF.type, SPINMAP.TargetFunction).addProperty(RDFS.subClassOf, SPINMAP.TargetFunctions);
        } else {
            f.addProperty(RDF.type, SPIN.Function).addProperty(RDFS.subClassOf, SPIN.Functions);
        }
        args.values().forEach(arg -> {
            Resource c = lib.createResource()
                    .addProperty(RDF.type, SPL.Argument)
                    .addProperty(SPL.predicate, lib.createResource(arg.name))
                    .addProperty(SPL.valueType, arg.getValueType());
            f.addProperty(SPIN.constraint, c);
            Resource a = arg.asResource();
            // copy comments and labels:
            a.listProperties(RDFS.comment).andThen(a.listProperties(RDFS.label))
                    .forEachRemaining(s -> c.addProperty(s.getPredicate(), s.getObject()));
            String dv = arg.defaultValue();
            if (dv != null) {
                c.addProperty(SPL.defaultValue, dv);
            }
            if (arg.isOptional()) {
                c.addProperty(SPL.optional, Models.TRUE);
            }
        });
        f.addProperty(SPIN.body, new ARQ2SPIN(lib).createQuery(query, null));
        manager.register(f);
        return res;
    }

    /**
     * Creates a new {@link MapFunction#isUserDefined() user-defined} function.
     *
     * @param m    {@link Model} to which the returning function will be belonged
     * @param name String, uri of new function
     * @return {@link MapFunctionImpl}
     */
    protected static MapFunctionImpl newFunction(Model m, String name) {
        return new MapFunctionImpl(m.createResource(name).as(org.topbraid.spin.model.Function.class)) {
            @Override
            public boolean isCustom() {
                return true;
            }

            @Override
            public boolean isUserDefined() {
                return true;
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
                                     Map<Resource, MapFunctionImpl.ArgImpl> args) {
        return String.format("<%s>(%s)", getFunction().name(),
                listSortedArgs().map(a -> writeExpression(a, func, args)).collect(Collectors.joining(", ")));
    }

    /**
     * Makes a string representation of an expression argument-part.
     *
     * @param arg  {@link MapFunctionImpl.ArgImpl} to parse
     * @param func a function to create, here it is used as a factory to create new {@link MapFunction.Arg}
     * @param args mutable map to store new arguments
     * @return a part of expression that corresponds the given argument
     */
    protected String writeExpression(MapFunctionImpl.ArgImpl arg,
                                     MapFunctionImpl func,
                                     Map<Resource, MapFunctionImpl.ArgImpl> args) {
        Object value = get(arg);
        if (!(value instanceof String)) {
            return ((ModelCallImpl) value).writeExpression(func, args);
        }
        RDFNode n = model.toNode((String) value);
        // literal:
        if (n.isLiteral()) {
            Literal literal = n.asLiteral();
            String txt = literal.getLexicalForm();
            String dtURI = literal.getDatatypeURI();
            // todo: what if the dt belongs to the mapping (i.e. source?)
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
            return "?" + model.getResource(args.computeIfAbsent(res,
                    r -> func.new ArgImpl(arg, SP.getArgProperty(args.size() + 1).getURI())).name())
                    .getLocalName();
        }
        // just standalone IRI (variable or constant)
        if (res.isURIResource()) {
            return "<" + res.asNode().toString() + ">";
        }
        // todo: meaningful exception
        throw new MapJenaException("TODO");
    }


}

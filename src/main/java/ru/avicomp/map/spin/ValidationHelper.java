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

import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapJenaException;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.ontapi.jena.model.*;
import ru.avicomp.ontapi.jena.utils.Iter;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.Set;
import java.util.stream.Stream;

import static ru.avicomp.map.spin.Exceptions.*;

/**
 * Auxiliary class-helper intended to validate a {@link MapFunction.Call function-call} argument values,
 * which could be either nested function or string representation of literal or resource.
 * Just to relieve the main (context) class.
 */
@SuppressWarnings("WeakerAccess")
public class ValidationHelper {
    private static final Set<Resource> PROPERTIES = Stream.of(RDF.Property,
            OWL.ObjectProperty, OWL.DatatypeProperty, OWL.AnnotationProperty).collect(Iter.toUnmodifiableSet());

    private final MapModelImpl model;
    private final MapFunction.Arg argument;

    ValidationHelper(MapModelImpl model, MapFunction.Arg argument) {
        this.model = model;
        this.argument = argument;
    }

    /**
     * Validates function argument input against specified mapping model.
     *
     * @param call {@link MapFunction} argument value (nested function call)
     * @throws MapJenaException if nested function return type does not match argument type
     */
    void testFunctionValue(MapFunction.Call call) throws MapJenaException {
        MapFunction function = call.getFunction();
        Resource type = model.createResource(argument.type());
        Resource value = model.createResource(function.type());
        Exceptions.Builder error = FUNCTION_CALL_INCOMPATIBLE_NESTED_FUNCTION.create()
                .addArg(argument)
                .addFunction(function)
                .addArgType(type)
                .addArgValue(value);
        if (type.equals(value)) {
            return;
        }
        if (AVC.undefined.equals(type) || AVC.undefined.equals(value)) {
            // seems it is okay
            return;
        }
        if (RDFS.Literal.equals(type)) {
            if (isDT(value)) return;
            throw error.build();
        }
        if (canSafeCast(type, value)) { // auto cast:
            return;
        }
        if (RDF.PlainLiteral.equals(type)) {
            if (XSD.xstring.equals(value)) return;
            throw error.build();
        }
        if (RDF.Property.equals(type)) {
            if (PROPERTIES.contains(value))
                return;
            throw error.build();
        }
        if (RDFS.Class.equals(type)) {
            if (OWL.Class.equals(value)) return;
            throw error.build();
        }
        if (RDFS.Resource.equals(type)) {
            if (!isDT(value))
                return;
            throw error.build();
        }
        throw error.build();
    }

    /**
     * Validates string argument input against specified mapping model.
     * todo: validate property ranges if it is possible
     *
     * @param string String argument value (iri or literal string form)
     * @throws MapJenaException if parameter string value does not match argument type
     */
    void testStringValue(String string) throws MapJenaException {
        Resource type = model.createResource(argument.type());
        RDFNode value = model.toNode(string);
        Exceptions.Builder error = FUNCTION_CALL_WRONG_ARGUMENT_VALUE.create()
                .addArg(argument)
                .addArgType(type)
                .addArgValue(value);
        // anything:
        if (AVC.undefined.equals(type)) {
            return;
        }
        // value is literal
        if (value.isLiteral()) {
            if (RDFS.Literal.equals(type)) return;
            Resource valueType = model.getResource(value.asLiteral().getDatatypeURI());
            if (type.equals(valueType)) return;
            if (canSafeCast(type, valueType)) { // auto-cast:
                return;
            }
            if (RDF.PlainLiteral.equals(type)) {
                if (XSD.xstring.equals(valueType)) return;
                throw error.build();
            }
            throw error.build();
        }
        // then resource
        if (RDFS.Resource.equals(type)) {
            return;
        }
        if (RDFS.Datatype.equals(type)) {
            if (value.canAs(OntDR.class)) return;
            throw error.build();
        }
        if (RDFS.Class.equals(type)) {
            if (value.canAs(OntCE.class)) return;
            throw error.build();
        }
        if (SPINMAP.Context.equals(type)) {
            if (value.asResource().hasProperty(RDF.type, SPINMAP.Context)) return;
            throw error.build();
        }
        if (RDF.Property.equals(type)) {
            //if (node.isURIResource() && node.canAs(OntPE.class)) return;
            // can be passed built-in property, e.g. rdf:type
            if (value.isURIResource()) return;
            throw error.build();
        }
        if (isDT(type) && (value.canAs(OntNDP.class) || value.canAs(OntNAP.class))) {
            // todo: validate also range for datatype properties while building mapping
            // (property can go both as iri or as assertion value, it is determined while building rule)
            return;
        }
        throw error.build();
    }

    private static boolean isDT(RDFNode node) {
        return AVC.numeric.equals(node) || node.canAs(OntDT.class);
    }

    /**
     * Tests the given function against the specified context, throwing {@link MapJenaException} on fail.
     *
     * @param function {@link MapFunction.Call} to test, not {@code null}
     * @param context  {@link ContextHelper}, not {@code null}
     * @param error    {@link MapJenaException} an error holder,
     *                 this exception will be thrown in case validation is fail
     * @return the same map-function as specified in first place if validation is OK
     * @throws MapJenaException the same exception as specified in second place
     */
    public static MapFunction.Call testFunction(MapFunction.Call function,
                                                ContextHelper context,
                                                MapJenaException error) throws MapJenaException {
        // todo: handle context in validation
        return testFunction(function, context.getModel(), error);
    }

    /**
     * Tests the given function against the specified mapping model, throwing {@link MapJenaException} on fail.
     *
     * @param function {@link MapFunction.Call} to test, not {@code null}
     * @param model    {@link MapModelImpl}, not {@code null}
     * @param error    {@link MapJenaException} an error holder,
     *                 this exception will be thrown in case validation is fail
     * @return the same map-function as specified in first place if validation is OK
     * @throws MapJenaException the same exception as specified in second place
     */
    public static MapFunction.Call testFunction(MapFunction.Call function,
                                                MapModelImpl model,
                                                MapJenaException error) throws MapJenaException {
        function.asMap().forEach((arg, value) -> {
            try {
                ValidationHelper v = new ValidationHelper(model, arg);
                if (value instanceof String) {
                    v.testStringValue((String) value);
                    return;
                }
                if (value instanceof MapFunction.Call) {
                    MapFunction.Call nested = (MapFunction.Call) value;
                    v.testFunctionValue(nested);
                    testFunction(nested, model, FUNCTION_CALL_WRONG_ARGUMENT_FUNCTION
                            .create().addFunction(nested).build());
                    return;
                }
                throw new MapJenaException.IllegalState("Should never happen, unexpected value: " + value);
            } catch (MapJenaException e) {
                error.addSuppressed(e);
            }
        });
        if (error.getSuppressed().length == 0)
            return function;
        throw error;
    }

    /**
     * Checks that the left type can be safely cast to the right type.
     * It is testing for Implicit Casting of numeric types, which is based on hierarchy and the rule
     * 'A decimal may be promoted to float, and a float may be promoted to double'.
     *
     * @param right {@link Resource}
     * @param left  {@link Resource}
     * @return boolean {@code true} if left type can be casted to the right type without precision loss
     * @see AVC#numeric
     * @see XSD
     * @see <a href='https://www.w3.org/TR/xpath-functions-31/#datatypes'>1.6.3 Atomic Type Hierarchy</a>
     * @see <a href='https://docs.microsoft.com/en-us/sql/xquery/type-casting-rules-in-xquery?view=sql-server-2017#implicit-casting'>XQuery Implicit Casting</a>
     */
    public static boolean canSafeCast(Resource right, Resource left) {
        if (right.equals(left)) return true;
        if (AVC.numeric.equals(right)) {
            return canSafeCast(XSD.xdouble, left);
        }
        if (XSD.xdouble.equals(right)) {
            return canSafeCast(XSD.xfloat, left);
        }
        if (XSD.xfloat.equals(right)) {
            return canSafeCast(XSD.decimal, left);
        }
        if (XSD.decimal.equals(right)) {
            return canSafeCast(XSD.integer, left);
        }
        if (XSD.integer.equals(right)) {
            return canSafeCast(XSD.nonPositiveInteger, left)
                    || canSafeCast(XSD.xlong, left)
                    || canSafeCast(XSD.nonNegativeInteger, left);
        }
        if (XSD.nonPositiveInteger.equals(right)) {
            return canSafeCast(XSD.negativeInteger, left);
        }
        if (XSD.xlong.equals(right)) {
            return canSafeCast(XSD.xint, left);
        }
        if (XSD.nonNegativeInteger.equals(right)) {
            return canSafeCast(XSD.unsignedLong, left) || canSafeCast(XSD.positiveInteger, left);
        }
        if (XSD.xint.equals(right)) {
            return canSafeCast(XSD.xshort, left);
        }
        if (XSD.xshort.equals(right)) {
            return canSafeCast(XSD.xbyte, left);
        }
        if (XSD.unsignedLong.equals(right)) {
            return canSafeCast(XSD.unsignedInt, left);
        }
        if (XSD.unsignedInt.equals(right)) {
            return canSafeCast(XSD.unsignedShort, left);
        }
        if (XSD.unsignedShort.equals(right)) {
            return canSafeCast(XSD.unsignedByte, left);
        }
        return false;
    }
}
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
import ru.avicomp.map.utils.ModelUtils;
import ru.avicomp.ontapi.jena.model.*;
import ru.avicomp.ontapi.jena.utils.BuiltIn;
import ru.avicomp.ontapi.jena.utils.Iter;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ru.avicomp.map.spin.Exceptions.*;

/**
 * Auxiliary class-helper intended to validate a {@link MapFunction.Call function-call} argument values,
 * which could be either nested function or string representation of literal or resource.
 * Just to relieve the main (context) class.
 */
@SuppressWarnings("WeakerAccess")
public class ValidationHelper {

    /**
     * Tests the given function against the specified context, throwing {@link MapJenaException} on fail.
     *
     * @param function {@link MapFunction.Call} to test, not {@code null}
     * @param context  {@link ContextHelper}, not {@code null}
     * @param error    {@link MapJenaException} an error holder,
     *                 this exception will be thrown in case validation is fail
     * @throws MapJenaException the same exception as specified in second place
     */
    public static void testFunction(MapFunction.Call function,
                                    ContextHelper context,
                                    MapJenaException error) throws MapJenaException {
        testFunction(function, context.getModel(), context, error);
    }

    /**
     * Tests the given function against the specified mapping model, throwing {@link MapJenaException} on fail.
     *
     * @param function {@link MapFunction.Call} to test, not {@code null}
     * @param model    {@link MapModelImpl}, not {@code null}
     * @param error    {@link MapJenaException} an error holder,
     *                 this exception will be thrown in case validation is fail
     * @throws MapJenaException the same exception as specified in second place
     */
    public static void testFunction(MapFunction.Call function,
                                    MapModelImpl model,
                                    MapJenaException error) throws MapJenaException {
        testFunction(function, model, null, error);
    }

    private static void testFunction(MapFunction.Call function,
                                     MapModelImpl model,
                                     ContextHelper context,
                                     MapJenaException error) throws MapJenaException {
        function.asMap().forEach((arg, value) -> getArgumentValidator(arg, value, model, context).validate(error));
        if (error.getSuppressed().length != 0)
            throw error;
    }

    /**
     * Creates an argument validator.
     *
     * @param arg     {@link MapFunction.Arg} argument
     * @param value   either String or {@link MapFunction.Call}
     * @param model   {@link MapModelImpl}
     * @param context {@link ContextHelper}
     * @return {@link Arg}
     */
    private static Arg getArgumentValidator(MapFunction.Arg arg,
                                            Object value,
                                            MapModelImpl model,
                                            ContextHelper context) {
        if (value instanceof String) {
            return new StringArg(arg, (String) value, model, context);
        }
        if (value instanceof MapFunction.Call) {
            return new CallArg(arg, (MapFunction.Call) value, model, context);
        }
        throw new MapJenaException.IllegalState("Should never happen, unexpected value: " + value);
    }

    /**
     * Answers {@code true} if the specified datatypes are matching.
     *
     * @param given   {@link Resource} the datatype from function argument constraint, not {@code null}
     * @param desired {@link Resource} the datatype specified as argument value (or as part of it), not {@code null}
     * @return boolean
     */
    public static boolean match(Resource given, Resource desired) {
        if (given.equals(desired)) return true;
        if (RDF.PlainLiteral.equals(given)) {
            return XSD.xstring.equals(desired);
        }
        // auto-cast:
        return canSafeCast(given, desired);
    }

    /**
     * Answers {@code true} if the given rdf-node represents a {@link OntDT OWL Datatype}.
     *
     * @param node {@link RDFNode} to test, not {@code null}
     * @return boolean
     */
    public static boolean isDT(RDFNode node) {
        return AVC.numeric.equals(node) || node.canAs(OntDT.class);
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

    private static class StringArg extends Arg {
        private StringArg(MapFunction.Arg argument,
                          String value,
                          MapModelImpl model,
                          ContextHelper context) {
            super(argument, value, model, context);
        }

        @Override
        String getValueKey() {
            return (String) value;
        }

        @Override
        void testValue() {
            String v = String.valueOf(this.value);
            if (argument.oneOf().contains(v)) {
                // then ok
                return;
            }
            Resource type = getRDFType();
            RDFNode value = model.toNode(v);
            Exceptions.Builder error = error(FUNCTION_CALL_WRONG_ARGUMENT_STRING_VALUE);
            // anything:
            if (AVC.undefined.equals(type)) {
                return;
            }
            // value is literal
            if (value.isLiteral()) {
                if (RDFS.Literal.equals(type)) {
                    return; // : any datatype can be accepted
                }
                if (match(type, model.getResource(value.asLiteral().getDatatypeURI()))) {
                    return;
                }
                throw error.build();
            }
            // value is resource
            if (RDFS.Resource.equals(type)) {
                // the given value is also resource
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
                if (SpinModels.isContext(value.asResource())) return;
                throw error.build();
            }
            if (RDF.Property.equals(type)) {
                if (context != null && getFrameFunction().isMappingPropertyFunction()) {
                    if (!context.getSourceClassProperties().contains(value)) {
                        throw error(FUNCTION_CALL_WRONG_ARGUMENT_NON_CONTEXT_PROPERTY).build();
                    }
                }
                if (value.isURIResource()) {
                    //noinspection SuspiciousMethodCalls
                    if (BuiltIn.get().reservedProperties().contains(value))
                        return;
                    if (value.canAs(OntPE.class))
                        return;
                }
                throw error(FUNCTION_CALL_WRONG_ARGUMENT_NONEXISTENT_PROPERTY).build();
            }
            if (isDT(type) && value.canAs(OntPE.class)) {
                if (!value.canAs(OntNDP.class) && !value.canAs(OntNAP.class)) {
                    // object property can't assert literals
                    throw error(FUNCTION_CALL_WRONG_ARGUMENT_OBJECT_PROPERTY).build();
                }
                if (context == null) { // can't validate
                    return;
                }
                if (!context.getSourceClassProperties().contains(value)) { // not a mapping property
                    throw error(FUNCTION_CALL_WRONG_ARGUMENT_NON_CONTEXT_PROPERTY).build();
                }
                // since actual value would be inferred, validate range for a mapping property
                if (RDFS.Literal.equals(type)) {
                    return; // : any datatype can be accepted
                }
                Set<Resource> ranges = Collections.emptySet();
                if (value.canAs(OntNDP.class)) { // datatype property
                    ranges = ModelUtils.ranges(value.as(OntNDP.class)).collect(Collectors.toSet());
                } else if (value.canAs(OntNAP.class)) { // annotation property
                    ranges = ModelUtils.ranges(value.as(OntNAP.class)).collect(Collectors.toSet());
                }
                if (ranges.isEmpty()) { // can't determine range, then can be passed everything
                    return;
                }
                if (ranges.stream().noneMatch(r -> match(type, r))) {
                    throw error(FUNCTION_CALL_WRONG_ARGUMENT_INCOMPATIBLE_RANGE).build();
                }
                return;
            }
            // unhandled situation:
            throw error(FUNCTION_CALL_WRONG_ARGUMENT_UNHANDLED_CASE).build();
        }
    }

    private static class CallArg extends Arg {
        private CallArg(MapFunction.Arg argument,
                        MapFunction.Call value,
                        MapModelImpl model,
                        ContextHelper context) {
            super(argument, value, model, context);
        }

        @Override
        String getValueKey() {
            return ((MapFunction.Call) value).getFunction().name();
        }

        @Override
        void testChain() {
            testValue();
            MapFunction.Call nested = (MapFunction.Call) value;
            testFunction(nested, model, context, FUNCTION_CALL_WRONG_ARGUMENT_FUNCTION_VALUE
                    .create().addFunction(nested).build());
        }

        @Override
        void testValue() {
            Resource type = getRDFType();
            Resource value = model.createResource(((MapFunction.Call) this.value).getFunction().type());
            Exceptions.Builder error = error(FUNCTION_CALL_INCOMPATIBLE_NESTED_FUNCTION);
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
                if (PROPERTY_TYPES.contains(value))
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
    }

    /**
     * An abstraction to validate argument value.
     */
    private abstract static class Arg {
        static final Set<Resource> PROPERTY_TYPES = Stream.of(RDF.Property,
                OWL.ObjectProperty, OWL.DatatypeProperty, OWL.AnnotationProperty)
                .collect(Iter.toUnmodifiableSet());
        protected final Object value;
        protected final MapModelImpl model;
        protected final ContextHelper context;
        protected final MapFunction.Arg argument;

        private Arg(MapFunction.Arg argument,
                    Object value,
                    MapModelImpl model,
                    ContextHelper context) {
            this.argument = Objects.requireNonNull(argument);
            this.value = Objects.requireNonNull(value);
            this.model = Objects.requireNonNull(model);
            this.context = context;
        }

        abstract void testValue();

        abstract String getValueKey();

        void testChain() {
            testValue();
        }

        void validate(MapJenaException error) {
            try {
                testChain();
            } catch (MapJenaException e) {
                error.addSuppressed(e);
            }
        }

        Exceptions.Builder error(Exceptions code) {
            return code.create().addArg(argument).add(Key.ARG_VALUE, getValueKey());
        }

        Resource getRDFType() {
            return model.createResource(argument.type());
        }

        MapFunctionImpl getFrameFunction() {
            return (MapFunctionImpl) argument.getFunction();
        }
    }
}

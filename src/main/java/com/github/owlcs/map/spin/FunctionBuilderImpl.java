/*
 * This file is part of the ONT MAP.
 * The contents of this file are subject to the Apache License, Version 2.0.
 * Copyright (c) 2019, The University of Manchester, owl.cs group.
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

package com.github.owlcs.map.spin;

import com.github.owlcs.map.MapFunction;
import com.github.owlcs.map.MapJenaException;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Property;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPINMAP;

import java.util.*;
import java.util.stream.Stream;

/**
 * The implementation of {@link MapFunction.Builder} to produce {@link MapFunctionImpl.CallImpl}.
 */
@SuppressWarnings("WeakerAccess")
public class FunctionBuilderImpl implements MapFunction.Builder {
    // either string or builder
    protected final Map<MapFunctionImpl.ArgImpl, Object> input = new HashMap<>();
    protected final MapFunctionImpl function;

    protected FunctionBuilderImpl(MapFunctionImpl function) {
        this.function = Objects.requireNonNull(function);
    }

    @Override
    public MapFunctionImpl getFunction() {
        return function;
    }

    @Override
    public FunctionBuilderImpl add(String arg, String value) {
        return put(arg, value);
    }

    @Override
    public FunctionBuilderImpl add(String arg, MapFunction.Builder other) {
        return put(arg, other);
    }

    @Override
    public FunctionBuilderImpl remove(String predicate) {
        input.keySet().stream().filter(a -> Objects.equals(a.name(), predicate)).findFirst().ifPresent(input::remove);
        return this;
    }

    @Override
    public FunctionBuilderImpl clear() {
        input.clear();
        return this;
    }

    @Override
    public FunctionBuilderImpl addLiteral(Property predicate, Literal literal) {
        return add(predicate.getURI(), function.getAsString(literal));
    }

    /**
     * Puts the predicate-value pair into the internal map
     * that will be passed into the {@link MapFunction.Call} while {@link #build() building}.
     *
     * @param predicate String, iri of predicate, not {@code null}
     * @param value     either {@link FunctionBuilderImpl} or {@link String}, not {@code null}
     * @return this instance
     * @throws MapJenaException if the given value is not suited for this function argument
     */
    protected FunctionBuilderImpl put(String predicate, Object value) throws MapJenaException {
        MapJenaException.notNull(value, "Null argument value");
        MapFunctionImpl function = getFunction();
        MapFunctionImpl.ArgImpl arg = function.getArg(predicate);
        if (!arg.isAssignable()) {
            throw Exceptions.FUNCTION_CALL_ILLEGAL_ARG_NOT_ASSIGNABLE.create().addArg(arg).build();
        }
        checkValue(arg, value);
        if (arg.isVararg()) {
            int index = nextIndex();
            arg = function.newArg(arg.arg, SP.getArgProperty(index).getURI());
        }
        input.put(arg, value);
        return this;
    }

    /**
     * @param arg argument
     * @param value either String or {@link FunctionBuilderImpl}, not {@code null}
     * @throws MapJenaException validation is fail
     */
    public void checkValue(MapFunctionImpl.ArgImpl arg, Object value) throws MapJenaException {
        if (value instanceof FunctionBuilderImpl) {
            validateValue(arg, (MapFunction.Builder) value);
            return;
        }
        if (value instanceof String) {
            return;
        }
        throw new MapJenaException.IllegalArgument("Incompatible argument value: " + value + ".");
    }

    public void validateValue(MapFunctionImpl.ArgImpl arg, MapFunction.Builder value) throws MapJenaException {
        MapFunctionImpl function = getFunction();
        FunctionBuilderImpl builder = (FunctionBuilderImpl) value;
        if (builder.calls().anyMatch(this::equals)) {
            // Attempt to build recursion
            throw Exceptions.FUNCTION_CALL_ILLEGAL_ARG_SELF_REF.create().addArg(arg).addFunction(function).build();
        }
        MapFunctionImpl nested = builder.getFunction();
        if (!function.canHaveNested()) {
            throw Exceptions.FUNCTION_CALL_ILLEGAL_ARG_CANNOT_HAVE_NESTED.create()
                    .addFunction(function)
                    .add(Exceptions.Key.ARG_VALUE, nested.name()).build();
        }
        if (!nested.canBeNested()) {
            throw Exceptions.FUNCTION_CALL_ILLEGAL_ARG_CANNOT_BE_NESTED.create()
                    .addFunction(nested)
                    .add(Exceptions.Key.ARG_VALUE, function.name())
                    .build();
        }
    }

    /**
     * Prepares next index for vararg argument.
     *
     * @return positive int
     * @see MapFunctionImpl.ArgImpl#isAutoGenerated()
     */
    protected int nextIndex() {
        return Stream.concat(getFunction().args(), input.keySet().stream())
                .map(MapFunction.Arg::name)
                .filter(MapFunctionImpl.ArgURIComparator::isIndexedArg)
                .mapToInt(MapFunctionImpl.ArgURIComparator::parseIndex)
                .max()
                .orElse(0) + 1;
    }

    /**
     * Recursively lists all associated {@link MapFunction.Builder}s including this one.
     *
     * @return Stream of {@link FunctionBuilderImpl}
     */
    public Stream<FunctionBuilderImpl> calls() {
        return Stream.concat(Stream.of(this), nestedCalls());
    }

    protected Stream<FunctionBuilderImpl> nestedCalls() {
        return input.values().stream()
                .filter(v -> v instanceof FunctionBuilderImpl)
                .map(FunctionBuilderImpl.class::cast)
                .flatMap(FunctionBuilderImpl::calls);
    }

    @Override
    public MapFunctionImpl.CallImpl build() throws MapJenaException {
        Exceptions.SpinMapException error = Exceptions.FUNCTION_CALL_BUILD_FAIL.create().addFunction(function).build();
        Map<MapFunctionImpl.ArgImpl, Object> map = new HashMap<>();
        input.forEach((key, value) -> {
            Object v;
            if (value instanceof FunctionBuilderImpl) {
                FunctionBuilderImpl b = (FunctionBuilderImpl) value;
                try {
                    v = b.build();
                } catch (Exceptions.SpinMapException j) {
                    error.addSuppressed(j);
                    v = null;
                }
            } else if (value instanceof String) {
                v = value;
            } else {
                throw new MapJenaException.IllegalState("Unexpected value: " + value);
            }
            map.put(key, v);
        });
        if (function.isTarget()) {
            // All of the spin-map target-function calls should have spinmap:_source variable
            // assigned for the argument spinmap:source...
            // although it does not seem it is really needed sometimes.
            function.arg(SPINMAP.source.getURI()).ifPresent(a -> map.put(a, SPINMAP.sourceVariable.getURI()));
        }
        // check all required arguments are assigned
        processArgValues(map, error);
        Throwable[] suppressed = error.getSuppressed();
        if (suppressed.length == 0) {
            return new MapFunctionImpl.CallImpl(function, map);
        }
        if (suppressed.length == 1) {
            throw (Exceptions.SpinMapException) suppressed[0];
        }
        throw error;
    }

    /**
     * Validates the input and sets default values.
     *
     * @param map   {@code Map} with argument values
     * @param error {@link Exceptions.SpinMapException} error-holder tp report
     */
    protected void processArgValues(Map<MapFunctionImpl.ArgImpl, Object> map,
                                    Exceptions.SpinMapException error) {
        List<MapFunctionImpl.ArgImpl> listArgs = function.getArguments();
        for (int i = 0; i < listArgs.size(); i++) {
            MapFunctionImpl.ArgImpl arg = listArgs.get(i);
            if (arg.isVararg()) {
                // must be last
                if (i == listArgs.size() - 1)
                    continue;
                throw new MapJenaException.IllegalState("Vararg is not in last place");
            }
            // has value assigned:
            if (map.containsKey(arg)) {
                // check if the prev is optional but without any value assigned:
                MapFunctionImpl.ArgImpl prev;
                if (i != 0 && !map.containsKey(prev = listArgs.get(i - 1))) {
                    if (prev.isOptional()) {
                        error.addSuppressed(Exceptions.FUNCTION_CALL_BUILD_MISSED_OPTIONAL_ARG.create().addArg(prev).build());
                    }
                }
                // process avc:oneOf
                Set<String> opts = arg.oneOf();
                if (opts.isEmpty()) {
                    continue;
                }
                Object v = map.get(arg);
                if (v instanceof String && opts.contains(v)) {
                    continue;
                }
                error.addSuppressed(Exceptions.FUNCTION_CALL_BUILD_MUST_BE_ONE_OF.create()
                        .addArg(arg)
                        .add(Exceptions.Key.ARG_VALUE, String.valueOf(v))
                        .build());
                continue;
            }
            // no value assigned:
            String def = arg.defaultValue();
            if (def != null) {
                map.put(arg, def);
                continue;
            }
            if (arg.isOptional()) {
                continue;
            }
            error.addSuppressed(Exceptions.FUNCTION_CALL_BUILD_NO_REQUIRED_ARG.create().addArg(arg).build());
        }
    }

    @Override
    public String toString() {
        return String.format("%s(%s)@%s", MapFunction.Builder.class.getSimpleName(),
                getFunction().name(), Integer.toHexString(hashCode()));
    }
}

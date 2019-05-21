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

import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapJenaException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static ru.avicomp.map.spin.Exceptions.*;

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
            throw Exceptions.FUNCTION_CALL_PUT_NOT_ASSIGNABLE_ARG.create().addArg(arg).build();
        }
        checkValue(value);
        if (arg.isVararg()) {
            int index = nextIndex();
            arg = function.newArg(arg.arg, SP.getArgProperty(index).getURI());
        }
        input.put(arg, value);
        return this;
    }

    /**
     * @param value either String or {@link FunctionBuilderImpl}, not {@code null}
     * @throws MapJenaException validation is fail
     */
    public void checkValue(Object value) throws MapJenaException {
        if (value instanceof FunctionBuilderImpl) {
            validateValue((MapFunction.Builder) value);
            return;
        }
        if (value instanceof String) {
            validateValue((String) value);
            return;
        }
        throw new MapJenaException.IllegalArgument("Incompatible argument value: " + value + ".");
    }

    @SuppressWarnings("unused")
    public void validateValue(String value) throws MapJenaException {
        // right now nothing here
    }

    public void validateValue(MapFunction.Builder value) throws MapJenaException {
        MapFunctionImpl function = getFunction();
        FunctionBuilderImpl builder = (FunctionBuilderImpl) value;
        if (builder.listCalls().anyMatch(this::equals)) {
            // Attempt to build recursion.
            throw Exceptions.FUNCTION_CALL_PUT_SELF_REF.create().addFunction(function).build();
        }
        MapFunctionImpl nested = builder.getFunction();
        if (!function.canHaveNested()) {
            throw Exceptions.FUNCTION_CALL_PUT_CANNOT_HAVE_NESTED.create()
                    .addFunction(function)
                    .add(Exceptions.Key.ARG_VALUE, nested.name()).build();
        }
        if (!nested.canBeNested()) {
            throw Exceptions.FUNCTION_CALL_PUT_CANNOT_BE_NESTED.create()
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
    public Stream<FunctionBuilderImpl> listCalls() {
        return Stream.concat(Stream.of(this), listNestedCalls());
    }

    protected Stream<FunctionBuilderImpl> listNestedCalls() {
        return input.values().stream()
                .filter(v -> v instanceof FunctionBuilderImpl)
                .map(FunctionBuilderImpl.class::cast)
                .flatMap(FunctionBuilderImpl::listCalls);
    }

    @Override
    public MapFunctionImpl.CallImpl build() throws MapJenaException {
        Exceptions.SpinMapException error = FUNCTION_CALL_BUILD_FAIL.create().addFunction(function).build();
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
        validateAndSetDefaults(map, error);
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
    protected void validateAndSetDefaults(Map<MapFunctionImpl.ArgImpl, Object> map,
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
            if (map.containsKey(arg)) {
                // already assigned as expected
                // but prev is optional without default value ?
                MapFunctionImpl.ArgImpl prev;
                if (i != 0 && !map.containsKey(prev = listArgs.get(i - 1))) {
                    if (prev.isOptional()) {
                        error.addSuppressed(FUNCTION_CALL_BUILD_MISSED_OPTIONAL_ARG.create().addArg(prev).build());
                    }
                }
                continue;
            }
            String def = arg.defaultValue();
            if (def != null) {
                map.put(arg, def);
            }
            if (arg.isOptional()) {
                continue;
            }
            error.addSuppressed(FUNCTION_CALL_BUILD_NO_REQUIRED_ARG.create().addArg(arg).build());
        }
    }

    @Override
    public String toString() {
        return String.format("%s(%s)@%s",
                MapFunction.Builder.class.getSimpleName(),
                getFunction().name(),
                Integer.toHexString(hashCode()));
    }
}

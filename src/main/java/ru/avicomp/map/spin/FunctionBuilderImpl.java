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
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static ru.avicomp.map.spin.Exceptions.FUNCTION_BUILD_FAIL;
import static ru.avicomp.map.spin.Exceptions.FUNCTION_BUILD_NO_REQUIRED_ARG;

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
     * @throws MapJenaException.IllegalArgument if the given pair is not suited for this function
     */
    protected FunctionBuilderImpl put(String predicate, Object value) throws MapJenaException.IllegalArgument {
        MapJenaException.notNull(value, "Null argument value");
        MapFunctionImpl function = getFunction();
        MapFunctionImpl.ArgImpl arg = function.getArg(predicate);
        if (!arg.isAssignable()) {
            throw new MapJenaException.IllegalArgument("Argument " + arg + " is not assignable.");
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
        FunctionBuilderImpl builder = (FunctionBuilderImpl) value;
        if (builder.listCalls().anyMatch(this::equals)) {
            throw new MapJenaException.IllegalArgument(String.format("[%s]: " +
                    "Attempt to build recursion. " +
                    "The function-call %s refers to this builder in a chain.", this, builder));
        }
        MapFunctionImpl function = getFunction();
        MapFunctionImpl nested = builder.getFunction();
        if (!function.canHaveNested()) {
            throw new MapJenaException.IllegalArgument(String.format("[%s]:" +
                    "The function %s is not allowed to accept nested functions.", this, function.name()));
        }
        if (!nested.canBeNested()) {
            throw new MapJenaException.IllegalArgument(String.format("[%s]: " +
                    "The function %s is not allowed to be a nested.", this, nested.name()));
        }
    }

    /**
     * Prepares next index for vararg argument.
     *
     * @return positive int
     */
    protected int nextIndex() {
        return Stream.concat(getFunction().args(), input.keySet().stream())
                .map(MapFunction.Arg::name)
                .filter(s -> s.matches("^.+#" + SP.ARG + "\\d+$"))
                .map(s -> s.replaceFirst("^.+(\\d+)$", "$1"))
                .mapToInt(Integer::parseInt)
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
        MapFunctionImpl function = getFunction();
        Exceptions.SpinMapException error = exception(FUNCTION_BUILD_FAIL).build();
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
        function.listArgs().forEach(a -> {
            if (a.isVararg()) return;
            if (map.containsKey(a) || a.isOptional())
                return;
            String def = a.defaultValue();
            if (def == null) {
                error.addSuppressed(exception(FUNCTION_BUILD_NO_REQUIRED_ARG).addArg(a).build());
            } else {
                map.put(a, def);
            }
        });
        Throwable[] suppressed = error.getSuppressed();
        if (suppressed.length == 0) {
            return new MapFunctionImpl.CallImpl(function, map);
        }
        if (suppressed.length == 1) {
            throw (Exceptions.SpinMapException) suppressed[0];
        }
        throw error;
    }

    protected Exceptions.Builder exception(Exceptions code) {
        return code.create().add(Exceptions.Key.FUNCTION, getFunction().name());
    }

    @Override
    public String toString() {
        return String.format("%s(%s)@%s",
                MapFunction.Builder.class.getSimpleName(),
                getFunction().name(),
                Integer.toHexString(hashCode()));
    }
}

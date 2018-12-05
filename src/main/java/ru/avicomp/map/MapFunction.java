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

package ru.avicomp.map;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResourceFactory;
import ru.avicomp.ontapi.jena.model.OntCE;
import ru.avicomp.ontapi.jena.model.OntPE;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A common interface for functions used while building OWL2 -&gt; OWL2 mappings.
 * It is not a part of jena model graph system (i.e. not a {@link org.apache.jena.rdf.model.RDFNode rdf-node}).
 * At the moment it is assumed that any string methods must return absolute IRIs.
 * <p>
 * Created by @szuev on 06.04.2018.
 */
public interface MapFunction extends Description {

    /**
     * Gets a name of function.
     *
     * @return String, iri
     */
    String name();

    /**
     * Gets a return type of function.
     *
     * @return String, iri
     */
    String type();

    /**
     * Lists all function arguments.
     * Note: some of the {@link Arg} may be not assignable, see {@link Arg#isAssignable()}.
     *
     * @return Stream of {@link Arg}s
     */
    Stream<Arg> args();

    /**
     * Answers {@code true} if it is a target function.
     * A target function allows to bind two class-expressions in one context.
     * Non-target function is used to bind property expressions.
     *
     * @return boolean
     */
    boolean isTarget();

    /**
     * Answers {@code true} if it is a boolean function and therefore can be used as filter in mappings.
     *
     * @return boolean
     */
    boolean isBoolean();

    /**
     * Answers {@code true} if it is a user-defined function.
     *
     * @return boolean
     */
    boolean isUserDefined();

    /**
     * Creates a new function call builder.
     *
     * @return {@link Builder}
     */
    Builder create();

    /**
     * Lists all functions that this one depends on.
     * If a function is SPARQL-based it may depend on other functions,
     * which in turn may also rely on some other SPARQL or ARQ(java) based functions.
     *
     * @return <b>distinct</b> Stream of {@link MapFunction}s
     * @see Call#functions()
     * @see MapResource#functions()
     */
    Stream<MapFunction> dependencies();

    /**
     * Answers {@code true} iff this function supports varargs.
     * Most functions does not support varargs and this method will return {@code false}.
     * Examples of vararg functions: {@code sp:concat}, {@code sp:in}.
     * To avoid ambiguous situations it is expected that vararg function has one and only one vararg argument.
     *
     * @return true if function has varargs
     */
    default boolean isVararg() {
        return args().anyMatch(Arg::isVararg);
    }

    /**
     * Gets an argument by predicate iri.
     *
     * @param predicate String, predicate iri
     * @return {@link Arg}
     * @throws MapJenaException in case no arg found
     */
    default Arg getArg(String predicate) throws MapJenaException {
        return args()
                .filter(f -> Objects.equals(predicate, f.name()))
                .findFirst()
                .orElseThrow(() -> new MapJenaException("Function (" + name() + ") argument " + predicate + " not found."));
    }

    /**
     * Answers iff this function contains an argument given by its name (predicate).
     *
     * @param predicate String, not null
     * @return {@code true} if argument is present
     */
    default boolean hasArg(String predicate) {
        return args().map(Arg::name).anyMatch(predicate::equals);
    }

    /**
     * A function argument.
     */
    interface Arg extends Description {

        /**
         * Returns an argument name (an iri of predicate currently).
         *
         * @return String, iri
         */
        String name();

        /**
         * Returns an argument type.
         *
         * @return String, iri
         */
        String type();

        /**
         * Gets a default value as string.
         *
         * @return String (iri) or {@code null}
         */
        String defaultValue();

        /**
         * Answers whether the argument must have an assigned value on the function call.
         *
         * @return {@code true} if it is optional argument
         */
        boolean isOptional();

        /**
         * Answers {@code true} if this argument is a fictitious indicator for a function that supports varargs.
         *
         * @return {@code true} in case of vararg
         */
        boolean isVararg();

        /**
         * Returns a function to which this argument belongs.
         *
         * @return {@link MapFunction}
         */
        MapFunction getFunction();

        /**
         * Answers {@code true} if this argument is available to assign value.
         * An argument could be inherited from parent function, or be hidden, or be disabled for some other reason.
         * In these cases the arg is not allowed to be used while building a function-call.
         *
         * @return boolean
         */
        default boolean isAssignable() {
            return true;
        }
    }

    /**
     * A Function Call, that is a ready-to-write container which contains a function with all its assigned arguments.
     * Inside a graph it represents a mapping expression.
     * Can hold only string representations of literals and resources or another {@link Call}s,
     * in last case it can be named a function-chain.
     * Cannot be modified.
     * Note: it is not a {@link org.apache.jena.rdf.model.Resource Jena Resorce}.
     */
    interface Call {

        /**
         * Returns a direct list of arguments, which are used as keys for the calls parameters.
         * The result stream is distinct and sorted by {@link Arg#name() argument name}.
         *
         * @return Stream of {@link Arg}s
         */
        Stream<Arg> args();

        /**
         * Returns a value assigned to the call for the specified argument.
         *
         * @param arg {@link Arg}
         * @return either String or another {@link Call function-call}
         * @throws MapJenaException in case no value found
         */
        Object get(Arg arg) throws MapJenaException;

        /**
         * Lists nested function calls.
         *
         * @param direct if {@code true} only top-level functions will be listed
         * @return <b>not</b> distinct Stream of {@link MapFunction.Call}
         * @see MapFunction#dependencies()
         */
        Stream<MapFunction.Call> functions(boolean direct);

        /**
         * Represents a call as unmodifiable builder instance.
         * To use in default methods.
         *
         * @return {@link Builder}
         */
        Builder asUnmodifiableBuilder();

        /**
         * Answers a function from which this call was created.
         *
         * @return {@link MapFunction}, never {@code null}
         */
        default MapFunction getFunction() {
            return asUnmodifiableBuilder().getFunction();
        }

        /**
         * Lists all nested function calls.
         * The returned Stream may contain duplicates.
         *
         * @return <b>not</b> distinct Stream of {@link MapFunction.Call}s
         */
        default Stream<MapFunction.Call> functions() {
            return functions(false);
        }

        /**
         * Represents this function call as java Map.
         * Implementations are free to return more wide map including hidden or inherited arguments.
         * Just for convenience, not to use in GUI.
         *
         * @return Map
         */
        default Map<Arg, Object> asMap() {
            return args().collect(Collectors.toMap(Function.identity(), this::get));
        }

        /**
         * Creates a fresh map-function from this function-chain.
         * <p>
         * Note: currently this functionality is available only to those {@link Call}s,
         * that belong to a {@link MapModel mapping}s.
         * In abstract (manager) level usage of this method will cause a {@link MapJenaException}.
         * To get models calls use {@link MapResource#getMapping()} and {@link MapResource#getFilter()}.
         *
         * @param name String, a new function name, not {@code null}
         * @return {@link MapFunction} a synonym for this function-chain
         * @throws MapJenaException unable to compose new function
         */
        default MapFunction save(String name) throws MapJenaException {
            throw new MapJenaException.Unsupported("This functionality is available only for function-calls " +
                    "that are attached to a mapping model");
        }
    }

    /**
     * A builder to create {@link Call function-call}.
     * <p>
     * Created by @szuev on 10.04.2018.
     */
    interface Builder {

        /**
         * Adds an argument value to a future function call.
         * The value must be an absolute iri or full literal form with an absolute datatype iri in the right part,
         * e.g. {@code "1"^^<http://www.w3.org/2001/XMLSchema#int>}.
         * Note: if an input cannot be treated as resource or literal inside model,
         * then it is assumed that it is a plain (string) literal,
         * e.g. if you set 'Anything' it would be actually {@code "Anything"^^<http://www.w3.org/2001/XMLSchema#string>}.
         *
         * @param predicate String, {@link Arg#name()}, not {@code null}
         * @param value     String, value, not {@code null}
         * @return this builder
         * @throws MapJenaException if wrong input
         */
        Builder add(String predicate, String value) throws MapJenaException;

        /**
         * Adds another function-call as argument to this function call, which will be available after building
         *
         * @param predicate String, iri, not null
         * @param other     {@link Builder}, not null
         * @return this builder
         * @throws MapJenaException if wrong input
         */
        Builder add(String predicate, Builder other) throws MapJenaException;

        /**
         * Answers a reference to function.
         *
         * @return {@link MapFunction}
         */
        MapFunction getFunction();

        /**
         * Builds a function call.
         *
         * @return {@link Call}, ready to use result.
         * @throws MapJenaException if building is not possible
         */
        Call build() throws MapJenaException;

        default Builder addClass(Property predicate, OntCE ce) {
            return add(predicate.getURI(), ce.asNode().toString());
        }

        default <P extends OntPE & Property> Builder addProperty(Property predicate, P property) {
            return add(predicate.getURI(), property.getURI());
        }

        default Builder addLiteral(Property predicate, Object value) {
            return add(predicate.getURI(), ResourceFactory.createTypedLiteral(value).toString());
        }

        default Builder addFunction(Property predicate, Call function) {
            return addFunction(predicate, function.asUnmodifiableBuilder());
        }

        default Builder addFunction(Property predicate, Builder function) {
            return add(predicate.getURI(), function);
        }
    }
}

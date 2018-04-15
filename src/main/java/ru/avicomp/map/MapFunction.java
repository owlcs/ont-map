package ru.avicomp.map;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * This is going to be a wrapper for {@link org.topbraid.spin.model.Function} and
 * common interface for any possible functions used while OWL2 -> OWL2 mappings.
 * It is not a part of jena model graph system (i.e. not a {@link org.apache.jena.rdf.model.RDFNode rdf-node}).
 * Currently, the methods result are assumed to be a full IRIs.
 * TODO: not ready. Implement creation of function-call
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
     * @return String, iri.
     */
    String returnType();

    /**
     * Returns a list of function arguments.
     *
     * @return Stream of {@link Arg}s.
     */
    Stream<Arg> args();

    /**
     * Answers if it is a target function.
     * A target function allows to bind two class-expressions in one context.
     * Non-target function is used to bind property expressions.
     *
     * @return boolean
     */
    boolean isTarget();

    /**
     * Creates a new function call builder.
     *
     * @return {@link Builder}
     */
    Builder createFunctionCall();

    /**
     * Gets an argument by predicate iri.
     *
     * @param predicate String, predicate iri
     * @return {@link Arg}
     * @throws MapJenaException in case no arg found.
     */
    default Arg getArg(String predicate) throws MapJenaException {
        return args()
                .filter(f -> Objects.equals(predicate, f.name()))
                .findFirst()
                .orElseThrow(() -> new MapJenaException("Function (" + name() + ") argument " + predicate + " not found."));
    }

    /**
     * A function argument.
     */
    interface Arg extends Description {

        /**
         * Returns an argument name (an iri of predicate currently).
         *
         * @return String
         */
        String name();

        /**
         * Returns an argument type.
         *
         * @return String
         */
        String type();

        /**
         * Gets a default value as string.
         *
         * @return String (iri) or null
         */
        String defaultValue();

        /**
         * Answers whether the argument must have an assigned value on the function call.
         *
         * @return boolean, true if it is optional argument
         */
        boolean isOptional();

        /**
         * Answers iff this argument is available to assign value.
         *
         * @return boolean
         */
        default boolean isAssignable() {
            return true;
        }
    }

    /**
     * A Function Call,
     * i.e. a container which contains function with assigned arguments ready for writing to graph.
     * Cannot be modified.
     * Note: it is not a {@link org.apache.jena.rdf.model.Resource jena resorce}.
     */
    interface Call {

        Map<Arg, Object> asMap();

        /**
         * Represents a call as unmodifiable builder instance.
         * To use in default methods.
         *
         * @return {@link Builder}
         */
        Builder asUnmodifiableBuilder();

        default MapFunction getFunction() {
            return asUnmodifiableBuilder().getFunction();
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
         *
         * @param predicate String, iri, not null
         * @param value     String, value, not null.
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

        default Builder add(Arg arg, Call function) throws MapJenaException {
            return add(arg.name(), function.asUnmodifiableBuilder());
        }

        /**
         * Builds a function call.
         *
         * @return {@link Call}, ready to use result.
         * @throws MapJenaException if building is not possible
         */
        Call build() throws MapJenaException;

    }
}

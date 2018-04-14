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

    boolean isTarget();

    FunctionBuilder createFunctionCall();

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
         * Presents a call as unmodifiable builder instance.
         * To use in default methods.
         *
         * @return {@link FunctionBuilder}
         */
        FunctionBuilder asUnmodifiableBuilder();

        default MapFunction getFunction() {
            return asUnmodifiableBuilder().getFunction();
        }
    }
}

package ru.avicomp.map;

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
     * Returns a list of function arguments
     *
     * @return Stream of {@link Arg}s.
     */
    Stream<Arg> args();

    boolean isTarget();

    FunctionBuilder createFunctionCall();

    /**
     * Finds an argument by predicate iri
     *
     * @param predicate String, predicate iri
     * @return {@link Arg}
     * @throws MapJenaException in case no
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
        String name();

        String type();

        boolean isOptional();
    }

    interface Call {
        MapFunction getFunction();
    }
}

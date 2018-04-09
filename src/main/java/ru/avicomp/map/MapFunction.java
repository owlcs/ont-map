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
public interface MapFunction {

    /**
     * Gets a name of function.
     *
     * @return String, iri
     */
    String name();

    /**
     * Gets a return type of function.
     * @return String, iri.
     */
    String returnType();

    /**
     * Returns a list of function arguments
     * @return Stream of {@link Arg}s.
     */
    Stream<Arg> args();

    boolean isTarget();

    Builder createFunctionCall();

    /**
     * Returns a {@code rdfs:comment} concatenated for the specified lang with symbol '\n'.
     *
     * @param lang String or null to get default
     * @return String comment
     */
    String getComment(String lang);

    default String getComment() {
        return getComment(null);
    }

    /**
     * Finds an argument by predicate iri
     *
     * @param name String, predicate iri
     * @return {@link Arg}
     * @throws MapJenaException in case no
     */
    default Arg getArg(String name) throws MapJenaException {
        return args()
                .filter(f -> Objects.equals(name, f.name()))
                .findFirst()
                .orElseThrow(() -> new MapJenaException("Function (" + name() + ") argument " + name + " not found."));
    }

    /**
     * Function argument.
     */
    interface Arg {
        String name();

        String type();

        boolean isOptional();

        /**
         * Returns string comment.
         *
         * @param lang String lang, null to get default
         * @return String
         * @see MapFunction#getComment(String)
         */
        String getComment(String lang);

        default String getComment() {
            return getComment(null);
        }
    }

    interface Builder {
        Builder add(Arg arg, Object value);

        Call build();
    }

    interface Call {
        MapFunction getFunction();
    }
}

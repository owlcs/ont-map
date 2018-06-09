package ru.avicomp.map;

/**
 * A package-local common interface to provide description based on {@code rdfs:comment} and {@code rdfs:label}.
 * <p>
 * Created by @szuev on 10.04.2018.
 */
interface Description {

    /**
     * Returns a {@code rdfs:comment} concatenated for the specified lang with symbol '\n'.
     *
     * @param lang String or null to get default
     * @return String comment
     */
    String getComment(String lang);

    /**
     * Returns a {@code rdfs:label} concatenated for the specified lang with symbol '\n'.
     *
     * @param lang String or null to get default
     * @return String label
     */
    String getLabel(String lang);

    /**
     * Returns default (no lang) merged comment.
     *
     * @return String
     */
    default String getComment() {
        return getComment(null);
    }

    /**
     * Returns default (no lang) merged label.
     *
     * @return String
     */
    default String getLabel() {
        return getLabel(null);
    }

}

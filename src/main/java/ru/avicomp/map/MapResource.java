package ru.avicomp.map;

import ru.avicomp.ontapi.jena.model.OntObject;

/**
 * Common interface for class and property bridges.
 * <p>
 * Created by @szuev on 05.06.2018.
 */
interface MapResource {

    /**
     * Wraps this map-resource as ont-resource.
     * This may be useful if you want to add annotations to the mapping rule.
     *
     * @return {@link OntObject}
     */
    OntObject asResource();


    /**
     * Returns a function call that describes this bridge.
     *
     * @return {@link MapFunction.Call}
     */
    MapFunction.Call getMapping();
}

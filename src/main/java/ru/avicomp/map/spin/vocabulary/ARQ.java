package ru.avicomp.map.spin.vocabulary;

import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 * The vocabulary for ARQ functions.
 * The namespace is deprecated, but it is used by spin-family (see file://resources/etc/functions-afn.ttl),
 * so we can either put up with it, or create our own library with the same functions but with minor customization changes.
 * The second way is easier.
 * Customization is delegated to AVC library (see file://resources/etc/avc.spin.ttl, ontology http://avc.ru/spin).
 * Created by @szuev on 11.06.2018.
 *
 * @see org.apache.jena.sparql.ARQConstants#ARQFunctionLibraryURI
 * @see org.apache.jena.sparql.ARQConstants#ARQFunctionLibraryURI_Jena2
 */
public class ARQ {
    // old ns:
    public static final String BASE_URI = "http://jena.hpl.hp.com/ARQ/function";
    public static final String URI = BASE_URI + "/";
    public static final String NS = BASE_URI + "#";

    /**
     * Can handle only numeric literals.
     *
     * @see org.apache.jena.sparql.function.library.max
     */
    public static final Resource max = resource("max");
    /**
     * Can handle only numeric literals.
     *
     * @see org.apache.jena.sparql.function.library.min
     */
    public static final Resource min = resource("min");

    public static Resource resource(String local) {
        return ResourceFactory.createResource(NS + local);
    }
}

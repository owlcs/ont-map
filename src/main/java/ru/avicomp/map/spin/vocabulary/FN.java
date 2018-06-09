package ru.avicomp.map.spin.vocabulary;

import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 * The vocabulary which describes https://www.w3.org/2005/xpath-functions/.
 * It is already defined in functions-fn.ttl, but not all functions are included.
 * <p>
 * Created by @szuev on 09.06.2018.
 *
 * @see org.apache.jena.sparql.function.StandardFunctions
 * @see org.apache.jena.sparql.ARQConstants#fnPrefix
 */
public class FN {
    public static final String BASE_URI = "http://www.w3.org/2005/xpath-functions";
    public static final String URI = BASE_URI + "/";
    public static final String NS = BASE_URI + "#";

    public static final Resource abs = resource("abs");

    public static Resource resource(String local) {
        return ResourceFactory.createResource(NS + local);
    }
}

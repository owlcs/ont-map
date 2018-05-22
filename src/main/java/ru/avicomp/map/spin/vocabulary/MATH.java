package ru.avicomp.map.spin.vocabulary;

import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import ru.avicomp.map.spin.SystemModels;

/**
 * The vocabulary which describes https://www.w3.org/2005/xpath-functions/math/.
 * An addition to the spin-family in order to provide access to math functions through spin-api.
 * See file://resources/etc/avc.math.ttl
 * TODO: not ready
 * <p>
 * Created by @szuev on 22.05.2018.
 *
 * @see org.apache.jena.sparql.function.StandardFunctions
 */
public class MATH {
    public static final String BASE_URI = SystemModels.Resources.MATH.getURI();
    public static final String NS = BASE_URI + "#";

    public static Resource cos = resource("cos");

    public static Resource resource(String local) {
        return ResourceFactory.createResource(NS + local);
    }
}

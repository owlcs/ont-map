package ru.avicomp.map.spin.vocabulary;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import ru.avicomp.map.spin.SystemModels;

/**
 * The vocabulary which describes http://avc.ru/spin.
 * An addition to the spin-family in order to customize spin-function behaviour.
 * See file://resources/etc/avc.spin.ttl
 * <p>
 * Created by @szuev on 07.04.2018.
 */
public class AVC {
    public static final String BASE_URI = SystemModels.Resources.AVC.getURI();
    public static final String NS = BASE_URI + "#";

    public static final Property hidden = property("hidden");

    // additional no-arg target function
    public static final Resource UUID = resource("UUID");

    // expression predicate to use in conditional templates as a filter
    public static final Property filter = property("filter");

    /**
     * The analogue of spinmap:Conditional-Mapping-1-1 but accepting expression (function call), not ask-query.
     *
     * @param i int
     * @param j int
     * @return Resource
     */
    public static Resource mapping(int i, int j) {
        if (i < 0 || j <= 0) throw new IllegalArgumentException();
        return resource(String.format("Conditional-Mapping-%d-%d", i, j));
    }

    public static Resource resource(String local) {
        return ResourceFactory.createResource(NS + local);
    }

    public static Property property(String local) {
        return ResourceFactory.createProperty(NS + local);
    }


}

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

    public static final String DEFAULT_PREDICATE_SUFFIX = "DefaultValue";

    public static final Property hidden = property("hidden");

    // additional no-arg target function
    public static final Resource UUID = resource("UUID");

    // additional property function that is used to provide a mapping mechanism for assertion default values
    public static final Resource withDefault = resource("withDefault");

    // resource, which is used as return type of function or argument
    public static final Resource undefined = resource("undefined");

    // expression predicate to use in conditional templates as a filter
    public static final Property filter = property("filter");

    // to customise spin:constraint arguments, sometimes they are wrong in the standard spin library
    public final static Property constraint = property("constraint");

    public static Resource Mapping(String filters, String sources) {
        return resource(String.format("Mapping--%s--%s--%d", filters, sources, 1));
    }

    public static Property sourceDefaultValue(String pref) {
        return property(pref + DEFAULT_PREDICATE_SUFFIX);
    }

    public static Resource resource(String local) {
        return ResourceFactory.createResource(NS + local);
    }

    public static Property property(String local) {
        return ResourceFactory.createProperty(NS + local);
    }


}

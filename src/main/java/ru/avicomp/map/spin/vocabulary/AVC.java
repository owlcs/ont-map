package ru.avicomp.map.spin.vocabulary;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import ru.avicomp.map.spin.SystemModels;

/**
 * The vocabulary which describes http://avc.ru/spin (an addition to the spin-family in order to customize spin-function behaviour)
 * and http://avc.ru/lib (functions).
 * See file://resources/etc/avc.spin.ttl and file://resources/etc/avc.lib.ttl
 * <p>
 * Created by @szuev on 07.04.2018.
 */
public class AVC {
    public static final String BASE_URI = SystemModels.Resources.AVC.getURI();
    public static final String LIB_URI = SystemModels.Resources.AVC_LIB.getURI();
    public static final String NS = BASE_URI + "#";

    public static final String DEFAULT_PREDICATE_SUFFIX = "DefaultValue";

    public static final Property hidden = property("hidden");

    // functions class to indicate that function is "magic" and may not work as expected in Composer
    public static final Resource MagicFunctions = resource("MagicFunctions");
    // spin-functions which use SPARQL aggregate functions
    public static final Resource AggregateFunctions = resource("AggregateFunctions");

    // a virtual number datatype to be used as function or argument type restriction.
    // it corresponds xs:numeric
    // see also https://www.w3.org/TR/sparql11-query/#operandDataTypes
    public static final Resource numeric = resource("numeric");

    // additional no-arg target function, for debugging and for convenience' sake
    public static final Resource UUID = resource("UUID");

    // additional single-arg target function, for debugging and for convenience' sake
    public static final Resource IRI = resource("IRI");

    // analogue of spl:object with filter
    public static final Resource objectWithFilter = resource("objectWithFilter");

    // additional map property function that is used to mapping to pass a default value
    // in case there is no data assertion on individual
    public static final Resource withDefault = resource("withDefault");
    // additional map property function that is used by mapping to get a property IRI as is,
    // i.e. not a value from a data assertion.
    public static final Resource asIRI = resource("asIRI");

    //  A magic function to get current individual while inference
    public static final Resource currentIndividual = resource("currentIndividual");

    // An aggregate function to concat values from assertions with the same individual and property
    public static final Resource groupConcat = resource("groupConcat");

    // resource, which is used as return type of function or argument in unclear case;
    // actually this means any rdf-node
    public static final Resource undefined = resource("undefined");

    // indicator that a function supports varargs
    public static final Property vararg = property("vararg");

    // require special treatment in runtime before inference,
    // the right part of statement with this predicate must be
    // a valid class-path to ru.avicomp.map.spin.AdjustFunctionBody impl as a string literal
    public static final Property runtime = property("runtime");

    // expression predicate to use in conditional templates as a filter
    public static final Property filter = property("filter");

    // to customise spin:constraint arguments, sometimes they are wrong in the standard spin library
    public static final Property constraint = property("constraint");

    // to customise function return type, sometimes it is wrong in the standard spin library
    public static final Property returnType = property("returnType");

    // an universal filtering mapping template name
    public static Resource Mapping(String filters, String sources) {
        return createMapping("Mapping", filters, sources);
    }

    // a property mapping template name
    public static Resource PropertyMapping(String filters, String sources) {
        return createMapping("PropertyMapping", filters, sources);
    }

    private static Resource createMapping(String prefix, String filters, String sources) {
        return resource(String.format("%s-f%s-s%s-t%d", prefix, filters, sources, 1));
    }

    public static Property predicateDefaultValue(String pref) {
        return property(pref + DEFAULT_PREDICATE_SUFFIX);
    }

    public static Resource resource(String local) {
        return ResourceFactory.createResource(NS + local);
    }

    public static Property property(String local) {
        return ResourceFactory.createProperty(NS + local);
    }


}

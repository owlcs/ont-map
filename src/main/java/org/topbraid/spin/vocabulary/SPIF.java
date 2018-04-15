package org.topbraid.spin.vocabulary;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import ru.avicomp.map.spin.SystemModels;

/**
 * Vocabulary of SPIF Library.
 * SPIF is a library of "generally useful" SPARQL functions defined using SPIN.
 * This library consists of functions that are impossible or difficult to express in terms of other functions,
 * but rather will require a native implementation in languages like Java.
 * In contrast, the SPL (http://spinrdf.org/spl#) namespace is reserved for functions that can be expressed entirely
 * in terms of other SPARQL expressions and standard built-ins.
 * <p>
 * Created by @szuev on 13.04.2018.
 */
public class SPIF {
    public static final String BASE_URI = SystemModels.Resources.SPIF.getURI();
    public static final String NS = BASE_URI + "#";
    public static final String PREFIX = "spif";
    public static final String ARG_NS = "http://spinrdf.org/arg#";
    public static final String ARG_PREFIX = "arg";

    public static final String BUILD_STRING_FROM_RDF_LIST_MEMBER_VAR_REF = "{?member}";

    public static final Resource regex = resource("regex");
    public static final Resource buildUniqueURI = resource("buildUniqueURI");
    public static final Resource buildURI = resource("buildURI");
    public static final Resource parseDate = resource("parseDate");
    public static final Resource dateFormat = resource("dateFormat");
    public static final Resource cast = resource("cast");
    public static final Resource buildStringFromRDFList = resource("buildStringFromRDFList");
    public static final Property range = property("range");

    public static final Property argDatatype = argProperty("datatype");
    public static final Property argDate = argProperty("date");
    public static final Property argNumber = argProperty("number");
    public static final Property argPattern = argProperty("pattern");


    public static Resource resource(String local) {
        return ResourceFactory.createResource(NS + local);
    }

    public static Property property(String local) {
        return ResourceFactory.createProperty(NS + local);
    }

    public static Property argProperty(String local) {
        return ResourceFactory.createProperty(ARG_NS + local);
    }
}

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

    // functions:
    public static final Resource regex = ResourceFactory.createResource(NS + "regex");
    public static final Resource buildUniqueURI = ResourceFactory.createProperty(NS + "buildUniqueURI");
    public static final Resource buildURI = ResourceFactory.createProperty(NS + "buildURI");
    public static final Resource parseDate = ResourceFactory.createProperty(NS + "parseDate");
    public static final Resource dateFormat = ResourceFactory.createProperty(NS + "dateFormat");
    public static final Resource cast = ResourceFactory.createResource(NS + "cast");
    public static final Resource buildStringFromRDFList = ResourceFactory.createResource(NS + "buildStringFromRDFList");

    // properties:
    public static final Property argDatatype = ResourceFactory.createProperty(ARG_NS + "datatype");
    public static final Property argDate = ResourceFactory.createProperty(ARG_NS + "date");
    public static final Property argNumber = ResourceFactory.createProperty(ARG_NS + "number");
    public static final Property argPattern = ResourceFactory.createProperty(ARG_NS + "pattern");
}

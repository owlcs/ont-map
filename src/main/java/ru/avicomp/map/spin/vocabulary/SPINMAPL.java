package ru.avicomp.map.spin.vocabulary;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import ru.avicomp.map.spin.SystemModels;

/**
 * Vocabulary of the Topbraid SPINMAPL library.
 * <p>
 * Created by @szuev on 10.04.2018.
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class SPINMAPL {
    public static final String BASE_URI = SystemModels.Resources.SPINMAPL.getURI();
    public static final String NS = BASE_URI + "#";
    public final static String PREFIX = "sinmapl";

    public static final String SMF_URI = "http://topbraid.org/functions-smf";
    public static final String AFN_NS = "http://jena.hpl.hp.com/ARQ/function#";
    public static final String AFN_PREFIX = "afn";
    public static final String FN_URI = "http://topbraid.org/functions-fn";
    public static final String FN_NS = "http://www.w3.org/2005/xpath-functions#";
    public static final String FN_PREFIX = "fn";
    public static final String SMF_NS = "http://topbraid.org/sparqlmotionfunctions#";
    public static final String SMF_PREFIX = "smf";

    public static final String OWL_RL_PROPERTY_CHAIN_HELPER = "http://topbraid.org/spin/owlrl#propertyChainHelper";

    // functions:
    public static final Resource self = resource("self");
    public static final Resource concatWithSeparator = resource("concatWithSeparator");
    public static final Resource buildURI1 = resource("buildURI1");
    public static final Resource buildURI2 = resource("buildURI2");
    public static final Resource buildURI3 = resource("buildURI3");
    public static final Resource buildURI4 = resource("buildURI4");
    public static final Resource buildURI5 = resource("buildURI5");
    public static final Resource relatedSubjectContext = resource("relatedSubjectContext");
    public static final Resource relatedObjectContext = resource("relatedObjectContext");
    public static final Resource changeNamespace = resource("changeNamespace");
    public static final Resource composeURI = resource("composeURI");
    public static final Resource concat = ResourceFactory.createResource(FN_NS + "concat");

    // properties:
    public static final Property separator = property("separator");
    public static final Property template = property("template");
    public static final Property context = property("context");
    public static final Property predicate = property("predicate");
    public static final Property targetNamespace = property("targetNamespace");

    public static Resource resource(String local) {
        return ResourceFactory.createResource(NS + local);
    }

    public static Property property(String local) {
        return ResourceFactory.createProperty(NS + local);
    }
}

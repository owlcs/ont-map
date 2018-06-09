package org.topbraid.spin.vocabulary;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import ru.avicomp.map.spin.SpinModelConfig;
import ru.avicomp.map.spin.SystemModels;

/**
 * A copy-paste from spin.
 * Created by @szuev on 13.04.2018.
 *
 * @see SP description about reasons of copy-pasting
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class SPL {
    public static final String BASE_URI = SystemModels.Resources.SPL.getURI();
    public static final String NS = BASE_URI + "#";
    public static final String PREFIX = "spl";

    public static final Resource Argument = resource("Argument");
    public static final Resource Attribute = resource("Attribute");
    public static final Resource InferDefaultValue = resource("InferDefaultValue");
    public static final Resource ObjectCountPropertyConstraint = resource("ObjectCountPropertyConstraint");
    public static final Resource primaryKeyProperty = resource("primaryKeyProperty");
    public static final Resource primaryKeyURIStart = resource("primaryKeyURIStart");
    public static final Resource PrimaryKeyPropertyConstraint = resource("PrimaryKeyPropertyConstraint");
    public static final Resource PropertyConstraintTemplates = resource("PropertyConstraintTemplates");
    public static final Resource RunTestCases = resource("RunTestCases");
    public static final Resource SPINOverview = resource("SPINOverview");
    public static final Resource TestCase = resource("TestCase");
    public static final Resource UnionTemplate = resource("UnionTemplate");
    public static final Resource object = resource("object");
    public static final Resource objectCount = resource("objectCount");
    public static final Resource subjectCount = resource("subjectCount");
    public static final Resource StringFunctions = resource("StringFunctions");
    public static final Resource ConstraintTemplate = resource("ConstraintTemplate");
    public static final Resource OntologyFunctions = resource("OntologyFunctions");
    public static final Resource MathematicalFunctions = resource("MathematicalFunctions");
    public static final Resource BooleanFunctions = resource("BooleanFunctions");
    public static final Resource DateFunctions = resource("DateFunctions");
    public static final Resource MiscFunctions = resource("MiscFunctions");
    public static final Resource URIFunctions = resource("URIFunctions");

    public static final Property defaultValue = property("defaultValue");
    public static final Property dynamicEnumRange = property("dynamicEnumRange");
    public static final Property hasValue = property("hasValue");
    public static final Property maxCount = property("maxCount");
    public static final Property minCount = property("minCount");
    public static final Property optional = property("optional");
    public static final Property predicate = property("predicate");
    public static final Property valueType = property("valueType");

    public static Resource resource(String local) {
        return ResourceFactory.createResource(NS + local);
    }

    public static Property property(String local) {
        return ResourceFactory.createProperty(NS + local);
    }

    public static Model getModel() {
        return SpinModelConfig.createSpinModel(SystemModels.graphs().get(BASE_URI));
    }
}

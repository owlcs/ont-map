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
public class SPL {
    public final static String BASE_URI = SystemModels.Resources.SPL.getURI();
    public final static String NS = BASE_URI + "#";
    public final static String PREFIX = "spl";

    public final static Resource Argument = resource("Argument");
    public final static Resource Attribute = resource("Attribute");
    public final static Resource InferDefaultValue = resource("InferDefaultValue");
    public final static Resource ObjectCountPropertyConstraint = resource("ObjectCountPropertyConstraint");
    public final static Resource primaryKeyProperty = resource("primaryKeyProperty");
    public final static Resource primaryKeyURIStart = resource("primaryKeyURIStart");
    public final static Resource PrimaryKeyPropertyConstraint = resource("PrimaryKeyPropertyConstraint");
    public final static Resource PropertyConstraintTemplates = resource("PropertyConstraintTemplates");
    public final static Resource RunTestCases = resource("RunTestCases");
    public final static Resource SPINOverview = resource("SPINOverview");
    public final static Resource TestCase = resource("TestCase");
    public final static Resource UnionTemplate = resource("UnionTemplate");
    public final static Resource object = resource("object");
    public final static Resource objectCount = resource("objectCount");
    public final static Resource subjectCount = resource("subjectCount");
    public final static Resource StringFunctions = resource("StringFunctions");
    public final static Resource ConstraintTemplate = resource("ConstraintTemplate");

    public final static Property defaultValue = property("defaultValue");
    public static final Property dynamicEnumRange = property("dynamicEnumRange");
    public final static Property hasValue = property("hasValue");
    public final static Property maxCount = property("maxCount");
    public final static Property minCount = property("minCount");
    public final static Property optional = property("optional");
    public final static Property predicate = property("predicate");
    public final static Property valueType = property("valueType");

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

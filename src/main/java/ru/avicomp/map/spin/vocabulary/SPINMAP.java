package ru.avicomp.map.spin.vocabulary;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import ru.avicomp.map.spin.SystemModels;

/**
 * Vocabulary for http://spinrdf.org/spinmap
 * <p>
 * Created by @szuev on 07.04.2018.
 *
 * @see org.topbraid.spin.vocabulary.SPINMAP
 */
public class SPINMAP {

    public static final String BASE_URI = SystemModels.Resources.SPINMAP.getURI();
    public static final String NS = BASE_URI + "#";
    public static final String PREFIX = "spinmap";

    public static final Resource Conditional_Mapping_1 = resource("Conditional-Mapping-1");
    public static final Resource Conditional_Mapping_1_1 = resource("Conditional-Mapping-1-1");

    public static final String TARGET_PREDICATE = "targetPredicate";

    public static final String SOURCE_PREDICATE = "sourcePredicate";

    public static final Resource Context = resource("Context");
    public static final Resource Mapping = resource("Mapping");
    public static final Resource Mapping_0_1 = resource("Mapping-0-1");
    public static final Resource Mapping_1 = resource("Mapping-1");
    public static final Resource Mapping_1_1 = resource("Mapping-1-1");
    public static final Resource Mapping_1_1_Inverse = resource("Mapping-1-1-Inverse");
    public static final Resource Mapping_1_Path_1 = resource("Mapping-1-Path-1");
    public static final Resource Mapping_2_1 = resource("Mapping-2-1");
    public static final Resource SplitMapping_1_1 = resource("SplitMapping-1-1");
    public static final Resource TargetFunction = resource("TargetFunction");
    public static final Resource TargetFunctions = resource("TargetFunctions");
    public static final Resource TransformationFunction = resource("TransformationFunction");
    public static final Resource TransformationFunctions = resource("TransformationFunctions");

    public static final Property context = property("context");
    public static final Resource equals = resource("equals");
    public static final Property expression = property("expression");
    public static final Property function = property("function");
    public static final Property inverseExpression = property("inverseExpression");
    public static final Property postRule = property("postRule");
    public static final Property predicate = property("predicate");
    public static final Property prepRule = property("prepRule");
    public static final Property rule = property("rule");
    public static final Property separator = property("separator");
    public static final Property shortLabel = property("shortLabel");
    public static final Property source = property("source");
    public static final Property sourceClass = property("sourceClass");
    public static final Property sourcePath = property("sourcePath");
    public static final Property sourcePredicate1 = property(SOURCE_PREDICATE + "1");
    public static final Property sourcePredicate2 = property(SOURCE_PREDICATE + "2");
    public static final Property sourcePredicate3 = property(SOURCE_PREDICATE + "3");
    public static final Resource sourceVariable = resource("_source");
    public static final Property suggestion_0_1 = property("suggestion-0-1");
    public static final Property suggestion_1_1 = property("suggestion-1-1");
    public static final Property suggestionScore = property("suggestionScore");
    public static final Property target = property("target");
    public static final Property targetClass = property("targetClass");
    public static final Property targetPredicate1 = property(TARGET_PREDICATE + "1");
    public static final Property targetPredicate2 = property(TARGET_PREDICATE + "2");

    public static final Resource targetResource = resource("targetResource");

    public static final Property template = property("template");
    public static final Property type = property("type");
    public static final Property value = property("value");
    public static final Property value1 = property("value1");
    public static final Property value2 = property("value2");
    public static final Property condition = property("condition");


    protected static Resource resource(String local) {
        return ResourceFactory.createResource(NS + local);
    }

    protected static Property property(String local) {
        return ResourceFactory.createProperty(NS + local);
    }


}

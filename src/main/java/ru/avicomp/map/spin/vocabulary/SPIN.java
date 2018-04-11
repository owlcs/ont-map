package ru.avicomp.map.spin.vocabulary;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import ru.avicomp.map.spin.SystemModels;

/**
 * Vocabulary of the SPIN Modeling Vocabulary.
 * Copy-pasted to avoid patching builtin personalities.
 * Created by @szuev on 11.04.2018.
 *
 * @see org.topbraid.spin.vocabulary.SPIN
 */
public class SPIN {

    public final static String BASE_URI = SystemModels.Resources.SPIN.getURI();
    public final static String NS = BASE_URI + "#";

    public final static String PREFIX = "spin";

    public final static String INVERSE_OBJECT_VAR_NAME = "object";
    public final static String THIS_VAR_NAME = "this";

    public final static Resource ask = resource("ask");
    public final static Resource AskTemplate = resource("AskTemplate");
    public final static Resource Column = resource("Column");
    public final static Resource ConstraintViolation = resource("ConstraintViolation");
    public final static Resource ConstraintViolationLevel = resource("ConstraintViolationLevel");
    public final static Resource construct = resource("construct");
    public final static Resource constructViolations = resource("constructViolations");
    public final static Resource ConstructTemplate = resource("ConstructTemplate");
    public final static Resource Error = resource("Error");
    public final static Resource eval = resource("eval");
    public final static Resource evalInGraph = resource("evalInGraph");
    public final static Resource Fatal = resource("Fatal");
    public final static Resource Function = resource("Function");
    public final static Resource Functions = resource("Functions");
    public final static Resource Info = resource("Info");
    public final static Resource LibraryOntology = resource("LibraryOntology");
    public final static Resource MagicProperties = resource("MagicProperties");
    public final static Resource MagicProperty = resource("MagicProperty");
    public final static Resource Module = resource("Module");
    public final static Resource Modules = resource("Modules");
    public final static Resource Rule = resource("Rule");
    public final static Resource RuleProperty = resource("RuleProperty");
    public final static Resource select = resource("select");
    public final static Resource SelectTemplate = resource("SelectTemplate");
    public final static Resource TableDataProvider = resource("TableDataProvider");
    public final static Resource Template = resource("Template");
    public final static Resource Templates = resource("Templates");
    public final static Resource UpdateTemplate = resource("UpdateTemplate");
    public final static Resource violatesConstraints = resource("violatesConstraints");
    public final static Resource Warning = resource("Warning");
    public final static Resource _arg1 = resource("_arg1");
    public final static Resource _arg2 = resource("_arg2");
    public final static Resource _arg3 = resource("_arg3");
    public final static Resource _arg4 = resource("_arg4");
    public final static Resource _arg5 = resource("_arg5");
    public final static Resource _this = resource("_this");

    public final static Property abstract_ = property("abstract");
    public final static Property body = property("body");
    public final static Property cachable = property("cachable");
    public final static Property cachableForOntologies = property("cachableForOntologies");
    public final static Property column = property("column");
    public final static Property columnIndex = property("columnIndex");
    public final static Property columnWidth = property("columnWidth");
    public final static Property columnType = property("columnType");
    public final static Property command = property("command");
    public final static Property constraint = property("constraint");
    public final static Property constructor = property("constructor");
    public final static Property fix = property("fix");
    public final static Property imports = property("imports");
    public final static Property inverseBody = property("inverseBody");
    public final static Property labelTemplate = property("labelTemplate");
    public final static Property nextRuleProperty = property("nextRuleProperty");
    public final static Property private_ = property("private");
    public final static Property query = property("query");
    public final static Property returnType = property("returnType");
    public final static Property rule = property("rule");
    public final static Property rulePropertyMaxIterationCount = property("rulePropertyMaxIterationCount");
    public final static Property symbol = property("symbol");
    public final static Property thisUnbound = property("thisUnbound");
    public final static Property violationDetail = property("violationDetail");
    public final static Property violationLevel = property("violationLevel");
    public final static Property violationPath = property("violationPath");
    public final static Property violationRoot = property("violationRoot");
    public final static Property violationSource = property("violationSource");
    public final static Property violationValue = property("violationValue");

    public static Resource resource(String local) {
        return ResourceFactory.createResource(NS + local);
    }

    public static Property property(String local) {
        return ResourceFactory.createProperty(NS + local);
    }
}

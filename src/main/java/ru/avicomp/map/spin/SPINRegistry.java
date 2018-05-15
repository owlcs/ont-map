package ru.avicomp.map.spin;

import org.apache.jena.sparql.function.FunctionRegistry;
import org.apache.jena.sparql.pfunction.PropertyFunctionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.arq.PropertyChainHelperPFunction;
import org.topbraid.spin.arq.functions.*;
import org.topbraid.spin.vocabulary.SPIN;
import ru.avicomp.map.spin.vocabulary.SPIF;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;

import java.util.Optional;

/**
 * A helper to register builtin (SPIF) and some common (SPIN) functions.
 * SPIF functions is getting from Topbraid Composer Free Edition (see org.topbraid.spin.functions_*.jar), ver 5.2.2.
 * New topbraid do not use SPIN-API, but rather SHACL-API, which has changes in namespaces.
 * Created by @szuev on 15.04.2018.
 *
 * @see SPIF
 * @see SPIN
 * @see SPINMAPL
 */
class SPINRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(SPINRegistry.class);
    static FunctionRegistry functionRegistry = FunctionRegistry.get();
    private static PropertyFunctionRegistry propertyFunctionRegistry = PropertyFunctionRegistry.get();

    static void initSPIF() {
        // from org.topbraid.spin.arq.functions:
        registerFunction("invoke", InvokeFunction.class);
        registerFunction("walkObjects", WalkObjectsFunction.class);
        // boolean:
        registerFunction("hasAllObjects", "org.topbraid.spin.functions.internal.bool.HasAllObjectsFunction");
        registerFunction("isReadOnlyTriple", "org.topbraid.spin.functions.internal.bool.IsReadOnlyTripleFunction");
        // date:
        registerFunction("currentTimeMillis", "org.topbraid.spin.functions.internal.date.CurrentTimeMillisFunction");
        registerFunction("dateFormat", "org.topbraid.spin.functions.internal.date.DateFormatFunction");
        registerFunction("parseDate", "org.topbraid.spin.functions.internal.date.ParseDateFunction");
        registerFunction("timeMillis", "org.topbraid.spin.functions.internal.date.TimeMillisFunction");
        // mathematical:
        registerFunction("mod", "org.topbraid.spin.functions.internal.mathematical.ModFunction");
        registerFunction("random", "org.topbraid.spin.functions.internal.mathematical.RandomFunction");
        // misc
        registerFunction("canInvoke", "org.topbraid.spin.functions.internal.misc.CanInvokeFunction");
        registerFunction("cast", "org.topbraid.spin.functions.internal.misc.CastFunction");
        registerFunction("countMatches", "org.topbraid.spin.functions.internal.misc.CountMatchesFunction");
        registerFunction("countTransitiveSubjects", "org.topbraid.spin.functions.internal.misc.CountTransitiveSubjectsFunction");
        registerFunction("shortestObjectsPath", "org.topbraid.spin.functions.internal.misc.ShortestObjectsPathFunction");
        registerFunction("shortestSubjectsPath", "org.topbraid.spin.functions.internal.misc.ShortestSubjectsPathFunction");
        // string:
        registerFunction("buildStringFromRDFList", "org.topbraid.spin.functions.internal.string.BuildStringFromRDFListFunction");
        registerFunction("buildString", "org.topbraid.spin.functions.internal.string.BuildStringFunction");
        registerFunction("camelCase", "org.topbraid.spin.functions.internal.string.CamelCaseFunction");
        registerFunction("convertSPINRDFToString", "org.topbraid.spin.functions.internal.string.ConvertSPINRDFToStringFunction");
        registerFunction("decimalFormat", "org.topbraid.spin.functions.internal.string.DecimalFormatFunction");
        registerFunction("decodeURL", "org.topbraid.spin.functions.internal.string.DecodeURLFunction");
        registerFunction("encodeURL", "org.topbraid.spin.functions.internal.string.EncodeURLFunction");
        registerFunction("generateUUID", "org.topbraid.spin.functions.internal.string.GenerateUUIDFunction");
        registerFunction("indexOf", "org.topbraid.spin.functions.internal.string.IndexOfFunction");
        registerFunction("lastIndexOf", "org.topbraid.spin.functions.internal.string.LastIndexOfFunction");
        registerFunction("lowerCamelCase", "org.topbraid.spin.functions.internal.string.LowerCamelCaseFunction");
        registerFunction("lowerCase", "org.topbraid.spin.functions.internal.string.LowerCaseFunction");
        registerFunction("lowerTitleCase", "org.topbraid.spin.functions.internal.string.LowerTitleCaseFunction");
        registerFunction("name", "org.topbraid.spin.functions.internal.string.NameFunction");
        registerFunction("regex", "org.topbraid.spin.functions.internal.string.RegexFunction");
        registerFunction("replaceAll", "org.topbraid.spin.functions.internal.string.ReplaceAllFunction");
        registerFunction("titleCase", "org.topbraid.spin.functions.internal.string.TitleCaseFunction");
        registerFunction("toJavaIdentifier", "org.topbraid.spin.functions.internal.string.ToJavaIdentifierFunction");
        registerFunction("trim", "org.topbraid.spin.functions.internal.string.TrimFunction");
        registerFunction("upperCase", "org.topbraid.spin.functions.internal.string.UpperCaseFunction");
        registerFunction("unCamelCase", "org.topbraid.spin.functions.internal.string.UnCamelCaseFunction");
        registerFunction("buildURI", "org.topbraid.spin.functions.internal.string.BuildURIFunction");
        registerFunction("buildUniqueURI", "org.topbraid.spin.functions.internal.string.BuildUniqueURIFunction");
        // uri
        registerFunction("isValidURI", "org.topbraid.spin.functions.internal.uri.IsValidURIFunction");
        // magical
        registerPropertyFunction("evalPath", "org.topbraid.spin.functions.internal.magic.EvalPathPFunction");
        registerPropertyFunction("for", "org.topbraid.spin.functions.internal.magic.ForPFunction");
        registerPropertyFunction("foreach", "org.topbraid.spin.functions.internal.magic.ForeachPFunction");
        registerPropertyFunction("labelTemplateSegment", "org.topbraid.spin.functions.internal.magic.LabelTemplateSegmentPFunction");
        registerPropertyFunction("prefix", "org.topbraid.spin.functions.internal.magic.PrefixPFunction");
        registerPropertyFunction("range", "org.topbraid.spin.functions.internal.magic.RangePFunction");
        registerPropertyFunction("split", "org.topbraid.spin.functions.internal.magic.SplitPFunction");
    }

    static void initSPIN() {
        functionRegistry.put(SPIN.ask.getURI(), new AskFunction());
        functionRegistry.put(SPIN.eval.getURI(), new EvalFunction());
        functionRegistry.put(SPIN.evalInGraph.getURI(), new EvalInGraphFunction());
        functionRegistry.put(SPIN.violatesConstraints.getURI(), new ViolatesConstraintsFunction());
        propertyFunctionRegistry.put(SPIN.construct.getURI(), ConstructPFunction.class);
        propertyFunctionRegistry.put(SPIN.constructViolations.getURI(), ConstructViolationsPFunction.class);
        propertyFunctionRegistry.put(SPIN.select.getURI(), SelectPFunction.class);
        propertyFunctionRegistry.put(SPINMAPL.OWL_RL_PROPERTY_CHAIN_HELPER, PropertyChainHelperPFunction.class);
    }

    private static void registerFunction(String localName, Class functionClass) {
        functionRegistry.put(SPIF.NS + localName, functionClass);
        functionRegistry.put(SPINMAPL.SMF_NS + localName, functionClass); //?
    }

    private static void registerPropertyFunction(String localName, Class functionClass) {
        propertyFunctionRegistry.put(SPIF.NS + localName, functionClass);
        propertyFunctionRegistry.put("http://www.topbraid.org/tops#" + localName, functionClass); //?
    }

    private static void registerFunction(String localName, String functionClass) {
        find(functionClass).ifPresent(c -> registerFunction(localName, c));
    }

    private static void registerPropertyFunction(String localName, String functionClass) {
        find(functionClass).ifPresent(c -> registerPropertyFunction(localName, c));
    }

    private static Optional<Class> find(String name) {
        try {
            return Optional.of(Class.forName(name));
        } catch (ClassNotFoundException e) {
            LOGGER.warn("Can't find class {}: {}", name, e.getMessage());
            return Optional.empty();
        }
    }

}

package ru.avicomp.map.spin;

import org.apache.jena.sparql.function.FunctionRegistry;
import org.apache.jena.sparql.pfunction.PropertyFunctionRegistry;
import org.topbraid.spin.arq.PropertyChainHelperPFunction;
import org.topbraid.spin.arq.functions.*;
import org.topbraid.spin.functions.internal.bool.HasAllObjectsFunction;
import org.topbraid.spin.functions.internal.bool.IsReadOnlyTripleFunction;
import org.topbraid.spin.functions.internal.date.CurrentTimeMillisFunction;
import org.topbraid.spin.functions.internal.date.DateFormatFunction;
import org.topbraid.spin.functions.internal.date.ParseDateFunction;
import org.topbraid.spin.functions.internal.date.TimeMillisFunction;
import org.topbraid.spin.functions.internal.magic.*;
import org.topbraid.spin.functions.internal.mathematical.ModFunction;
import org.topbraid.spin.functions.internal.mathematical.RandomFunction;
import org.topbraid.spin.functions.internal.misc.*;
import org.topbraid.spin.functions.internal.string.*;
import org.topbraid.spin.functions.internal.uri.IsValidURIFunction;
import org.topbraid.spin.vocabulary.SPIF;
import org.topbraid.spin.vocabulary.SPIN;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;

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

    static void initSPIF() {
        // from org.topbraid.spin.arq.functions:
        registerFunction("invoke", InvokeFunction.class);
        registerFunction("walkObjects", WalkObjectsFunction.class);
        // boolean:
        registerFunction("hasAllObjects", HasAllObjectsFunction.class);
        registerFunction("isReadOnlyTriple", IsReadOnlyTripleFunction.class);
        // date:
        registerFunction("currentTimeMillis", CurrentTimeMillisFunction.class);
        registerFunction("dateFormat", DateFormatFunction.class);
        registerFunction("parseDate", ParseDateFunction.class);
        registerFunction("timeMillis", TimeMillisFunction.class);
        // mathematical:
        registerFunction("mod", ModFunction.class);
        registerFunction("random", RandomFunction.class);
        // misc
        registerFunction("canInvoke", CanInvokeFunction.class);
        registerFunction("cast", CastFunction.class);
        registerFunction("countMatches", CountMatchesFunction.class);
        registerFunction("countTransitiveSubjects", CountTransitiveSubjectsFunction.class);
        registerFunction("shortestObjectsPath", ShortestObjectsPathFunction.class);
        registerFunction("shortestSubjectsPath", ShortestSubjectsPathFunction.class);
        // string:
        registerFunction("buildStringFromRDFList", BuildStringFromRDFListFunction.class);
        registerFunction("buildString", BuildStringFunction.class);
        registerFunction("camelCase", CamelCaseFunction.class);
        registerFunction("convertSPINRDFToString", ConvertSPINRDFToStringFunction.class);
        registerFunction("decimalFormat", DecimalFormatFunction.class);
        registerFunction("decodeURL", DecodeURLFunction.class);
        registerFunction("encodeURL", EncodeURLFunction.class);
        registerFunction("generateUUID", GenerateUUIDFunction.class);
        registerFunction("indexOf", IndexOfFunction.class);
        registerFunction("lastIndexOf", LastIndexOfFunction.class);
        registerFunction("lowerCamelCase", LowerCamelCaseFunction.class);
        registerFunction("lowerCase", LowerCaseFunction.class);
        registerFunction("lowerTitleCase", LowerTitleCaseFunction.class);
        registerFunction("name", NameFunction.class);
        registerFunction("regex", RegexFunction.class);
        registerFunction("replaceAll", ReplaceAllFunction.class);
        registerFunction("titleCase", TitleCaseFunction.class);
        registerFunction("toJavaIdentifier", ToJavaIdentifierFunction.class);
        registerFunction("trim", TrimFunction.class);
        registerFunction("upperCase", UpperCaseFunction.class);
        registerFunction("unCamelCase", UnCamelCaseFunction.class);
        registerFunction("buildURI", BuildURIFunction.class);
        registerFunction("buildUniqueURI", BuildUniqueURIFunction.class);
        // uri
        registerFunction("isValidURI", IsValidURIFunction.class);
        // magical
        registerPropertyFunction("evalPath", EvalPathPFunction.class);
        registerPropertyFunction("for", ForPFunction.class);
        registerPropertyFunction("foreach", ForeachPFunction.class);
        registerPropertyFunction("labelTemplateSegment", LabelTemplateSegmentPFunction.class);
        registerPropertyFunction("prefix", PrefixPFunction.class);
        registerPropertyFunction("range", RangePFunction.class);
        registerPropertyFunction("split", SplitPFunction.class);
    }

    static void initSPIN() {
        FunctionRegistry.get().put(SPIN.ask.getURI(), new AskFunction());
        FunctionRegistry.get().put(SPIN.eval.getURI(), new EvalFunction());
        FunctionRegistry.get().put(SPIN.evalInGraph.getURI(), new EvalInGraphFunction());
        FunctionRegistry.get().put(SPIN.violatesConstraints.getURI(), new ViolatesConstraintsFunction());
        PropertyFunctionRegistry.get().put(SPIN.construct.getURI(), ConstructPFunction.class);
        PropertyFunctionRegistry.get().put(SPIN.constructViolations.getURI(), ConstructViolationsPFunction.class);
        PropertyFunctionRegistry.get().put(SPIN.select.getURI(), SelectPFunction.class);
        PropertyFunctionRegistry.get().put(SPINMAPL.OWL_RL_PROPERTY_CHAIN_HELPER, PropertyChainHelperPFunction.class);
    }

    private static void registerFunction(String localName, Class functionClass) {
        // todo: we don't need sparqlmotionfunctions functions. all of them (from the list above) are deprecated.
        FunctionRegistry.get().put(SPIF.NS + localName, functionClass);
        FunctionRegistry.get().put(SPINMAPL.SMF_NS + localName, functionClass); //?
    }

    private static void registerPropertyFunction(String localName, Class functionClass) {
        PropertyFunctionRegistry.get().put(SPIF.NS + localName, functionClass);
        //PropertyFunctionRegistry.get().put("http://www.topbraid.org/tops#" + localName, functionClass); //?
    }
}

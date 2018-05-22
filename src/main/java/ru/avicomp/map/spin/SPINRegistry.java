package ru.avicomp.map.spin;

import org.apache.jena.sparql.function.FunctionRegistry;
import org.apache.jena.sparql.function.library.leviathan.cos;
import org.apache.jena.sparql.pfunction.PropertyFunctionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.arq.PropertyChainHelperPFunction;
import org.topbraid.spin.arq.functions.*;
import org.topbraid.spin.vocabulary.SPIN;
import ru.avicomp.map.spin.vocabulary.MATH;
import ru.avicomp.map.spin.vocabulary.SPIF;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;

import java.util.Optional;

/**
 * A helper to register builtin (SPIF), some common (SPIN) and math (MATH) functions.
 * SPIF functions is getting from Topbraid Composer Free Edition (see org.topbraid.spin.functions_*.jar), ver 5.2.2.
 * Notice that new topbraid (5.5.1) do not use SPIN-API, but rather SHACL-API, which has changes in namespaces.
 * Created by @szuev on 15.04.2018.
 *
 * @see SPIF
 * @see SPIN
 * @see MATH
 * @see SPINMAPL
 */
class SPINRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(SPINRegistry.class);
    private final FunctionRegistry functionRegistry;
    private final PropertyFunctionRegistry propertyFunctionRegistry;

    SPINRegistry(FunctionRegistry functionRegistry, PropertyFunctionRegistry propertyFunctionRegistry) {
        this.functionRegistry = functionRegistry;
        this.propertyFunctionRegistry = propertyFunctionRegistry;
    }

    void initSPIF() {
        // from org.topbraid.spin.arq.functions:
        registerSPIFFunction("invoke", InvokeFunction.class);
        registerSPIFFunction("walkObjects", WalkObjectsFunction.class);
        // boolean:
        registerSPIFFunction("hasAllObjects", "org.topbraid.spin.functions.internal.bool.HasAllObjectsFunction");
        registerSPIFFunction("isReadOnlyTriple", "org.topbraid.spin.functions.internal.bool.IsReadOnlyTripleFunction");
        // date:
        registerSPIFFunction("currentTimeMillis", "org.topbraid.spin.functions.internal.date.CurrentTimeMillisFunction");
        registerSPIFFunction("dateFormat", "org.topbraid.spin.functions.internal.date.DateFormatFunction");
        registerSPIFFunction("parseDate", "org.topbraid.spin.functions.internal.date.ParseDateFunction");
        registerSPIFFunction("timeMillis", "org.topbraid.spin.functions.internal.date.TimeMillisFunction");
        // mathematical:
        registerSPIFFunction("mod", "org.topbraid.spin.functions.internal.mathematical.ModFunction");
        registerSPIFFunction("random", "org.topbraid.spin.functions.internal.mathematical.RandomFunction");
        // misc
        registerSPIFFunction("canInvoke", "org.topbraid.spin.functions.internal.misc.CanInvokeFunction");
        registerSPIFFunction("cast", "org.topbraid.spin.functions.internal.misc.CastFunction");
        registerSPIFFunction("countMatches", "org.topbraid.spin.functions.internal.misc.CountMatchesFunction");
        registerSPIFFunction("countTransitiveSubjects", "org.topbraid.spin.functions.internal.misc.CountTransitiveSubjectsFunction");
        registerSPIFFunction("shortestObjectsPath", "org.topbraid.spin.functions.internal.misc.ShortestObjectsPathFunction");
        registerSPIFFunction("shortestSubjectsPath", "org.topbraid.spin.functions.internal.misc.ShortestSubjectsPathFunction");
        // string:
        registerSPIFFunction("buildStringFromRDFList", "org.topbraid.spin.functions.internal.string.BuildStringFromRDFListFunction");
        registerSPIFFunction("buildString", "org.topbraid.spin.functions.internal.string.BuildStringFunction");
        registerSPIFFunction("camelCase", "org.topbraid.spin.functions.internal.string.CamelCaseFunction");
        registerSPIFFunction("convertSPINRDFToString", "org.topbraid.spin.functions.internal.string.ConvertSPINRDFToStringFunction");
        registerSPIFFunction("decimalFormat", "org.topbraid.spin.functions.internal.string.DecimalFormatFunction");
        registerSPIFFunction("decodeURL", "org.topbraid.spin.functions.internal.string.DecodeURLFunction");
        registerSPIFFunction("encodeURL", "org.topbraid.spin.functions.internal.string.EncodeURLFunction");
        registerSPIFFunction("generateUUID", "org.topbraid.spin.functions.internal.string.GenerateUUIDFunction");
        registerSPIFFunction("indexOf", "org.topbraid.spin.functions.internal.string.IndexOfFunction");
        registerSPIFFunction("lastIndexOf", "org.topbraid.spin.functions.internal.string.LastIndexOfFunction");
        registerSPIFFunction("lowerCamelCase", "org.topbraid.spin.functions.internal.string.LowerCamelCaseFunction");
        registerSPIFFunction("lowerCase", "org.topbraid.spin.functions.internal.string.LowerCaseFunction");
        registerSPIFFunction("lowerTitleCase", "org.topbraid.spin.functions.internal.string.LowerTitleCaseFunction");
        registerSPIFFunction("name", "org.topbraid.spin.functions.internal.string.NameFunction");
        registerSPIFFunction("regex", "org.topbraid.spin.functions.internal.string.RegexFunction");
        registerSPIFFunction("replaceAll", "org.topbraid.spin.functions.internal.string.ReplaceAllFunction");
        registerSPIFFunction("titleCase", "org.topbraid.spin.functions.internal.string.TitleCaseFunction");
        registerSPIFFunction("toJavaIdentifier", "org.topbraid.spin.functions.internal.string.ToJavaIdentifierFunction");
        registerSPIFFunction("trim", "org.topbraid.spin.functions.internal.string.TrimFunction");
        registerSPIFFunction("upperCase", "org.topbraid.spin.functions.internal.string.UpperCaseFunction");
        registerSPIFFunction("unCamelCase", "org.topbraid.spin.functions.internal.string.UnCamelCaseFunction");
        registerSPIFFunction("buildURI", "org.topbraid.spin.functions.internal.string.BuildURIFunction");
        registerSPIFFunction("buildUniqueURI", "org.topbraid.spin.functions.internal.string.BuildUniqueURIFunction");
        // uri
        registerSPIFFunction("isValidURI", "org.topbraid.spin.functions.internal.uri.IsValidURIFunction");
        // magical
        registerSPIFPropertyFunction("evalPath", "org.topbraid.spin.functions.internal.magic.EvalPathPFunction");
        registerSPIFPropertyFunction("for", "org.topbraid.spin.functions.internal.magic.ForPFunction");
        registerSPIFPropertyFunction("foreach", "org.topbraid.spin.functions.internal.magic.ForeachPFunction");
        registerSPIFPropertyFunction("labelTemplateSegment", "org.topbraid.spin.functions.internal.magic.LabelTemplateSegmentPFunction");
        registerSPIFPropertyFunction("prefix", "org.topbraid.spin.functions.internal.magic.PrefixPFunction");
        registerSPIFPropertyFunction("range", "org.topbraid.spin.functions.internal.magic.RangePFunction");
        registerSPIFPropertyFunction("split", "org.topbraid.spin.functions.internal.magic.SplitPFunction");
    }

    void initSPIN() {
        functionRegistry.put(SPIN.ask.getURI(), new AskFunction());
        functionRegistry.put(SPIN.eval.getURI(), new EvalFunction());
        functionRegistry.put(SPIN.evalInGraph.getURI(), new EvalInGraphFunction());
        functionRegistry.put(SPIN.violatesConstraints.getURI(), new ViolatesConstraintsFunction());
        propertyFunctionRegistry.put(SPIN.construct.getURI(), ConstructPFunction.class);
        propertyFunctionRegistry.put(SPIN.constructViolations.getURI(), ConstructViolationsPFunction.class);
        propertyFunctionRegistry.put(SPIN.select.getURI(), SelectPFunction.class);
        propertyFunctionRegistry.put(SPINMAPL.OWL_RL_PROPERTY_CHAIN_HELPER, PropertyChainHelperPFunction.class);
    }

    void initMath() {
        // todo: complete
        functionRegistry.put(MATH.cos.getURI(), cos.class);
    }

    private void registerSPIFFunction(String localName, Class functionClass) {
        functionRegistry.put(SPIF.NS + localName, functionClass);
        functionRegistry.put(SPINMAPL.SMF_NS + localName, functionClass); //?
    }

    private void registerSPIFPropertyFunction(String localName, Class functionClass) {
        propertyFunctionRegistry.put(SPIF.NS + localName, functionClass);
        propertyFunctionRegistry.put("http://www.topbraid.org/tops#" + localName, functionClass); //?
    }

    private void registerSPIFFunction(String localName, String functionClass) {
        find(functionClass).ifPresent(c -> registerSPIFFunction(localName, c));
    }

    private void registerSPIFPropertyFunction(String localName, String functionClass) {
        find(functionClass).ifPresent(c -> registerSPIFPropertyFunction(localName, c));
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

package ru.avicomp.map;

import org.apache.jena.graph.Factory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDFS;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import org.topbraid.spin.vocabulary.SPL;
import ru.avicomp.map.spin.MapManagerImpl;
import ru.avicomp.map.spin.SpinModelConfig;
import ru.avicomp.map.spin.SystemModels;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.spin.vocabulary.MATH;
import ru.avicomp.map.utils.AutoPrefixListener;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.impl.conf.OntModelConfig;
import ru.avicomp.ontapi.jena.impl.conf.OntPersonality;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntID;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;
import ru.avicomp.ontapi.jena.vocabulary.XSD;

/**
 * Created by @szuev on 22.05.2018.
 */
public class MathLibraryMaker {

    public static void main(String... args) {
        OntPersonality p = OntModelConfig.ONT_PERSONALITY_BUILDER.build(SpinModelConfig.LIB_PERSONALITY, OntModelConfig.StdMode.LAX);
        OntGraphModel m = OntModelFactory.createModel(Factory.createGraphMem(), p);
        AutoPrefixListener.addAutoPrefixListener((UnionGraph) m.getGraph(), MapManagerImpl.collectPrefixes(SystemModels.graphs().values()));
        OntID id = m.setID(SystemModels.Resources.MATH.getURI());
        id.setVersionIRI(AVC.NS + "1.0");
        id.addComment("A library that contains mathematical functions for some reason missing in the standard spin delivery.", null);
        id.addProperty(RDFS.seeAlso, m.getResource(MATH.URI));
        id.addAnnotation(m.getAnnotationProperty(OWL.versionInfo), "version 1.0", null);

        createFunctionDouble2Double(MATH.acos.inModel(m), "acos", "arccosine", "Returns the arc cosine of the argument.", null);
        createFunctionDouble2Double(MATH.asin.inModel(m), "asin", "arcsine", "Returns the arc sine of the argument.", null);
        createFunctionDouble2Double(MATH.atan.inModel(m), "atan", "arctangent", "Returns the arc tangent of the argument.", null);
        createFunctionDouble2Double(MATH.atan2.inModel(m), "atan2", "arctangent 2",
                "Returns the angle in radians subtended at the origin by the point on a plane with coordinates (x, y) and the positive x-axis.", null);
        createFunctionDouble2Double(MATH.cos.inModel(m), "cos", "cosinus", "Returns the cosine of the argument. The argument is an angle in radians.", "Radians");
        createFunctionDouble2Double(MATH.exp.inModel(m), "exp", "exponent", "Returns the value of e^x.", null);
        createFunctionDouble2Double(MATH.exp10.inModel(m), "exp10", "base-ten exponent", "Returns the value of 10^x.", null);
        createFunctionDouble2Double(MATH.log.inModel(m), "log", "natural logarithm", "Returns the natural logarithm of the argument.", null);
        createFunctionDouble2Double(MATH.log10.inModel(m), "log10", "base-ten logarithm", "Returns the base-ten logarithm of the argument.", null);
        createFunctionDouble2Double(MATH.pi.inModel(m), "pi", "pi", "Returns an approximation to the mathematical constant π.", null);

        createFunctionDouble2Double(MATH.pow.inModel(m), "pow", "power", "Returns the result of raising the first argument to the power of the second.", null)
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg2)
                        .addProperty(SPL.valueType, XSD.xdouble));

        createFunctionDouble2Double(MATH.sin.inModel(m), "sin", "sine", "Returns the sine of the argument. The argument is an angle in radians.", "Radians");
        createFunctionDouble2Double(MATH.sqrt.inModel(m), "sqrt", "square root", "Returns the non-negative square root of the argument.", null);
        createFunctionDouble2Double(MATH.tan.inModel(m), "tan", "tangent", "Returns the tangent of the argument. The argument is an angle in radians.", null);

        m.write(System.out, "ttl");
    }

    private static Resource createFunction(Resource name, String shortLabel, String label, String comment) {
        return name.addProperty(RDF.type, SPIN.Function)
                .addProperty(RDFS.subClassOf, SPL.MathematicalFunctions)
                .addProperty(SPINMAP.shortLabel, shortLabel)
                .addProperty(RDFS.label, label)
                .addProperty(RDFS.comment, comment);
    }

    private static Resource createFunctionDouble2Double(Resource name, String shortLabel, String label, String comment, String argComment) {
        Resource arg = name.getModel().createResource()
                .addProperty(RDF.type, SPL.Argument)
                .addProperty(SPL.predicate, SP.arg1)
                .addProperty(SPL.valueType, XSD.xdouble);
        if (argComment != null) arg.addProperty(RDFS.comment, argComment);
        return createFunction(name, shortLabel, label, comment)
                .addProperty(SPIN.returnType, XSD.xdouble)
                .addProperty(SPIN.constraint, arg);
    }
}

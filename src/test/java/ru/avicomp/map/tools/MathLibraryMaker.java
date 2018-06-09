package ru.avicomp.map.tools;

import org.apache.jena.graph.Factory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDFS;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import org.topbraid.spin.vocabulary.SPL;
import ru.avicomp.map.spin.SystemModels;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.spin.vocabulary.MATH;
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
        OntGraphModel m = LibraryMaker.createModel(Factory.createGraphMem());
        OntID id = m.setID(SystemModels.Resources.AVC_MATH.getURI());
        id.setVersionIRI(id.getURI() + "#1.0");
        id.addComment("A library that contains mathematical functions for some reason missing in the standard spin delivery.", null);
        id.addProperty(RDFS.seeAlso, m.getResource(MATH.URI));
        id.addAnnotation(m.getAnnotationProperty(OWL.versionInfo), "version 1.0", null);
        m.addImport(LibraryMaker.createModel(LibraryMaker.getAVCGraph()));

        createDoubleFuncWithDoubleArg(MATH.acos.inModel(m), "acos", "arccosine", "Returns the arc cosine of the argument.", null);
        createDoubleFuncWithDoubleArg(MATH.asin.inModel(m), "asin", "arcsine", "Returns the arc sine of the argument.", null);
        createDoubleFuncWithDoubleArg(MATH.atan.inModel(m), "atan", "arctangent", "Returns the arc tangent of the argument.", null);
        createDoubleFuncWithDoubleArg(MATH.atan2.inModel(m), "atan2", "arctangent 2",
                "Returns the angle in radians subtended at the origin by the point on a plane with coordinates (x, y) and the positive x-axis.", null);
        createDoubleFuncWithDoubleArg(MATH.cos.inModel(m), "cos", "cosinus", "Returns the cosine of the argument. The argument is an angle in radians.", "Radians");
        createDoubleFuncWithDoubleArg(MATH.exp.inModel(m), "exp", "exponent", "Returns the value of e^x.", null);
        createDoubleFuncWithDoubleArg(MATH.exp10.inModel(m), "exp10", "base-ten exponent", "Returns the value of 10^x.", null);
        createDoubleFuncWithDoubleArg(MATH.log.inModel(m), "log", "natural logarithm", "Returns the natural logarithm of the argument.", null);
        createDoubleFuncWithDoubleArg(MATH.log10.inModel(m), "log10", "base-ten logarithm", "Returns the base-ten logarithm of the argument.", null);
        createDoubleFunction(MATH.pi.inModel(m), "pi", "pi", "Returns an approximation to the mathematical constant π.");

        createDoubleFuncWithDoubleArg(MATH.pow.inModel(m), "pow", "power", "Returns the result of raising the first argument to the power of the second.", null)
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg2)
                        .addProperty(SPL.valueType, AVC.numeric));

        createDoubleFuncWithDoubleArg(MATH.sin.inModel(m), "sin", "sine", "Returns the sine of the argument. The argument is an angle in radians.", "Radians");
        createDoubleFuncWithDoubleArg(MATH.sqrt.inModel(m), "sqrt", "square root", "Returns the non-negative square root of the argument.", null);
        createDoubleFuncWithDoubleArg(MATH.tan.inModel(m), "tan", "tangent", "Returns the tangent of the argument. The argument is an angle in radians.", null);

        m.write(System.out, "ttl");
    }

    private static Resource createDoubleFunction(Resource name, String shortLabel, String label, String comment) {
        return name.addProperty(RDF.type, SPIN.Function)
                .addProperty(RDFS.subClassOf, SPL.MathematicalFunctions)
                .addProperty(SPINMAP.shortLabel, shortLabel)
                .addProperty(RDFS.label, label)
                .addProperty(RDFS.comment, comment)
                .addProperty(SPIN.returnType, XSD.xdouble);
    }

    private static Resource createDoubleFuncWithDoubleArg(Resource name, String shortLabel, String label, String comment, String argComment) {
        Resource arg = name.getModel().createResource()
                .addProperty(RDF.type, SPL.Argument)
                .addProperty(SPL.predicate, SP.arg1)
                .addProperty(SPL.valueType, XSD.xdouble);
        if (argComment != null) arg.addProperty(RDFS.comment, argComment);
        return createDoubleFunction(name, shortLabel, label, comment).addProperty(SPIN.constraint, arg);
    }

}

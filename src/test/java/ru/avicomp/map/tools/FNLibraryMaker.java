package ru.avicomp.map.tools;

import org.apache.jena.graph.Factory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import org.topbraid.spin.vocabulary.SPL;
import ru.avicomp.map.spin.SystemModels;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.spin.vocabulary.FN;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntID;
import ru.avicomp.ontapi.jena.utils.Models;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.XSD;

/**
 * For developing and demonstration.
 * NOTE: Not a part of API or APIs Tests: will be removed.
 * Created by @szuev on 09.06.2018.
 *
 * @see FN
 */
public class FNLibraryMaker {

    public static void main(String... args) {
        OntGraphModel m = LibraryMaker.createModel(Factory.createGraphMem());
        OntID id = m.setID(SystemModels.Resources.AVC_FN.getURI());
        id.setVersionIRI(id.getURI() + "#1.0");
        id.addComment("XQuery, XPath, and XSLT Functions and Operators.\n" +
                "A customisation and an addition to the <http://topbraid.org/functions-fn> library.", null);
        id.addProperty(RDFS.seeAlso, m.getResource(FN.URI));
        id.addProperty(RDFS.seeAlso, m.getResource("http://topbraid.org/functions-fn"));
        id.addAnnotation(m.getAnnotationProperty(OWL.versionInfo), "version 1.0", null);
        m.addImport(LibraryMaker.createModel(LibraryMaker.getAVCGraph()));

        // FN:abs takes a number, not any literal
        FN.abs.inModel(m)
                .addProperty(RDFS.seeAlso, m.getResource("https://www.w3.org/TR/xpath-functions-31/#func-abs"))
                .addProperty(AVC.returnType, AVC.numeric)
                .addProperty(AVC.constraint, LibraryMaker.createConstraint(m, SP.arg1, AVC.numeric));

        // FN:round
        FN.round.inModel(m)
                .addProperty(RDFS.seeAlso, m.getResource("https://www.w3.org/2005/xpath-functions/#round"))
                .addProperty(RDFS.comment, "Rounds a value to a specified number of decimal places, rounding upwards if two such values are equally near.")
                .addProperty(AVC.returnType, AVC.numeric)
                .addProperty(AVC.constraint, LibraryMaker.createConstraint(m, SP.arg1, AVC.numeric))
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg2)
                        .addProperty(SPL.valueType, XSD.integer)
                        .addProperty(SPL.optional, Models.TRUE)
                        .addProperty(RDFS.comment, "The precision, int"));

        // FN:format-number
        FN.format_number.inModel(m)
                .addProperty(RDF.type, SPIN.Function)
                .addProperty(RDFS.subClassOf, SPL.StringFunctions)
                .addProperty(SPIN.returnType, XSD.xstring)
                .addProperty(RDFS.seeAlso, m.getResource("https://www.w3.org/TR/xpath-functions-31/#func-format-number"))
                .addProperty(SPINMAP.shortLabel, "format-number")
                .addProperty(RDFS.label, "formats a numeric literal")
                .addProperty(RDFS.comment, "Returns a string containing a number formatted according to a given picture string, " +
                        "taking account of decimal formats specified in the static context ")
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg1)
                        .addProperty(SPL.valueType, AVC.numeric)
                        .addProperty(RDFS.comment, "Value to format"))
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg2)
                        .addProperty(SPL.optional, Models.TRUE)
                        .addProperty(SPL.valueType, XSD.xstring)
                        .addProperty(RDFS.comment, "Picture"))
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg3)
                        .addProperty(SPL.optional, Models.TRUE)
                        .addProperty(SPL.valueType, XSD.xstring)
                        .addProperty(RDFS.comment, "Decimal format name")
                        .addProperty(RDFS.seeAlso, m.getResource("https://tools.ietf.org/html/bcp47")));

        m.write(System.out, "ttl");
    }
}

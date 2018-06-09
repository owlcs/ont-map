package ru.avicomp.map.tools;

import org.apache.jena.graph.Factory;
import org.apache.jena.vocabulary.RDFS;
import org.topbraid.spin.vocabulary.SP;
import ru.avicomp.map.spin.SystemModels;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.spin.vocabulary.FN;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntID;
import ru.avicomp.ontapi.jena.vocabulary.OWL;

/**
 * Created by @szuev on 09.06.2018.
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

        // https://www.w3.org/TR/xpath-functions-31/#func-abs
        // FN:abs takes a number, not any literal
        FN.abs.inModel(m)
                .addProperty(RDFS.seeAlso, m.getResource("https://www.w3.org/TR/xpath-functions-31/#func-abs"))
                .addProperty(AVC.returnType, AVC.numeric)
                .addProperty(AVC.constraint, LibraryMaker.createConstraint(m, SP.arg1, AVC.numeric));

        m.write(System.out, "ttl");
    }
}

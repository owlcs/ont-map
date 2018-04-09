package ru.avicomp.map;

import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.spin.vocabulary.SP;
import ru.avicomp.map.spin.vocabulary.SPINMAP;
import ru.avicomp.map.utils.OntObjects;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntID;
import ru.avicomp.ontapi.jena.model.OntNDP;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

/**
 * Created by @szuev on 07.04.2018.
 *
 * @see AVC
 */
@Deprecated // todo: it is temporary and will be remove
public class AVCLibraryBulder {

    public static void main(String... args) {
        PrefixMapping pm = PrefixMapping.Factory.create()
                .setNsPrefix("owl", OWL.NS)
                .setNsPrefix("avc", AVC.NS)
                .setNsPrefix("rdfs", RDFS.uri)
                .setNsPrefix("rdf", RDF.uri)
                .setNsPrefix("xsd", XSD.NS)
                .setNsPrefix("sp", SP.NS)
                .lock();
        OntGraphModel m = OntModelFactory.createModel();
        m.setNsPrefixes(pm);
        OntID id = m.setID(AVC.BASE_URI);
        id.setVersionIRI(AVC.BASE_URI + "/1.0");
        OntObjects.setComment(id, "This is an addition to the spin-family in order to customize spin-function behaviour in GUI.");
        id.addAnnotation(OntObjects.versionInfo(m), "version 1.0", null);

        OntNDP hidden = OntObjects.createOWLDataProperty(m, AVC.hidden.getURI());
        hidden.addRange(OntObjects.xsdString(m));

        SP.resource("abs").inModel(m).addProperty(hidden, "Duplicates the function fn:abs, which is preferable, since it has information about return types.");

        SP.resource("UUID").inModel(m).addProperty(RDF.type, SPINMAP.TargetFunction)
                .addProperty(RDFS.comment, "Also, it can be used as target function to produce anonymous individuals.");

        m.write(System.out, "ttl");

    }
}

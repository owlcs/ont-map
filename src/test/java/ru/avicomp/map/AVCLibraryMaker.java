package ru.avicomp.map;

import org.apache.jena.graph.Factory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDFS;
import org.topbraid.spin.arq.ARQ2SPIN;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import org.topbraid.spin.vocabulary.SPL;
import ru.avicomp.map.spin.SpinModelConfig;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.utils.AutoPrefixListener;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.impl.conf.OntModelConfig;
import ru.avicomp.ontapi.jena.impl.conf.OntPersonality;
import ru.avicomp.ontapi.jena.model.OntDT;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntID;
import ru.avicomp.ontapi.jena.model.OntNDP;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;
import ru.avicomp.ontapi.jena.vocabulary.XSD;

/**
 * An utility class to produce avc.spin.ttl (see resources/etc directory).
 * For developing and demonstration.
 * Can be removed.
 * <p>
 * Created by @szuev on 07.04.2018.
 *
 * @see AVC
 */
public class AVCLibraryMaker {

    public static void main(String... args) {
        OntPersonality p = OntModelConfig.ONT_PERSONALITY_BUILDER.build(SpinModelConfig.LIB_PERSONALITY, OntModelConfig.StdMode.LAX);
        OntGraphModel m = OntModelFactory.createModel(Factory.createGraphMem(), p);
        AutoPrefixListener.addAutoPrefixListener((UnionGraph) m.getGraph(), Managers.getMapManager().prefixes());
        OntID id = m.setID(AVC.BASE_URI);
        id.setVersionIRI(AVC.NS + "1.0");
        id.addComment("This is an addition to the spin-family in order to customize spin-functions behaviour in GUI.\n" +
                "Also it contains several custom functions, which can be expressed through the other spin-library and SPARQL.\n" +
                "Currently it is assumed that this library is not going to be included as \"owl:import\" to the mappings produces by the API,\n" +
                "and all listed custom  functions can be considered as templates.", null);
        id.addAnnotation(m.getAnnotationProperty(OWL.versionInfo), "version 1.0", null);

        OntNDP hidden = m.createOntEntity(OntNDP.class, AVC.hidden.getURI());
        hidden.addRange(m.getOntEntity(OntDT.class, XSD.xstring));

        // SP:abs (todo: right now not sure this is correct)
        SP.resource("abs").inModel(m).addProperty(hidden, "Duplicates the function fn:abs, which is preferable, since it has information about return types.");

        // SP:eq
        SP.eq.inModel(m)
                .addProperty(AVC.constraint, m.createResource().addProperty(SPL.predicate, SP.arg1).addProperty(SPL.valueType, AVC.undefined))
                .addProperty(AVC.constraint, m.createResource().addProperty(SPL.predicate, SP.arg2).addProperty(SPL.valueType, AVC.undefined));

        // AVC:UUID
        Resource uuid = m.createResource(AVC.UUID.getURI()).addProperty(RDF.type, SPINMAP.TargetFunction);
        uuid.addProperty(SPIN.returnType, RDFS.Resource);
        uuid.addProperty(RDFS.subClassOf, SPINMAP.TargetFunctions);
        uuid.addProperty(RDFS.seeAlso, SP.resource("UUID"));
        uuid.addProperty(RDFS.seeAlso, ResourceFactory.createResource("https://www.w3.org/TR/sparql11-query/#func-uuid"));
        uuid.addProperty(SPINMAP.shortLabel, "UUID");
        uuid.addProperty(RDFS.label, "MD5 UUID");
        uuid.addProperty(RDFS.comment, "A target function.\n" +
                "Generates an IRI from the UUID URN scheme based on source individual MD5 sum.\n" +
                "Each call of AVC.UUID returns the same UUID IRI.\n" +
                "Example: <urn:uuid:f3bf688d44e249fade9ca8ca23e29884>.");
        uuid.addProperty(SPIN.body,
                ARQ2SPIN.parseQuery("SELECT (IRI(?uri) AS ?result)\n" +
                        "WHERE {\n" +
                        "    BIND (MD5(str(?source)) AS ?value) .\n" +
                        "    BIND (CONCAT(\"urn:uuid:\", ?value) AS ?uri) .\n" +
                        "}", m));
        m.write(System.out, "ttl");

    }
}

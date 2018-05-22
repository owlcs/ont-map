package ru.avicomp.map;

import org.apache.jena.graph.Factory;
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
        OntID id = m.setID(MATH.BASE_URI);
        id.setVersionIRI(AVC.NS + "1.0");
        id.addComment("A library that contains mathematical functions for some reason missing in the standard spin delivery.", null);
        id.addProperty(RDFS.seeAlso, m.getResource("https://www.w3.org/2005/xpath-functions/math/"));
        id.addAnnotation(m.getAnnotationProperty(OWL.versionInfo), "version 1.0", null);

        MATH.cos.inModel(m)
                .addProperty(RDF.type, SPIN.Function)
                .addProperty(RDFS.subClassOf, SPL.MathematicalFunctions)
                .addProperty(SPINMAP.shortLabel, "cos")
                .addProperty(RDFS.label, "cosinus")
                .addProperty(RDFS.comment, "Returns the cosine of the argument. The argument is an angle in radians.")
                .addProperty(SPIN.returnType, XSD.xdouble)
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg1)
                        .addProperty(SPL.valueType, XSD.xdouble)
                        .addProperty(RDFS.comment, "Radians"));

        m.write(System.out, "ttl");
    }
}

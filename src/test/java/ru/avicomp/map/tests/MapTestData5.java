package ru.avicomp.map.tests;

import org.apache.jena.vocabulary.XSD;
import ru.avicomp.ontapi.jena.model.*;

import java.util.stream.Stream;

/**
 * Created by @szuev on 23.05.2018.
 */
abstract class MapTestData5 extends AbstractMapTest {

    @Override
    public OntGraphModel assembleSource() {
        OntGraphModel m = createDataModel("source");
        String ns = m.getID().getURI() + "#";
        OntClass class1 = m.createOntEntity(OntClass.class, ns + "SrcClass1");
        OntNDP prop1 = m.createOntEntity(OntNDP.class, ns + "srcDataProperty1");
        OntNDP prop2 = m.createOntEntity(OntNDP.class, ns + "srcDataProperty2");
        OntDT xdouble = m.getOntEntity(OntDT.class, XSD.xdouble);
        prop1.addDomain(class1);
        prop1.addRange(xdouble);
        prop2.addDomain(class1);
        prop2.addRange(xdouble);

        // data
        OntIndividual.Named individual1 = class1.createIndividual(ns + "A");
        OntIndividual.Named individual2 = class1.createIndividual(ns + "B");
        individual1.addProperty(prop1, xdouble.createLiteral(1));
        individual2.addProperty(prop2, xdouble.createLiteral(Math.E));
        return m;
    }

    @Override
    public OntGraphModel assembleTarget() {
        OntGraphModel m = createDataModel("target");
        String ns = m.getID().getURI() + "#";
        OntClass clazz = m.createOntEntity(OntClass.class, ns + "DstClass1");
        OntDT xdouble = m.getOntEntity(OntDT.class, XSD.xdouble);
        Stream.of("dstDataProperty1", "dstDataProperty2").forEach(s -> {
            OntNDP p = m.createOntEntity(OntNDP.class, ns + s);
            p.addDomain(clazz);
            p.addRange(xdouble);
        });
        return m;
    }

    @Override
    public String getDataNameSpace() {
        return getNameSpace(MapTestData5.class);
    }
}

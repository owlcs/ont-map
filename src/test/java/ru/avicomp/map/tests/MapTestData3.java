package ru.avicomp.map.tests;

import org.apache.jena.vocabulary.XSD;
import ru.avicomp.ontapi.jena.model.*;

/**
 * Created by @szuev on 10.05.2018.
 */
abstract class MapTestData3 extends AbstractMapTest {

    @Override
    public OntGraphModel assembleSource() {
        OntGraphModel m = createDataModel("people");
        String ns = m.getID().getURI() + "#";
        OntClass person = m.createOntEntity(OntClass.class, ns + "Person");
        OntDT string = m.getOntEntity(OntDT.class, XSD.xstring);

        OntNDP address = createDataProperty(m, "address", person, string);
        OntNDP firstName = createDataProperty(m, "first-name", person, string);
        OntNDP secondName = createDataProperty(m, "second-name", person, string);
        OntNDP middleName = createDataProperty(m, "middle-name", person, string);
        OntNDP gender = createDataProperty(m, "gender", person, m.getOntEntity(OntDT.class, XSD.xboolean));

        //todo:
        return m;
    }

    private static OntNDP createDataProperty(OntGraphModel m, String name, OntClass domain, OntDT range) {
        OntNDP res = m.createOntEntity(OntNDP.class, m.getID().getURI() + "#" + name);
        res.addDomain(domain);
        res.addRange(range);
        return res;
    }

    @Override
    public OntGraphModel assembleTarget() {
        OntGraphModel m = createDataModel("contacts");
        String ns = m.getID().getURI() + "#";
        OntClass person = m.createOntEntity(OntClass.class, ns + "Person");
        OntClass address = m.createOntEntity(OntClass.class, ns + "Address");

        OntNOP hasAddress = m.createOntEntity(OntNOP.class, ns + "person-address");
        hasAddress.addRange(address);
        hasAddress.addDomain(person);

        // todo:
        return m;
    }

    @Override
    public String getDataNameSpace() {
        return getNameSpace(MapTestData2.class);
    }
}

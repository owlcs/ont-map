package ru.avicomp.map.tests;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.shared.PrefixMapping;
import org.junit.Assert;
import ru.avicomp.map.Context;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.MapModel;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntIndividual;
import ru.avicomp.ontapi.jena.model.OntNDP;

/**
 * Created by @szuev on 01.05.2018.
 */
public class RelatedContextMapTest extends MapTestData2 {

    @Override
    public void validate(OntGraphModel result) {
        Assert.assertEquals(4, result.listNamedIndividuals().count());

        OntIndividual.Named iBob = TestUtils.findOntEntity(result, OntIndividual.Named.class, "Bob");
        OntIndividual.Named iJane = TestUtils.findOntEntity(result, OntIndividual.Named.class, "Jane");
        OntIndividual.Named iJhon = TestUtils.findOntEntity(result, OntIndividual.Named.class, "Jhon");
        OntIndividual.Named iKarl = TestUtils.findOntEntity(result, OntIndividual.Named.class, "Karl");

        // no address for Jane and Jhon
        Assert.assertEquals(0, TestUtils.plainAssertions(iJane).count());
        Assert.assertEquals(0, TestUtils.plainAssertions(iJhon).count());

        // Bob and Karl:
        Assert.assertEquals(1, TestUtils.plainAssertions(iBob).count());
        Assert.assertEquals(1, TestUtils.plainAssertions(iKarl).count());
        Assert.assertEquals(DATA_ADDRESS_BOB, getString(iBob));
        Assert.assertEquals(DATA_ADDRESS_KARL, getString(iKarl));
    }

    private static String getString(OntIndividual i) {
        return TestUtils.plainAssertions(i)
                .map(Statement::getObject)
                .filter(RDFNode::isLiteral)
                .map(RDFNode::asLiteral)
                .map(Literal::getString).findFirst()
                .orElseThrow(AssertionError::new);
    }

    @Override
    public MapModel assembleMapping(MapManager manager, OntGraphModel src, OntGraphModel dst) {
        PrefixMapping pm = manager.prefixes();
        String targetNS = dst.getID().getURI() + "#";
        OntClass person = TestUtils.findOntEntity(src, OntClass.class, "Person");
        OntClass contact = TestUtils.findOntEntity(src, OntClass.class, "Contact");
        OntClass user = TestUtils.findOntEntity(dst, OntClass.class, "User");
        OntNDP contactAddress = TestUtils.findOntEntity(src, OntNDP.class, "address");
        OntNDP userAddress = TestUtils.findOntEntity(dst, OntNDP.class, "user-address");

        MapModel res = manager.createMapModel();
        res.setID(getNameSpace() + "/map")
                .addComment("Used functions: spinmapl:relatedSubjectContext, spinmapl:changeNamespace, spinmap:equals", null);

        Context person2user = res.createContext(person, user);
        Context contact2user = person2user.createRelatedContext(contact);

        MapFunction changeNamespace = manager.getFunction(pm.expandPrefix("spinmapl:changeNamespace"));
        person2user.addClassBridge(changeNamespace
                .create().add(pm.expandPrefix("spinmapl:targetNamespace"), targetNS).build());
        MapFunction equals = manager.getFunction(pm.expandPrefix("spinmap:equals"));
        String arg1 = pm.expandPrefix("sp:arg1");
        contact2user.addPropertyBridge(equals.create().add(arg1, contactAddress.getURI()).build(), userAddress);
        return res;
    }
}

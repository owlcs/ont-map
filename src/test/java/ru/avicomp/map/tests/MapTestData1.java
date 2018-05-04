package ru.avicomp.map.tests;

import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.Assert;
import org.junit.Test;
import ru.avicomp.map.Context;
import ru.avicomp.map.Managers;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.MapModel;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntIndividual;
import ru.avicomp.ontapi.jena.model.OntNDP;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by @szuev on 25.04.2018.
 */
abstract class MapTestData1 extends AbstractMapTest {

    @Test
    public abstract void testInference();

    @Test
    public void testDeleteContext() {
        OntGraphModel src = assembleSource();
        OntGraphModel dst = assembleTarget();
        MapManager manager = Managers.getMapManager();
        MapModel mapping = assembleMapping(manager, src, dst);
        List<Context> contexts = mapping.contexts().collect(Collectors.toList());
        Assert.assertEquals(1, contexts.size());
        mapping = mapping.removeContext(contexts.get(0));
        TestUtils.debug(mapping);
        Assert.assertEquals(0, mapping.contexts().count());
        Assert.assertEquals(5, mapping.getGraph().getBaseGraph().size());
    }

    @Override
    public OntGraphModel assembleSource() {
        OntGraphModel m = createModel("source");
        String ns = m.getID().getURI() + "#";
        OntClass class1 = m.createOntEntity(OntClass.class, ns + "SourceClass1");
        OntNDP prop1 = m.createOntEntity(OntNDP.class, ns + "sourceDataProperty1");
        OntNDP prop2 = m.createOntEntity(OntNDP.class, ns + "sourceDataProperty2");
        OntNDP prop3 = m.createOntEntity(OntNDP.class, ns + "sourceDataProperty3");
        prop1.addDomain(class1);
        prop2.addDomain(class1);
        OntIndividual.Named individual1 = class1.createIndividual(ns + "a");
        OntIndividual.Named individual2 = class1.createIndividual(ns + "b");
        class1.createIndividual(ns + "c");
        // data property assertions:
        individual1.addProperty(prop1, "x y z", "e");
        individual1.addProperty(prop2, ResourceFactory.createTypedLiteral(2));
        individual1.addProperty(prop3, "individual#1 - property#3 value");

        individual2.addProperty(prop1, "A");
        individual2.addProperty(prop2, "B");
        individual2.addProperty(prop3, "individual#2 - property#3 value");
        return m;
    }

    @Override
    public OntGraphModel assembleTarget() {
        OntGraphModel m = createModel("target");
        String ns = m.getID().getURI() + "#";
        OntClass clazz = m.createOntEntity(OntClass.class, ns + "TargetClass1");
        m.createOntEntity(OntNDP.class, ns + "targetDataProperty2").addDomain(clazz);
        return m;
    }

    MapModel createFreshMapping(MapManager manager, String comment) {
        MapModel res = manager.createMapModel();
        // TopBraid Composer (gui, not spin) has difficulties with anonymous ontologies:
        res.setID(getNameSpace() + "/map")
                .addComment(comment, null);
        return res;
    }
}

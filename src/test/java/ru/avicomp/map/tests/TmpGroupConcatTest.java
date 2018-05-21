package ru.avicomp.map.tests;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;
import ru.avicomp.map.*;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntIndividual;
import ru.avicomp.ontapi.jena.model.OntNDP;

/**
 * Created by @szuev on 17.05.2018.
 */
public class TmpGroupConcatTest extends AbstractMapTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TmpGroupConcatTest.class);

    @Override
    public MapModel assembleMapping(MapManager manager, OntGraphModel src, OntGraphModel dst) {
        MapModel res = createMappingModel(manager, "TODO");
        OntClass srcClass = TestUtils.findOntEntity(src, OntClass.class, "SourceClass1");
        OntClass dstClass = TestUtils.findOntEntity(dst, OntClass.class, "TargetClass1");
        OntNDP srcProp = TestUtils.findOntEntity(src, OntNDP.class, "sourceDataProperty1");
        OntNDP dstProp = TestUtils.findOntEntity(dst, OntNDP.class, "targetDataProperty1");
        Context context = res.createContext(srcClass, dstClass,
                manager.getFunction(SPINMAPL.composeURI.getURI()).create()
                        .addLiteral(SPINMAPL.template, "http://{?1}").build());

        MapFunction groupConcat = manager.getFunction(AVC.groupConcat);
        MapFunction getIRI = manager.getFunction(AVC.asIRI);
        MapFunction get = manager.getFunction(AVC.currentIndividual);

        context.addPropertyBridge(groupConcat.create()
                .addLiteral(SPINMAPL.separator, ",")
                .addFunction(SP.arg2, get.create())
                .add(SP.arg1.getURI(), getIRI.create()
                        .addProperty(SP.arg1, srcProp))
                .build(), dstProp);
        return res;
    }

    @Test
    public void _developTest() {
        OntGraphModel s = assembleSource();
        TestUtils.debug(s);
        OntGraphModel t = assembleTarget();
        TestUtils.debug(t);
        MapManager manager = Managers.getMapManager();
        MapModel map = assembleMapping(manager, s, t);
        TestUtils.debug(map);

        manager.getInferenceEngine().run(map, s.getGraph(), t.getGraph());
        TestUtils.debug(t);
        //todo: validate
    }


    @Override
    public OntGraphModel assembleSource() {
        OntGraphModel m = createDataModel("source");
        String ns = m.getID().getURI() + "#";
        OntClass class1 = m.createOntEntity(OntClass.class, ns + "SourceClass1");
        OntNDP prop1 = m.createOntEntity(OntNDP.class, ns + "sourceDataProperty1");
        OntNDP prop2 = m.createOntEntity(OntNDP.class, ns + "sourceDataProperty2");
        OntNDP prop3 = m.createOntEntity(OntNDP.class, ns + "sourceDataProperty3");
        prop1.addDomain(class1);
        prop2.addDomain(class1);
        prop3.addDomain(class1);
        OntIndividual.Named individual1 = class1.createIndividual(ns + "individual-1");
        OntIndividual.Named individual2 = class1.createIndividual(ns + "individual-2");
        OntIndividual.Named individual3 = class1.createIndividual(ns + "individual-3");

        // data property assertions:
        individual1.addProperty(prop1, "C");
        individual1.addProperty(prop1, "A");
        individual1.addProperty(prop1, "B");

        individual2.addProperty(prop1, m.createTypedLiteral(2));
        individual2.addProperty(prop1, m.createTypedLiteral(4.34));

        individual3.addProperty(prop1, "D");
        individual3.addProperty(prop1, m.createTypedLiteral(23));
        return m;
    }

    @Override
    public OntGraphModel assembleTarget() {
        OntGraphModel m = createDataModel("target");
        String ns = m.getID().getURI() + "#";
        OntClass clazz = m.createOntEntity(OntClass.class, ns + "TargetClass1");
        m.createOntEntity(OntNDP.class, ns + "targetDataProperty1").addDomain(clazz);
        m.createOntEntity(OntNDP.class, ns + "targetDataProperty2").addDomain(clazz);
        return m;
    }
}
package ru.avicomp.map.tests;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.*;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntNDP;

/**
 * TODO not ready
 * Created by @szuev on 10.05.2018.
 */
public class SplitMapTest extends MapTestData3 {
    private static final Logger LOGGER = LoggerFactory.getLogger(SplitMapTest.class);

    @Test
    public void testInference() {
        LOGGER.info("Assembly models.");
        OntGraphModel s = assembleSource();
        TestUtils.debug(s);
        OntGraphModel t = assembleTarget();
        TestUtils.debug(t);
        MapManager manager = Managers.getMapManager();
        MapModel m = assembleMapping(manager, s, t);
        // todo: debug
        //m.asOntModel().add(SPINMAP.rule, SPIN.rulePropertyMaxIterationCount, ResourceFactory.createTypedLiteral(2));
        // todo: for debug
        /*OntStatement st = m.asOntModel().statements(null, SP.arg1, TestUtils.findOntEntity(t, OntNOP.class, "contact-address")).findFirst().orElseThrow(AssertionError::new);
        m.asOntModel().add(st.getSubject(), st.getPredicate(), SPIN._arg1);
        m.asOntModel().remove(st);*/

        TestUtils.debug(m);

        LOGGER.info("Run inference.");
        manager.getInferenceEngine().run(m, s, t);
        TestUtils.debug(t);


        LOGGER.info("Validate.");
        //todo:
        Assert.assertEquals(s.listNamedIndividuals().count() * 2, t.listNamedIndividuals().count());
    }

    @Override
    public MapModel assembleMapping(MapManager manager, OntGraphModel src, OntGraphModel dst) {
        OntNDP firstName = TestUtils.findOntEntity(src, OntNDP.class, "first-name");
        OntNDP secondName = TestUtils.findOntEntity(src, OntNDP.class, "second-name");
        OntNDP address = TestUtils.findOntEntity(src, OntNDP.class, "address");
        OntClass srcPerson = TestUtils.findOntEntity(src, OntClass.class, "Person");
        OntClass dstContact = TestUtils.findOntEntity(dst, OntClass.class, "Contact");
        OntClass dstAddress = TestUtils.findOntEntity(dst, OntClass.class, "Address");
        OntNDP dstName = TestUtils.findOntEntity(dst, OntNDP.class, "full-name");
        String dstNS = dst.getNsURIPrefix(dst.getID().getURI() + "#");

        MapFunction buildURI1 = manager.getFunction(SPINMAPL.buildURI1.getURI());
        MapFunction buildURI2 = manager.getFunction(SPINMAPL.buildURI2.getURI());
        MapFunction equals = manager.getFunction(SPINMAP.equals.getURI());

        MapModel res = createMappingModel(manager, "TODO");
        Context context1 = res.createContext(srcPerson, dstContact, buildURI2.create()
                .addProperty(SP.arg1, firstName)
                .addProperty(SP.arg2, secondName)
                .addLiteral(SPINMAPL.template, dstNS + ":{?1}-{?2}").build());
        context1.addPropertyBridge(equals.create().addProperty(SP.arg1, firstName).build(), dstName);
        Context context2 = res.createContext(srcPerson, dstAddress, buildURI1.create()
                .addProperty(SP.arg1, address).addLiteral(SPINMAPL.template, dstNS + ":{?1}").build());

        return res.bindContexts(context1, context2);
    }
}

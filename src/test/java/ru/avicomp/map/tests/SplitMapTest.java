package ru.avicomp.map.tests;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;
import ru.avicomp.map.*;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntNDP;

/**
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
        TestUtils.debug(m);

        LOGGER.info("Run inference.");
        manager.getInferenceEngine().run(m, s, t);
        TestUtils.debug(t);

        LOGGER.info("Validate.");
        //todo:
        throw new AssertionError("TODO");
    }

    @Override
    public MapModel assembleMapping(MapManager manager, OntGraphModel src, OntGraphModel dst) {
        OntNDP firstName = TestUtils.findOntEntity(src, OntNDP.class, "first-name");
        OntNDP secondName = TestUtils.findOntEntity(src, OntNDP.class, "first-name");
        OntNDP address = TestUtils.findOntEntity(src, OntNDP.class, "address");
        OntClass srcPerson = TestUtils.findOntEntity(src, OntClass.class, "Person");
        OntClass dstPerson = TestUtils.findOntEntity(dst, OntClass.class, "Person");
        OntClass dstAddress = TestUtils.findOntEntity(dst, OntClass.class, "Address");
        String dstNS = dst.getNsURIPrefix(dst.getID().getURI() + "#");

        MapFunction buildURI1 = manager.getFunction(SPINMAPL.buildURI1.getURI());
        MapFunction buildURI2 = manager.getFunction(SPINMAPL.buildURI2.getURI());

        MapModel res = createMappingModel(manager, "TODO");
        Context context1 = res.createContext(srcPerson, dstPerson, buildURI2.create()
                .add(SP.arg1, firstName)
                .add(SP.arg2, secondName)
                .add(SPINMAPL.template, dstNS + ":{?1}-{?2}").build());
        Context context2 = res.createContext(srcPerson, dstAddress, buildURI1.create()
                .add(SP.arg1, address).add(SPINMAPL.template, dstNS + ":{?1}").build());

        res.bindContexts(context1, context2);
        //todo:
        return res;
    }
}

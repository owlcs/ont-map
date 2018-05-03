package ru.avicomp.map.tests;

import org.apache.jena.shared.PrefixMapping;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.map.*;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntNDP;

/**
 * Created by @szuev on 01.05.2018.
 */
public class RelatedContextMapTest extends SimpleMapData2 {
    private final Logger LOGGER = LoggerFactory.getLogger(RelatedContextMapTest.class);

    @Test
    @Override
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

        // todo:
        Assert.fail("Not ready");
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
                .addComment("Used functions: spinmapl:relatedSubjectContext, spinmapl:changeNamespace", null);

        Context person2user = res.createContext(person, user);
        Context contact2user = person2user.createRelatedContext(contact);

        MapFunction changeNamespace = manager.getFunction(pm.expandPrefix("spinmapl:changeNamespace"));
        person2user.addExpression(changeNamespace
                .createFunctionCall().add(pm.expandPrefix("spinmapl:targetNamespace"), targetNS).build());
        MapFunction ifFunc = manager.getFunction(pm.expandPrefix("sp:if"));
        MapFunction bound = manager.getFunction(pm.expandPrefix("sp:bound"));
        MapFunction isBound = manager.getFunction(pm.expandPrefix("smf:isBound"));
        MapFunction equals = manager.getFunction(pm.expandPrefix("spinmap:equals"));
        String arg1 = pm.expandPrefix("sp:arg1");
        String arg2 = pm.expandPrefix("sp:arg2");
        String arg3 = pm.expandPrefix("sp:arg3");
        contact2user.addPropertyBridge(
                equals.createFunctionCall().add(arg1, contactAddress.getURI()).build(),
//                ifFunc.createFunctionCall()
//                        .add(arg1,
//                                bound.createFunctionCall().add(arg1, contactAddress.getURI()))
//                        .add(arg2, equals.createFunctionCall().add(arg1, contactAddress.getURI()))
//                        .add(arg3, "Unknown address")
//                        .build(),
                userAddress);
        // todo:
        return res;
    }
}

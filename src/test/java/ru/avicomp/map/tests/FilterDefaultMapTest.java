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
import ru.avicomp.ontapi.jena.model.OntNDP;

/**
 * Created by @szuev on 07.05.2018.
 */
public class FilterDefaultMapTest extends MapTestData2 {
    private static final Logger LOGGER = LoggerFactory.getLogger(FilterDefaultMapTest.class);

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
        //todo:
    }

    @Override
    public MapModel assembleMapping(MapManager manager, OntGraphModel src, OntGraphModel dst) {
        OntClass person = TestUtils.findOntEntity(src, OntClass.class, "Person");
        OntClass user = TestUtils.findOntEntity(dst, OntClass.class, "User");
        OntNDP firstName = TestUtils.findOntEntity(src, OntNDP.class, "firstName");
        OntNDP secondName = TestUtils.findOntEntity(src, OntNDP.class, "secondName");
        OntNDP age = TestUtils.findOntEntity(src, OntNDP.class, "age");
        OntNDP resultName = TestUtils.findOntEntity(dst, OntNDP.class, "user-name");

        MapModel res = manager.createMapModel();
        res.setID(getNameSpace() + "/map")
                .addComment("Used functions: avc:UUID, avc:withDefault, spinmapl:concatWithSeparator", null);
        Context context = res.createContext(person, user, manager.getFunction(AVC.UUID.getURI()).create().build());
        MapFunction concatWithSeparator = manager.getFunction(SPINMAPL.concatWithSeparator.getURI());
        MapFunction withDefault = manager.getFunction(AVC.withDefault.getURI());
        MapFunction.Call propertyMapFunc = concatWithSeparator.create()
                .add(SP.arg1.getURI(), withDefault.create()
                        .add(SP.arg1.getURI(), firstName.getURI())
                        .add(SP.arg2.getURI(), "Unknown first name"))
                .add(SP.arg2.getURI(), withDefault.create()
                        .add(SP.arg1.getURI(), secondName.getURI())
                        .add(SP.arg2.getURI(), "Unknown second name"))
                .add(SPINMAPL.separator.getURI(), ", ")
                .build();
        context.addPropertyBridge(propertyMapFunc, resultName);
        // todo:
        return res;
    }
}

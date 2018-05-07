package ru.avicomp.map.tests;

import org.junit.Test;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.MapModel;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntNDP;

/**
 * Created by @szuev on 07.05.2018.
 */
public class FilterDefaultMapTest extends MapTestData2 {

    @Test
    @Override
    public void testInference() {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public MapModel assembleMapping(MapManager manager, OntGraphModel src, OntGraphModel dst) {
        OntClass person = TestUtils.findOntEntity(src, OntClass.class, "Person");
        OntClass user = TestUtils.findOntEntity(dst, OntClass.class, "User");
        OntNDP firstName = TestUtils.findOntEntity(src, OntNDP.class, "firstName");
        OntNDP secondName = TestUtils.findOntEntity(src, OntNDP.class, "secondName");
        OntNDP resultName = TestUtils.findOntEntity(dst, OntNDP.class, "user-name");
        // todo:
        return null;
    }
}

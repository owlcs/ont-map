package ru.avicomp.map.tests;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.map.*;
import ru.avicomp.map.spin.Exceptions;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntGraphModel;

import static ru.avicomp.map.spin.Exceptions.CONTEXT_REQUIRE_TARGET_FUNCTION;
import static ru.avicomp.map.spin.Exceptions.Key;

/**
 * Created by @szuev on 18.04.2018.
 */
public class ExceptionsTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExceptionsTest.class);

    @Test
    public void testNonTargetFuncError() {
        String uri = "ex://test";
        String ns = uri + "#";
        OntGraphModel m = OntModelFactory.createModel();
        m.setID(uri);
        OntClass src = m.createOntEntity(OntClass.class, ns + "src");
        OntClass dst = m.createOntEntity(OntClass.class, ns + "dst");
        MapManager manager = Managers.getMapManager();
        MapModel map = manager.createMapModel();
        Context context = map.createContext(src, dst);
        Assert.assertNotNull(context);
        MapFunction f = manager.getFunction(manager.prefixes().expandPrefix("smf:currentUserName"));
        Assert.assertNotNull(f);
        try {
            context.addClassBridge(f.create().build());
            Assert.fail("Expression has been added successfully");
        } catch (Exceptions.SpinMapException e) {
            LOGGER.debug("Exception: {}", e.getMessage());
            Assert.assertSame(CONTEXT_REQUIRE_TARGET_FUNCTION, e.getCode());
            Assert.assertEquals(f.name(), e.getString(Key.FUNCTION));
            Assert.assertEquals(src.getURI(), e.getString(Key.CONTEXT_SOURCE));
            Assert.assertEquals(dst.getURI(), e.getString(Key.CONTEXT_TARGET));
        }
    }

    @Test
    public void testBuildFunction() {
        MapManager m = Managers.getMapManager();
        MapFunction f = m.getFunction(m.prefixes().expandPrefix("spinmapl:concatWithSeparator"));
        try {
            f.create().build();
            Assert.fail("Expected error");
        } catch (MapJenaException j) {
            LOGGER.debug("Exception: {}", j.getMessage());
            Assert.assertEquals(3, ((Exceptions.SpinMapException) j).getList(Key.ARG).size());
            Assert.assertEquals(f.name(), ((Exceptions.SpinMapException) j).getString(Key.FUNCTION));
        }
        try {
            f.create().add(m.prefixes().expandPrefix("sp:arg1"), "x").build();
            Assert.fail("Expected error");
        } catch (MapJenaException j) {
            LOGGER.debug("Exception: {}", j.getMessage());
            Assert.assertEquals(2, ((Exceptions.SpinMapException) j).getList(Key.ARG).size());
            Assert.assertEquals(f.name(), ((Exceptions.SpinMapException) j).getString(Key.FUNCTION));
        }
        String p = "http://unknown-prefix.org";
        try {
            f.create().add(p, "xxx");
            Assert.fail("Expected error");
        } catch (Exceptions.SpinMapException e) {
            LOGGER.debug("Exception: {}", e.getMessage());
            Assert.assertEquals(p, e.getString(Key.ARG));
            Assert.assertEquals(f.name(), e.getString(Key.FUNCTION));
        }
    }
}

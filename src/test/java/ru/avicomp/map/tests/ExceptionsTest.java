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
            context.addExpression(f.createFunctionCall().build());
            Assert.fail("Expression has been added successfully");
        } catch (Exceptions.SpinMapException e) {
            LOGGER.debug("Exception: {}", e.getMessage());
            Assert.assertSame(CONTEXT_REQUIRE_TARGET_FUNCTION, e.getCode());
            Assert.assertEquals(f.name(), e.getInfo().get(Key.FUNCTION));
            Assert.assertEquals(src.getURI(), e.getInfo().get(Key.CONTEXT_SOURCE));
            Assert.assertEquals(dst.getURI(), e.getInfo().get(Key.CONTEXT_TARGET));
        }
    }
}

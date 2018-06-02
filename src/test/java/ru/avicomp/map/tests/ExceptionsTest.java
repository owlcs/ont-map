package ru.avicomp.map.tests;

import org.apache.jena.shared.PrefixMapping;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;
import ru.avicomp.map.*;
import ru.avicomp.map.spin.Exceptions;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntNDP;

import java.util.Arrays;

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
        } catch (MapJenaException j) { // no required arg
            LOGGER.debug("Exception: {}", j.getMessage());
            Assert.assertEquals(2, ((Exceptions.SpinMapException) j).getList(Key.ARG).size());
            Assert.assertEquals(f.name(), ((Exceptions.SpinMapException) j).getString(Key.FUNCTION));
        }
        String p = "http://unknown-prefix.org";
        try {
            f.create().add(p, "xxx");
            Assert.fail("Expected error");
        } catch (Exceptions.SpinMapException e) { // non existent arg
            LOGGER.debug("Exception: {}", e.getMessage());
            Assert.assertEquals(p, e.getString(Key.ARG));
            Assert.assertEquals(f.name(), e.getString(Key.FUNCTION));
        }
    }

    @Test(expected = Exceptions.SpinMapException.class)
    public void testBuildCallNoRequiredArg() {
        Managers.getMapManager().getFunction(SPINMAPL.buildURI1).create().build();
    }

    @Test(expected = Exceptions.SpinMapException.class)
    public void testBuildCallNonExistArg() {
        Managers.getMapManager().getFunction(SP.resource("contains"))
                .create()
                .addLiteral(SP.arg1, "a")
                .addLiteral(SP.arg2, "b")
                .addLiteral(SPINMAPL.template, "target:xxx")
                .build();
    }

    @Test
    public void testBuildImproperMapping() {
        MapManager manager = Managers.getMapManager();
        PrefixMapping pm = manager.prefixes();

        AbstractMapTest test = new BuildURIMapTest();
        OntGraphModel s = test.assembleSource();
        OntGraphModel t = test.assembleTarget();

        OntClass sc1 = TestUtils.findOntEntity(s, OntClass.class, "SourceClass1");
        OntNDP sp1 = TestUtils.findOntEntity(s, OntNDP.class, "sourceDataProperty1");
        OntNDP sp2 = TestUtils.findOntEntity(s, OntNDP.class, "sourceDataProperty2");
        OntClass tc1 = TestUtils.findOntEntity(t, OntClass.class, "TargetClass1");
        OntNDP tp1 = TestUtils.findOntEntity(t, OntNDP.class, "targetDataProperty2");

        MapModel m = test.createMappingModel(manager, "Test improper mappings");
        Context c = m.createContext(sc1, tc1);
        Assert.assertEquals(1, m.contexts().count());
        long count = m.asOntModel().statements().count();

        MapFunction.Call mapFunction, filterFunction;

        LOGGER.debug("Class bridge: wrong filter function.");
        mapFunction = manager.getFunction(SPINMAPL.buildURI2)
                .create()
                .addProperty(SP.arg1, sp1)
                .addProperty(SP.arg2, sp2)
                .addLiteral(SPINMAPL.template, "target:xxx")
                .build();
        filterFunction = manager.getFunction(SPINMAPL.self).create().build();
        try {
            c.addClassBridge(filterFunction, mapFunction);
            Assert.fail("Class bridge is added successfully");
        } catch (MapJenaException j) {
            print(j);
        }
        Assert.assertEquals(count, m.asOntModel().statements().count());

        LOGGER.debug("Class bridge: wrong mapping function.");
        filterFunction = manager.getFunction(pm.expandPrefix("sp:strends"))
                .create()
                .addProperty(SP.arg1, sp1)
                .addProperty(SP.arg2, sp2)
                .build();
        mapFunction = manager.getFunction(pm.expandPrefix("spl:subClassOf"))
                .create()
                .addClass(SP.arg1, sc1)
                .addClass(SP.arg2, tc1)
                .build();
        try {
            c.addClassBridge(filterFunction, mapFunction);
            Assert.fail("Class bridge is added successfully");
        } catch (MapJenaException j) {
            print(j);
        }
        Assert.assertEquals(count, m.asOntModel().statements().count());

        LOGGER.debug("Property bridge: wrong mapping function.");
        mapFunction = manager.getFunction(pm.expandPrefix("spl:subClassOf")).create()
                .addProperty(SP.arg1, sp1)
                .addClass(SP.arg2, tc1).build();
        filterFunction = manager.getFunction(pm.expandPrefix("sp:ge")).create()
                .addProperty(SP.arg1, sp2)
                .addLiteral(SP.arg2, "x").build();
        try {
            c.addPropertyBridge(filterFunction, mapFunction, tp1);
            Assert.fail("Property bridge is added successfully");
        } catch (MapJenaException j) {
            print(j);
        }
        Assert.assertEquals(count, m.asOntModel().statements().count());

        LOGGER.debug("Property bridge: wrong filter function.");
        filterFunction = manager.getFunction(pm.expandPrefix("spl:instanceOf")).create()
                .addClass(SP.arg1, sc1)
                .addLiteral(SP.arg2, sp1.getURI()).build();
        mapFunction = manager.getFunction(pm.expandPrefix("sp:day")).create()
                .addProperty(SP.arg1, sp2).build();
        try {
            c.addPropertyBridge(filterFunction, mapFunction, tp1);
            Assert.fail("Property bridge is added successfully");
        } catch (MapJenaException j) {
            print(j);
        }
        Assert.assertEquals(count, m.asOntModel().statements().count());

        LOGGER.debug("Property bridge: wrong target property.");
        filterFunction = manager.getFunction(pm.expandPrefix("sp:isBlank")).create()
                .addClass(SP.arg1, sc1).build();
        mapFunction = manager.getFunction(pm.expandPrefix("sp:timezone")).create()
                .addProperty(SP.arg1, sp1).build();
        try {
            c.addPropertyBridge(filterFunction, mapFunction, sp2);
            Assert.fail("Property bridge is added successfully");
        } catch (MapJenaException j) {
            print(j);
        }
        Assert.assertEquals(count, m.asOntModel().statements().count());
    }

    private static void print(MapJenaException j) {
        LOGGER.debug("Exception: {}", j.getMessage());
        Arrays.stream(j.getSuppressed()).forEach(e -> LOGGER.debug("Suppressed: {}", e.getMessage()));
    }
}


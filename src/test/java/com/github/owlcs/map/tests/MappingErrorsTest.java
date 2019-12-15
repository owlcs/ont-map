/*
 * This file is part of the ONT MAP.
 * The contents of this file are subject to the Apache License, Version 2.0.
 * Copyright (c) 2019, The University of Manchester, owl.cs group.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.owlcs.map.tests;

import com.github.owlcs.map.*;
import com.github.owlcs.map.spin.Exceptions;
import com.github.owlcs.map.spin.vocabulary.AVC;
import com.github.owlcs.map.spin.vocabulary.FN;
import com.github.owlcs.map.spin.vocabulary.MATH;
import com.github.owlcs.map.spin.vocabulary.SPINMAPL;
import com.github.owlcs.map.tests.maps.AbstractMapTest;
import com.github.owlcs.map.tests.maps.BuildURIMapTest;
import com.github.owlcs.map.tests.maps.MathOpsMapTest;
import com.github.owlcs.map.utils.TestUtils;
import com.github.owlcs.ontapi.jena.OntModelFactory;
import com.github.owlcs.ontapi.jena.impl.conf.OntModelConfig;
import com.github.owlcs.ontapi.jena.model.OntClass;
import com.github.owlcs.ontapi.jena.model.OntDataProperty;
import com.github.owlcs.ontapi.jena.model.OntModel;
import com.github.owlcs.ontapi.jena.model.OntObjectProperty;
import org.apache.jena.graph.Factory;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.XSD;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;

/**
 * Created by @szuev on 18.04.2018.
 */
public class MappingErrorsTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(MappingErrorsTest.class);

    private static MapManager manager;

    @BeforeClass
    public static void before() {
        manager = Managers.createMapManager();
    }

    @Test
    public void testWrongContextFilterFunction() {
        AbstractMapTest test = new BuildURIMapTest();
        OntModel s = test.assembleSource();
        OntModel t = test.assembleTarget();
        OntClass sc1 = TestUtils.findOntEntity(s, OntClass.Named.class, "SourceClass1");
        OntDataProperty sp1 = TestUtils.findOntEntity(s, OntDataProperty.class, "sourceDataProperty1");
        OntDataProperty sp2 = TestUtils.findOntEntity(s, OntDataProperty.class, "sourceDataProperty2");
        OntClass tc1 = TestUtils.findOntEntity(t, OntClass.Named.class, "TargetClass1");
        MapModel m = test.createMappingModel(manager, "Test improper mappings");
        MapContext c = m.createContext(sc1, tc1);
        Assert.assertEquals(1, m.contexts().count());
        long count = m.asGraphModel().statements().count();

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
            TestUtils.assertCode(j, Exceptions.CONTEXT_NOT_BOOLEAN_FILTER_FUNCTION);
        }
        Assert.assertEquals(count, m.asGraphModel().statements().count());
    }

    @Test
    public void testWrongContextMappingFunction() {
        String uri = "ex://test";
        String ns = uri + "#";
        OntModel m = OntModelFactory.createModel();
        m.setID(uri);
        OntClass src = m.createOntClass(ns + "src");
        OntClass dst = m.createOntClass(ns + "dst");
        MapModel map = manager.createMapModel();
        MapContext c = map.createContext(src, dst);
        Assert.assertNotNull(c);
        MapFunction f = manager.getFunction(manager.prefixes().expandPrefix("smf:currentUserName"));
        Assert.assertNotNull(f);
        try {
            c.addClassBridge(f.create().build());
            Assert.fail("Expression has been added successfully");
        } catch (Exceptions.SpinMapException e) {
            TestUtils.assertCode(e, Exceptions.CONTEXT_REQUIRE_TARGET_FUNCTION);
            Assert.assertEquals(f.name(), e.getDetails(Exceptions.Key.FUNCTION));
            Assert.assertEquals(c.name(), e.getDetails(Exceptions.Key.CONTEXT));
        }
    }

    @Test
    public void testWrongPropertyBridgeMappingFunction() {
        AbstractMapTest test = new BuildURIMapTest();
        OntModel s = test.assembleSource();
        OntModel t = test.assembleTarget();
        OntClass sc1 = TestUtils.findOntEntity(s, OntClass.Named.class, "SourceClass1");
        OntDataProperty sp1 = TestUtils.findOntEntity(s, OntDataProperty.class, "sourceDataProperty1");
        OntClass tc1 = TestUtils.findOntEntity(t, OntClass.Named.class, "TargetClass1");
        OntDataProperty tp1 = TestUtils.findOntEntity(t, OntDataProperty.class, "targetDataProperty2");
        MapModel m = test.createMappingModel(manager, "Test improper mappings");
        MapContext c = m.createContext(sc1, tc1);
        Assert.assertEquals(1, m.contexts().count());
        long count = m.asGraphModel().statements().count();


        LOGGER.debug("Property bridge: a target function is given.");
        MapFunction.Call mapFunction = manager.getFunction(SPINMAPL.buildURI1)
                .create()
                .addProperty(SP.arg1, sp1)
                .addLiteral(SPINMAPL.template, "target:xxx")
                .build();
        try {
            c.addPropertyBridge(mapFunction, tp1);
            Assert.fail("Property bridge is added successfully");
        } catch (MapJenaException j) {
            TestUtils.assertCode(j, Exceptions.PROPERTY_BRIDGE_REQUIRE_NONTARGET_FUNCTION);
        }
        Assert.assertEquals(count, m.asGraphModel().statements().count());
    }

    @Test
    public void testWrongPropertyBridgeFilterFunction() {
        PrefixMapping pm = manager.prefixes();
        AbstractMapTest test = new BuildURIMapTest();
        OntModel s = test.assembleSource();
        OntModel t = test.assembleTarget();
        OntClass sc1 = TestUtils.findOntEntity(s, OntClass.Named.class, "SourceClass1");
        OntDataProperty sp1 = TestUtils.findOntEntity(s, OntDataProperty.class, "sourceDataProperty1");
        OntDataProperty sp2 = TestUtils.findOntEntity(s, OntDataProperty.class, "sourceDataProperty2");
        OntClass tc1 = TestUtils.findOntEntity(t, OntClass.Named.class, "TargetClass1");
        OntDataProperty tp1 = TestUtils.findOntEntity(t, OntDataProperty.class, "targetDataProperty2");
        MapModel m = test.createMappingModel(manager, "Test improper mappings");
        MapContext c = m.createContext(sc1, tc1);
        Assert.assertEquals(1, m.contexts().count());
        long count = m.asGraphModel().statements().count();

        MapFunction.Call mapFunction, filterFunction;
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
            TestUtils.assertCode(j, Exceptions.PROPERTY_BRIDGE_WRONG_FILTER_FUNCTION);
        }
        Assert.assertEquals(count, m.asGraphModel().statements().count());
    }

    @Test
    public void testWrongPropertyBridgeTargetProperty() {
        PrefixMapping pm = manager.prefixes();
        AbstractMapTest test = new BuildURIMapTest();
        OntModel s = test.assembleSource();
        OntModel t = test.assembleTarget();
        OntClass sc1 = TestUtils.findOntEntity(s, OntClass.Named.class, "SourceClass1");
        OntDataProperty sp1 = TestUtils.findOntEntity(s, OntDataProperty.class, "sourceDataProperty1");
        OntDataProperty sp2 = TestUtils.findOntEntity(s, OntDataProperty.class, "sourceDataProperty2");
        OntClass tc1 = TestUtils.findOntEntity(t, OntClass.Named.class, "TargetClass1");
        MapModel m = test.createMappingModel(manager, "Test improper mappings");
        MapContext c = m.createContext(sc1, tc1);
        Assert.assertEquals(1, m.contexts().count());
        long count = m.asGraphModel().statements().count();

        MapFunction.Call mapFunction, filterFunction;
        LOGGER.debug("Property bridge: wrong target property.");
        filterFunction = manager.getFunction(pm.expandPrefix("sp:isBlank")).create()
                .addClass(SP.arg1, sc1).build();
        mapFunction = manager.getFunction(pm.expandPrefix("sp:timezone")).create()
                .addProperty(SP.arg1, sp1).build();
        try {
            c.addPropertyBridge(filterFunction, mapFunction, sp2);
            Assert.fail("Property bridge is added successfully");
        } catch (MapJenaException j) {
            TestUtils.assertCode(j, Exceptions.PROPERTY_BRIDGE_WRONG_TARGET_PROPERTY);
        }
        Assert.assertEquals(count, m.asGraphModel().statements().count());
    }

    @Test
    public void testWrongFunctionArgumentValue() {
        PrefixMapping pm = manager.prefixes();
        AbstractMapTest test = new BuildURIMapTest();
        OntModel s = test.assembleSource();
        OntModel t = test.assembleTarget();
        OntClass sc1 = TestUtils.findOntEntity(s, OntClass.Named.class, "SourceClass1");
        OntDataProperty sp1 = TestUtils.findOntEntity(s, OntDataProperty.class, "sourceDataProperty1");
        OntDataProperty sp2 = TestUtils.findOntEntity(s, OntDataProperty.class, "sourceDataProperty2");
        OntClass tc1 = TestUtils.findOntEntity(t, OntClass.Named.class, "TargetClass1");
        OntDataProperty tp1 = TestUtils.findOntEntity(t, OntDataProperty.class, "targetDataProperty2");
        MapModel m = test.createMappingModel(manager, "Test improper mappings");
        MapContext c = m.createContext(sc1, tc1);
        Assert.assertEquals(1, m.contexts().count());
        long count = m.asGraphModel().statements().count();

        MapFunction.Call mapFunction, filterFunction;
        LOGGER.debug("Property bridge: wrong mapping function (incompatible argument type).");
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
            TestUtils.assertCode(j, Exceptions.PROPERTY_BRIDGE_WRONG_MAPPING_FUNCTION);
            Assert.assertEquals(1, j.getSuppressed().length);
            Throwable e = j.getSuppressed()[0];
            TestUtils.assertCode(e, Exceptions.FUNCTION_CALL_WRONG_ARGUMENT_STRING_VALUE);
        }
        Assert.assertEquals(count, m.asGraphModel().statements().count());
    }

    @Test(expected = Exceptions.SpinMapException.class)
    public void testInferenceFail() {
        AbstractMapTest tc = new MathOpsMapTest();
        OntModel src = tc.assembleSource();
        OntModel dst = tc.assembleTarget();

        OntClass srcClass = TestUtils.findOntEntity(src, OntClass.Named.class, "SrcClass1");
        OntClass dstClass = TestUtils.findOntEntity(dst, OntClass.Named.class, "DstClass1");
        OntDataProperty srcProp2 = TestUtils.findOntEntity(src, OntDataProperty.class, "srcDataProperty2");
        OntDataProperty dstProp3 = TestUtils.findOntEntity(dst, OntDataProperty.class, "dstDataProperty3");

        MapModel map = tc.createMappingModel(manager, "Wrong fn:format-number picture");
        MapContext c = map.createContext(srcClass, dstClass, manager.getFunction(SPINMAPL.self).create().build());
        c.addPropertyBridge(manager.getFunction(FN.format_number).create()
                .addProperty(SP.arg1, srcProp2)
                .addLiteral(SP.arg2, "9,9.000"), dstProp3);
        manager.getInferenceEngine(map).run(src, dst);
        TestUtils.debug(dst);
    }

    @Test
    public void testValidateFunctionAgainstModel() {
        MapManager m = Managers.createMapManager();

        MapFunction.Call func1 = m.getFunction(SP.floor).create().addLiteral(SP.arg1, "x").build();
        try {
            m.createMapModel().validate(func1);
            Assert.fail("Validation passed.");
        } catch (MapJenaException j) {
            TestUtils.assertCode(j, Exceptions.MAPPING_FUNCTION_VALIDATION_FAIL);
            Assert.assertEquals(1, j.getSuppressed().length);
            TestUtils.assertCode(j.getSuppressed()[0], Exceptions.FUNCTION_CALL_WRONG_ARGUMENT_STRING_VALUE);
        }
        MapFunction.Call func2 = m.getFunction(SP.floor).create().addLiteral(SP.arg1, 2.3).build();
        m.createMapModel().validate(func2);

        MapFunction.Call func3 = m.getFunction(SP.floor).create()
                .addFunction(SP.arg1, m.getFunction(SP.not)
                        .create().addLiteral(SP.arg1, "x").build()).build();
        try {
            m.createMapModel().validate(func3);
            Assert.fail("Validation passed.");
        } catch (MapJenaException j) {
            TestUtils.assertCode(j, Exceptions.MAPPING_FUNCTION_VALIDATION_FAIL);
            Assert.assertEquals(1, j.getSuppressed().length);
            TestUtils.assertCode(j.getSuppressed()[0], Exceptions.FUNCTION_CALL_INCOMPATIBLE_NESTED_FUNCTION);
        }
        MapFunction.Call func4 = m.getFunction(SP.floor).create()
                .addFunction(SP.arg1, m.getFunction(SP.ceil)
                        .create().addLiteral(SP.arg1, "x").build()).build();

        try {
            m.createMapModel().validate(func4);
            Assert.fail("Validation passed.");
        } catch (MapJenaException j) {
            TestUtils.assertCode(j, Exceptions.MAPPING_FUNCTION_VALIDATION_FAIL);
            Assert.assertEquals(1, j.getSuppressed().length);
            MapJenaException j2 = (MapJenaException) j.getSuppressed()[0];
            TestUtils.assertCode(j2, Exceptions.FUNCTION_CALL_WRONG_ARGUMENT_FUNCTION_VALUE);
            Assert.assertEquals(1, j2.getSuppressed().length);
            TestUtils.assertCode(j2.getSuppressed()[0], Exceptions.FUNCTION_CALL_WRONG_ARGUMENT_STRING_VALUE);
        }

        MapFunction.Call func5 = m.getFunction(SP.floor).create()
                .addFunction(SP.arg1, m.getFunction(SP.ceil)
                        .create().addLiteral(SP.arg1, -1).build()).build();
        m.createMapModel().validate(func5);
    }

    @Test
    public void testContextValidatePropertyFunction() {
        MapManager m = Managers.createMapManager();
        OntModel s = OntModelFactory.createModel().setNsPrefixes(OntModelFactory.STANDARD);
        s.setID("http://ont");
        OntClass c1 = s.createOntClass("s");
        OntClass c2 = s.createOntClass("t");
        OntDataProperty p = s.createOntEntity(OntDataProperty.class, "p");
        MapContext context = m.createMapModel().createContext(c1, c2, m.getFunction(SPINMAPL.self).create());
        MapFunction.Call toTest = m.getFunction(AVC.asIRI).create().addProperty(SP.arg1, p).build();
        try {
            context.validate(toTest);
            Assert.fail("Validation passed.");
        } catch (MapJenaException j) {
            TestUtils.assertCode(j, Exceptions.CONTEXT_FUNCTION_VALIDATION_FAIL);
            Assert.assertEquals(1, j.getSuppressed().length);
            MapJenaException j2 = (MapJenaException) j.getSuppressed()[0];
            TestUtils.assertCode(j2, Exceptions.FUNCTION_CALL_WRONG_ARGUMENT_NON_CONTEXT_PROPERTY);
            Assert.assertEquals(0, j2.getSuppressed().length);
        }
        // add to context
        p.addDomain(c1);
        context.validate(toTest);
    }

    @Test
    public void testContextValidatePropertyRange() {
        MapManager m = Managers.createMapManager();
        OntModel s = OntModelFactory.createModel().setNsPrefixes(OntModelFactory.STANDARD);
        s.setID("http://ont");
        OntClass c1 = s.createOntClass("s");
        OntClass c2 = s.createOntClass("t");
        OntDataProperty p = s.createOntEntity(OntDataProperty.class, "p");

        MapContext context = m.createMapModel().createContext(c1, c2, m.getFunction(SPINMAPL.self).create());
        MapFunction.Call toTest = m.getFunction(MATH.atan).create().addProperty(SP.arg1, p).build();
        try {
            context.validate(toTest);
            Assert.fail("Validation passed.");
        } catch (MapJenaException j) {
            TestUtils.assertCode(j, Exceptions.CONTEXT_FUNCTION_VALIDATION_FAIL);
            MapJenaException j2 = (MapJenaException) j.getSuppressed()[0];
            TestUtils.assertCode(j2, Exceptions.FUNCTION_CALL_WRONG_ARGUMENT_NON_CONTEXT_PROPERTY);
            Assert.assertEquals(0, j2.getSuppressed().length);
        }
        // add to context
        p.addDomain(c1);
        context.validate(toTest);

        // add range:
        p.addRange(s.getDatatype(XSD.xstring));
        try {
            context.validate(toTest);
            Assert.fail("Validation passed.");
        } catch (MapJenaException j) {
            TestUtils.assertCode(j, Exceptions.CONTEXT_FUNCTION_VALIDATION_FAIL);
            MapJenaException j2 = (MapJenaException) j.getSuppressed()[0];
            TestUtils.assertCode(j2, Exceptions.FUNCTION_CALL_WRONG_ARGUMENT_INCOMPATIBLE_RANGE);
            Assert.assertEquals(0, j2.getSuppressed().length);
        }
    }

    @Test
    public void testContextValidateObjectProperty() {
        MapManager m = Managers.createMapManager();
        OntModel s = OntModelFactory.createModel(Factory.createGraphMem(), OntModelConfig.ONT_PERSONALITY_LAX)
                .setNsPrefixes(OntModelFactory.STANDARD);
        s.setID("http://ont");
        OntClass c1 = s.createOntClass("s");
        OntClass c2 = s.createOntClass("t");
        OntObjectProperty.Named p1 = s.createObjectProperty("p1");
        p1.addDomain(c1);
        OntDataProperty p2 = s.createDataProperty("p2").addDomain(c1).addRange(XSD.xdouble);

        MapContext context = m.createMapModel().createContext(c1, c2, m.getFunction(SPINMAPL.self).create());
        MapFunction.Call toTest = m.getFunction(MATH.atan2).create()
                .addProperty(SP.arg1, p1).addProperty(SP.arg2, p2).build();
        try {
            context.validate(toTest);
            Assert.fail("Validation passed.");
        } catch (MapJenaException j) {
            TestUtils.assertCode(j, Exceptions.CONTEXT_FUNCTION_VALIDATION_FAIL);
            MapJenaException j2 = (MapJenaException) j.getSuppressed()[0];
            TestUtils.assertCode(j2, Exceptions.FUNCTION_CALL_WRONG_ARGUMENT_OBJECT_PROPERTY);
            Assert.assertEquals(0, j2.getSuppressed().length);
        }

        // add punning
        s.createDataProperty("p1");
        context.validate(toTest);
    }

}


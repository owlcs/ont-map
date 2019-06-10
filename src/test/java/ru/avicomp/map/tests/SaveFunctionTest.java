/*
 * This file is part of the ONT MAP.
 * The contents of this file are subject to the Apache License, Version 2.0.
 * Copyright (c) 2018, Avicomp Services, AO
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

package ru.avicomp.map.tests;

import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.*;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;
import ru.avicomp.map.utils.GraphUtils;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntNDP;
import ru.avicomp.ontapi.jena.model.OntNOP;

import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Created by @szz on 27.11.2018.
 */
public class SaveFunctionTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(SaveFunctionTest.class);

    @Test
    public void testMathGeoMapping() {
        double[][] data = new double[][]{{23, 54}, {89, 346.2}};
        MapManager m = Managers.createMapManager();
        long count = m.functions().count();

        OntGraphModel src = MathGeoMapTest.createSourceModel(data);
        OntGraphModel dst1 = MathGeoMapTest.createTargetModel();
        MapModel map1 = MathGeoMapTest.createMapping(m, src, dst1);
        TestUtils.debug(map1);

        MapContext c = map1.contexts().findFirst().orElseThrow(AssertionError::new);
        PropertyBridge p = c.properties().findFirst().orElseThrow(AssertionError::new);
        MapFunction.Call call = p.getMapping();
        LOGGER.debug("Call: {}", call);

        String name = "http://ex.com#hypotenuse";
        LOGGER.info("Create new function <{}>.", name);
        MapFunction func = call.save(name);
        LOGGER.debug("New function: {}", func);
        TestUtils.debug(TestUtils.getPrimaryGraph(m));
        Assert.assertNotNull(func);
        Assert.assertEquals(name, func.name());
        Assert.assertTrue(m.getFunction(name).isUserDefined());
        Assert.assertEquals(count + 1, m.functions().count());
        Assert.assertEquals(XSD.xdouble.getURI(), func.type());
        Assert.assertEquals(2, func.args().count());
        Assert.assertEquals(XSD.xdouble.getURI(), func.args().map(MapFunction.Arg::type)
                .distinct().collect(Collectors.joining()));
        assertFunctionDependencies(func, m);

        LOGGER.info("Create new mapping using function <{}> and run inference.", name);
        OntGraphModel dst2 = MathGeoMapTest.createTargetModel();
        OntClass s = TestUtils.findOntEntity(src, OntClass.class, "Coordinates");
        OntClass t = TestUtils.findOntEntity(dst2, OntClass.class, "Coordinates");
        OntNDP x = TestUtils.findOntEntity(src, OntNDP.class, "x");
        OntNDP y = TestUtils.findOntEntity(src, OntNDP.class, "y");
        OntNDP r = TestUtils.findOntEntity(dst2, OntNDP.class, "r");

        MapModel map2 = m.createMapModel();
        map2.asGraphModel().setID(map1.name())
                .setVersionIRI(map1.name() + "/version#2");
        map2.createContext(s, t, m.getFunction(SPINMAPL.self).create().build())
                .addPropertyBridge(func.create().addProperty(SP.arg1, x).addProperty(SP.arg2, y).build(), r);

        map2.runInference(src.getGraph(), dst2.getGraph());
        MathGeoMapTest.validate(dst2, data);
    }

    @Test
    public void testNestedFuncMapping() {
        MapManager m = Managers.createMapManager();
        long count = m.functions().count();
        NestedFuncMapTest data = new NestedFuncMapTest();
        OntGraphModel src = data.assembleSource();
        OntGraphModel dst = data.assembleTarget();
        MapModel map1 = data.assembleMapping(m, src, dst);
        TestUtils.debug(map1);

        MapContext c = map1.contexts().findFirst().orElseThrow(AssertionError::new);
        PropertyBridge p = c.properties().findFirst().orElseThrow(AssertionError::new);

        String ns = "http://xxx#";
        MapFunction.Call targetCall = c.getMapping();
        LOGGER.debug("Target call: {}", targetCall);
        MapFunction target = targetCall.save(ns + "changeNamespace");
        MapFunction.Call propertyCall = p.getMapping();
        LOGGER.debug("Property call: {}", propertyCall);
        MapFunction property = propertyCall.save(ns + "formatInSomeWeirdStyle");
        TestUtils.debug(TestUtils.getPrimaryGraph(m));
        Assert.assertEquals(count + 2, m.functions().count());
        Assert.assertEquals(2, target.args().count());
        Assert.assertEquals(3, property.args().count());
        Assert.assertTrue(target.isTarget());
        Assert.assertFalse(property.isTarget());
        assertFunctionDependencies(target, m);
        assertFunctionDependencies(property, m);

        OntClass s = TestUtils.findOntEntity(src, OntClass.class, "SourceClass1");
        OntClass t = TestUtils.findOntEntity(dst, OntClass.class, "TargetClass1");
        List<OntNDP> sp = src.dataProperties().collect(Collectors.toList());
        OntNDP tp = dst.dataProperties().findFirst().orElseThrow(AssertionError::new);
        MapModel map2 = m.createMapModel(ns + "test");
        map2.createContext(s, t, target.create().addClass(SP.arg1, t).build())
                .addPropertyBridge(property.create().addProperty(SP.arg1, sp.get(0))
                        .addProperty(SP.arg2, sp.get(1)).addProperty(SP.arg3, sp.get(2)).build(), tp);
        TestUtils.debug(map2);

        Model res = ModelFactory.createDefaultModel();
        m.getInferenceEngine(map2).run(src, res);
        TestUtils.debug(res);
        long expected = TestUtils.shouldGenerateNamedIndividuals(m) ? 8 : 5;
        Assert.assertEquals(expected, res.size());
    }

    @Test
    public void testPropertyChainMapping() {
        MapManager m = Managers.createMapManager();
        PropertyChainMapTest data = new PropertyChainMapTest();
        OntGraphModel src = data.assembleSource();
        OntGraphModel dst = data.assembleTarget();
        MapModel map1 = data.assembleMapping(m, src, dst);
        TestUtils.debug(map1);

        String ns = "http://xxx#";
        OntNDP name = TestUtils.findOntEntity(dst, OntNDP.class, "name");
        OntNDP message = TestUtils.findOntEntity(dst, OntNDP.class, "message");
        MapContext c = map1.contexts().findFirst().orElseThrow(AssertionError::new);
        MapFunction target = c.getMapping().getFunction();
        MapFunction deriveName = c.properties().filter(s -> name.equals(s.getTarget()))
                .findFirst().orElseThrow(AssertionError::new)
                .getMapping().save(ns + "deriveName");
        MapFunction deriveMessage = c.properties().filter(s -> message.equals(s.getTarget()))
                .findFirst().orElseThrow(AssertionError::new)
                .getMapping().save(ns + "deriveMessage");

        LOGGER.debug("New first func: {}", deriveName);
        LOGGER.debug("New second func: {}", deriveMessage);
        TestUtils.debug(TestUtils.getPrimaryGraph(m));
        Assert.assertEquals(2, m.functions().filter(MapFunction::isUserDefined).count());
        assertFunctionDependencies(deriveMessage, m);
        assertFunctionDependencies(deriveName, m);

        LOGGER.info("Create new mapping using functions <{}> and <{}>. Then run inference.", deriveName, deriveMessage);
        OntClass CDSPR_D00001 = TestUtils.findOntEntity(src, OntClass.class, "CDSPR_D00001");
        OntClass CCPAS_000011 = TestUtils.findOntEntity(src, OntClass.class, "CCPAS_000011");
        OntClass CCPAS_000005 = TestUtils.findOntEntity(src, OntClass.class, "CCPAS_000005");
        OntClass CCPAS_000006 = TestUtils.findOntEntity(src, OntClass.class, "CCPAS_000006");
        MapFunction.Call OASUU = m.getFunction(AVC.asIRI).create().addProperty(SP.arg1,
                TestUtils.findOntEntity(src, OntNOP.class, "OASUU")).build();
        OntNDP DEUUU = TestUtils.findOntEntity(src, OntNDP.class, "DEUUU");

        OntClass resClass = TestUtils.findOntEntity(dst, OntClass.class, "Res");
        OntNDP nameProp = TestUtils.findOntEntity(dst, OntNDP.class, "name");
        OntNDP messageProp = TestUtils.findOntEntity(dst, OntNDP.class, "message");

        MapModel map2 = m.createMapModel()
                .createContext(CDSPR_D00001, resClass, target.create()
                        .addLiteral(SPINMAPL.template, "result:res-{?1}"))
                .addPropertyBridge(deriveName.create()
                        .addFunction(SP.arg1, OASUU)
                        .addProperty(SP.arg3, DEUUU)
                        .addClass(SP.arg2, CCPAS_000011), nameProp)
                .getContext()
                .addPropertyBridge(deriveMessage.create()
                        .addFunction(SP.arg1, OASUU)
                        .addProperty(SP.arg3, DEUUU)
                        .addClass(SP.arg2, CCPAS_000005)
                        .addClass(SP.arg4, CCPAS_000006), messageProp)
                .getModel();
        TestUtils.debug(map2);
        map2.runInference(src.getBaseGraph(), dst.getBaseGraph());
        TestUtils.debug(dst);
        data.validate(dst);
    }

    @Test
    public void testFilterIndividualsMapping() {
        MapManager m = Managers.createMapManager();
        FilterIndividualsMapTest data = new FilterIndividualsMapTest();
        OntGraphModel src = data.assembleSource();
        OntGraphModel dst = data.assembleTarget();
        MapModel map1 = data.assembleMapping(m, src, dst);
        TestUtils.debug(map1);

        MapContext c = map1.contexts().findFirst().orElseThrow(AssertionError::new);
        MapFunction.Call call = c.getFilter();

        String ns = "http://xxx#";
        MapFunction isAdult = call.save(ns + "isAdult");
        TestUtils.debug(TestUtils.getPrimaryGraph(m));
        Assert.assertEquals(1, m.functions().filter(MapFunction::isUserDefined).count());
        Assert.assertTrue(isAdult.isBoolean());
        Assert.assertTrue(isAdult.isUserDefined());
        assertFunctionDependencies(isAdult, m);

        LOGGER.info("Create new mapping using filter function <{}> and run inference.", isAdult);
        OntClass person = TestUtils.findOntEntity(src, OntClass.class, "Person");
        OntClass user = TestUtils.findOntEntity(dst, OntClass.class, "User");
        OntNDP srcAge = TestUtils.findOntEntity(src, OntNDP.class, "age");
        OntNDP dstAge = TestUtils.findOntEntity(dst, OntNDP.class, "user-age");
        MapModel map2 = m.createMapModel()
                .createContext(person, user)
                .addClassBridge(isAdult.create().addProperty(SP.arg1, srcAge).build(), c.getMapping())
                .addPropertyBridge(m.getFunction(SPINMAP.equals).create().addProperty(SP.arg1, srcAge).build(), dstAge)
                .getModel();
        TestUtils.debug(map2);
        map2.runInference(src.getBaseGraph(), dst.getBaseGraph());
        data.validate(dst);
    }

    @Test
    public void testVarArgMappingAndShare() {
        MapManager m1 = Managers.createMapManager();
        long count = m1.functions().count();
        VarArgMapTest data = new VarArgMapTest();
        OntGraphModel src = data.assembleSource();
        OntGraphModel dst = data.assembleTarget();
        MapModel map1 = data.assembleMapping(m1, src, dst);
        TestUtils.debug(map1);

        MapContext c = map1.contexts().findFirst().orElseThrow(AssertionError::new);
        OntNDP tp1 = TestUtils.findOntEntity(dst, OntNDP.class, "tp1");
        MapFunction.Call call = c.properties()
                .filter(x -> tp1.equals(x.getTarget())).findFirst().orElseThrow(AssertionError::new).getMapping();
        String funcName = "http://zzz#formatString";
        LOGGER.info("Create function <{}>", funcName);
        MapFunction formatString = call.save(funcName);
        TestUtils.debug(TestUtils.getPrimaryGraph(m1));
        Assert.assertTrue(formatString.isUserDefined());
        assertFunctionDependencies(formatString, m1);
        Assert.assertEquals(7, formatString.args().count());
        Assert.assertEquals(count + 1, m1.functions().count());
        Assert.assertEquals(2, m1.getGraph().find(Node.ANY, RDFS.subPropertyOf.asNode(), Node.ANY).toSet().size());

        LOGGER.info("Copy to another manager");
        MapManager m2 = Managers.createMapManager();
        Assert.assertEquals(count, m2.functions().count());
        m2.addGraph(m1.getGraph());
        Assert.assertEquals(count + 1, m2.functions().count());

        LOGGER.info("Assemble new mapping using property mapping function <{}> and run inference.", formatString);
        OntClass sc = TestUtils.findOntEntity(src, OntClass.class, "C1");
        OntClass tc = TestUtils.findOntEntity(dst, OntClass.class, "TC1");
        OntNDP p1 = TestUtils.findOntEntity(src, OntNDP.class, "p1");
        OntNDP p2 = TestUtils.findOntEntity(src, OntNDP.class, "p2");
        OntNDP p3 = TestUtils.findOntEntity(src, OntNDP.class, "p3");
        OntNDP p4 = TestUtils.findOntEntity(src, OntNDP.class, "p4");
        OntNDP p5 = TestUtils.findOntEntity(src, OntNDP.class, "p5");
        OntNDP p6 = TestUtils.findOntEntity(src, OntNDP.class, "p6");
        OntNDP p7 = TestUtils.findOntEntity(src, OntNDP.class, "p7");

        MapModel map2 = m2.createMapModel()
                .createContext(sc, tc, m2.getFunction(SPINMAPL.changeNamespace).create()
                        .addLiteral(SPINMAPL.targetNamespace, "urn://x#"))
                .addPropertyBridge(m2.getFunction(formatString.name()).create()
                        .addProperty(SP.arg1, p1)
                        .addProperty(SP.arg2, p2)
                        .addProperty(SP.arg3, p3)
                        .addProperty(SP.arg4, p4)
                        .addProperty(SP.arg5, p5)
                        .addProperty(SP.getArgProperty(6), p6)
                        .addProperty(SP.getArgProperty(7), p7)
                        .build(), tp1)
                .getModel();
        TestUtils.debug(map2);
        map2.runInference(src.getBaseGraph(), dst.getBaseGraph());

        TestUtils.debug(dst);
        VarArgMapTest.validate(dst, false);
    }

    @Test
    public void testShareUserDefinedFunctions() {
        String ns = "http://my-functions.ex#";
        MapManager m = Managers.createMapManager();
        // prepare data:
        MapModel map1 = new FilterIndividualsMapTest().assembleMapping(m);
        MapModel map2 = new MathGeoMapTest().assembleMapping(m);
        MapModel map3 = new NestedFuncMapTest().assembleMapping(m);
        // save functions:
        map1.contexts().findFirst().orElseThrow(AssertionError::new).getFilter().save(ns + "isAdult");
        map2.contexts().findFirst().orElseThrow(AssertionError::new)
                .properties().findFirst().orElseThrow(AssertionError::new).getMapping().save(ns + "hypotenuse");
        map3.contexts().findFirst().orElseThrow(AssertionError::new).getMapping().save(ns + "changeNamespace");
        TestUtils.debug(TestUtils.getPrimaryGraph(m));
        Assert.assertEquals(3, m.functions().filter(MapFunction::isUserDefined).count());

        // tests:
        MapManager m2 = Managers.createMapManager(m.getGraph()); // <-- passing an unmodifiable graph!
        Assert.assertEquals(3, m2.functions().filter(MapFunction::isUserDefined).count());

        MapManager m3 = Managers.createOWLMapManager(GraphUtils.unwrap(m.getGraph()), new ReentrantReadWriteLock());
        TestUtils.debug(TestUtils.getPrimaryGraph(m3));
        Assert.assertEquals(3, m3.functions().filter(MapFunction::isUserDefined).count());

        MapManager m4 = Managers.createOWLMapManager();
        Assert.assertEquals(0, m4.functions().filter(MapFunction::isUserDefined).count());
        m4.addGraph(m.getGraph());
        Assert.assertEquals(3, m4.functions().filter(MapFunction::isUserDefined).count());
        TestUtils.debug(TestUtils.getPrimaryGraph(m4));
    }

    private static void assertFunctionDependencies(MapFunction function, MapManager manager) {
        Assert.assertEquals(function.dependencies().map(MapFunction::name).collect(Collectors.toSet()),
                manager.getFunction(function.name()).dependencies().map(MapFunction::name).collect(Collectors.toSet()));
    }

}

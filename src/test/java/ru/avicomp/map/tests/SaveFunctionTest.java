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

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.XSD;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;
import ru.avicomp.map.*;
import ru.avicomp.map.spin.MapContextImpl;
import ru.avicomp.map.spin.MapPropertiesImpl;
import ru.avicomp.map.spin.ModelCallImpl;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntNDP;

import java.util.List;
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

        String uri = "http://ex.com#hypotenuse";
        LOGGER.info("Create new function <{}>.", uri);
        MapFunction func = call.save(uri);
        TestUtils.debug(TestUtils.getPrimaryGraph(m));
        Assert.assertNotNull(func);
        Assert.assertEquals(uri, func.name());
        Assert.assertTrue(m.getFunction(uri).isUserDefined());
        Assert.assertEquals(count + 1, m.functions().count());
        Assert.assertEquals(XSD.xdouble.getURI(), func.type());
        Assert.assertEquals(2, func.args().count());
        Assert.assertEquals(XSD.xdouble.getURI(), func.args().map(MapFunction.Arg::type)
                .distinct().collect(Collectors.joining()));

        LOGGER.info("Create new mapping using function <{}> and run inference.", uri);
        OntGraphModel dst2 = MathGeoMapTest.createTargetModel();
        OntClass s = TestUtils.findOntEntity(src, OntClass.class, "Coordinates");
        OntClass t = TestUtils.findOntEntity(dst2, OntClass.class, "Coordinates");
        OntNDP x = TestUtils.findOntEntity(src, OntNDP.class, "x");
        OntNDP y = TestUtils.findOntEntity(src, OntNDP.class, "y");
        OntNDP r = TestUtils.findOntEntity(dst2, OntNDP.class, "r");

        MapModel map2 = m.createMapModel();
        map2.setID(map1.getID().getURI()).setVersionIRI(map1.getID().getURI() + "/version#2");
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
        MapFunction target = targetCall.save(ns + "getNamespace");
        MapFunction.Call propertyCall = p.getMapping();
        LOGGER.debug("Property call: {}", propertyCall);
        MapFunction property = propertyCall.save(ns + "formatInSomeWeirdStyle");
        TestUtils.debug(TestUtils.getPrimaryGraph(m));
        Assert.assertEquals(count + 2, m.functions().count());
        Assert.assertEquals(2, target.args().count());
        Assert.assertEquals(3, property.args().count());

        OntClass s = TestUtils.findOntEntity(src, OntClass.class, "SourceClass1");
        OntClass t = TestUtils.findOntEntity(dst, OntClass.class, "TargetClass1");
        List<OntNDP> sp = src.listDataProperties().collect(Collectors.toList());
        OntNDP tp = dst.listDataProperties().findFirst().orElseThrow(AssertionError::new);
        MapModel map2 = m.createMapModel(ns + "test");
        map2.createContext(s, t, target.create().addClass(SP.arg1, t).build())
                .addPropertyBridge(property.create().addProperty(SP.arg1, sp.get(0))
                        .addProperty(SP.arg2, sp.get(1)).addProperty(SP.arg3, sp.get(2)).build(), tp);
        TestUtils.debug(map2);

        Model res = ModelFactory.createDefaultModel();
        m.getInferenceEngine(map2).run(src, res);
        TestUtils.debug(res);
        Assert.assertEquals(8, res.size());
    }

    // todo: not ready
    @Test
    public void testPropertyChainMapping() {
        MapManager m = Managers.createMapManager();
        PropertyChainMapTest t = new PropertyChainMapTest();
        OntGraphModel src = t.assembleSource();
        OntGraphModel dst = t.assembleTarget();
        MapModel map = t.assembleMapping(m, src, dst);
        TestUtils.debug(map);

        MapContextImpl c = (MapContextImpl) map.contexts().findFirst().orElseThrow(AssertionError::new);
        MapPropertiesImpl p = c.listPropertyBridges().findFirst().orElseThrow(AssertionError::new);
        ModelCallImpl call = p.getMapping();

        call.save("xxxx");
        TestUtils.debug(TestUtils.getPrimaryGraph(m));
        // TODO: check
    }

    // todo: not ready
    @Test
    public void testFilterIndividualsMapping() {
        MapManager m = Managers.createMapManager();
        FilterIndividualsMapTest t = new FilterIndividualsMapTest();
        OntGraphModel src = t.assembleSource();
        OntGraphModel dst = t.assembleTarget();
        MapModel map = t.assembleMapping(m, src, dst);

        TestUtils.debug(map);

        MapContextImpl c = (MapContextImpl) map.contexts().findFirst().orElseThrow(AssertionError::new);
        ModelCallImpl call = c.getFilter();

        call.save("xxxx");
        TestUtils.debug(TestUtils.getPrimaryGraph(m));
        // TODO: check
    }


}

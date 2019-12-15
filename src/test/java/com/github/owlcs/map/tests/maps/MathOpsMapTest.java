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

package com.github.owlcs.map.tests.maps;

import com.github.owlcs.map.*;
import com.github.owlcs.map.spin.SpinModels;
import com.github.owlcs.map.spin.vocabulary.AVC;
import com.github.owlcs.map.spin.vocabulary.MATH;
import com.github.owlcs.map.utils.TestUtils;
import com.github.owlcs.ontapi.jena.model.OntClass;
import com.github.owlcs.ontapi.jena.model.OntDataProperty;
import com.github.owlcs.ontapi.jena.model.OntIndividual;
import com.github.owlcs.ontapi.jena.model.OntModel;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.PrefixMapping;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by @szuev on 23.05.2018.
 */
public class MathOpsMapTest extends MapTestData5 {
    private static final Logger LOGGER = LoggerFactory.getLogger(MathOpsMapTest.class);
    private static final String RES_URI = "http://x";

    @Test
    public void testInference() {
        OntModel s = assembleSource();
        TestUtils.debug(s);
        OntModel t = assembleTarget();
        TestUtils.debug(t);

        MapManager man = manager();
        MapModel m = assembleMapping(man, s, t);
        TestUtils.debug(m);

        LOGGER.info("Run inference");
        man.getInferenceEngine(m).run(s, t);

        TestUtils.debug(t);

        // validate:
        OntDataProperty p1 = TestUtils.findOntEntity(t, OntDataProperty.class, "dstDataProperty1");
        OntDataProperty p2 = TestUtils.findOntEntity(t, OntDataProperty.class, "dstDataProperty2");
        OntDataProperty p3 = TestUtils.findOntEntity(t, OntDataProperty.class, "dstDataProperty3");
        Assert.assertEquals(1, t.statements(null, p1, null).count());
        Assert.assertEquals(1, t.statements(null, p2, null).count());

        List<OntIndividual> res = t.individuals().collect(Collectors.toList());
        Assert.assertEquals(1, res.size());
        OntIndividual i = res.get(0);
        Assert.assertEquals(RES_URI, i.getURI());
        double v1 = TestUtils.getDoubleValue(i, p1);
        double v2 = TestUtils.getDoubleValue(i, p2);
        String v3 = TestUtils.getStringValue(i, p3);
        Assert.assertEquals(Math.PI - 1, v2, 0.01);
        Assert.assertEquals(Math.E * Math.E, v1, 0.01);
        Assert.assertEquals("2,720", v3);
        commonValidate(t);
    }

    @Override
    public MapModel assembleMapping(MapManager manager, OntModel src, OntModel dst) {
        PrefixMapping pm = manager.prefixes();

        MapFunction iri = manager.getFunction(AVC.IRI);
        MapFunction mul = manager.getFunction(SP.resource("mul"));
        MapFunction ln = manager.getFunction(MATH.log);
        MapFunction exp = manager.getFunction(MATH.exp);
        MapFunction sub = manager.getFunction(SP.sub);
        MapFunction p = manager.getFunction(MATH.pi);
        MapFunction e = manager.getFunction(pm.expandPrefix("afn:e"));
        MapFunction abs = manager.getFunction(pm.expandPrefix("fn:abs"));

        MapFunction round = manager.getFunction(pm.expandPrefix("fn:round"));
        MapFunction formatNumber = manager.getFunction(pm.expandPrefix("fn:format-number"));

        MapModel res = createMappingModel(manager, "Used functions: " +
                toMessage(pm, iri, mul, ln, exp, sub, p, e, abs, round, formatNumber));

        OntClass srcClass = TestUtils.findOntEntity(src, OntClass.Named.class, "SrcClass1");
        OntClass dstClass = TestUtils.findOntEntity(dst, OntClass.Named.class, "DstClass1");
        OntDataProperty srcProp1 = TestUtils.findOntEntity(src, OntDataProperty.class, "srcDataProperty1");
        OntDataProperty srcProp2 = TestUtils.findOntEntity(src, OntDataProperty.class, "srcDataProperty2");
        OntDataProperty dstProp1 = TestUtils.findOntEntity(dst, OntDataProperty.class, "dstDataProperty1");
        OntDataProperty dstProp2 = TestUtils.findOntEntity(dst, OntDataProperty.class, "dstDataProperty2");
        OntDataProperty dstProp3 = TestUtils.findOntEntity(dst, OntDataProperty.class, "dstDataProperty3");

        MapContext c = res.createContext(srcClass, dstClass, iri.create().addLiteral(SP.arg1, RES_URI).build());
        // e * exp($val) ::: src-prop1(=1) => dst-prop1 ::: 2.72 * exp(1) = e^2 = 7.39
        c.addPropertyBridge(mul.create()
                .addFunction(SP.arg1, e.create())
                .addFunction(SP.arg2, exp.create().addProperty(SP.arg1, srcProp1)), dstProp1);

        //  [ln($val) - pi] ::: src-prop2(=2.72) => dst-prop2 ::: - pi + ln(2.72) = 2.1415
        c.addPropertyBridge(abs.create().addFunction(SP.arg1, sub.create()
                .addFunction(SP.arg1, ln.create().addProperty(SP.arg1, srcProp2))
                .addFunction(SP.arg2, p.create())), dstProp2);

        // round(2.718281828459045, 2) => 2.72e0, format(2.72e0, '#.000', 'ru') => 2,720
        c.addPropertyBridge(formatNumber.create()
                .addFunction(SP.arg1, round.create()
                        .addProperty(SP.arg1, srcProp2)
                        .addLiteral(SP.arg2, 2))
                .addLiteral(SP.arg2, "#.000")
                .addLiteral(SP.arg3, "ru"), dstProp3);
        return res;
    }

    @Test
    public void testDeletePropertyBridge() {
        MapModel m = assembleMapping();
        m.rules().flatMap(MapResource::functions).distinct().forEach(f -> LOGGER.debug("{}", f));
        OntDataProperty prop = TestUtils.findOntEntity(m.asGraphModel(), OntDataProperty.class, "dstDataProperty2");
        MapContext c = m.contexts().findFirst().orElseThrow(AssertionError::new);
        PropertyBridge p = c.properties().filter(x -> prop.equals(x.getTarget())).findFirst().orElseThrow(AssertionError::new);
        c.deletePropertyBridge(p);
        TestUtils.debug(m);
        Assert.assertEquals(1, m.contexts().count());
        Assert.assertEquals(2, m.ontologies().count());
        Assert.assertEquals(3, m.rules().count());
        Set<MapFunction> functions = m.rules().flatMap(MapResource::functions).collect(Collectors.toSet());
        functions.forEach(f -> LOGGER.debug("{}", f));
        // no fn:abs, sp:sub, math:log, math:pi
        Assert.assertEquals(6, functions.size());
        // math:exp, avc:IRI and fn:format-number still there:
        Set<Resource> localFunctions = SpinModels.spinFunctions(m.asGraphModel().getBaseModel()).collect(Collectors.toSet());
        localFunctions.forEach(f -> LOGGER.debug("Local function: <{}>", m.asGraphModel().shortForm(f.getURI())));
        Assert.assertEquals(3, localFunctions.size());
    }
}

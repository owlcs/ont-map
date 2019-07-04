/*
 * This file is part of the ONT MAP.
 * The contents of this file are subject to the Apache License, Version 2.0.
 * Copyright (c) 2019, Avicomp Services, AO
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

package ru.avicomp.map.tests.maps;

import org.apache.jena.vocabulary.XSD;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;
import ru.avicomp.map.MapContext;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.MapModel;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.model.*;

/**
 * Created by @szuev on 04.06.2018.
 */
public class VarArgMapTest extends AbstractMapTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(VarArgMapTest.class);

    @Test
    public void testInference() {
        OntGraphModel s = assembleSource();
        OntGraphModel t = assembleTarget();

        MapManager man = manager();
        MapModel m = assembleMapping(man, s, t);
        TestUtils.debug(m);

        LOGGER.info("Run inference");
        man.getInferenceEngine(m).run(s, t);
        TestUtils.debug(t);

        LOGGER.info("Validate");
        validate(t);
    }

    public void validate(OntGraphModel t) {
        validate(t, true);
    }

    public static void validate(OntGraphModel t, boolean includeSpInResult) {
        Assert.assertEquals(2, t.individuals().count());
        OntIndividual i1 = TestUtils.findOntEntity(t, OntIndividual.Named.class, "I1");
        OntIndividual i2 = TestUtils.findOntEntity(t, OntIndividual.Named.class, "I2");
        OntNDP p1 = TestUtils.findOntEntity(t, OntNDP.class, "tp1");
        Assert.assertEquals("a+b,c[g,h,i,m]", TestUtils.getStringValue(i1, p1));
        Assert.assertEquals("d+e,f[j,k,l,n]", TestUtils.getStringValue(i2, p1));
        if (includeSpInResult) {
            OntNDP p2 = TestUtils.findOntEntity(t, OntNDP.class, "tp2");
            Assert.assertFalse(TestUtils.getBooleanValue(i1, p2));
            Assert.assertTrue(TestUtils.getBooleanValue(i2, p2));
        } else {
            Assert.assertEquals(1, i1.positiveAssertions().count());
            Assert.assertEquals(1, i2.positiveAssertions().count());
        }
        AbstractMapTest.commonValidate(t);
    }

    @Override
    public MapModel assembleMapping(MapManager manager, OntGraphModel src, OntGraphModel dst) {
        MapFunction changeNamespace = manager.getFunction(SPINMAPL.changeNamespace);
        MapFunction concat = manager.getFunction(SP.resource("concat"));
        MapFunction notIn = manager.getFunction(SP.resource("notIn"));

        MapModel res = createMappingModel(manager, "Used functions: " + toMessage(manager.prefixes(), changeNamespace, concat, notIn));

        OntClass srcClass = TestUtils.findOntEntity(src, OntClass.class, "C1");
        OntClass dstClass = TestUtils.findOntEntity(dst, OntClass.class, "TC1");
        OntNDP srcProp1 = TestUtils.findOntEntity(src, OntNDP.class, "p1");
        OntNDP srcProp2 = TestUtils.findOntEntity(src, OntNDP.class, "p2");
        OntNDP srcProp3 = TestUtils.findOntEntity(src, OntNDP.class, "p3");
        OntNDP srcProp4 = TestUtils.findOntEntity(src, OntNDP.class, "p4");
        OntNDP srcProp5 = TestUtils.findOntEntity(src, OntNDP.class, "p5");
        OntNDP srcProp6 = TestUtils.findOntEntity(src, OntNDP.class, "p6");
        OntNDP srcProp7 = TestUtils.findOntEntity(src, OntNDP.class, "p7");
        OntNDP dstProp1 = TestUtils.findOntEntity(dst, OntNDP.class, "tp1");
        OntNDP dstProp2 = TestUtils.findOntEntity(dst, OntNDP.class, "tp2");

        MapContext c = res.createContext(srcClass, dstClass, changeNamespace.create()
                .addLiteral(SPINMAPL.targetNamespace, "urn://x#")
                .build());
        c.addPropertyBridge(concat.create()
                .addProperty(AVC.vararg, srcProp1)
                .addLiteral(AVC.vararg, "+")
                .addProperty(AVC.vararg, srcProp2)
                .addLiteral(AVC.vararg, ",")
                .addProperty(AVC.vararg, srcProp3)
                .addLiteral(AVC.vararg, "[")
                .addProperty(AVC.vararg, srcProp4)
                .addLiteral(AVC.vararg, ",")
                .addProperty(AVC.vararg, srcProp5)
                .addLiteral(AVC.vararg, ",")
                .addProperty(AVC.vararg, srcProp6)
                .addLiteral(AVC.vararg, ",")
                .addProperty(AVC.vararg, srcProp7)
                .addLiteral(AVC.vararg, "]")
                .build(), dstProp1);

        c.addPropertyBridge(notIn.create()
                .addProperty(SP.arg1, srcProp1)
                .addLiteral(AVC.vararg, "a")
                .addLiteral(AVC.vararg, "b")
                .addLiteral(AVC.vararg, "d1")
                .build(), dstProp2);
        return res;
    }

    @Override
    public OntGraphModel assembleSource() {
        OntGraphModel m = createDataModel("source");
        String ns = m.getID().getURI() + "#";
        OntClass class1 = m.createOntEntity(OntClass.class, ns + "C1");
        OntNDP prop1 = m.createOntEntity(OntNDP.class, ns + "p1");
        OntNDP prop2 = m.createOntEntity(OntNDP.class, ns + "p2");
        OntNDP prop3 = m.createOntEntity(OntNDP.class, ns + "p3");
        OntNDP prop4 = m.createOntEntity(OntNDP.class, ns + "p4");
        OntNDP prop5 = m.createOntEntity(OntNDP.class, ns + "p5");
        OntNDP prop6 = m.createOntEntity(OntNDP.class, ns + "p6");
        OntNDP prop7 = m.createOntEntity(OntNDP.class, ns + "p7");
        m.dataProperties().forEach(p -> p.addDomain(class1));

        OntIndividual.Named individual1 = class1.createIndividual(ns + "I1");
        OntIndividual.Named individual2 = class1.createIndividual(ns + "I2");
        individual1.addProperty(prop1, "a");
        individual1.addProperty(prop2, "b");
        individual1.addProperty(prop3, "c");
        individual1.addProperty(prop4, "g");
        individual1.addProperty(prop5, "h");
        individual1.addProperty(prop6, "i");
        individual1.addProperty(prop7, "m");

        individual2.addProperty(prop1, "d");
        individual2.addProperty(prop2, "e");
        individual2.addProperty(prop3, "f");
        individual2.addProperty(prop4, "j");
        individual2.addProperty(prop5, "k");
        individual2.addProperty(prop6, "l");
        individual2.addProperty(prop7, "n");
        return m;
    }

    @Override
    public OntGraphModel assembleTarget() {
        OntGraphModel m = createDataModel("target");
        String ns = m.getID().getURI() + "#";
        OntClass clazz = m.createOntEntity(OntClass.class, ns + "TC1");
        OntNDP p1 = m.createOntEntity(OntNDP.class, ns + "tp1");
        p1.addDomain(clazz);
        p1.addRange(m.getOntEntity(OntDT.class, XSD.xstring));
        OntNDP p2 = m.createOntEntity(OntNDP.class, ns + "tp2");
        p2.addDomain(clazz);
        p2.addRange(m.getOntEntity(OntDT.class, XSD.xboolean));
        return m;
    }
}

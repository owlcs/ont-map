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

import com.github.owlcs.map.MapContext;
import com.github.owlcs.map.MapFunction;
import com.github.owlcs.map.MapManager;
import com.github.owlcs.map.MapModel;
import com.github.owlcs.map.spin.vocabulary.AVC;
import com.github.owlcs.map.spin.vocabulary.SPINMAPL;
import com.github.owlcs.map.utils.TestUtils;
import com.github.owlcs.ontapi.jena.model.OntClass;
import com.github.owlcs.ontapi.jena.model.OntDataProperty;
import com.github.owlcs.ontapi.jena.model.OntIndividual;
import com.github.owlcs.ontapi.jena.model.OntModel;
import org.apache.jena.vocabulary.XSD;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;

/**
 * Created by @szuev on 04.06.2018.
 */
public class VarArgMapTest extends AbstractMapTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(VarArgMapTest.class);

    @Test
    public void testInference() {
        OntModel s = assembleSource();
        OntModel t = assembleTarget();

        MapManager man = manager();
        MapModel m = assembleMapping(man, s, t);
        TestUtils.debug(m);

        LOGGER.info("Run inference");
        man.getInferenceEngine(m).run(s, t);
        TestUtils.debug(t);

        LOGGER.info("Validate");
        validate(t);
    }

    public void validate(OntModel t) {
        validate(t, true);
    }

    public static void validate(OntModel t, boolean includeSpInResult) {
        Assert.assertEquals(2, t.individuals().count());
        OntIndividual i1 = TestUtils.findOntEntity(t, OntIndividual.Named.class, "I1");
        OntIndividual i2 = TestUtils.findOntEntity(t, OntIndividual.Named.class, "I2");
        OntDataProperty p1 = TestUtils.findOntEntity(t, OntDataProperty.class, "tp1");
        Assert.assertEquals("a+b,c[g,h,i,m]", TestUtils.getStringValue(i1, p1));
        Assert.assertEquals("d+e,f[j,k,l,n]", TestUtils.getStringValue(i2, p1));
        if (includeSpInResult) {
            OntDataProperty p2 = TestUtils.findOntEntity(t, OntDataProperty.class, "tp2");
            Assert.assertFalse(TestUtils.getBooleanValue(i1, p2));
            Assert.assertTrue(TestUtils.getBooleanValue(i2, p2));
        } else {
            Assert.assertEquals(1, i1.positiveAssertions().count());
            Assert.assertEquals(1, i2.positiveAssertions().count());
        }
        AbstractMapTest.commonValidate(t);
    }

    @Override
    public MapModel assembleMapping(MapManager manager, OntModel src, OntModel dst) {
        MapFunction changeNamespace = manager.getFunction(SPINMAPL.changeNamespace);
        MapFunction concat = manager.getFunction(SP.resource("concat"));
        MapFunction notIn = manager.getFunction(SP.resource("notIn"));

        MapModel res = createMappingModel(manager, "Used functions: " + toMessage(manager.prefixes(), changeNamespace, concat, notIn));

        OntClass srcClass = TestUtils.findOntEntity(src, OntClass.Named.class, "C1");
        OntClass dstClass = TestUtils.findOntEntity(dst, OntClass.Named.class, "TC1");
        OntDataProperty srcProp1 = TestUtils.findOntEntity(src, OntDataProperty.class, "p1");
        OntDataProperty srcProp2 = TestUtils.findOntEntity(src, OntDataProperty.class, "p2");
        OntDataProperty srcProp3 = TestUtils.findOntEntity(src, OntDataProperty.class, "p3");
        OntDataProperty srcProp4 = TestUtils.findOntEntity(src, OntDataProperty.class, "p4");
        OntDataProperty srcProp5 = TestUtils.findOntEntity(src, OntDataProperty.class, "p5");
        OntDataProperty srcProp6 = TestUtils.findOntEntity(src, OntDataProperty.class, "p6");
        OntDataProperty srcProp7 = TestUtils.findOntEntity(src, OntDataProperty.class, "p7");
        OntDataProperty dstProp1 = TestUtils.findOntEntity(dst, OntDataProperty.class, "tp1");
        OntDataProperty dstProp2 = TestUtils.findOntEntity(dst, OntDataProperty.class, "tp2");

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
    public OntModel assembleSource() {
        OntModel m = createDataModel("source");
        String ns = m.getID().getURI() + "#";
        OntClass class1 = m.createOntClass(ns + "C1");
        OntDataProperty prop1 = m.createOntEntity(OntDataProperty.class, ns + "p1");
        OntDataProperty prop2 = m.createOntEntity(OntDataProperty.class, ns + "p2");
        OntDataProperty prop3 = m.createOntEntity(OntDataProperty.class, ns + "p3");
        OntDataProperty prop4 = m.createOntEntity(OntDataProperty.class, ns + "p4");
        OntDataProperty prop5 = m.createOntEntity(OntDataProperty.class, ns + "p5");
        OntDataProperty prop6 = m.createOntEntity(OntDataProperty.class, ns + "p6");
        OntDataProperty prop7 = m.createOntEntity(OntDataProperty.class, ns + "p7");
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
    public OntModel assembleTarget() {
        OntModel m = createDataModel("target");
        String ns = m.getID().getURI() + "#";
        OntClass clazz = m.createOntClass(ns + "TC1");
        OntDataProperty p1 = m.createOntEntity(OntDataProperty.class, ns + "tp1");
        p1.addDomain(clazz);
        p1.addRange(m.getDatatype(XSD.xstring));
        OntDataProperty p2 = m.createOntEntity(OntDataProperty.class, ns + "tp2");
        p2.addDomain(clazz);
        p2.addRange(m.getDatatype(XSD.xboolean));
        return m;
    }
}

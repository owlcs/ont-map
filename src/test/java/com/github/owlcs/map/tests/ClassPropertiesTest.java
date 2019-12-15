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

import com.github.owlcs.map.ClassPropertyMap;
import com.github.owlcs.map.Managers;
import com.github.owlcs.map.MapManager;
import com.github.owlcs.map.utils.TestUtils;
import com.github.owlcs.ontapi.jena.OntModelFactory;
import com.github.owlcs.ontapi.jena.impl.conf.OntModelConfig;
import com.github.owlcs.ontapi.jena.model.*;
import com.github.owlcs.ontapi.jena.utils.OntModels;
import org.apache.jena.graph.Factory;
import org.apache.jena.graph.FrontsNode;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.vocabulary.XSD;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by @szuev on 18.04.2018.
 */
public class ClassPropertiesTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClassPropertiesTest.class);
    private static MapManager manager;

    @BeforeClass
    public static void before() {
        manager = Managers.createMapManager();
    }

    @Test
    public void testPizza() throws Exception {
        OntModel m = TestUtils.load("/pizza.ttl", Lang.TURTLE);
        m.setNsPrefix("pizza", m.getID().getURI() + "#");
        m.removeNsPrefix("");
        doPrint(m);
        Map<String, Integer> expected = new LinkedHashMap<>();
        expected.put("pizza:SweetPepperTopping", 5);
        expected.put("pizza:Napoletana", 6);
        expected.put("pizza:SundriedTomatoTopping", 5);
        expected.put("pizza:DeepPanBase", 4);
        expected.put("pizza:Food", 3);
        expected.put("pizza:VegetarianPizzaEquivalent1", 2);
        expected.put("pizza:Medium", 1);
        expected.put("pizza:SpicyPizzaEquivalent", 2);
        expected.put("pizza:CheeseyPizza", 2);
        expected.put("pizza:Capricciosa", 5);
        validateClasses(m, expected);
    }

    @Test
    public void testFoaf() throws Exception {
        OntModel m = TestUtils.load("/foaf.rdf", Lang.RDFXML);
        m.setNsPrefix("foaf", "http://xmlns.com/foaf/0.1/");
        doPrint(m);
        Map<String, Integer> expected = new LinkedHashMap<>();
        expected.put("foaf:Person", 38);
        expected.put("foaf:Organization", 21);
        expected.put("foaf:PersonalProfileDocument", 4);
        expected.put("foaf:LabelProperty", 1);
        expected.put("foaf:Agent", 21);
        expected.put("foaf:OnlineAccount", 3);
        validateClasses(m, expected);
    }

    @Test
    public void testGoodrelations() throws Exception {
        OntModel m = TestUtils.load("/goodrelations.rdf", Lang.RDFXML);
        m.setNsPrefix("schema", "http://schema.org/");
        OntClass c = m.getOntClass(m.expandPrefix("gr:QuantitativeValue"));
        Assert.assertNotNull(c);
        doPrint(m);
        Map<String, Integer> expected = new LinkedHashMap<>();
        expected.put("gr:QuantitativeValueInteger", 11);
        expected.put("schema:Product", 23);
        expected.put("gr:ProductOrService", 23);
        expected.put("gr:Offering", 32);
        expected.put("gr:SomeItems", 26);
        expected.put("gr:BusinessEntityType", 3);
        expected.put("gr:License", 7);
        expected.put("gr:ProductOrServiceModel", 28);
        expected.put("gr:WarrantyPromise", 5);
        expected.put("schema:Place", 5);
        validateClasses(m, expected);
    }

    @Test
    public void testISWC() throws Exception {
        OntModel m = TestUtils.load("/iswc.ttl", Lang.TURTLE);
        doPrint(m);
        Map<String, Integer> expected = new LinkedHashMap<>();
        expected.put("vocab:organizations", 10);
        expected.put("vocab:conferences", 7);
        expected.put("vocab:papers", 9);
        expected.put("vocab:persons", 13);
        expected.put("vocab:rel_paper_topic", 4);
        expected.put("vocab:topics", 4);
        validateClasses(m, expected);
    }

    @Test
    public void testExTest() throws Exception {
        OntModel m = TestUtils.load("/ex-sup-test.ttl", Lang.TURTLE);
        doPrint(m);
        Map<String, Integer> expected = new LinkedHashMap<>();
        expected.put("ex:CCPAS_000011", 11);
        expected.put("ex:CCPIU", 19);
        expected.put("ex:CCPRU", 9);
        expected.put("ex:CRNK_000006", 5);
        expected.put("ex:CAUUU_000008", 4);
        validateClasses(m, expected);
    }

    @Test
    public void testCustom() {
        String uri = "http://ex.com";
        String ns = uri + "#";
        OntModel m = OntModelFactory.createModel(Factory.createGraphMem(), OntModelConfig.ONT_PERSONALITY_LAX);
        m.setID(uri);
        m.setNsPrefix("e", ns);
        m.setNsPrefixes(OntModelFactory.STANDARD);

        OntObjectProperty.Named o1 = m.createOntEntity(OntObjectProperty.Named.class, ns + "o1");
        OntClass c1 = m.createOntClass(ns + "C1");
        m.createOntEntity(OntDataProperty.class, ns + "d1").addDomain(c1);
        m.createOntEntity(OntDataProperty.class, ns + "d2").addDomain(c1);
        OntClass c2 = m.createOntClass(ns + "C2");
        OntClass ce1 = m.createObjectIntersectionOf(Arrays.asList(c1, m.createObjectAllValuesFrom(o1, c2)));

        OntClass c3 = m.createOntClass(ns + "C3");
        c3.addEquivalentClass(ce1);

        TestUtils.debug(m);
        m.ontObjects(OntClass.class).map(ClassPropertiesTest::classProperties).forEach(x -> LOGGER.debug("{}", x));

        Map<String, Integer> expected = new LinkedHashMap<>();
        expected.put("e:C1", 3);
        expected.put("e:C2", 1);
        expected.put("e:C3", 2);
        validateClasses(m, expected);
    }

    @Test
    public void testModifying() throws IOException {
        OntModel sub = TestUtils.load("/pizza.ttl", Lang.TURTLE);
        OntModel top = OntModelFactory.createModel();
        top.setID("http://test.x");
        top.addImport(sub);

        OntClass clazz = top.getOntClass(sub.expandPrefix(":Siciliana"));
        ClassPropertyMap map = manager.getClassProperties(top);
        Assert.assertEquals(5, map.properties(clazz).count());

        OntObject p1 = top.createOntEntity(OntDataProperty.class, "http://dp1").addDomain(clazz);
        Assert.assertEquals(6, map.properties(clazz).count());

        top.removeOntObject(p1);
        Assert.assertEquals(5, map.properties(clazz).count());

        OntObject p2 = sub.createOntEntity(OntDataProperty.class, "http://dp2").addDomain(clazz);
        Assert.assertEquals(6, map.properties(clazz).count());

        sub.removeOntObject(p2);
        Assert.assertEquals(5, map.properties(clazz).count());
    }

    private static void validateClasses(OntModel m, Map<String, Integer> expected) {
        ClassPropertyMap map = manager.getClassProperties(m);
        expected.forEach((c, v) -> {
            OntClass clazz = m.getOntClass(m.expandPrefix(c));
            Assert.assertNotNull("Can't find class " + c, clazz);
            Assert.assertEquals("Wrong properties count for " + c, v.longValue(), map.properties(clazz).count());
        });
    }

    private static void doPrint(OntModel m) {
        TestUtils.debug(m);
        m.ontObjects(OntClass.class).map(ClassPropertiesTest::classProperties).forEach(x -> LOGGER.debug("{}", x));
        LOGGER.debug("=============");
        m.ontObjects(OntProperty.class).map(ClassPropertiesTest::propertyClasses).forEach(x -> LOGGER.debug("{}", x));
    }

    private static String classProperties(OntClass ce) {
        OntModel m = ce.getModel();
        return Stream.concat(Stream.of(String.format("[%s]%s",
                OntModels.getOntType(ce).getSimpleName(), m.shortForm(ce.asNode().toString()))),
                manager.getClassProperties(m)
                        .properties(ce)
                        .sorted(Comparator.comparing(Resource::getURI))
                        .map(p -> m.shortForm(p.getURI())))
                .collect(Collectors.joining("\n\t"));
    }

    private static String propertyClasses(OntProperty pe) {
        OntModel m = pe.getModel();
        return Stream.concat(Stream.of(String.format("[%s]%s",
                OntModels.getOntType(pe).getSimpleName(), m.shortForm(pe.asNode().toString()))),
                manager.getClassProperties(m)
                        .classes(pe)
                        .map(FrontsNode::asNode)
                        .sorted(Comparator.comparing(Node::isURI)
                                .thenComparing((Function<Node, String>) Node::toString))
                        .map(c -> m.shortForm(c.toString())))
                .collect(Collectors.joining("\n\t"));
    }

    @Test
    public void testWithSubProperties() {
        OntModel m = OntModelFactory.createModel().setNsPrefixes(OntModelFactory.STANDARD);
        m.setID("http://test-class-properties-with-subPropertyOf");
        OntClass c = m.createOntClass("C");
        OntDataProperty d1 = m.createDataProperty("d1");
        OntDataProperty d2 = m.createDataProperty("d2");
        OntObjectProperty.Named o1 = m.createObjectProperty("o1");
        OntObjectProperty.Named o2 = m.createObjectProperty("o2");
        OntObjectProperty.Named o3 = m.createObjectProperty("o3");
        OntObjectProperty.Named o4 = m.createObjectProperty("o4");

        d1.addDomain(c).addRange(m.getDatatype(XSD.xstring));
        o1.addDomain(c);

        MapManager man = Managers.createMapManager();
        Set<Property> properties1 = man.getClassProperties(m).properties(c)
                .peek(x -> LOGGER.debug("1) Property: {}", x)).collect(Collectors.toSet());
        Assert.assertEquals(3, properties1.size());
        Assert.assertTrue(properties1.contains(d1));
        Assert.assertTrue(properties1.contains(o1));
        Assert.assertFalse(properties1.contains(d2));

        d2.addSuperProperty(d1);
        Set<Property> properties2 = man.getClassProperties(m).properties(c)
                .peek(x -> LOGGER.debug("2) Property: {}", x)).collect(Collectors.toSet());
        Assert.assertEquals(4, properties2.size());
        Assert.assertTrue(properties2.contains(d1));
        Assert.assertTrue(properties2.contains(d2));

        o4.addSuperProperty(o3.addSuperProperty(o2.addSuperProperty(o1)));
        Set<Property> properties3 = man.getClassProperties(m).properties(c)
                .peek(x -> LOGGER.debug("3) Property: {}", x)).collect(Collectors.toSet());
        Assert.assertEquals(7, properties3.size());

        o2.addDomain(m.getOWLThing());
        Set<Property> properties4 = man.getClassProperties(m).properties(c)
                .peek(x -> LOGGER.debug("4) Property: {}", x)).collect(Collectors.toSet());
        Assert.assertEquals(4, properties4.size());

        o3.addDomain(c);
        Set<Property> properties5 = man.getClassProperties(m).properties(c)
                .peek(x -> LOGGER.debug("5) Property: {}", x)).collect(Collectors.toSet());
        Assert.assertEquals(6, properties5.size());
    }
}

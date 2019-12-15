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
import com.github.owlcs.map.MapModel;
import com.github.owlcs.map.OWLMapManager;
import com.github.owlcs.map.spin.vocabulary.SPINMAPL;
import com.github.owlcs.map.tests.maps.PropertyChainMapTest;
import com.github.owlcs.map.utils.TestUtils;
import com.github.owlcs.ontapi.Ontology;
import com.github.owlcs.ontapi.jena.UnionGraph;
import com.github.owlcs.ontapi.jena.model.OntClass;
import com.github.owlcs.ontapi.jena.model.OntDataProperty;
import com.github.owlcs.ontapi.jena.model.OntModel;
import com.github.owlcs.ontapi.jena.utils.Graphs;
import com.github.owlcs.ontapi.jena.utils.OntModels;
import org.apache.jena.graph.Factory;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Created by @szuev on 21.06.2018.
 */
@RunWith(Parameterized.class)
public class OWLAPITest {
    private static final Logger LOGGER = LoggerFactory.getLogger(OWLAPITest.class);

    private final ManagerProvider factory;

    public OWLAPITest(ManagerProvider factory) {
        this.factory = factory;
    }

    @Parameterized.Parameters(name = "{0}")
    public static ManagerProvider[] getTestData() {
        return ManagerProvider.values();
    }

    @Test
    public void testLoadMapping() throws OWLOntologyCreationException {
        String uri = "http://test.com/some-function3";
        OntModel m = new LoadMapTestData(uri, "data-x")
                .assembleMapping(Managers.createMapManager(), null, null)
                .asGraphModel();
        String s = TestUtils.asString(m);

        OWLMapManager manager = factory.create();
        Ontology o = manager.loadOntologyFromOntologyDocument(TestUtils.createTurtleDocumentSource(s));
        TestUtils.debug(o.asGraphModel());
        Assert.assertEquals(uri, manager.getFunction(uri).name());
        Assert.assertEquals(1, manager.ontologies().count());
        Assert.assertEquals(1, manager.mappings().count());
        manager.createMapModel();
        Assert.assertEquals(2, manager.mappings().count());
    }

    @Test
    public void testLoadFunction() {
        String uri = "http://test.com/some-function4";
        OntModel m = new LoadMapTestData(uri, "data-x")
                .assembleMapping(Managers.createMapManager(), null, null)
                .asGraphModel();

        OWLMapManager manager = factory.create();
        manager.asMapModel(m);
        Assert.assertEquals(uri, manager.getFunction(uri).name());
        Assert.assertEquals(0, manager.ontologies().count());
        Assert.assertEquals(0, manager.mappings().count());
        manager.createMapModel();
        Assert.assertEquals(1, manager.mappings().count());
    }

    @Test
    public void testCreate() {
        OWLMapManager manager = factory.create();
        MapModel m1 = manager.createMapModel();
        TestUtils.debug(m1);
        Assert.assertEquals(1, manager.ontologies().count());
        Assert.assertEquals(1, manager.mappings().count());
        Assert.assertEquals(0, m1.rules().count());
        String iri = "http://x.com";
        m1.asGraphModel().setID(iri);
        Assert.assertEquals(1, manager.ontologies().count());
        Assert.assertEquals(1, manager.mappings().count());
        Ontology o1 = manager.getOntology(IRI.create(iri));
        Assert.assertNotNull(o1);
        TestUtils.debug(o1.asGraphModel());

        OntClass class1 = o1.asGraphModel().createOntClass(iri + "#Class1");
        OntClass class2 = o1.asGraphModel().createOntClass(iri + "#Class2");
        Assert.assertEquals(2, o1.axioms().count());
        manager.asMapModel(o1.asGraphModel()).createContext(class1, class2,
                // do not choose a function that produce additional content in the mapping itself (like avc:.*),
                // otherwise additional annotation assertion axioms are expected:
                manager.getFunction(SPINMAPL.composeURI).create().addLiteral(SPINMAPL.template, "x").build());
        TestUtils.debug(m1);
        Assert.assertEquals(1, m1.rules().count());
        MapModel m2 = manager.mappings().findFirst().orElseThrow(AssertionError::new);
        OWLOntology o2 = manager.ontologies().findFirst().orElseThrow(AssertionError::new);
        Assert.assertEquals(1, m2.rules().count());
        Assert.assertEquals(2, o2.axioms().peek(x -> LOGGER.debug("AXIOMS: {}", x)).count());
    }

    @Test
    public void testInference() {
        OWLMapManager manager = factory.create();
        PropertyChainMapTest test = new PropertyChainMapTest();
        Ontology src1 = manager.addOntology(test.assembleSource().getGraph());
        Ontology dst1 = manager.addOntology(test.assembleTarget().getGraph());
        MapModel m1 = test.assembleMapping(manager, src1.asGraphModel(), dst1.asGraphModel());

        Assert.assertEquals(3, manager.ontologies().count());
        Assert.assertEquals(1, manager.mappings().count());

        Assert.assertEquals(1, OntModels.importsClosure(src1.asGraphModel()).count());
        Assert.assertEquals(1, OntModels.importsClosure(dst1.asGraphModel()).count());

        OntModels.importsClosure(m1.asGraphModel()).forEach(x -> LOGGER.debug("{}", x));
        Assert.assertEquals(30, OntModels.importsClosure(m1.asGraphModel()).count());
        MapModel m2 = manager.mappings().findFirst().orElseThrow(AssertionError::new);
        String tree = Graphs.importsTreeAsString(m2.asGraphModel().getGraph());
        LOGGER.debug("Imports-tree: \n{}", tree);
        Assert.assertEquals(30, tree.split("\n").length);

        LOGGER.debug("Run");
        manager.getInferenceEngine(m2).run(src1.asGraphModel(), dst1.asGraphModel());
        LOGGER.debug("Validate");
        test.validate(dst1.asGraphModel());
        dst1.axioms().forEach(x -> LOGGER.debug("AXIOM: {}", x));
        // 6 property assertions
        Assert.assertEquals(6, dst1.axioms(AxiomType.DATA_PROPERTY_ASSERTION).count());
        // 3 named individual declarations
        Assert.assertEquals(3, dst1.individualsInSignature().count());

        // Add new individual and rerun inference
        LOGGER.debug("Add individual");
        Ontology src2 = manager.getOntology(src1.getOntologyID());
        Assert.assertNotNull(src2);
        PropertyChainMapTest.addDataIndividual(src2.asGraphModel(), "Tirpitz", new double[]{56.23, 34.2});

        LOGGER.debug("Re-Run");
        manager.getInferenceEngine(m1).run(src2.asGraphModel(), dst1.asGraphModel());
        LOGGER.debug("Validate");
        TestUtils.debug(dst1.asGraphModel());
        Ontology dst2 = manager.getOntology(dst1.getOntologyID());
        Assert.assertNotNull(dst2);
        dst2.axioms().forEach(x -> LOGGER.debug("AXIOM: {}", x));
        Assert.assertEquals(8, dst1.axioms(AxiomType.DATA_PROPERTY_ASSERTION).count());
        Assert.assertEquals(4, dst2.individualsInSignature().count());
    }

    @Test
    public void testClassPropertiesMap() throws Exception {
        Path path_sup = Paths.get(OWLAPITest.class.getResource("/ex-sup-test.ttl").toURI()).toRealPath();
        Path path_sub = Paths.get(OWLAPITest.class.getResource("/ex-sub-test.ttl").toURI()).toRealPath();

        OWLMapManager manager = factory.create();
        OntModel sup = manager.loadOntologyFromOntologyDocument(path_sup.toFile()).asGraphModel();
        OntModel sub = manager.loadOntologyFromOntologyDocument(path_sub.toFile()).asGraphModel();

        UnionGraph g_sup = (UnionGraph) sup.getGraph();
        UnionGraph g_sub = (UnionGraph) sub.getGraph();
        ClassPropertyMap map_sub = manager.getClassProperties(sub);
        ClassPropertyMap map_sup = manager.getClassProperties(sup);

        // ensure graphs have ClassProperty listener attached:
        Assert.assertEquals(2, g_sub.getEventManager().listeners().count());
        Assert.assertEquals(2, g_sup.getEventManager().listeners().count());

        OntClass CCPIU_000012 = sub.getOntClass("ttt://ex.com/sub/test#CCPIU_000012");
        Assert.assertNotNull(CCPIU_000012);
        OntClass CCPAS_000006 = TestUtils.findOntEntity(sub, OntClass.Named.class, "CCPAS_000006");
        OntDataProperty DAUUU = TestUtils.findOntEntity(sup, OntDataProperty.class, "DAUUU");

        Assert.assertEquals(19, map_sub.properties(CCPIU_000012)
                .peek(p -> LOGGER.debug("1::{}-property {}", CCPIU_000012.getLocalName(), p.getURI())).count());
        Assert.assertEquals(11, map_sup.properties(CCPAS_000006)
                .peek(p -> LOGGER.debug("1::{}-property = {}", CCPIU_000012.getLocalName(), p.getURI())).count());

        sup.removeOntObject(DAUUU);

        Assert.assertEquals(18, map_sub.properties(CCPIU_000012)
                .peek(p -> LOGGER.debug("2::{}-property = {}", CCPIU_000012.getLocalName(), p.getURI())).count());
        Assert.assertEquals(10, map_sup.properties(CCPAS_000006)
                .peek(p -> LOGGER.debug("2::{}-property = {}", CCPIU_000012.getLocalName(), p.getURI())).count());

        String ns = sup.getID().getURI() + "#";
        OntClass c = sup.createOntClass(ns + "OneMoreClass");
        sup.createObjectProperty(ns + "OneMoreProperty1").addDomain(c);
        sub.createObjectProperty(sub.getID().getURI() + "#OneMorePropery2").addDomain(CCPIU_000012);
        CCPIU_000012.addSuperClass(c);

        TestUtils.debug(sub);
        Assert.assertEquals(20, map_sub.properties(CCPIU_000012)
                .peek(p -> LOGGER.debug("3::{}-property = {}", CCPIU_000012.getLocalName(), p.getURI())).count());
        Assert.assertEquals(10, map_sup.properties(CCPAS_000006)
                .peek(p -> LOGGER.debug("3::{}-property = {}", CCPIU_000012.getLocalName(), p.getURI())).count());

    }

    enum ManagerProvider {
        COMMON {
            @Override
            public OWLMapManager create() {
                return Managers.createOWLMapManager();
            }
        },
        CONCURRENT {
            @Override
            public OWLMapManager create() {
                return Managers.createOWLMapManager(Factory.createGraphMem(), new ReentrantReadWriteLock());
            }
        },
        ;

        public abstract OWLMapManager create();
    }
}

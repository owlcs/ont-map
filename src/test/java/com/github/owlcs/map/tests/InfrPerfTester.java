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
import com.github.owlcs.map.spin.MapConfigImpl;
import com.github.owlcs.map.spin.vocabulary.SPINMAPL;
import com.github.owlcs.map.utils.TestUtils;
import com.github.owlcs.ontapi.OntGraphDocumentSource;
import com.github.owlcs.ontapi.OntManagers;
import com.github.owlcs.ontapi.Ontology;
import com.github.owlcs.ontapi.OntologyManager;
import com.github.owlcs.ontapi.jena.OntModelFactory;
import com.github.owlcs.ontapi.jena.model.OntClass;
import com.github.owlcs.ontapi.jena.model.OntDT;
import com.github.owlcs.ontapi.jena.model.OntGraphModel;
import com.github.owlcs.ontapi.jena.model.OntNDP;
import com.github.owlcs.ontapi.jena.vocabulary.OWL;
import com.github.owlcs.ontapi.jena.vocabulary.XSD;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.compose.Union;
import org.apache.jena.mem.GraphMem;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.apache.log4j.Level;
import org.junit.*;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A tester (not a test) for checking inference performance,
 * for investigation and finding an optimal way.
 * It demonstrates that query and function optimizations speed up inference up to <b>2.5</b> times.
 * <p>
 * Created by @szz on 13.11.2018.
 */
@SuppressWarnings("WeakerAccess")
@Ignore // not a test - ignore
public class InfrPerfTester {
    private static final Logger LOGGER = LoggerFactory.getLogger(InfrPerfTester.class);

    private final int individualsNum;
    private static Level log4jLevel;

    public InfrPerfTester() {
        this(100_000);
    }

    protected InfrPerfTester(int individualsNum) {
        this.individualsNum = individualsNum;
    }

    @BeforeClass
    public static void before() {
        log4jLevel = org.apache.log4j.Logger.getRootLogger().getLevel();
        org.apache.log4j.Logger.getRootLogger().setLevel(Level.INFO);
    }

    @AfterClass
    public static void after() {
        org.apache.log4j.Logger.getRootLogger().setLevel(log4jLevel);
    }

    @Before
    public void beforeTest() {
        LOGGER.info("Start.");
    }

    @After
    public void afterTest() {
        LOGGER.info("Fin.");
    }

    @Test
    public void testInference() {
        OWLMapManager m = Managers.createOWLMapManager();
        testInference(m, m);
    }

    @Test
    public void testInferenceNoOptimization() {
        OntologyManager m1 = OntManagers.createONT();
        MapManager m2 = TestUtils.withConfig(MapConfigImpl.INSTANCE.setAllOptimizations(false));
        testInference(m1, m2);
    }

    public void testInference(OntologyManager ontologyManager, MapManager mappingManager) {
        OntGraphModel target = createTargetModel(ontologyManager);
        OntGraphModel source = createSourceModel(ontologyManager, individualsNum);
        MapModel map = composeMapping(mappingManager, source, target);
        Graph data = ((Union) source.getBaseGraph()).getR();

        map.runInference(data, target.getBaseGraph());
        validate(target, individualsNum);
    }

    public static void validate(OntGraphModel target, long c) {
        Assert.assertEquals(c, target.individuals()
                .peek(i -> Assert.assertEquals(1, i.positiveAssertions()
                        .map(Statement::getObject)
                        .peek(x -> {
                            if (LOGGER.isDebugEnabled()) {
                                LOGGER.debug("{} => {}", i, x);
                            }
                            Long ind = Long.parseLong(i.getLocalName().replaceFirst("[^\\d]+", ""));
                            String actual = x.asLiteral().getString();
                            String expected = String.format("val-2-%d, val-1-%d", ind, ind);
                            Assert.assertEquals(expected, actual);
                        })
                        .count()))
                .count());
    }


    public static MapModel composeMapping(MapManager manager, OntGraphModel source, OntGraphModel target) {
        LOGGER.debug("Compose the (spin) mapping.");
        OntClass sourceClass = source.classes().findFirst().orElseThrow(AssertionError::new);
        OntClass targetClass = target.classes().findFirst().orElseThrow(AssertionError::new);
        List<OntNDP> sourceProperties = source.dataProperties().collect(Collectors.toList());
        OntNDP targetProperty = target.dataProperties().findFirst().orElse(null);
        MapModel res = manager.createMapModel();

        MapFunction.Builder self = manager.getFunction(SPINMAPL.self).create();
        MapFunction.Builder concat = manager.getFunction(SPINMAPL.concatWithSeparator).create();

        res.createContext(sourceClass, targetClass, self.build())
                .addPropertyBridge(concat
                        .addProperty(SP.arg1, sourceProperties.get(0))
                        .addProperty(SP.arg2, sourceProperties.get(1))
                        .addLiteral(SPINMAPL.separator, ", "), targetProperty);
        return res;
    }

    public static OntGraphModel createTargetModel(OntologyManager manager) {
        LOGGER.debug("Create the target model.");
        String uri = "http://target.avicomp.ru";
        String ns = uri + "#";
        OntGraphModel res = manager.createGraphModel(uri).setNsPrefixes(OntModelFactory.STANDARD);
        OntClass clazz = res.createOntEntity(OntClass.class, ns + "ClassTarget");
        OntNDP prop = res.createOntEntity(OntNDP.class, ns + "targetProperty");
        prop.addRange(res.getOntEntity(OntDT.class, XSD.xstring));
        prop.addDomain(clazz);
        Ontology o = manager.getOntology(IRI.create(uri));
        Assert.assertNotNull("Can't find ontology " + uri, o);
        o.axioms().forEach(x -> LOGGER.debug("{}", x));
        return res;
    }

    public static OntGraphModel createSourceModel(OntologyManager manager, long num) {
        LOGGER.debug("Create the source model with {} individuals", num);
        String uri = "http://source.avicomp.ru";
        String ns = uri + "#";
        GraphMem schema = new GraphMem();
        GraphMem data = new GraphMem();
        OntGraphDocumentSource source = OntGraphDocumentSource.wrap(new Union(schema, data));
        long numberOfOntologies = manager.ontologies().count();
        OntGraphModel res;
        try {
            res = manager.loadOntologyFromOntologyDocument(source).asGraphModel();
        } catch (OWLOntologyCreationException e) {
            throw new AssertionError(e);
        }
        res.setID(uri).getModel().setNsPrefixes(OntModelFactory.STANDARD);
        OntClass clazz = res.createOntEntity(OntClass.class, ns + "ClassSource");
        OntDT xsdString = res.getOntEntity(OntDT.class, XSD.xstring);
        OntNDP prop1 = res.createOntEntity(OntNDP.class, ns + "sourceProperty1").addRange(xsdString).addDomain(clazz);
        OntNDP prop2 = res.createOntEntity(OntNDP.class, ns + "sourceProperty2").addRange(xsdString).addDomain(clazz);

        Model m = ModelFactory.createModelForGraph(data);
        for (long i = 1; i < num + 1; i++) {
            m.createResource(ns + "Individual-" + i, OWL.NamedIndividual)
                    .addProperty(RDF.type, clazz)
                    .addProperty(prop1, "val-1-" + i)
                    .addProperty(prop2, "val-2-" + i);
        }
        Assert.assertEquals(8, schema.size());
        Assert.assertEquals(num, res.namedIndividuals().count());
        Assert.assertEquals(numberOfOntologies + 1, manager.ontologies().count());
        Assert.assertTrue(manager.contains(IRI.create(uri)));
        return res;
    }


}

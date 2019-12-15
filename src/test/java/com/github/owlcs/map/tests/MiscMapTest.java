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
import com.github.owlcs.map.spin.vocabulary.AVC;
import com.github.owlcs.map.spin.vocabulary.SPIF;
import com.github.owlcs.map.spin.vocabulary.SPINMAPL;
import com.github.owlcs.map.tests.maps.ConditionalMapTest;
import com.github.owlcs.map.tests.maps.NestedFuncMapTest;
import com.github.owlcs.map.tests.maps.SplitMapTest;
import com.github.owlcs.map.tests.maps.UUIDMapTest;
import com.github.owlcs.map.utils.TestUtils;
import com.github.owlcs.ontapi.OntFormat;
import com.github.owlcs.ontapi.jena.OntModelFactory;
import com.github.owlcs.ontapi.jena.model.*;
import com.github.owlcs.ontapi.jena.vocabulary.XSD;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.shared.PrefixMapping;
import org.junit.Assert;
import org.junit.Test;
import org.semanticweb.owlapi.io.FileDocumentSource;
import org.semanticweb.owlapi.model.OWLDocumentFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPINMAP;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by @ssz on 01.12.2018.
 */
public class MiscMapTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(MiscMapTest.class);

    @Test
    public void testInferenceOnSimpleMappingWithUUIDAndSubClasses() {
        // Assembly source:
        OntModel s = OntModelFactory.createModel();
        s.setNsPrefixes(OntModelFactory.STANDARD);
        s.setID("source");
        OntClass A = s.createOntClass("A");
        OntClass B = s.createOntClass("B").addSuperClass(A);
        OntDataProperty p1 = s.createOntEntity(OntDataProperty.class, "d1").addDomain(A);
        OntDataProperty p2 = s.createOntEntity(OntDataProperty.class, "d2").addDomain(B);
        B.createIndividual("I").addProperty(p1, "v1").addProperty(p2, "v2");
        TestUtils.debug(s);

        // Assembly target:
        OntModel t = OntModelFactory.createModel();
        t.setNsPrefixes(OntModelFactory.STANDARD);
        t.setID("target");
        OntClass C = t.createOntClass("C");
        OntDataProperty p = t.createOntEntity(OntDataProperty.class, "p");
        p.addDomain(C);
        TestUtils.debug(t);

        // Assembly mapping:
        MapManager manager = Managers.createMapManager();
        MapFunction uuid = manager.getFunction(AVC.UUID);
        MapFunction concat = manager.getFunction(SPINMAPL.concatWithSeparator);
        MapModel map = manager.createMapModel();
        MapContext c = map.createContext(B, C, uuid.create().build());
        c.addPropertyBridge(concat.create()
                .addProperty(SP.arg1, p1)
                .addProperty(SP.arg2, p2)
                .addLiteral(SPINMAPL.separator, ", ")
                .build(), p);
        TestUtils.debug(map);

        // Run inference:
        manager.getInferenceEngine(map).run(s, t);

        // Check results:
        TestUtils.debug(t);
        Assert.assertEquals(1, t.individuals()
                .peek(i -> LOGGER.debug("New target individual: {}", i)).count());
    }

    @Test
    public void testInferenceOnNestedFuncMappingWithAlternativeTargetFunction() {
        NestedFuncMapTest t = new NestedFuncMapTest();
        OntModel src = t.assembleSource();
        TestUtils.debug(src);

        OntModel dst = t.assembleTarget();
        TestUtils.debug(dst);

        MapManager manager = t.manager();
        PrefixMapping pm = manager.prefixes();
        OntClass dstClass = TestUtils.findOntEntity(dst, OntClass.Named.class, "TargetClass1");
        MapModel mapping = t.createMapping(manager, src, dst, () ->
                manager.getFunction(AVC.IRI).create()
                        .addFunction(SP.arg1, manager.getFunction(SPINMAPL.concatWithSeparator).create()
                                .addLiteral(SPINMAPL.separator, "")
                                .addFunction(SP.arg1, manager.getFunction(pm.expandPrefix("afn:namespace")).create()
                                        .addClass(SP.arg1, dstClass).build())
                                .addFunction(SP.arg2, manager.getFunction(pm.expandPrefix("afn:localname")).create()
                                        .addFunction(SP.arg1, manager.getFunction(AVC.currentIndividual).create())))
                        .build());
        TestUtils.debug(mapping);

        LOGGER.info("Run inference.");
        manager.getInferenceEngine(mapping).run(src, dst);
        TestUtils.debug(dst);

        LOGGER.info("Validate.");
        t.validateAfterInference(src, dst);
    }

    @Test
    public void testInferenceOnMappingWithVarargBuildStringFunction() {
        OntModel src = new SplitMapTest().assembleSource();
        OntModel dst = new ConditionalMapTest().assembleTarget();
        TestUtils.debug(src);
        TestUtils.debug(dst);
        Assert.assertEquals(0, dst.individuals().count());

        OntClass srcPerson = TestUtils.findOntEntity(src, OntClass.Named.class, "Person");
        OntDataProperty firstName = TestUtils.findOntEntity(src, OntDataProperty.class, "first-name");
        OntDataProperty secondName = TestUtils.findOntEntity(src, OntDataProperty.class, "second-name");
        OntDataProperty middleName = TestUtils.findOntEntity(src, OntDataProperty.class, "middle-name");
        OntClass dstClass = TestUtils.findOntEntity(dst, OntClass.Named.class, "User");
        OntDataProperty dstName = TestUtils.findOntEntity(dst, OntDataProperty.class, "user-name");

        String ns = "http://ex.com#";
        MapManager m = Managers.createMapManager();
        MapModel map = m.createMapModel();
        map.createContext(srcPerson, dstClass).addClassBridge(m.getFunction(SPINMAPL.buildURI3).create()
                .addProperty(SP.arg1, firstName)
                .addProperty(SP.arg2, middleName)
                .addProperty(SP.arg3, secondName)
                .addLiteral(SPINMAPL.template, ns + "{?1}-{?2}-{?3}").build())
                .addPropertyBridge(m.getFunction(SPIF.buildString).create()
                        .addProperty(AVC.vararg, firstName)
                        .addProperty(AVC.vararg, middleName)
                        .addProperty(AVC.vararg, secondName)
                        .addLiteral(SP.arg1, "{?3}, {?1} {?2}").build(), dstName);
        TestUtils.debug(map);
        map.runInference(src.getBaseGraph(), dst.getBaseGraph());
        TestUtils.debug(dst);
        Assert.assertEquals(TestUtils.getMappingConfiguration(m).generateNamedIndividuals() ? 2 : 0,
                dst.namedIndividuals().count());
        Assert.assertEquals(2, dst.individuals().peek(i -> {
            Assert.assertEquals(ns, i.getNameSpace());
            String[] names = i.getLocalName().split("-");
            Assert.assertEquals(3, names.length);
            String n = i.getRequiredProperty(dstName).getString();
            Assert.assertEquals(names[2] + ", " + names[0] + " " + names[1], n);
        }).count());
    }

    @Test
    public void testInferenceWhenTargetPropertyIsArgument() {
        OntModel src = OntModelFactory.createModel().setNsPrefixes(OntModelFactory.STANDARD);
        src.setID("http://src");

        OntClass c1 = src.createOntClass("C1");
        c1.createIndividual("I1");
        src.createOntEntity(OntDataProperty.class, "P1");

        OntModel dst = OntModelFactory.createModel().setNsPrefixes(OntModelFactory.STANDARD);
        dst.setID("http://dst");
        OntClass c2 = dst.createOntClass("C2");
        OntAnnotationProperty p2 = dst.createAnnotationProperty("P2");
        p2.addDomain(c2);
        p2.addRange(src.getDatatype(XSD.xstring));
        OntAnnotationProperty p1 = dst.createAnnotationProperty("P1");
        p1.addDomain(c2);
        p1.addRange(src.getDatatype(XSD.xstring));


        MapManager man = Managers.createMapManager();
        MapFunction.Call uuid = man.getFunction(AVC.UUID).create().build();
        MapFunction eq = man.getFunction(SPINMAP.equals);

        MapModel map = man.createMapModel();
        map.createContext(c1, c2, uuid)
                .addPropertyBridge(eq.create()
                        .add(SP.arg1.getURI(), p1.getURI()).build(), p2);

        man.getInferenceEngine(map).run(src, dst);
        TestUtils.debug(dst);

        Set<OntIndividual> individuals = c2.individuals().collect(Collectors.toSet());
        Assert.assertEquals(1, individuals.size());
        Set<Statement> assertions = individuals.iterator().next().listProperties(p2).toSet();
        Assert.assertEquals(1, assertions.size());
        Assert.assertEquals(p1, assertions.iterator().next().getResource());
    }

    @Test
    public void testReloadToOWLManager() throws Exception {
        UUIDMapTest d = new UUIDMapTest();
        MapModel map = d.assembleMapping();
        List<Path> imports = new ArrayList<>();
        for (OntModel m : map.ontologies().collect(Collectors.toList())) {
            imports.add(saveAsTempTurtle(null, m));
        }
        Path mapSrc = saveAsTempTurtle("mapping", map.asGraphModel());

        LOGGER.debug("Reload to OWL Manager");
        OWLMapManager manager = Managers.createOWLMapManager();
        OWLDocumentFormat ttl = OntFormat.TURTLE.createOwlFormat();
        for (Path p : imports) {
            manager.loadOntologyFromOntologyDocument(new FileDocumentSource(p.toFile(), ttl));
        }
        OntModel model = manager.loadOntologyFromOntologyDocument(new FileDocumentSource(mapSrc.toFile(), ttl))
                .asGraphModel();
        TestUtils.debug(model);
        Assert.assertEquals(map.asGraphModel().size(), model.size());
    }

    private static Path saveAsTempTurtle(String prefix, Model m) throws IOException {
        LOGGER.debug("Save model {}", prefix);
        Path res = Files.createTempFile(prefix, "ttl");
        m.write(Files.newBufferedWriter(res, StandardOpenOption.WRITE), "ttl");
        return res;
    }

    @Test
    public void testInferenceWhenSourceAndTargetGraphsMatch() {
        OntModel m = OntModelFactory.createModel().setNsPrefixes(OntModelFactory.STANDARD);
        m.setID("http://src");
        OntClass c1 = m.createOntClass("C1");
        c1.createIndividual("I1");
        OntClass c2 = m.createOntClass("C2");

        MapManager man = Managers.createMapManager();
        MapFunction.Call uuid = man.getFunction(AVC.UUID).create().build();
        MapModel map = man.createMapModel();
        map.createContext(c1, c2, uuid);
        man.getInferenceEngine(map).run(m, m);
        Assert.assertEquals(2, map.asGraphModel().individuals().peek(x -> LOGGER.debug("Res:{}", x)).count());
    }

}

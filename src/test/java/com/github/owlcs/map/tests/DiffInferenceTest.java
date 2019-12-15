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

import com.github.owlcs.map.Managers;
import com.github.owlcs.map.MapFunction;
import com.github.owlcs.map.MapManager;
import com.github.owlcs.map.MapModel;
import com.github.owlcs.map.spin.vocabulary.AVC;
import com.github.owlcs.map.spin.vocabulary.SPINMAPL;
import com.github.owlcs.map.utils.TestUtils;
import com.github.owlcs.ontapi.jena.OntModelFactory;
import com.github.owlcs.ontapi.jena.model.OntClass;
import com.github.owlcs.ontapi.jena.model.OntDataProperty;
import com.github.owlcs.ontapi.jena.model.OntDataRange;
import com.github.owlcs.ontapi.jena.model.OntModel;
import com.github.owlcs.ontapi.jena.vocabulary.XSD;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.graph.compose.Union;
import org.apache.jena.graph.impl.WrappedGraph;
import org.apache.jena.mem.GraphMem;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;

import java.util.List;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/**
 * To test different ways inference.
 * Created by @ssz on 29.10.2018.
 */
@SuppressWarnings("WeakerAccess")
public class DiffInferenceTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(DiffInferenceTest.class);
    private static int numberNamedIndividuals = 20;
    private static int numberAnonymousIndividuals = 15;

    @Test
    public void testInferenceRawData() {
        Graph schema = assembleSrcSchema();
        Graph data = assembleSrcData(schema);
        OntModel target = assembleTargetModel();

        MapManager manager = Managers.createMapManager();
        MapModel mapping = assembleMapping(manager, schema, target.getGraph());
        TestUtils.debug(mapping);
        LoggedGraph logGraph = new LoggedGraph(data);
        mapping.runInference(logGraph, target.getGraph());
        LOGGER.debug("Inference is done. Find count : {}", logGraph.counter.longValue());

        validate(target);

        Graph t = new GraphMem();
        mapping.runInference(data, t);
        TestUtils.debug(ModelFactory.createModelForGraph(t));
        int factor = TestUtils.getMappingConfiguration(manager).generateNamedIndividuals() ? 3 : 2;
        long expected = (numberNamedIndividuals + numberAnonymousIndividuals) * factor;
        Assert.assertEquals(expected, t.size());
    }

    @Test
    public void testInferenceWithSchema() {
        Graph schema = assembleSrcSchema();
        Graph data = assembleSrcData(schema);
        OntModel target = assembleTargetModel();

        OntModel withData = OntModelFactory.createModel(schema);
        withData.add(ModelFactory.createModelForGraph(data));
        LoggedGraph logGraph = new LoggedGraph(withData.getGraph());

        MapManager manager = Managers.createMapManager();
        MapModel mapping = assembleMapping(manager, logGraph, target.getGraph());
        TestUtils.debug(mapping);

        long before = logGraph.counter.longValue();
        LOGGER.debug("Count before : {}", before);
        mapping.runInference(logGraph, target.getGraph());
        LOGGER.debug("Inference is done. Find count : {}", logGraph.counter.longValue() - before);

        validate(target);
    }

    public void validate(OntModel t) {
        TestUtils.debug(t);
        int expected = numberAnonymousIndividuals + numberNamedIndividuals;
        Assert.assertEquals("Incorrect number of result individuals.", expected,
                t.individuals()
                        .peek(x -> LOGGER.debug("{}", x))
                        .count());
        OntDataProperty prop = TestUtils.findOntEntity(t, OntDataProperty.class, "targetProperty");
        Assert.assertEquals(expected, t.statements(null, prop, null)
                .peek(x -> LOGGER.debug("{}", x))
                .count());
    }

    public static Graph assembleSrcSchema() {
        String uri = "http://xxx";
        String ns = uri + "#";
        OntModel m = OntModelFactory.createModel().setNsPrefixes(OntModelFactory.STANDARD).setNsPrefix("x", ns);
        m.setID(uri);
        OntClass c = m.createOntClass(ns + "Class");
        OntDataProperty p1 = m.createOntEntity(OntDataProperty.class, ns + "Property-1");
        OntDataRange xs = m.getDatatype(XSD.xstring);
        p1.addDomain(c);
        p1.addRange(xs);
        OntDataProperty p2 = m.createOntEntity(OntDataProperty.class, ns + "Property-2");
        p2.addDomain(c);
        p2.addRange(xs);
        Assert.assertEquals(8, m.size());
        return m.getBaseGraph();
    }

    public static Graph assembleSrcData(Graph schema) {
        Union u = new Union(new GraphMem(), schema);
        OntModel m = OntModelFactory.createModel(u);
        String ns = m.getNsPrefixURI("x");
        OntClass c = TestUtils.findOntEntity(m, OntClass.Named.class, "Class");
        OntDataProperty p1 = TestUtils.findOntEntity(m, OntDataProperty.class, "Property-1");
        OntDataProperty p2 = TestUtils.findOntEntity(m, OntDataProperty.class, "Property-2");
        for (int i = 1; i <= numberNamedIndividuals; i++) {
            c.createIndividual(ns + "i" + i).addProperty(p1, "forNamed(1)#" + i).addProperty(p2, "forNamed(2)#" + i);
        }
        for (int i = 1; i <= numberAnonymousIndividuals; i++) {
            c.createIndividual().addProperty(p1, "forAnon(1)#" + i).addProperty(p2, "forAnon(2)#" + i);
        }
        Graph res = u.getL();
        Assert.assertEquals(numberNamedIndividuals * 4 + numberAnonymousIndividuals * 3, res.size());
        return res;
    }

    public static OntModel assembleTargetModel() {
        LOGGER.debug("Create the target model.");
        String uri = "http://target.avicomp.ru";
        String ns = uri + "#";
        OntModel res = OntModelFactory.createModel().setNsPrefixes(OntModelFactory.STANDARD).setID(uri).getModel();
        OntClass clazz = res.createOntClass(ns + "ClassTarget");
        OntDataProperty prop = res.createOntEntity(OntDataProperty.class, ns + "targetProperty");
        prop.addRange(res.getDatatype(XSD.xstring));
        prop.addDomain(clazz);
        return res;
    }

    public static MapModel assembleMapping(MapManager manager, Graph source, Graph target) {
        OntModel src = OntModelFactory.createModel(source);
        OntModel dst = OntModelFactory.createModel(target);
        LOGGER.debug("Compose the (spin) mapping.");
        OntClass sourceClass = TestUtils.findOntEntity(src, OntClass.Named.class, "Class");
        OntClass targetClass = TestUtils.findOntEntity(dst, OntClass.Named.class, "ClassTarget");
        List<OntDataProperty> sourceProperties = src.dataProperties().collect(Collectors.toList());
        OntDataProperty targetProperty = dst.dataProperties().findFirst().orElse(null);
        MapModel res = manager.createMapModel();

        MapFunction.Builder self = manager.getFunction(AVC.UUID).create();//manager.getFunction(AVC.UUID).create();
        MapFunction.Builder concat = manager.getFunction(SPINMAPL.concatWithSeparator).create();

        res.createContext(sourceClass, targetClass, self.build())
                .addPropertyBridge(concat
                        .addProperty(SP.arg1, sourceProperties.get(0))
                        .addProperty(SP.arg2, sourceProperties.get(1))
                        .addLiteral(SPINMAPL.separator, "-"), targetProperty);
        return res;
    }

    private static class LoggedGraph extends WrappedGraph {
        private static final Logger LOGGER = LoggerFactory.getLogger(LoggedGraph.class);
        LongAdder counter = new LongAdder();

        public LoggedGraph(Graph base) {
            super(base);
        }

        @Override
        public ExtendedIterator<Triple> find(Triple m) {
            debug(m);
            return super.find(m);
        }

        @Override
        public ExtendedIterator<Triple> find(Node s, Node p, Node o) {
            debug(Triple.create(s, p, o));
            return super.find(s, p, o);
        }

        void debug(Triple m) {
            LOGGER.debug("Triple pattern: {}", m);
            counter.increment();
        }

    }
}

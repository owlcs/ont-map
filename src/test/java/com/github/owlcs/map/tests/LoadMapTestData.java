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
import com.github.owlcs.map.spin.QueryHelper;
import com.github.owlcs.map.spin.vocabulary.SPINMAPL;
import com.github.owlcs.map.tests.maps.AbstractMapTest;
import com.github.owlcs.map.utils.TestUtils;
import com.github.owlcs.ontapi.jena.OntModelFactory;
import com.github.owlcs.ontapi.jena.model.OntClass;
import com.github.owlcs.ontapi.jena.model.OntModel;
import com.github.owlcs.ontapi.jena.model.OntStatement;
import com.github.owlcs.ontapi.jena.vocabulary.RDF;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import org.topbraid.spin.vocabulary.SPL;

/**
 * Created by @szuev on 19.06.2018.
 */
public class LoadMapTestData extends AbstractMapTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadMapTestData.class);

    private final String val;
    private final String individualIRI, classIRI, contextIRI, ontologyIRI, functionIRI;
    private final String individualLabel;

    public LoadMapTestData() {
        this("http://ex.com/func-xxx", "--suffix");
    }

    LoadMapTestData(String uri, String val) {
        this.val = val;
        this.individualIRI = uri + "#Individ-X";
        this.classIRI = uri + "#Class-X";
        this.contextIRI = uri + "#Context-X";
        this.ontologyIRI = uri;
        this.functionIRI = uri;
        this.individualLabel = "The label";
    }

    void validateMapping(MapModel m) {
        Assert.assertEquals(2, m.rules().count());
        MapContext c = m.contexts().findFirst().orElseThrow(AssertionError::new);
        Assert.assertEquals(c.getSource(), c.getTarget());
        Assert.assertEquals(classIRI, c.getSource().getURI());
        Assert.assertEquals(contextIRI, c.name());
        Assert.assertEquals(ontologyIRI, m.name());
        MapFunction target = c.functions().findFirst().orElseThrow(AssertionError::new);
        Assert.assertEquals(SPINMAPL.self.getURI(), target.name());
        PropertyBridge p = c.properties().findFirst().orElseThrow(AssertionError::new);
        Assert.assertEquals(RDFS.label, p.getTarget());
        Assert.assertEquals(RDFS.label, p.sources().findFirst().orElseThrow(AssertionError::new));
        Assert.assertTrue(m.asGraphModel().contains(m.asGraphModel().getResource(individualIRI), RDFS.label, individualLabel));
        Assert.assertEquals(functionIRI, p.getMapping().getFunction().name());
    }

    void validateResult(OntModel res) {
        res.getGraph().find(Triple.ANY).forEachRemaining(t -> LOGGER.debug("TRIPLE: {}", t));
        Assert.assertEquals(1, res.size());
        OntStatement s = res.statements().findFirst().orElseThrow(AssertionError::new);
        Assert.assertEquals(individualIRI, s.getSubject().getURI());
        Assert.assertEquals(RDFS.label, s.getPredicate());
        Assert.assertEquals(individualLabel + val, s.getObject().asLiteral().getString());
    }

    @Override
    public MapModel assembleMapping(MapManager manager, OntModel src, OntModel dst) {
        String q = "SELECT (CONCAT(?arg1, \"" + val + "\"))\n" +
                "WHERE {\n" +
                "}";
        OntModel m = TestUtils.createMapModel(ontologyIRI);
        Resource function = m.createResource(functionIRI)
                .addProperty(RDF.type, SPIN.Function)
                .addProperty(SPIN.returnType, XSD.xstring)
                .addProperty(RDFS.subClassOf, SPIN.Functions)
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg1)
                        .addProperty(SPL.valueType, XSD.xstring))
                .addProperty(SPIN.body, QueryHelper.parseQuery(q, m));

        OntClass clazz = m.createOntClass(classIRI);
        Resource context = m.createResource(contextIRI, SPINMAP.Context)
                .addProperty(SPINMAP.sourceClass, clazz)
                .addProperty(SPINMAP.targetClass, clazz)
                .addProperty(SPINMAP.target, m.createResource()
                        .addProperty(RDF.type, SPINMAPL.self)
                        .addProperty(SPINMAP.source, SPINMAP.sourceVariable));
        Resource rule = m.createResource().addProperty(RDF.type, SPINMAP.Mapping_1_1)
                .addProperty(SPINMAP.context, context)
                .addProperty(SPINMAP.sourcePredicate1, RDFS.label)
                .addProperty(SPINMAP.targetPredicate1, RDFS.label)
                .addProperty(SPINMAP.expression, m.createResource()
                        .addProperty(RDF.type, function)
                        .addProperty(SP.arg1, SPIN._arg1));

        clazz.addProperty(SPINMAP.rule, rule);
        clazz.createIndividual(individualIRI).addLabel(individualLabel, null);
        return manager.asMapModel(m);
    }

    @Override
    public OntModel assembleSource() {
        return OntModelFactory.createModel();
    }

    @Override
    public OntModel assembleTarget() {
        return OntModelFactory.createModel();
    }
}

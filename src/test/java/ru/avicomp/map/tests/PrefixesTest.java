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

import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDFS;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.Managers;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;
import ru.avicomp.map.utils.AutoPrefixListener;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;
import ru.avicomp.ontapi.jena.vocabulary.XSD;

/**
 * Created by @szuev on 13.04.2018.
 */
public class PrefixesTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(PrefixesTest.class);

    @Test
    public void testPrefixes() {
        OntGraphModel m = OntModelFactory.createModel();
        AutoPrefixListener.addAutoPrefixListener((UnionGraph) m.getGraph(), Managers.createMapManager().prefixes());
        m.getID().addImport(SPINMAPL.BASE_URI);
        LOGGER.debug("\n{}", TestUtils.asString(m));
        // rdf:type, owl:imports, owl:Ontology - 2 prefixes:
        Assert.assertEquals(2, m.numPrefixes());

        LOGGER.debug("----------------");
        Resource r = m.createResource()
                .addProperty(RDF.type, SPINMAP.Context)
                .addProperty(RDFS.seeAlso, m.createResource("http://info.com"));
        LOGGER.debug("\n{}", TestUtils.asString(m));
        // 3 new prefixes: rdf, rdfs. spinmap
        Assert.assertEquals(4, m.numPrefixes());

        LOGGER.debug("----------------");
        String ns = "http://ex.com#";
        String uri = ns + "Cl";
        r.removeAll(RDFS.seeAlso);
        m.createResource().addProperty(RDF.type, SP.Clear);
        m.createResource().addProperty(RDF.type, m.createResource(uri));
        LOGGER.debug("\n{}", TestUtils.asString(m));
        Assert.assertEquals(5, m.numPrefixes());
        Assert.assertNull(m.getNsURIPrefix(RDFS.uri));
        Assert.assertNotNull(m.getNsURIPrefix(OWL.NS));
        Assert.assertNotNull(m.getNsURIPrefix(SPINMAP.NS));
        Assert.assertNotNull(m.getNsURIPrefix(SP.NS));
        Assert.assertNotNull(m.getNsURIPrefix(RDF.uri));
        Assert.assertNotNull(m.getNsURIPrefix(ns));

        LOGGER.debug("----------------");
        r.addProperty(RDFS.comment, "xxx");
        LOGGER.debug("\n{}", TestUtils.asString(m));
        Assert.assertEquals(7, m.numPrefixes());
        Assert.assertNotNull(m.getNsURIPrefix(RDFS.uri));
        Assert.assertNotNull(m.getNsURIPrefix(XSD.NS));

        LOGGER.debug("----------------");
        m.removeAll(r, null, null);
        LOGGER.debug("\n{}", TestUtils.asString(m));
        Assert.assertEquals(4, m.numPrefixes());
    }
}

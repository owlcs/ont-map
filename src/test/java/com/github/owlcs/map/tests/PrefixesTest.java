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
import com.github.owlcs.map.spin.vocabulary.SPINMAPL;
import com.github.owlcs.map.utils.AutoPrefixListener;
import com.github.owlcs.map.utils.TestUtils;
import com.github.owlcs.ontapi.jena.OntModelFactory;
import com.github.owlcs.ontapi.jena.UnionGraph;
import com.github.owlcs.ontapi.jena.model.OntClass;
import com.github.owlcs.ontapi.jena.model.OntGraphModel;
import com.github.owlcs.ontapi.jena.vocabulary.OWL;
import com.github.owlcs.ontapi.jena.vocabulary.RDF;
import com.github.owlcs.ontapi.jena.vocabulary.XSD;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.RDFS;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPINMAP;

import java.util.Collection;

/**
 * Created by @szuev on 13.04.2018.
 */
public class PrefixesTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(PrefixesTest.class);

    @Test
    public void testMiscAutoPrefixFunctionality() {
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

    @Test
    public void testCustomLibraryAndRename() {
        String a_ns = "http://a#";
        String b_ns = "http://b#";
        String x_ns = "http://x#";
        String y_ns = "http://y#";
        String z_ns = "http://zzz#";
        String w_ns = "http://www-jkjjrke.com#";
        String xsd_ns = XSD.getURI();
        String owl_ns = OWL.getURI();
        String rdf_ns = RDF.getURI();
        String rdfs_ns = RDFS.getURI();

        String a_p = "a";
        String b_p = "b";
        String x_p = "x";
        String y_p = "y";
        String xsd_p = "xsd-test";
        String owl_p = "owl-test";
        String rdf_p = "rdf-test";
        String rdfs_p = "rdfs-test";

        PrefixMapping lib = PrefixMapping.Factory.create()
                .setNsPrefix(a_p, a_ns)
                .setNsPrefix(b_p, b_ns)
                .setNsPrefix(xsd_p, xsd_ns)
                .setNsPrefix(rdf_p, rdf_ns)
                .setNsPrefix(owl_p, owl_ns)
                .setNsPrefix(rdfs_p, rdfs_ns)
                .lock();
        OntGraphModel m = OntModelFactory.createModel()
                .setNsPrefix(x_p, x_ns)
                .setNsPrefix(y_p, y_ns);
        AutoPrefixListener.addAutoPrefixListener((UnionGraph) m.getGraph(), lib);
        Assert.assertEquals(2, m.numPrefixes());
        m.createResource(a_ns + "A-res")
                .addProperty(m.createResource(z_ns + "P").as(Property.class),
                        m.createTypedLiteral("x"));
        LOGGER.debug("1):\n{}", TestUtils.asString(m));
        // + a (from library) + z (autogenerated) + xsd (from literal datatype)
        Assert.assertEquals(5, m.numPrefixes());
        checkMappingHasAllPrefixes(m, xsd_p, x_p, y_p, a_p);
        checkMappingHasAllNamespaces(m, xsd_ns, z_ns, a_ns, x_ns, y_ns);

        OntClass c = m.createOntClass(w_ns + "CL1");
        // + owl + rdf + w_ns
        LOGGER.debug("2):\n{}", TestUtils.asString(m));
        Assert.assertEquals(8, m.numPrefixes());
        checkMappingHasAllPrefixes(m, owl_p, rdf_p, xsd_p, x_p, y_p, a_p);
        checkMappingHasAllNamespaces(m, xsd_ns, owl_ns, rdf_ns, w_ns, z_ns, a_ns, x_ns, y_ns);

        // 'xsd' instead of autogenerated + 'rdfs'
        c.addComment("x");
        LOGGER.debug("3):\n{}", TestUtils.asString(m));
        checkMappingHasAllPrefixes(m, rdfs_p, owl_p, rdf_p, xsd_p, x_p, y_p, a_p);
        checkMappingHasAllNamespaces(m, rdfs_ns, xsd_ns, owl_ns, rdf_ns, w_ns, z_ns, a_ns, x_ns, y_ns);
        Assert.assertEquals(9, m.numPrefixes());

        // rename:
        m.removeNsPrefix(xsd_p).setNsPrefix("x1", xsd_ns).removeNsPrefix(a_p).setNsPrefix("x2", a_ns);
        LOGGER.debug("4):\n{}", TestUtils.asString(m));
        checkMappingHasAllPrefixes(m, rdfs_p, owl_p, rdf_p, "x1", x_p, y_p, "x2");
        checkMappingHasAllNamespaces(m, rdfs_ns, xsd_ns, owl_ns, rdf_ns, w_ns, z_ns, a_ns, x_ns, y_ns);
        Assert.assertEquals(9, m.numPrefixes());

        // remove class and comment (-4 prefixes)
        m.removeOntObject(c);
        LOGGER.debug("5):\n{}", TestUtils.asString(m));
        Assert.assertEquals(5, m.numPrefixes());
    }

    @Test
    public void testURIAbbreviation() {
        Assert.assertEquals("map", AutoPrefixListener.calculatePrefix("http://example.com/FilterDefaultMapTest/map#"));
        Assert.assertEquals("target", AutoPrefixListener.calculatePrefix("http://example.com/GroupConcatTest/target#"));
        Assert.assertEquals("r", AutoPrefixListener.calculatePrefix("r"));
        Assert.assertEquals("u", AutoPrefixListener.calculatePrefix("urew#xxxx"));
        Assert.assertEquals("ytyew", AutoPrefixListener.calculatePrefix("urn:uuid:ytyew#xxxx"));
        Assert.assertEquals("tucv", AutoPrefixListener.calculatePrefix("https://translate.ugle.com/#view=home&op=translate&sl=en&tl=ru&text=test"));
        Assert.assertEquals("mc", AutoPrefixListener.calculatePrefix("http://mail.com//"));
        Assert.assertEquals("mc", AutoPrefixListener.calculatePrefix("http://mail.com##"));
        Assert.assertEquals("wgrss", AutoPrefixListener.calculatePrefix("https://www.Goo.ru/search?newwindow=1&source=hp&ei=PuQcXYT&q=sd&oq=sd&gs_l=psy-ab.3..0i10i1j0l7.11..1...0.0..0.100.21...0....1..gws-wiz..0..30i131.26u_J719x"));
        Assert.assertEquals("e", AutoPrefixListener.calculatePrefix("http://ex.mail.com#examp09234`e#"));
        Assert.assertEquals("dfjjk", AutoPrefixListener.calculatePrefix("http://twast.iroi/dfjjk#"));
        Assert.assertEquals("dfjjk", AutoPrefixListener.calculatePrefix("http://xxx.iroi/dfjjk#xxx"));
        Assert.assertEquals("dfjjk", AutoPrefixListener.calculatePrefix("http://uiuui.iroi/dfjjk/www"));
        Assert.assertEquals("owl", AutoPrefixListener.calculatePrefix("http://www.w3.org/2002/07/owl#Class"));
        Assert.assertEquals("wworsn", AutoPrefixListener.calculatePrefix("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"));
    }

    private static void checkMappingHasAllPrefixes(PrefixMapping pm, String... prefixes) {
        Collection<String> existing = pm.getNsPrefixMap().keySet();
        for (String p : prefixes) {
            Assert.assertTrue(existing.contains(p));
        }
    }

    private static void checkMappingHasAllNamespaces(PrefixMapping pm, String... uris) {
        Collection<String> existing = pm.getNsPrefixMap().values();
        for (String p : uris) {
            Assert.assertTrue(existing.contains(p));
        }
    }
}

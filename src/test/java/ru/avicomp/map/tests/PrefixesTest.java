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
import ru.avicomp.map.MapManager;
import ru.avicomp.map.MapModel;
import ru.avicomp.map.utils.TestUtils;
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
        MapManager manager = Managers.getMapManager();
        MapModel m = manager.createModel();
        LOGGER.debug("\n{}", TestUtils.asString(m));
        // owl:imports:
        Assert.assertEquals(1, m.numPrefixes());

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

package ru.avicomp.map.tests;

import org.apache.jena.graph.Factory;
import org.apache.jena.graph.FrontsNode;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.map.ClassPropertyMap;
import ru.avicomp.map.Managers;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.impl.OntObjectImpl;
import ru.avicomp.ontapi.jena.impl.conf.OntModelConfig;
import ru.avicomp.ontapi.jena.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by @szuev on 18.04.2018.
 */
public class ClassPropertiesTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClassPropertiesTest.class);

    private OntGraphModel load(String file, Lang format) throws IOException {
        Graph g = Factory.createGraphMem();
        try (InputStream in = ClassPropertiesTest.class.getResourceAsStream(file)) {
            RDFDataMgr.read(g, in, null, format);
        }
        return OntModelFactory.createModel(g, OntModelConfig.ONT_PERSONALITY_LAX);
    }

    @Test
    public void testPizza() throws Exception {
        OntGraphModel m = load("/pizza.ttl", Lang.TURTLE);
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
        validateClasses(m, expected);
    }

    @Test
    public void testFoaf() throws Exception {
        OntGraphModel m = load("/foaf.rdf", Lang.RDFXML);
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
        OntGraphModel m = load("/goodrelations.rdf", Lang.RDFXML);
        m.setNsPrefix("schema", "http://schema.org/");
        OntClass c = m.getOntEntity(OntClass.class, m.expandPrefix("gr:QuantitativeValue"));
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
        OntGraphModel m = load("/iswc.ttl", Lang.TURTLE);
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
    public void testCustom() {
        String uri = "http://ex.com";
        String ns = uri + "#";
        OntGraphModel m = OntModelFactory.createModel(Factory.createGraphMem(), OntModelConfig.ONT_PERSONALITY_LAX);
        m.setID(uri);
        m.setNsPrefix("e", ns);
        m.setNsPrefixes(OntModelFactory.STANDARD);

        OntNOP o1 = m.createOntEntity(OntNOP.class, ns + "o1");
        OntClass c1 = m.createOntEntity(OntClass.class, ns + "C1");
        m.createOntEntity(OntNDP.class, ns + "d1").addDomain(c1);
        m.createOntEntity(OntNDP.class, ns + "d2").addDomain(c1);
        OntClass c2 = m.createOntEntity(OntClass.class, ns + "C2");
        OntCE ce1 = m.createIntersectionOf(Arrays.asList(c1, m.createObjectAllValuesFrom(o1, c2)));

        OntClass c3 = m.createOntEntity(OntClass.class, ns + "C3");
        c3.addEquivalentClass(ce1);

        TestUtils.debug(m);
        m.ontObjects(OntClass.class).map(ClassPropertiesTest::classProperties).forEach(x -> LOGGER.debug("{}", x));

        Map<String, Integer> expected = new LinkedHashMap<>();
        expected.put("e:C1", 3);
        expected.put("e:C2", 1);
        expected.put("e:C3", 2);
        validateClasses(m, expected);
    }

    private static void validateClasses(OntGraphModel m, Map<String, Integer> expected) {
        ClassPropertyMap map = Managers.getMapManager().getClassProperties(m);
        expected.forEach((c, v) -> {
            OntClass clazz = m.getOntEntity(OntClass.class, m.expandPrefix(c));
            Assert.assertNotNull("Can't find class " + c, clazz);
            Assert.assertEquals("Wrong properties count for " + c, v.longValue(), map.properties(clazz).count());
        });
    }

    private static void doPrint(OntGraphModel m) {
        TestUtils.debug(m);
        m.ontObjects(OntCE.class).map(ClassPropertiesTest::classProperties).forEach(x -> LOGGER.debug("{}", x));
        LOGGER.debug("=============");
        m.ontObjects(OntPE.class).map(ClassPropertiesTest::propertyClasses).forEach(x -> LOGGER.debug("{}", x));
    }

    private static String classProperties(OntCE ce) {
        OntGraphModel m = ce.getModel();
        return Stream.concat(Stream.of("[" + ((OntObjectImpl) ce).getActualClass().getSimpleName() + "]" + m.shortForm(ce.asNode().toString())),
                Managers.getMapManager().getClassProperties(m)
                        .properties(ce)
                        .sorted(Comparator.comparing(Resource::getURI))
                        .map(p -> m.shortForm(p.getURI())))
                .collect(Collectors.joining("\n\t"));
    }

    private static String propertyClasses(OntPE pe) {
        OntGraphModel m = pe.getModel();
        return Stream.concat(Stream.of("[" + ((OntObjectImpl) pe).getActualClass().getSimpleName() + "]" + m.shortForm(pe.asNode().toString())),
                Managers.getMapManager().getClassProperties(m)
                        .classes(pe)
                        .map(FrontsNode::asNode)
                        .sorted(Comparator.comparing(Node::isURI).thenComparing((Function<Node, String>) Node::toString))
                        .map(c -> m.shortForm(c.toString())))
                .collect(Collectors.joining("\n\t"));
    }

}

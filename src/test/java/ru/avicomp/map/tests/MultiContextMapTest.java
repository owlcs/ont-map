package ru.avicomp.map.tests;

import org.apache.jena.riot.Lang;
import org.apache.jena.vocabulary.XSD;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.*;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.model.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by @szuev on 29.05.2018.
 */
public class MultiContextMapTest extends AbstractMapTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(MultiContextMapTest.class);

    // Test Data:
    private static final String SHIP_1_NAME = "Bismarck";
    private static final String SHIP_2_NAME = "Yamashiro";
    private static final String SHIP_3_NAME = "King George V";
    private static final double[] SHIP_1_COORDINATES = new double[]{43.296492, 12.3234};
    private static final double[] SHIP_2_COORDINATES = new double[]{-32, 151.56};
    private static final double[] SHIP_3_COORDINATES = new double[]{46.34542, 28.674692};

    @Test
    public void testInference() {
        LOGGER.info("Assembly models.");
        OntGraphModel src = assembleSource();
        OntGraphModel dst = assembleTarget();
        //TestUtils.debug(src);

        MapManager manager = Managers.getMapManager();
        MapModel map = assembleMapping(manager, src, dst);

        TestUtils.debug(map);

        LOGGER.info("Run inference.");
        manager.getInferenceEngine().run(map, src, dst);
        TestUtils.debug(dst);

        LOGGER.info("Validate.");
        Assert.assertEquals(3, dst.listNamedIndividuals().count());

        validate(dst, SHIP_1_NAME, SHIP_1_COORDINATES);
        validate(dst, SHIP_2_NAME, SHIP_2_COORDINATES);
        validate(dst, SHIP_3_NAME, SHIP_3_COORDINATES);
        commonValidate(dst);
    }

    private static void validate(OntGraphModel m, String name, double[] coordinates) {
        String s = "res-" + name.toLowerCase().replace(" ", "-");
        LOGGER.debug("Validate '{}'", s);
        OntIndividual.Named i = TestUtils.findOntEntity(m, OntIndividual.Named.class, s);
        List<OntStatement> assertions = TestUtils.plainAssertions(i).collect(Collectors.toList());
        Assert.assertEquals(4, assertions.size());
        OntNDP nameProp = TestUtils.findOntEntity(m, OntNDP.class, "name");
        OntNDP latitudeProp = TestUtils.findOntEntity(m, OntNDP.class, "latitude");
        OntNDP longitudeProp = TestUtils.findOntEntity(m, OntNDP.class, "longitude");
        OntNDP messageProp = TestUtils.findOntEntity(m, OntNDP.class, "message");
        Assert.assertEquals(name, TestUtils.getStringValue(i, nameProp));
        Assert.assertEquals(String.valueOf(coordinates[0]), TestUtils.getStringValue(i, latitudeProp));
        Assert.assertEquals(String.valueOf(coordinates[1]), TestUtils.getStringValue(i, longitudeProp));
        String expected = Arrays.stream(coordinates).mapToObj(String::valueOf).collect(Collectors.joining("-"));
        Assert.assertEquals(expected, TestUtils.getStringValue(i, messageProp));
    }

    @Override
    public MapModel assembleMapping(MapManager manager, OntGraphModel src, OntGraphModel dst) {
        OntClass CDSPR_D00001 = TestUtils.findOntEntity(src, OntClass.class, "CDSPR_D00001");
        // Name
        OntClass CDSPR_000011 = TestUtils.findOntEntity(src, OntClass.class, "CDSPR_000011");
        OntClass CCPAS_000011 = TestUtils.findOntEntity(src, OntClass.class, "CCPAS_000011");
        // Latitude
        OntClass CDSPR_000005 = TestUtils.findOntEntity(src, OntClass.class, "CDSPR_000005");
        OntClass CCPAS_000005 = TestUtils.findOntEntity(src, OntClass.class, "CCPAS_000005");
        // Longitude
        OntClass CDSPR_000006 = TestUtils.findOntEntity(src, OntClass.class, "CDSPR_000006");
        OntClass CCPAS_000006 = TestUtils.findOntEntity(src, OntClass.class, "CCPAS_000006");

        OntNOP OAHUU = TestUtils.findOntEntity(src, OntNOP.class, "OAHUU");
        OntNDP DEUUU = TestUtils.findOntEntity(src, OntNDP.class, "DEUUU");
        OntClass resClass = TestUtils.findOntEntity(dst, OntClass.class, "Res");
        OntNDP nameProp = TestUtils.findOntEntity(dst, OntNDP.class, "name");
        OntNDP latitudeProp = TestUtils.findOntEntity(dst, OntNDP.class, "latitude");
        OntNDP longitudeProp = TestUtils.findOntEntity(dst, OntNDP.class, "longitude");
        OntNDP messageProp = TestUtils.findOntEntity(dst, OntNDP.class, "message");
        MapModel res = createMappingModel(manager, "TODO");

        MapFunction composeURI = manager.getFunction(SPINMAPL.composeURI);
        MapFunction self = manager.getFunction(SPINMAPL.self);
        MapFunction equals = manager.getFunction(SPINMAP.equals);
        MapFunction concatWithSeparator = manager.getFunction(SPINMAPL.concatWithSeparator);

        Context mainContext = res.createContext(CDSPR_D00001, resClass, composeURI.create().addLiteral(SPINMAPL.template, "result:res-{?1}").build());
        // ship name
        Context nameContext = mainContext.createRelatedContext(CDSPR_000011).createRelatedContext(CCPAS_000011, OAHUU);
        nameContext.addPropertyBridge(equals.create().addProperty(SP.arg1, DEUUU).build(), nameProp);
        // ship latitude
        mainContext.createRelatedContext(CDSPR_000005).createRelatedContext(CCPAS_000005, OAHUU)
                .addPropertyBridge(equals.create().addProperty(SP.arg1, DEUUU).build(), latitudeProp);
        // ship longitude
        mainContext.createRelatedContext(CDSPR_000006).createRelatedContext(CCPAS_000006, OAHUU)
                .addPropertyBridge(equals.create().addProperty(SP.arg1, DEUUU).build(), longitudeProp);

        res.createContext(resClass, resClass, self.create().build())
                .addPropertyBridge(concatWithSeparator.create()
                        .addProperty(SP.arg1, latitudeProp)
                        .addProperty(SP.arg2, longitudeProp)
                        .addLiteral(SPINMAPL.separator, "-").build(), messageProp);

        return res;
    }

    @Override
    public OntGraphModel assembleSource() {
        OntGraphModel m;
        try {
            m = TestUtils.load("/ex-test.ttl", Lang.TURTLE);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        m.setID(getDataNameSpace() + "/source");
        m.setNsPrefix("data", m.getID().getURI() + "/data#");
        addDataIndividual(m, SHIP_1_NAME, SHIP_1_COORDINATES);
        addDataIndividual(m, SHIP_2_NAME, SHIP_2_COORDINATES);
        addDataIndividual(m, SHIP_3_NAME, SHIP_3_COORDINATES);
        return m;
    }


    @Override
    public OntGraphModel assembleTarget() {
        OntGraphModel m = createDataModel("result");
        String ns = m.getID().getURI() + "#";
        OntClass res = m.createOntEntity(OntClass.class, ns + "Res");
        OntDT string = m.getOntEntity(OntDT.class, XSD.xstring);
        Stream.of("name", "latitude", "longitude", "message").forEach(s -> {
            OntNDP p = m.createOntEntity(OntNDP.class, ns + s);
            p.addRange(string);
            p.addDomain(res);
        });
        return m;
    }

    private static void addDataIndividual(OntGraphModel m, String shipName, double[] coordinates) {
        OntNOP OAGUU = TestUtils.findOntEntity(m, OntNOP.class, "OAGUU"); // CDSPR_D00001 -> CDSPR_0000**
        OntNOP OAHUU = TestUtils.findOntEntity(m, OntNOP.class, "OAHUU"); // CDSPR_0000** -> CCPAS_0000**
        OntNDP DEUUU = TestUtils.findOntEntity(m, OntNDP.class, "DEUUU"); // data
        OntClass CDSPR_D00001 = TestUtils.findOntEntity(m, OntClass.class, "CDSPR_D00001");

        OntClass CDSPR_000005 = TestUtils.findOntEntity(m, OntClass.class, "CDSPR_000005"); // Latitude
        OntClass CCPAS_000005 = TestUtils.findOntEntity(m, OntClass.class, "CCPAS_000005");
        OntClass CDSPR_000006 = TestUtils.findOntEntity(m, OntClass.class, "CDSPR_000006"); // Longitude
        OntClass CCPAS_000006 = TestUtils.findOntEntity(m, OntClass.class, "CCPAS_000006");
        OntClass CDSPR_000011 = TestUtils.findOntEntity(m, OntClass.class, "CDSPR_000011"); // Name
        OntClass CCPAS_000011 = TestUtils.findOntEntity(m, OntClass.class, "CCPAS_000011");

        OntIndividual res = CDSPR_D00001.createIndividual(m.expandPrefix("data:" + shipName.toLowerCase().replace(" ", "-")));

        res.addAssertion(OAGUU, CDSPR_000005.createIndividual()
                .addAssertion(OAHUU, CCPAS_000005.createIndividual()
                        .addAssertion(DEUUU, m.createLiteral(String.valueOf(coordinates[0])))));

        res.addAssertion(OAGUU, CDSPR_000006.createIndividual()
                .addAssertion(OAHUU, CCPAS_000006.createIndividual()
                        .addAssertion(DEUUU, m.createLiteral(String.valueOf(coordinates[1])))));
        res.addAssertion(OAGUU, CDSPR_000011.createIndividual()
                .addAssertion(OAHUU, CCPAS_000011.createIndividual()
                        .addAssertion(DEUUU, m.createLiteral(shipName))));
    }

}

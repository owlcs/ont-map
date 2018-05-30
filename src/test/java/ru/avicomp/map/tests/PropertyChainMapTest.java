package ru.avicomp.map.tests;

import ru.avicomp.map.MapManager;
import ru.avicomp.map.MapModel;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.model.*;
import ru.avicomp.ontapi.jena.vocabulary.OWL;

import java.util.Arrays;

/**
 * Created by @szuev on 30.05.2018.
 */
public class PropertyChainMapTest extends MapTestData6 {

    @Override
    public MapModel assembleMapping(MapManager manager, OntGraphModel src, OntGraphModel dst) {
        return null;
    }

    @Override
    public OntGraphModel assembleSource() {
        OntGraphModel m = super.assembleSource();
        OntNOP OAGUU = TestUtils.findOntEntity(m, OntNOP.class, "OAGUU");
        OntNOP OAHUU = TestUtils.findOntEntity(m, OntNOP.class, "OAHUU");
        OntNOP OASUU = m.createOntEntity(OntNOP.class, m.expandPrefix("ex:OASUU"));
        OASUU.addSubPropertyOf(m.getOntEntity(OntNOP.class, OWL.topObjectProperty));
        OASUU.setTransitive(true);
        // property chain
        OASUU.addSuperPropertyOf(Arrays.asList(OAGUU, OAHUU));
        addDataIndividual(m, SHIP_1_NAME, SHIP_1_COORDINATES);
        addDataIndividual(m, SHIP_2_NAME, SHIP_2_COORDINATES);
        addDataIndividual(m, SHIP_3_NAME, SHIP_3_COORDINATES);
        return m;
    }


    private static void addDataIndividual(OntGraphModel m, String shipName, double[] coordinates) {
        OntNOP OASUU = TestUtils.findOntEntity(m, OntNOP.class, "OASUU");
        OntNDP DEUUU = TestUtils.findOntEntity(m, OntNDP.class, "DEUUU");
        OntClass CDSPR_D00001 = TestUtils.findOntEntity(m, OntClass.class, "CDSPR_D00001");
        OntClass CCPAS_000005 = TestUtils.findOntEntity(m, OntClass.class, "CCPAS_000005"); // Latitude
        OntClass CCPAS_000006 = TestUtils.findOntEntity(m, OntClass.class, "CCPAS_000006"); // Longitude
        OntClass CCPAS_000011 = TestUtils.findOntEntity(m, OntClass.class, "CCPAS_000011"); // Name
        OntIndividual res = CDSPR_D00001.createIndividual(m.expandPrefix("data:" + shipName.toLowerCase().replace(" ", "-")));
        res.addAssertion(OASUU, CCPAS_000005.createIndividual()
                .addAssertion(DEUUU, m.createLiteral(String.valueOf(coordinates[0]))));
        res.addAssertion(OASUU, CCPAS_000006.createIndividual()
                .addAssertion(DEUUU, m.createLiteral(String.valueOf(coordinates[1]))));
        res.addAssertion(OASUU, CCPAS_000011.createIndividual()
                .addAssertion(DEUUU, m.createLiteral(shipName)));
    }

    public static void main(String... args) {
        OntGraphModel m = new PropertyChainMapTest().assembleSource();
        m.write(System.out, "ttl");
    }

}

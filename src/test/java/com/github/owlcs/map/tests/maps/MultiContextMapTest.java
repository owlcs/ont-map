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

package com.github.owlcs.map.tests.maps;

import com.github.owlcs.map.*;
import com.github.owlcs.map.spin.vocabulary.SPINMAPL;
import com.github.owlcs.map.utils.TestUtils;
import com.github.owlcs.ontapi.jena.model.*;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPINMAP;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by @szuev on 29.05.2018.
 */
public class MultiContextMapTest extends MapTestData6 {
    private static final Logger LOGGER = LoggerFactory.getLogger(MultiContextMapTest.class);

    @Ignore // for manual running: ignored since it is called by #testInferenceCycle
    @Test
    public void testInferenceOnce() {
        LOGGER.info("Assembly models.");
        OntModel src = assembleSource();
        OntModel dst = assembleTarget();
        //TestUtils.debug(src);

        MapManager manager = manager();
        MapModel map = assembleMapping(manager, src, dst);

        TestUtils.debug(map);

        LOGGER.info("Run inference.");
        manager.getInferenceEngine(map).run(src, dst);
        TestUtils.debug(dst);
        validate(dst);
    }

    private void validate(OntModel dst) {
        LOGGER.info("Validate.");
        Assert.assertEquals(3, dst.individuals().count());
        validateIndividual(dst, SHIP_1_NAME, SHIP_1_COORDINATES);
        validateIndividual(dst, SHIP_2_NAME, SHIP_2_COORDINATES);
        validateIndividual(dst, SHIP_3_NAME, SHIP_3_COORDINATES);
        commonValidate(dst);
    }

    @Test
    public void testInferenceCycle() {
        int n = 10;
        for (int i = 0; i < n; i++) {
            LOGGER.info("ITER#{}", i);
            testInferenceOnce();
        }
    }

    private static void validateIndividual(OntModel m, String name, double[] coordinates) {
        String s = "res-" + name.toLowerCase().replace(" ", "-");
        LOGGER.debug("Validate '{}'", s);
        OntIndividual.Named i = TestUtils.findOntEntity(m, OntIndividual.Named.class, s);
        List<OntStatement> assertions = i.positiveAssertions().collect(Collectors.toList());
        Assert.assertEquals("<" + m.shortForm(i.getURI()) + ">: wrong assertion number", 4, assertions.size());
        OntDataProperty nameProp = TestUtils.findOntEntity(m, OntDataProperty.class, "name");
        OntDataProperty latitudeProp = TestUtils.findOntEntity(m, OntDataProperty.class, "latitude");
        OntDataProperty longitudeProp = TestUtils.findOntEntity(m, OntDataProperty.class, "longitude");
        OntDataProperty messageProp = TestUtils.findOntEntity(m, OntDataProperty.class, "message");
        Assert.assertEquals(name, TestUtils.getStringValue(i, nameProp));
        Assert.assertEquals(String.valueOf(coordinates[0]), TestUtils.getStringValue(i, latitudeProp));
        Assert.assertEquals(String.valueOf(coordinates[1]), TestUtils.getStringValue(i, longitudeProp));
        String expected = Arrays.stream(coordinates).mapToObj(String::valueOf).collect(Collectors.joining("-"));
        Assert.assertEquals(expected, TestUtils.getStringValue(i, messageProp));
    }

    @Override
    public MapModel assembleMapping(MapManager manager, OntModel src, OntModel dst) {
        OntClass CDSPR_D00001 = TestUtils.findOntEntity(src, OntClass.Named.class, "CDSPR_D00001");
        // Name
        OntClass CDSPR_000011 = TestUtils.findOntEntity(src, OntClass.Named.class, "CDSPR_000011");
        OntClass CCPAS_000011 = TestUtils.findOntEntity(src, OntClass.Named.class, "CCPAS_000011");
        // Latitude
        OntClass CDSPR_000005 = TestUtils.findOntEntity(src, OntClass.Named.class, "CDSPR_000005");
        OntClass CCPAS_000005 = TestUtils.findOntEntity(src, OntClass.Named.class, "CCPAS_000005");
        // Longitude
        OntClass CDSPR_000006 = TestUtils.findOntEntity(src, OntClass.Named.class, "CDSPR_000006");
        OntClass CCPAS_000006 = TestUtils.findOntEntity(src, OntClass.Named.class, "CCPAS_000006");

        OntObjectProperty OAHUU = TestUtils.findOntEntity(src, OntObjectProperty.Named.class, "OAHUU");
        OntDataProperty DEUUU = TestUtils.findOntEntity(src, OntDataProperty.class, "DEUUU");
        OntClass resClass = TestUtils.findOntEntity(dst, OntClass.Named.class, "Res");
        OntDataProperty nameProp = TestUtils.findOntEntity(dst, OntDataProperty.class, "name");
        OntDataProperty latitudeProp = TestUtils.findOntEntity(dst, OntDataProperty.class, "latitude");
        OntDataProperty longitudeProp = TestUtils.findOntEntity(dst, OntDataProperty.class, "longitude");
        OntDataProperty messageProp = TestUtils.findOntEntity(dst, OntDataProperty.class, "message");

        MapFunction composeURI = manager.getFunction(SPINMAPL.composeURI);
        MapFunction self = manager.getFunction(SPINMAPL.self);
        MapFunction equals = manager.getFunction(SPINMAP.equals);
        MapFunction concatWithSeparator = manager.getFunction(SPINMAPL.concatWithSeparator);

        MapModel res = createMappingModel(manager, "Used functions: " + toMessage(manager.prefixes(), composeURI, self, equals, concatWithSeparator));
        MapContext mainContext = res.createContext(CDSPR_D00001, resClass, composeURI.create().addLiteral(SPINMAPL.template, "result:res-{?1}").build());
        // ship name
        MapContext nameContext = mainContext.createRelatedContext(CDSPR_000011).createRelatedContext(CCPAS_000011, OAHUU);
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
    public OntModel assembleSource() {
        OntModel m = super.assembleSource();
        addDataIndividual(m, SHIP_1_NAME, SHIP_1_COORDINATES);
        addDataIndividual(m, SHIP_2_NAME, SHIP_2_COORDINATES);
        addDataIndividual(m, SHIP_3_NAME, SHIP_3_COORDINATES);
        return m;
    }

    private static void addDataIndividual(OntModel m, String shipName, double[] coordinates) {
        OntObjectProperty.Named OAGUU = TestUtils.findOntEntity(m, OntObjectProperty.Named.class, "OAGUU"); // CDSPR_D00001 -> CDSPR_0000**
        OntObjectProperty.Named OAHUU = TestUtils.findOntEntity(m, OntObjectProperty.Named.class, "OAHUU"); // CDSPR_0000** -> CCPAS_0000**
        OntDataProperty DEUUU = TestUtils.findOntEntity(m, OntDataProperty.class, "DEUUU"); // data
        OntClass CDSPR_D00001 = TestUtils.findOntEntity(m, OntClass.Named.class, "CDSPR_D00001");

        OntClass CDSPR_000005 = TestUtils.findOntEntity(m, OntClass.Named.class, "CDSPR_000005"); // Latitude
        OntClass CCPAS_000005 = TestUtils.findOntEntity(m, OntClass.Named.class, "CCPAS_000005");
        OntClass CDSPR_000006 = TestUtils.findOntEntity(m, OntClass.Named.class, "CDSPR_000006"); // Longitude
        OntClass CCPAS_000006 = TestUtils.findOntEntity(m, OntClass.Named.class, "CCPAS_000006");
        OntClass CDSPR_000011 = TestUtils.findOntEntity(m, OntClass.Named.class, "CDSPR_000011"); // Name
        OntClass CCPAS_000011 = TestUtils.findOntEntity(m, OntClass.Named.class, "CCPAS_000011");

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

    @Test
    public void testDeleteContext() {
        MapModel m = assembleMapping();
        int contextsNum = 8;
        int propertiesNum = 4;
        assertContext(m, contextsNum, propertiesNum);

        OntClass Res = TestUtils.findOntEntity(m.asGraphModel(), OntClass.Named.class, "Res");
        OntClass CDSPR_000011 = TestUtils.findOntEntity(m.asGraphModel(), OntClass.Named.class, "CDSPR_000011");
        OntClass CCPAS_000011 = TestUtils.findOntEntity(m.asGraphModel(), OntClass.Named.class, "CCPAS_000011");
        MapContext Res_2Res = find(m, Res, Res);
        MapContext CDSPR_000011_2Res = find(m, CDSPR_000011, Res);
        MapContext CCPAS_000011_2Res = find(m, CCPAS_000011, Res);
        // check can't delete CDSPR_000011->Res and CCPAS_000011->Res
        Stream.of(CCPAS_000011_2Res, CDSPR_000011_2Res).forEach(MultiContextMapTest::testDeleteDependentContext);
        // delete Res->Res context
        m.deleteContext(Res_2Res);
        // on this context there is one property rule (spinmapl:concatWithSeparator)
        assertContext(m, --contextsNum, --propertiesNum);
        // check can't delete CDSPR_000011->Res
        testDeleteDependentContext(CDSPR_000011_2Res);
        // delete CCPAS_000011->Res
        m.deleteContext(CCPAS_000011_2Res);
        // on this context there is one property rule (spinmap:equals)
        assertContext(m, --contextsNum, --propertiesNum);
        // celete CDSPR_000011->Res
        m.deleteContext(CDSPR_000011_2Res);
        // on this context there is no properties any more (was connected with spinmapl:relatedSubjectContext)
        assertContext(m, --contextsNum, propertiesNum);
    }

    private static void testDeleteDependentContext(MapContext context) {
        try {
            context.getModel().deleteContext(context);
            Assert.fail("Context " + context + " has been deleted!");
        } catch (MapJenaException j) {
            LOGGER.debug("Tried to delete {}. Expected error: '{}'", context, j.getMessage());
        }
    }

    private static void assertContext(MapModel m, int contextsNum, int propertiesNum) {
        Assert.assertEquals(contextsNum + propertiesNum, m.rules().count());
        Assert.assertEquals(contextsNum, m.contexts().count());
    }

    private static MapContext find(MapModel m, OntClass src, OntClass dst) {
        return m.contexts()
                .filter(c -> Objects.equals(src, c.getSource()))
                .filter(c -> Objects.equals(dst, c.getTarget()))
                .findFirst().orElseThrow(AssertionError::new);
    }

}

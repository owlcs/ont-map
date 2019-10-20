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

package ru.avicomp.map.tests.maps;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPL;
import ru.avicomp.map.MapContext;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.MapModel;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.model.*;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by @szuev on 30.05.2018.
 */
public class PropertyChainMapTest extends MapTestData6 {
    private static final Logger LOGGER = LoggerFactory.getLogger(PropertyChainMapTest.class);

    @Test
    public void testInference() {
        LOGGER.info("Assembly models.");
        OntGraphModel src = assembleSource();
        OntGraphModel dst = assembleTarget();
        TestUtils.debug(src);

        MapManager manager = manager();
        MapModel map = assembleMapping(manager, src, dst);

        TestUtils.debug(map);

        LOGGER.info("Run inference.");
        manager.getInferenceEngine(map).run(src, dst);
        TestUtils.debug(dst);

        LOGGER.info("Validate.");
        validate(dst);
    }

    public void validate(OntGraphModel dst) {
        Assert.assertEquals(3, dst.individuals().count());
        validateIndividual(dst, SHIP_1_NAME, SHIP_1_COORDINATES);
        validateIndividual(dst, SHIP_2_NAME, SHIP_2_COORDINATES);
        validateIndividual(dst, SHIP_3_NAME, SHIP_3_COORDINATES);
        commonValidate(dst);
    }

    private static void validateIndividual(OntGraphModel m, String name, double[] coordinates) {
        String s = "res-" + name.toLowerCase().replace(" ", "-");
        LOGGER.debug("Validate '{}'", s);
        OntIndividual.Named i = TestUtils.findOntEntity(m, OntIndividual.Named.class, s);
        List<OntStatement> assertions = i.positiveAssertions().collect(Collectors.toList());
        Assert.assertEquals("<" + m.shortForm(i.getURI()) + ">: wrong assertion number", 2, assertions.size());
        OntNDP nameProp = TestUtils.findOntEntity(m, OntNDP.class, "name");
        OntNDP messageProp = TestUtils.findOntEntity(m, OntNDP.class, "message");
        Assert.assertEquals(name, TestUtils.getStringValue(i, nameProp));
        String expected = Arrays.stream(coordinates).mapToObj(String::valueOf).collect(Collectors.joining("-"));
        Assert.assertEquals(expected, TestUtils.getStringValue(i, messageProp));
    }

    @Override
    public MapModel assembleMapping(MapManager manager, OntGraphModel src, OntGraphModel dst) {
        OntClass CDSPR_D00001 = TestUtils.findOntEntity(src, OntClass.class, "CDSPR_D00001");
        OntClass CCPAS_000011 = TestUtils.findOntEntity(src, OntClass.class, "CCPAS_000011");
        OntClass CCPAS_000005 = TestUtils.findOntEntity(src, OntClass.class, "CCPAS_000005");
        OntClass CCPAS_000006 = TestUtils.findOntEntity(src, OntClass.class, "CCPAS_000006");
        OntNOP OASUU = TestUtils.findOntEntity(src, OntNOP.class, "OASUU");
        OntNDP DEUUU = TestUtils.findOntEntity(src, OntNDP.class, "DEUUU");

        OntClass resClass = TestUtils.findOntEntity(dst, OntClass.class, "Res");
        OntNDP nameProp = TestUtils.findOntEntity(dst, OntNDP.class, "name");
        OntNDP messageProp = TestUtils.findOntEntity(dst, OntNDP.class, "message");

        MapFunction composeURI = manager.getFunction(SPINMAPL.composeURI);
        MapFunction concatWithSeparator = manager.getFunction(SPINMAPL.concatWithSeparator);
        MapFunction currentIndividual = manager.getFunction(AVC.currentIndividual);
        MapFunction asIRI = manager.getFunction(AVC.asIRI);
        MapFunction object = manager.getFunction(SPL.object);
        MapFunction objectWithFilter = manager.getFunction(AVC.objectWithFilter);

        MapFunction.Call OASUU_IRI = asIRI.create().addProperty(SP.arg1, OASUU).build();

        MapModel res = createMappingModel(manager, "Used functions: " +
                toMessage(manager.prefixes(), composeURI,
                        concatWithSeparator, currentIndividual, asIRI, object, objectWithFilter));

        MapContext context = res.createContext(CDSPR_D00001, resClass, composeURI.create()
                .addLiteral(SPINMAPL.template, "result:res-{?1}").build());
        // name
        context.addPropertyBridge(object.create()
                .addFunction(SP.arg1, objectWithFilter.create()
                        .addFunction(SP.arg1, currentIndividual.create().build())
                        .addFunction(SP.arg2, OASUU_IRI)
// todo: handle <rdf:type> in ClassPropertyMap ?
                        .add(SP.arg3.getURI(), RDF.type.getURI())
                        .addClass(SP.arg4, CCPAS_000011)
                        .build())
                .addProperty(SP.arg2, DEUUU)
                .build(), nameProp);
        // message
        context.addPropertyBridge(concatWithSeparator.create()
                .addFunction(SP.arg1, object.create()
                        .addFunction(SP.arg1, objectWithFilter.create()
                                .addFunction(SP.arg1, currentIndividual.create().build())
                                .addFunction(SP.arg2, OASUU_IRI)
// todo: there is no <rdf:type> in ClassProperties map, where to get this IRI-value? maybe it is need a special datatype ?
                                .add(SP.arg3.getURI(), RDF.type.getURI())
                                .addClass(SP.arg4, CCPAS_000005)
                                .build())
                        .addProperty(SP.arg2, DEUUU))
                .addFunction(SP.arg2, object.create()
                        .addFunction(SP.arg1, objectWithFilter.create()
                                .addFunction(SP.arg1, currentIndividual.create().build())
                                .addFunction(SP.arg2, OASUU_IRI)
                                .add(SP.arg3.getURI(), RDF.type.getURI())
                                .addClass(SP.arg4, CCPAS_000006)
                                .build())
                        .addProperty(SP.arg2, DEUUU))
                .addLiteral(SPINMAPL.separator, "-").build(), messageProp);
        return res;
    }

    @Override
    public OntGraphModel assembleSource() {
        OntGraphModel m = super.assembleSource();
        OntNOP OAGUU = TestUtils.findOntEntity(m, OntNOP.class, "OAGUU");
        OntNOP OAHUU = TestUtils.findOntEntity(m, OntNOP.class, "OAHUU");
        OntNOP OASUU = m.createOntEntity(OntNOP.class, m.expandPrefix("ex:OASUU"));
        OASUU.addSuperProperty(m.getOntEntity(OntNOP.class, OWL.topObjectProperty)).setTransitive(true);
        // add property chain relationship:
        OASUU.addPropertyChain(OAGUU, OAHUU);
        addDataIndividual(m, SHIP_1_NAME, SHIP_1_COORDINATES);
        addDataIndividual(m, SHIP_2_NAME, SHIP_2_COORDINATES);
        addDataIndividual(m, SHIP_3_NAME, SHIP_3_COORDINATES);
        return m;
    }

    public static void addDataIndividual(OntGraphModel m, String shipName, double[] coordinates) {
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


}

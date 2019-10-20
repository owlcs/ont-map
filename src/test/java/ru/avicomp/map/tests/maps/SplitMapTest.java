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
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.MapContext;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.MapModel;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.model.*;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * TODO not ready
 * Created by @szuev on 10.05.2018.
 */
public class SplitMapTest extends MapTestData3 {
    private static final Logger LOGGER = LoggerFactory.getLogger(SplitMapTest.class);

    @Test
    public void testInference() {
        LOGGER.info("Assembly models.");
        OntGraphModel s = assembleSource();
        TestUtils.debug(s);
        OntGraphModel t = assembleTarget();
        TestUtils.debug(t);
        MapManager manager = manager();
        MapModel m = assembleMapping(manager, s, t);

        TestUtils.debug(m);

        LOGGER.info("Run inference.");
        manager.getInferenceEngine(m).run(s, t);
        TestUtils.debug(t);

        LOGGER.info("Validate.");
        Assert.assertEquals(s.namedIndividuals().count() * 2, t.individuals().count());
        OntClass contact = TestUtils.findOntEntity(t, OntClass.class, "Contact");
        OntClass address = TestUtils.findOntEntity(t, OntClass.class, "Address");
        OntNOP link = TestUtils.findOntEntity(t, OntNOP.class, "contact-address");
        contact.individuals().forEach(p -> {
            List<OntStatement> assertions = p.positiveAssertions()
                    .filter(s1 -> s1.getPredicate().canAs(OntNOP.class))
                    .collect(Collectors.toList());
            Assert.assertEquals(1, assertions.size());
            Assert.assertEquals(link, assertions.get(0).getPredicate());
            OntIndividual a = assertions.get(0).getObject().as(OntIndividual.Named.class);
            Assert.assertTrue(a.classes().anyMatch(address::equals));
        });
        commonValidate(t);
    }

    @Test
    public void testDeleteContext() {
        MapModel m = assembleMapping();
        OntClass address = m.asGraphModel().classes()
                .filter(s -> Objects.equals(s.getLocalName(), "Address")).findFirst().orElseThrow(AssertionError::new);
        OntClass contact = m.asGraphModel().classes()
                .filter(s -> Objects.equals(s.getLocalName(), "Contact")).findFirst().orElseThrow(AssertionError::new);
        Function<List<MapContext>, MapContext> firstContext = contexts -> contexts.stream()
                .filter(c -> Objects.equals(c.getTarget(), contact)).findFirst().orElseThrow(AssertionError::new);
        Function<List<MapContext>, MapContext> secondContext = contexts -> contexts.stream()
                .filter(c -> Objects.equals(c.getTarget(), address)).findFirst().orElseThrow(AssertionError::new);
        RelatedContextMapTest.deleteDependentContextTest(m, firstContext, secondContext);
    }

    @Override
    public MapModel assembleMapping(MapManager manager, OntGraphModel src, OntGraphModel dst) {
        OntNDP firstName = TestUtils.findOntEntity(src, OntNDP.class, "first-name");
        OntNDP secondName = TestUtils.findOntEntity(src, OntNDP.class, "second-name");
        OntNDP middleName = TestUtils.findOntEntity(src, OntNDP.class, "middle-name");
        OntNDP address = TestUtils.findOntEntity(src, OntNDP.class, "address");
        OntClass srcPerson = TestUtils.findOntEntity(src, OntClass.class, "Person");
        OntClass dstContact = TestUtils.findOntEntity(dst, OntClass.class, "Contact");
        OntClass dstAddress = TestUtils.findOntEntity(dst, OntClass.class, "Address");
        OntNDP dstName = TestUtils.findOntEntity(dst, OntNDP.class, "full-name");
        String dstNS = dst.getNsURIPrefix(dst.getID().getURI() + "#");

        MapFunction buildURI1 = manager.getFunction(SPINMAPL.buildURI1.getURI());
        MapFunction buildURI3 = manager.getFunction(SPINMAPL.buildURI3.getURI());
        MapFunction equals = manager.getFunction(SPINMAP.equals.getURI());

        MapModel res = createMappingModel(manager, "Used functions: spinmapl:buildURI3, spinmapl:buildURI2, spinmap:equals");
        MapContext context1 = res.createContext(srcPerson, dstContact, buildURI3.create()
                .addProperty(SP.arg1, firstName)
                .addProperty(SP.arg2, middleName)
                .addProperty(SP.arg3, secondName)
                .addLiteral(SPINMAPL.template, dstNS + ":{?1}-{?2}-{?3}").build());
        context1.addPropertyBridge(equals.create().addProperty(SP.arg1, firstName).build(), dstName);
        MapContext context2 = res.createContext(srcPerson, dstAddress, buildURI1.create()
                .addProperty(SP.arg1, address)
                .addLiteral(SPINMAPL.template, dstNS + ":{?1}").build());

        return res.bindContexts(context1, context2);
    }
}

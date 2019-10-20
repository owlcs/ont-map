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

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.shared.PrefixMapping;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPINMAP;
import org.topbraid.spin.vocabulary.SPL;
import ru.avicomp.map.MapContext;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.MapModel;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.model.*;

import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by @szuev on 17.05.2018.
 */
public class IntersectConcatMapTest extends MapTestData4 {
    private static final Logger LOGGER = LoggerFactory.getLogger(IntersectConcatMapTest.class);
    private static final String SEPARATOR = "; ";

    @Test
    public void testInference() {
        LOGGER.info("Assembly models.");
        OntGraphModel src = assembleSource();
        OntGraphModel dst = assembleTarget();

        MapManager manager = manager();
        MapModel map = assembleMapping(manager, src, dst);
        TestUtils.debug(map);

        LOGGER.info("Run inference.");
        manager.getInferenceEngine(map).run(src, dst);
        TestUtils.debug(dst);

        LOGGER.info("Validate.");
        validate(map, src, dst);
    }

    void validate(MapModel map, OntGraphModel src, OntGraphModel dst) {
        PrefixMapping pm = map.asGraphModel();
        dst.namedIndividuals()
                .flatMap(OntIndividual::positiveAssertions)
                .sorted(Comparator.comparing((Statement s) -> s.getPredicate().getURI()).thenComparing(s -> s.getSubject().getURI()))
                .forEach(a -> LOGGER.debug("Assertion: {}", TestUtils.toString(pm, a)));
        // number of source and target individuals are the same:
        OntClass persons = TestUtils.findOntEntity(src, OntClass.class, "persons");
        Assert.assertEquals(persons.individuals().count(), dst.individuals().count());
        // have 5 data property assertions for address (two of them on the same individual):
        OntNDP userAddress = TestUtils.findOntEntity(dst, OntNDP.class, "user-address");
        Set<String> addresses = dst.statements(null, userAddress, null)
                .map(Statement::getObject)
                .map(RDFNode::asLiteral)
                .map(Literal::getString).collect(Collectors.toSet());
        addresses.forEach(LOGGER::debug);
        Assert.assertEquals(5, addresses.size());
        Assert.assertTrue(addresses.stream().anyMatch(str -> str.contains(SEPARATOR)));

        commonValidate(dst);
    }

    @Override
    public MapModel assembleMapping(MapManager manager, OntGraphModel src, OntGraphModel dst) {
        OntClass persons = TestUtils.findOntEntity(src, OntClass.class, "persons");
        OntClass organizations = TestUtils.findOntEntity(src, OntClass.class, "organizations");
        OntClass users = TestUtils.findOntEntity(dst, OntClass.class, "User");

        OntNOP personOrganization = TestUtils.findOntEntity(src, OntNOP.class, "rel_person_organization");
        OntNDP personAddress = TestUtils.findOntEntity(src, OntNDP.class, "persons_Address");
        OntNDP personsFirstName = TestUtils.findOntEntity(src, OntNDP.class, "persons_FirstName");
        OntNDP organizationsAddress = TestUtils.findOntEntity(src, OntNDP.class, "organizations_Address");

        OntNDP userAddress = TestUtils.findOntEntity(dst, OntNDP.class, "user-address");
        OntNDP userName = TestUtils.findOntEntity(dst, OntNDP.class, "user-name");

        MapFunction composeURI = manager.getFunction(SPINMAPL.composeURI.getURI());
        MapFunction equals = manager.getFunction(SPINMAP.equals.getURI());
        MapFunction concatWithSeparator = manager.getFunction(SPINMAPL.concatWithSeparator.getURI());
        MapFunction object = manager.getFunction(SPL.object.getURI());

        MapModel res = createMappingModel(manager, "Concat data properties from different contexts.\n" +
                "Used functions: spinmapl:composeURI, spl:object, spinmap:equals, spinmapl:concatWithSeparator");
        MapContext context = res.createContext(persons, users).addClassBridge(composeURI.create().addLiteral(SPINMAPL.template, "users:{?1}").build());
        context.createRelatedContext(organizations);
        context.addPropertyBridge(equals.create().addProperty(SP.arg1, personsFirstName).build(), userName);
        context.addPropertyBridge(concatWithSeparator.create()
                .addProperty(SP.arg1, personAddress)
                .addFunction(SP.arg2, object.create()
                        .addProperty(SP.arg1, personOrganization)
                        .addProperty(SP.arg2, organizationsAddress))
                .addLiteral(SPINMAPL.separator, SEPARATOR)
                .build(), userAddress);
        return res;
    }
}

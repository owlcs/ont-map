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

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.map.*;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.model.OntID;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by @szuev on 05.06.2018.
 */
@RunWith(Parameterized.class)
public class CommonMappingTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommonMappingTest.class);

    private final Data data;

    private static MapManager manager;

    @BeforeClass
    public static void before() {
        manager = Managers.createMapManager();
    }

    public CommonMappingTest(Data data) {
        this.data = data;
    }

    @Parameterized.Parameters(name = "{0}")
    public static List<Data> getData() {
        return Arrays.asList(
                of(UUIDMapTest.class, 1, 0, 1),
                of(BuildURIMapTest.class, 1, 2, 3),
                of(NestedFuncMapTest.class, 1, 1, 3),
                of(ConditionalMapTest.class, 1, 3, 4),
                of(RelatedContextMapTest.class, 2, 1, 3),
                of(FilterDefaultMapTest.class, 1, 1, 4),
                of(FilterIndividualsMapTest.class, 1, 1, 5),
                of(SplitMapTest.class, 2, 2, 4),
                of(IntersectConcatMapTest.class, 2, 2, 5),
                of(GroupConcatTest.class, 1, 1, 4),
                of(MathOpsMapTest.class, 1, 3, 10),
                of(MultiContextMapTest.class, 8, 4, 5),
                of(PropertyChainMapTest.class, 1, 2, 6),
                of(VarArgMapTest.class, 1, 2, 3),
                of(LoadMapTestData.class, 1, 1, 2, 1)
        );
    }

    private static Data of(Class<? extends AbstractMapTest> clazz,
                           int classBridgeNumber,
                           int propertyBridgeNumber,
                           int functionsNumber) {
        return of(clazz, classBridgeNumber, propertyBridgeNumber, functionsNumber, 3);
    }

    private static Data of(Class<? extends AbstractMapTest> clazz,
                           int classBridgeNumber,
                           int propertyBridgeNumber,
                           int functionsNumber,
                           int ontologiesCount) {
        AbstractMapTest m;
        try {
            m = clazz.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new AssertionError(e);
        }
        return new Data(m, classBridgeNumber, propertyBridgeNumber, functionsNumber, ontologiesCount);
    }

    private MapModel createMapping() {
        return data.map.assembleMapping(manager, data.map.assembleSource(), data.map.assembleTarget());
    }

    @Test
    public void testListMapping() {
        MapModel m = createMapping();
        OntID id = m.getID();
        // list contexts
        info("Contexts:");
        LOGGER.debug("Model <{}> ::: {}", id, m.contexts().map(Object::toString).collect(Collectors.joining(", ", "[", "]")));

        // list property-bridges
        info("Properties:");
        m.contexts().forEach(c -> LOGGER.debug("Context properties ({}) :: {}", c, c.properties().map(Object::toString).collect(Collectors.joining(", ", "[", "]"))));

        // list contexts mapping functions (including filters):
        info("Context function calls:");
        m.contexts().forEach(CommonMappingTest::printFunctions);

        // list properties mapping functions (including filters):
        info("PropertyMap function calls:");
        m.contexts().flatMap(MapContext::properties).forEach(CommonMappingTest::printFunctions);

        // list all functions:
        info("Functions:");
        m.rules().flatMap(MapResource::functions).distinct().forEach(function -> LOGGER.debug("{}", function));

        // list resources
        info("Root statements:");
        m.rules().map(MapResource::asResource)
                .forEach(r -> LOGGER.debug("{} :: {}", r, TestUtils.toString(m.asGraphModel(), r.getRoot())));

        Assert.assertEquals(data.contexts, m.contexts().count());
        Assert.assertEquals(data.properties, m.contexts().flatMap(MapContext::properties).count());
        Assert.assertEquals(data.functions, m.rules().flatMap(MapResource::functions).distinct().count());
    }

    @Test
    public void testLoadOWL() {
        OWLMapManager manager = Managers.createOWLMapManager();
        manager.addOntology(createMapping().asGraphModel().getGraph());
        Assert.assertEquals(data.ontologies, manager.ontologies().count());
        Assert.assertEquals(1, manager.mappings().count());
        MapModel m = manager.mappings().findFirst().orElseThrow(AssertionError::new);
        Assert.assertEquals(data.contexts, m.contexts().count());
    }

    private static void printFunctions(MapResource r) {
        LOGGER.debug("{} Mapping Function :: {}", r, r.getMapping());
        MapFunction.Call f = r.getFilter();
        if (f != null)
            LOGGER.debug("{} Filter Function :: {}", r, f);
    }

    private static void info(String message) {
        LOGGER.info("==={}===========================================", message);
    }

    private static class Data {
        private final AbstractMapTest map;
        private final int contexts, properties, functions, ontologies;

        private Data(AbstractMapTest map, int contexts, int properties, int functions, int ontologies) {
            this.map = map;
            this.contexts = contexts;
            this.properties = properties;
            this.functions = functions;
            this.ontologies = ontologies;
        }

        @Override
        public String toString() {
            return map.getClass().getSimpleName();
        }
    }
}

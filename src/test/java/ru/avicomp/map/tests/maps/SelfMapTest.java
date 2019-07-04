/*
 * This file is part of the ONT MAP.
 * The contents of this file are subject to the Apache License, Version 2.0.
 * Copyright (c) 2019, Avicomp Services, AO
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
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;
import ru.avicomp.map.Managers;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.MapModel;
import ru.avicomp.map.spin.vocabulary.ARQ;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntGraphModel;

/**
 * Created by @ssz on 18.11.2018.
 */
@SuppressWarnings("WeakerAccess")
public class SelfMapTest extends AbstractMapTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(SelfMapTest.class);

    @Test
    // todo: not ready - infer anonymous individuals without query optimization (https://github.com/avicomp/ont-map/issues/4)
    @Ignore
    public void runInfr() {
        long n = 12;
        OntGraphModel s = createSourceModel(n);
        OntGraphModel t = createTargetModel();
        MapModel m = composeSimplestMapping(Managers.createMapManager(), s, t);
        TestUtils.debug(m);
        m.runInference(s.getGraph(), t.getGraph());
        Assert.assertEquals(n, t.namedIndividuals().count());
    }

    @Test
    public void testFilterMappingInference() {
        OntGraphModel s = createSourceModel(12);
        OntGraphModel t = createTargetModel();
        MapModel m = composeIfMapping(Managers.createMapManager(), s, t);
        TestUtils.debug(m);
        m.runInference(s.getGraph(), t.getGraph());
        Assert.assertEquals(4, t.individuals().count());
    }

    public static OntGraphModel createTargetModel() {
        LOGGER.debug("Create the target model.");
        String uri = "http://target.avicomp.ru";
        String ns = uri + "#";
        OntGraphModel res = OntModelFactory.createModel().setNsPrefixes(OntModelFactory.STANDARD);
        res.setID(uri);
        res.createOntEntity(OntClass.class, ns + "ClassTarget");
        return res;
    }

    public static OntGraphModel createSourceModel(long num) {
        LOGGER.debug("Create the source model with {} individuals", num);
        String uri = "http://source.avicomp.ru";
        String ns = uri + "#";
        OntGraphModel res = OntModelFactory.createModel().setNsPrefixes(OntModelFactory.STANDARD);
        res.setID(uri);
        OntClass clazz = res.createOntEntity(OntClass.class, ns + "ClassSource");
        for (long i = 1; i < num + 1; i++) {
            clazz.createIndividual(ns + "Individual-" + i);
        }
        return res;
    }

    public static MapModel composeSimplestMapping(MapManager manager, OntGraphModel source, OntGraphModel target) {
        LOGGER.debug("Compose the (spin) mapping.");
        OntClass sourceClass = source.classes().findFirst().orElseThrow(AssertionError::new);
        OntClass targetClass = target.classes().findFirst().orElseThrow(AssertionError::new);
        MapModel res = manager.createMapModel();
        MapFunction.Builder self = manager.getFunction(SPINMAPL.self).create();
        res.createContext(sourceClass, targetClass).addClassBridge(self.build());
        return res;
    }

    public static MapModel composeIfMapping(MapManager manager, OntGraphModel source, OntGraphModel target) {
        LOGGER.debug("Compose the (spin) mapping.");
        OntClass sourceClass = source.classes().findFirst().orElseThrow(AssertionError::new);
        OntClass targetClass = target.classes().findFirst().orElseThrow(AssertionError::new);

        MapModel res = manager.createMapModel();
        res.asGraphModel().getID().addComment("WARNING: " +
                "this mapping may not work in Composer (at least it does not work for ver. 5.5.2)\n" +
                "The reason is in variable ?this which is used by MagicFunction avc:currentIndividual");

        MapFunction.Builder self = manager.getFunction(SPINMAPL.self).create();
        MapFunction cur = manager.getFunction(AVC.currentIndividual);
        MapFunction loc = manager.getFunction(ARQ.resource("localname"));
        MapFunction contains = manager.getFunction(SP.resource("contains"));
        MapFunction.Builder filter = contains.create()
                .addFunction(SP.arg1, loc.create().addFunction(SP.arg1, cur.create().build()))
                .addLiteral(SP.arg2, "-1");
        res.createContext(sourceClass, targetClass).addClassBridge(filter.build(), self.build());
        return res;
    }

    @Override
    public MapModel assembleMapping(MapManager manager, OntGraphModel src, OntGraphModel dst) {
        return composeIfMapping(manager, src, dst);
    }

    @Override
    public OntGraphModel assembleSource() {
        return createSourceModel(12);
    }

    @Override
    public OntGraphModel assembleTarget() {
        return createTargetModel();
    }
}

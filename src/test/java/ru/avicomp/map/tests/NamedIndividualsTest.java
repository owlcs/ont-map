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

package ru.avicomp.map.tests;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.vocabulary.RDF;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.MapContext;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.MapModel;
import ru.avicomp.map.spin.MapConfigImpl;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.vocabulary.OWL;

/**
 * Created by @ssz on 10.06.2019.
 */
@RunWith(Parameterized.class)
public class NamedIndividualsTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(NamedIndividualsTest.class);
    private static final MapConfigImpl CONFIG = MapConfigImpl.INSTANCE.setGenerateNamedIndividuals(true);

    private final TD data;

    public NamedIndividualsTest(TD data) {
        this.data = data;
    }

    @Parameterized.Parameters(name = "{0}")
    public static TD[] data() {
        return TD.values();
    }

    @SuppressWarnings("SameParameterValue")
    private static void validateNamedIndividuals(String log, OntGraphModel m, int expected) {
        Assert.assertEquals(expected, m.individuals().peek(x -> {
            LOGGER.debug("{} Individual {}", log, x);
            if (x.isURIResource()) {
                Assert.assertTrue(x.hasProperty(RDF.type, OWL.NamedIndividual));
            } else {
                Assert.assertFalse(x.hasProperty(RDF.type, OWL.NamedIndividual));
            }
        }).count());
    }

    @Test
    public void testGeneratingNamedIndividuals() {
        MapManager manager = TestUtils.withConfig(CONFIG.setOptimizations(TD.WITH_OPT == data));

        String dom = "http://ex.com/";
        String src_uri = dom + "src";
        String src_ns = src_uri + "#";
        OntGraphModel src = OntModelFactory.createModel()
                .setNsPrefixes(OntModelFactory.STANDARD).setNsPrefix("src", src_ns);
        src.setID(src_uri);
        OntClass c1 = src.createOntClass(src_ns + "CN01");
        c1.createIndividual(src_ns + "aN01");
        c1.createIndividual(src_ns + "aN02");
        c1.createIndividual();

        String dst_uri = dom + "dst";
        String dst_ns = dst_uri + "#";
        OntGraphModel dst = OntModelFactory.createModel()
                .setNsPrefixes(OntModelFactory.STANDARD).setNsPrefix("dst", dst_ns);
        dst.setID(dst_uri);
        OntClass c2 = dst.createOntClass(dst_ns + "CN02");

        MapModel map = manager.createMapModel();
        map.asGraphModel().setID(dom + "map01");
        MapContext context = map.createContext(c1, c2, manager.getFunction(SPINMAPL.self).create());
        TestUtils.debug(map);
        Model base = map.asGraphModel().getBaseModel();
        Assert.assertTrue(base.contains(AVC.self, RDF.type, SPINMAP.TargetFunction));
        Assert.assertFalse(base.containsResource(AVC.UUID));
        Assert.assertEquals(2, base.listStatements(null, RDF.type, SPINMAP.Context).toList().size());
        Assert.assertEquals(2, base.listResourcesWithProperty(SPINMAP.rule).toList().size());
        Assert.assertEquals(2, base.listResourcesWithProperty(SPINMAP.context).toList().size());

        OntGraphModel dst1 = TestUtils.forSchema(dst);
        map.runInference(src.getBaseGraph(), dst1.getGraph());
        TestUtils.debug(dst1);

        validateNamedIndividuals("1)", dst1, 3);

        // change target function:
        context.addClassBridge(manager.getFunction(AVC.UUID).create().build());
        TestUtils.debug(map);
        base = map.asGraphModel().getBaseModel();
        Assert.assertFalse(base.containsResource(AVC.self));
        Assert.assertTrue(base.contains(AVC.UUID, RDF.type, SPINMAP.TargetFunction));
        Assert.assertEquals(2, base.listStatements(null, RDF.type, SPINMAP.Context).toList().size());
        Assert.assertEquals(2, base.listResourcesWithProperty(SPINMAP.rule).toList().size());
        Assert.assertEquals(2, base.listResourcesWithProperty(SPINMAP.context).toList().size());

        OntGraphModel dst2 = TestUtils.forSchema(dst);
        map.runInference(src.getBaseGraph(), dst2.getGraph());
        TestUtils.debug(dst2);

        validateNamedIndividuals("2)", dst2, 3);
    }

    enum TD {
        WITHOUT_OPT,
        WITH_OPT,
    }

}
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

package com.github.owlcs.map.tests;

import com.github.owlcs.map.MapContext;
import com.github.owlcs.map.MapManager;
import com.github.owlcs.map.MapModel;
import com.github.owlcs.map.spin.MapConfig;
import com.github.owlcs.map.spin.MapConfigImpl;
import com.github.owlcs.map.spin.vocabulary.AVC;
import com.github.owlcs.map.spin.vocabulary.SPINMAPL;
import com.github.owlcs.map.utils.TestUtils;
import com.github.owlcs.ontapi.jena.OntModelFactory;
import com.github.owlcs.ontapi.jena.model.OntClass;
import com.github.owlcs.ontapi.jena.model.OntModel;
import com.github.owlcs.ontapi.jena.vocabulary.OWL;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.vocabulary.RDF;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SPINMAP;

/**
 * Created by @ssz on 10.06.2019.
 */
@RunWith(Parameterized.class)
public class InternalConfigTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(InternalConfigTest.class);

    private final TD data;

    public InternalConfigTest(TD data) {
        this.data = data;
    }

    @Parameterized.Parameters(name = "{0}")
    public static TD[] data() {
        return TD.values();
    }

    @SuppressWarnings("SameParameterValue")
    private void validateNamedIndividuals(String log, OntModel m, int expected) {
        Assert.assertEquals(expected, m.individuals().peek(x -> {
            LOGGER.debug("{} Individual {}", log, x);
            if (x.isURIResource()) {
                Assert.assertEquals(data.withNamedIndividuals,
                        x.hasProperty(RDF.type, OWL.NamedIndividual));
            } else {
                Assert.assertFalse(x.hasProperty(RDF.type, OWL.NamedIndividual));
            }
        }).count());
    }

    @Test
    public void testGeneratingNamedIndividuals() {
        MapManager manager = TestUtils.withConfig(data.createConfig());

        String dom = "http://ex.com/";
        String src_uri = dom + "src";
        String src_ns = src_uri + "#";
        OntModel src = OntModelFactory.createModel()
                .setNsPrefixes(OntModelFactory.STANDARD).setNsPrefix("src", src_ns);
        src.setID(src_uri);
        OntClass c1 = src.createOntClass(src_ns + "CN01");
        c1.createIndividual(src_ns + "aN01");
        c1.createIndividual(src_ns + "aN02");
        c1.createIndividual();

        String dst_uri = dom + "dst";
        String dst_ns = dst_uri + "#";
        OntModel dst = OntModelFactory.createModel()
                .setNsPrefixes(OntModelFactory.STANDARD).setNsPrefix("dst", dst_ns);
        dst.setID(dst_uri);
        OntClass c2 = dst.createOntClass(dst_ns + "CN02");

        MapModel map = manager.createMapModel();
        map.asGraphModel().setID(dom + "map01");
        MapContext context = map.createContext(c1, c2, manager.getFunction(SPINMAPL.self).create());
        TestUtils.debug(map);
        Model base = map.asGraphModel().getBaseModel();
        int expected;
        Assert.assertFalse(base.containsResource(AVC.UUID));
        Assert.assertEquals(1, base.listResourcesWithProperty(RDF.type, SPINMAPL.self).toList().size());
        if (data.withNamedIndividuals) {
            expected = 2;
            Assert.assertTrue(base.contains(AVC.self, RDF.type, SPINMAP.TargetFunction));
        } else {
            expected = 1;
            Assert.assertFalse(base.containsResource(AVC.self));
        }
        Assert.assertEquals(expected, base.listStatements(null, RDF.type, SPINMAP.Context).toList().size());
        Assert.assertEquals(expected, base.listResourcesWithProperty(SPINMAP.rule).toList().size());
        Assert.assertEquals(expected, base.listResourcesWithProperty(SPINMAP.context).toList().size());

        OntModel dst1 = TestUtils.forSchema(dst);
        map.runInference(src.getBaseGraph(), dst1.getGraph());
        TestUtils.debug(dst1);

        validateNamedIndividuals("1)", dst1, 3);

        // change target function:
        context.addClassBridge(manager.getFunction(AVC.UUID).create().build());
        TestUtils.debug(map);
        base = map.asGraphModel().getBaseModel();
        Assert.assertFalse(base.containsResource(AVC.self));
        Assert.assertTrue(base.contains(AVC.UUID, RDF.type, SPINMAP.TargetFunction));
        Assert.assertEquals(expected, base.listStatements(null, RDF.type, SPINMAP.Context).toList().size());
        Assert.assertEquals(expected, base.listResourcesWithProperty(SPINMAP.rule).toList().size());
        Assert.assertEquals(expected, base.listResourcesWithProperty(SPINMAP.context).toList().size());

        OntModel dst2 = TestUtils.forSchema(dst);
        map.runInference(src.getBaseGraph(), dst2.getGraph());
        TestUtils.debug(dst2);

        validateNamedIndividuals("2)", dst2, 3);
    }

    @Test
    public void testConfigSettings() {
        MapManager m = TestUtils.withConfig(data.createConfig());
        MapConfig conf = TestUtils.getMappingConfiguration(m);
        Assert.assertEquals(data.withNamedIndividuals, conf.generateNamedIndividuals());
        Assert.assertEquals(data.withAllOptimization, conf.useAllOptimizations());
    }

    enum TD {
        NO_OPTIMIZATION_NO_NAMED_INDIVIDUALS(false, false),
        NO_OPTIMIZATION_WITH_NAMED_INDIVIDUALS(false, true),
        WITH_OPTIMIZATION_NO_NAMED_INDIVIDUALS(true, false),
        WITH_OPTIMIZATION_WITH_NAMED_INDIVIDUALS(true, true),
        ;
        private final boolean withAllOptimization;
        private final boolean withNamedIndividuals;

        TD(boolean withOptimization, boolean withNamedIndividuals) {
            this.withAllOptimization = withOptimization;
            this.withNamedIndividuals = withNamedIndividuals;
        }

        public MapConfigImpl createConfig() {
            return MapConfigImpl.INSTANCE
                    .setAllOptimizations(withAllOptimization)
                    .setGenerateNamedIndividuals(withNamedIndividuals);
        }
    }

}

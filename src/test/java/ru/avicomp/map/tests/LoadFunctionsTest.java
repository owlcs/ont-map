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

package ru.avicomp.map.tests;

import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.sparql.function.FunctionRegistry;
import org.apache.jena.sparql.pfunction.PropertyFunctionRegistry;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPL;
import ru.avicomp.map.Managers;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.MapModel;
import ru.avicomp.map.spin.QueryHelper;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by @szuev on 15.06.2018.
 */
public class LoadFunctionsTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadFunctionsTest.class);

    @Test
    public void testLoadSingleFunction() {
        MapManager manager = Managers.createMapManager();
        long c1 = manager.functions().count();
        LOGGER.debug("Count before adding model: {}", c1);
        long g1 = Iter.count(FunctionRegistry.get().keys());
        long g2 = Iter.count(PropertyFunctionRegistry.get().keys());
        Assert.assertEquals(0, manager.getGraph().size());

        OntGraphModel m1 = makeSingleFunctionModel();
        TestUtils.debug(m1);

        MapModel m2 = manager.asMapModel(m1);
        Assert.assertEquals(0, m2.rules().count());
        long c2 = manager.functions().count();
        LOGGER.info("Count after adding model: {}", c2);
        Assert.assertEquals(c1 + 1, c2);
        Assert.assertEquals(1, manager.functions().filter(MapFunction::isUserDefined).count());
        TestUtils.debug(ModelFactory.createModelForGraph(manager.getGraph()));
        Assert.assertEquals(50, manager.getGraph().size());

        long c3 = Managers.createMapManager().functions().count();
        LOGGER.debug("Count in new manager: {}", c3);
        Assert.assertEquals(c1, c3);

        LOGGER.debug("Global counts: {}/{}", g1, g2);
        Assert.assertEquals("Changes in PropertyFunctionRegistry", g2, Iter.count(PropertyFunctionRegistry.get().keys()));
        Assert.assertEquals("Changes in FunctionRegistry", g1, Iter.count(FunctionRegistry.get().keys()));
    }

    @Test
    public void testLoadAndInference() {
        String uri = "http://test.com/some-function1";

        LoadMapTestData data = new LoadMapTestData(uri, "--suff");
        MapManager manager = Managers.createMapManager();
        long c1 = manager.functions().count();
        LOGGER.debug("Count before adding model: {}", c1);

        MapModel m = data.assembleMapping(manager, null, null);
        TestUtils.debug(m);

        Assert.assertEquals(1, manager.getFunction(uri).args().count());
        long c2 = manager.functions().count();
        Assert.assertEquals(c1 + 1, c2);
        Assert.assertEquals(1, manager.functions().filter(MapFunction::isUserDefined).count());

        data.validateMapping(m);

        OntGraphModel res = data.assembleTarget();
        m.runInference(m.asGraphModel().getGraph(), res.getGraph());
        data.validateResult(res);
    }

    @Test
    public void testConcurrentLoading() throws ExecutionException, InterruptedException {
        simpleTestInference(Managers.createMapManager(), "A");
        simpleTestInference(Managers.createMapManager(), "B");

        int threadsNum = 8;
        int iterNum = 3;
        ExecutorService service = Executors.newFixedThreadPool(threadsNum);
        Set<Future<?>> res = IntStream.rangeClosed(0, threadsNum)
                .mapToObj(i -> service.submit(() -> IntStream.rangeClosed(0, iterNum)
                        .forEach(j -> {
                            LOGGER.debug("Test thread #{}, iter #{}", i, j);
                            simpleTestInference(Managers.createMapManager(), "Test#" + i + "-" + j);
                        }))).collect(Collectors.toSet());
        service.shutdown();
        for (Future<?> f : res) {
            f.get();
        }
    }

    private void simpleTestInference(MapManager manager, String suffix) {
        final String uri = "http://test.com/some-function2";
        LoadMapTestData data = new LoadMapTestData(uri, suffix);
        MapModel map = data.assembleMapping(manager, null, null);
        OntGraphModel res = data.assembleTarget();
        map.runInference(map.asGraphModel().getGraph(), res.getGraph());
        data.validateResult(res);
    }

    private static OntGraphModel makeSingleFunctionModel() {
        String q = "SELECT (xsd:string(?untyped) AS ?result)\n" +
                "WHERE {\n" +
                "    BIND (CONCAT(xsd:string(?arg1), ?separator, xsd:string(?arg2), ?separator, xsd:string(?arg3)) AS ?untyped) .\n" +
                "}";
        String uri = "http://test.func.com";
        OntGraphModel m = TestUtils.createMapModel(uri);

        m.createResource(uri + "#concatWithSeparator_3")
                .addProperty(RDF.type, SPIN.Function)
                .addProperty(RDFS.subClassOf, SPL.StringFunctions)
                .addProperty(SPIN.returnType, XSD.xstring)
                .addProperty(RDFS.comment, "Test func")
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg1)
                        .addProperty(SPL.valueType, XSD.xstring))
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg2)
                        .addProperty(SPL.valueType, XSD.xstring))
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg2)
                        .addProperty(SPL.valueType, XSD.xstring))
                .addProperty(SPIN.body, QueryHelper.parseQuery(q, m));
        return m;
    }


}

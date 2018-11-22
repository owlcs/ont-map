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

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.function.FunctionFactory;
import org.apache.jena.sparql.function.FunctionRegistry;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;
import ru.avicomp.map.Managers;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.spin.MapManagerImpl;
import ru.avicomp.map.spin.SystemModels;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.utils.Graphs;
import ru.avicomp.ontapi.transforms.vocabulary.AVC;

import java.util.*;
import java.util.stream.Stream;

/**
 * Created by @szuev on 05.04.2018.
 */
public class SystemModelsTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(SystemModelsTest.class);

    @Test
    public void testInit() {
        Map<String, Graph> graphs = SystemModels.graphs();
        Assert.assertEquals(15, graphs.size());
        graphs.forEach((expected, g) -> Assert.assertEquals(expected, Graphs.getURI(g)));
        OntModelFactory.init();
        Assert.assertSame(graphs, SystemModels.graphs());
        Model lib = ((MapManagerImpl) Managers.createMapManager()).getLibrary();
        String tree = Graphs.importsTreeAsString(lib.getGraph());
        LOGGER.debug("Graphs tree:\n{}", tree);
        Assert.assertEquals(33, tree.split("\n").length);
        Set<String> imports = Graphs.getImports(lib.getGraph());
        LOGGER.debug("Imports: {}", imports);
        Assert.assertEquals(11, imports.size());
        Assert.assertFalse(imports.contains(AVC.URI));
    }

    @Test
    public void testListFunctions() { // todo: for debug right now
        MapManager manager = Managers.createMapManager();
        manager.functions()
                .sorted(Comparator.comparing((MapFunction f) -> !f.isTarget()).thenComparing(MapFunction::type).thenComparing(MapFunction::name))
                .forEach(System.out::println);
        System.out.println("-----");
        System.out.println(manager.functions().count());
        MapFunction f = manager.getFunction(SP.resource("UUID").getURI());
        Assert.assertNotNull(f);
        System.out.println(f.getComment());
        System.out.println(f.getComment("ru"));
        System.out.println(f.getLabel());
        System.out.println(f.getLabel("ru"));
        System.out.println("-----");
        PrefixMapping pm = manager.prefixes();
        pm.getNsPrefixMap().forEach((p, u) -> System.out.println(p + "=> " + u));
        manager.functions()
                .flatMap(x -> Stream.concat(Stream.of(x.type()), x.args().map(MapFunction.Arg::type)))
                .map(u -> manager.prefixes().shortForm(u))
                .sorted()
                .distinct()
                .forEach(System.out::println);

        System.out.println("----------------------------");
        FunctionRegistry fr = FunctionRegistry.get();
        Map<String, Set<String>> all = new TreeMap<>();
        fr.keys().forEachRemaining(key -> {
            String v = get(fr, key);
            all.computeIfAbsent(v, k -> new TreeSet<>()).add(pm.shortForm(key));
        });
        all.forEach((k, v) -> System.out.println("[" + k + "] === " + v));
    }

    private static String get(FunctionRegistry fr, String key) {
        FunctionFactory factory = fr.get(key);
        if (factory.toString().startsWith("org.apache.jena.sparql.function.FunctionFactoryAuto@")) {
            return factory.create(null).getClass().getName();
        }
        return factory.getClass().toString();
    }


}

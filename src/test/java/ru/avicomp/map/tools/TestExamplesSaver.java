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

package ru.avicomp.map.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.map.Managers;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.tests.*;
import ru.avicomp.ontapi.jena.model.OntGraphModel;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;

/**
 * An utility class to save tests examples to some directory in order to test or show in Composer.
 * For developing and demonstration.
 * Not a test or part of system: can be removed.
 * Created by @szuev on 24.04.2018.
 */
public class TestExamplesSaver {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestExamplesSaver.class);

    public static void main(String... args) throws IOException {
        Path dir = Paths.get("out");
        if (!Files.exists(dir)) {
            Files.createDirectory(dir);
        }
        MapManager manager = Managers.createMapManager();
        Collection<AbstractMapTest> mapTests = Arrays.asList(
                new UUIDMapTest(),              //1
                new BuildURIMapTest(),
                new NestedFuncMapTest(),
                new ConditionalMapTest(),
                new RelatedContextMapTest(),    //5
                new FilterDefaultMapTest(),
                new FilterIndividualsMapTest(), //7
                new SplitMapTest(),
                new IntersectConcatMapTest(),
                new GroupConcatTest(),          //10
                new MathOpsMapTest(),
                new MultiContextMapTest(),
                new PropertyChainMapTest(),
                new VarArgMapTest(),            //14
                new LoadMapTestData(),
                new SelfMapTest(),              //16
                new MathGeoMapTest()
        );

        for (AbstractMapTest mapTest : mapTests) {
            OntGraphModel src = mapTest.assembleSource();
            OntGraphModel dst = mapTest.assembleTarget();
            OntGraphModel map = mapTest.assembleMapping(manager, src, dst).asGraphModel();

            String mappingFile;
            if (map.getID().isAnon()) {
                mappingFile = mapTest.getClass().getSimpleName() + "-map";
            } else {
                mappingFile = makeTurtleFileName(map.getID().getURI());
            }
            saveTurtle(dir.resolve(mappingFile + ".ttl"), map);

            if (!src.isEmpty()) {
                Path f = dir.resolve(makeTurtleFileName(src.getID().getURI()) + ".ttl");
                saveTurtle(f, src);
            }
            if (!dst.isEmpty()) {
                Path f = dir.resolve(makeTurtleFileName(dst.getID().getURI()) + ".ttl");
                saveTurtle(f, dst);
            }
        }
    }

    private static void saveTurtle(Path file, OntGraphModel m) throws IOException {
        LOGGER.info("Save ontology <{}> to file <{}>", m.getID().getURI(), file);
        try (Writer out = Files.newBufferedWriter(file)) {
            m.write(out, "ttl");
        }
    }

    private static String makeTurtleFileName(String url) {
        return url.replaceFirst("^\\w+://[^/]+/+(.+)$", "$1")
                .replaceFirst("^\\w+://", "")
                .replaceAll("/+", "/")
                .replaceFirst("^/+", "")
                .replaceFirst("/+$", "")
                .replace("#", "-")
                .replace("/", "-");
    }

}

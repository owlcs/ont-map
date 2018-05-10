package ru.avicomp.map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.map.tests.*;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.impl.conf.OntModelConfig;
import ru.avicomp.ontapi.jena.model.OntGraphModel;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;

/**
 * An utility class to save tests examples to some directory in order to test in Composer.
 * For developing and demonstration.
 * Can be removed.
 * Created by @szuev on 24.04.2018.
 */
public class TestExamplesSaver {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestExamplesSaver.class);

    public static void main(String... args) throws IOException {
        Path dir = Paths.get("out");
        if (!Files.exists(dir)) {
            Files.createDirectory(dir);
        }
        MapManager manager = Managers.getMapManager();
        Collection<AbstractMapTest> mapTests = Arrays.asList(
                new UUIDMapTest(),
                new BuildURIMapTest(),
                new NestedFuncMapTest(),
                new ConditionalMapTest(),
                new RelatedContextMapTest(),
                new FilterDefaultMapTest(),
                new FilterIndividualsMapTest()
        );

        for (AbstractMapTest mapTest : mapTests) {
            OntGraphModel src = mapTest.assembleSource();
            OntGraphModel dst = mapTest.assembleTarget();
            OntGraphModel map = OntModelFactory.createModel(mapTest.assembleMapping(manager, src, dst).getGraph(),
                    OntModelConfig.ONT_PERSONALITY_LAX);
            Path srcFile = dir.resolve(getURILastPart(mapTest.getDataNameSpace()) + "-src.ttl");
            Path dstFile = dir.resolve(getURILastPart(mapTest.getDataNameSpace()) + "-dst.ttl");
            Path mapFile = dir.resolve(getURILastPart(mapTest.getMapNameSpace()) + "-map.ttl");
            saveTurtle(srcFile, src);
            saveTurtle(dstFile, dst);
            saveTurtle(mapFile, map);
        }
    }

    private static void saveTurtle(Path file, OntGraphModel m) throws IOException {
        LOGGER.info("Save ontology <{}> to file <{}>", m.getID().getURI(), file);
        try (Writer out = Files.newBufferedWriter(file)) {
            m.write(out, "ttl");
        }
    }

    private static String getURILastPart(String uri) {
        return uri.replaceFirst(".+/([^/]+)/*$", "$1");
    }
}

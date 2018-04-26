package ru.avicomp.map;

import org.apache.jena.rdf.model.Model;
import ru.avicomp.map.tests.AbstractMapTest;
import ru.avicomp.map.tests.BuildURIMapTest;
import ru.avicomp.map.tests.NestedMapTest;
import ru.avicomp.map.tests.UUIDMapTest;
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

    public static void main(String... args) throws IOException {
        Path dir = Paths.get("out");
        if (!Files.exists(dir)) {
            Files.createDirectory(dir);
        }
        MapManager manager = Managers.getMapManager();
        Collection<AbstractMapTest> mapTests = Arrays.asList(new UUIDMapTest(), new BuildURIMapTest(), new NestedMapTest());
        for (AbstractMapTest mapTest : mapTests) {
            String file = mapTest.getClass().getSimpleName() + "-%s.ttl";
            OntGraphModel src = mapTest.assembleSource();
            OntGraphModel dst = mapTest.assembleTarget();
            OntGraphModel map = OntModelFactory.createModel(mapTest.assembleMapping(manager, src, dst).getGraph(),
                    OntModelConfig.ONT_PERSONALITY_LAX);
            save(dir.resolve(String.format(file, "src")), src);
            save(dir.resolve(String.format(file, "dst")), dst);
            save(dir.resolve(String.format(file, "map")), map);
        }
    }

    private static void save(Path file, Model m) throws IOException {
        System.out.println("Save to file " + file);
        try (Writer out = Files.newBufferedWriter(file)) {
            m.write(out, "ttl");
        }
    }
}

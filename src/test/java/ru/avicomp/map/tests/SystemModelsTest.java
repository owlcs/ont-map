package ru.avicomp.map.tests;

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.map.Managers;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.spin.MapManagerImpl;
import ru.avicomp.map.spin.SystemModels;
import ru.avicomp.map.spin.vocabulary.SP;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.utils.Graphs;
import ru.avicomp.ontapi.transforms.vocabulary.AVC;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;

/**
 * Created by @szuev on 05.04.2018.
 */
public class SystemModelsTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(SystemModelsTest.class);

    @Test
    public void testInit() {
        Map<String, Graph> graphs = SystemModels.graphs();
        Assert.assertEquals(11, graphs.size());
        graphs.forEach((expected, g) -> Assert.assertEquals(expected, Graphs.getURI(g)));
        OntModelFactory.init();
        Assert.assertSame(graphs, SystemModels.graphs());
        Model lib = ((MapManagerImpl) Managers.getMapManager()).library();
        String tree = Graphs.importsTreeAsString(lib.getGraph());
        LOGGER.debug("Graphs tree:\n{}", tree);
        Assert.assertEquals(28, tree.split("\n").length);
        Set<String> imports = Graphs.getImports(lib.getGraph());
        LOGGER.debug("Imports: {}", imports);
        Assert.assertEquals(9, imports.size());
        Assert.assertFalse(imports.contains(AVC.URI));
    }

    @Test
    public void testListFunctions() {
        Managers.getMapManager().functions()
                .sorted(Comparator.comparing((MapFunction f) -> !f.isTarget()).thenComparing(MapFunction::returnType).thenComparing(MapFunction::name))
                .forEach(System.out::println);
        System.out.println(Managers.getMapManager().functions().count());
        MapFunction f = Managers.getMapManager().getFunction(SP.resource("UUID").getURI());
        Assert.assertNotNull(f);
        System.out.println(f.getComment());
        System.out.println(f.getComment("ru"));
        System.out.println(f.getLabel());
        System.out.println(f.getLabel("ru"));
    }

}

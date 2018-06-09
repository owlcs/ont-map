package ru.avicomp.map.tests;

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.shared.PrefixMapping;
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

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Created by @szuev on 05.04.2018.
 */
public class SystemModelsTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(SystemModelsTest.class);

    @Test
    public void testInit() {
        Map<String, Graph> graphs = SystemModels.graphs();
        Assert.assertEquals(12, graphs.size());
        graphs.forEach((expected, g) -> Assert.assertEquals(expected, Graphs.getURI(g)));
        OntModelFactory.init();
        Assert.assertSame(graphs, SystemModels.graphs());
        Model lib = ((MapManagerImpl) Managers.getMapManager()).getLibrary();
        String tree = Graphs.importsTreeAsString(lib.getGraph());
        LOGGER.debug("Graphs tree:\n{}", tree);
        Assert.assertEquals(30, tree.split("\n").length);
        Set<String> imports = Graphs.getImports(lib.getGraph());
        LOGGER.debug("Imports: {}", imports);
        Assert.assertEquals(10, imports.size());
        Assert.assertFalse(imports.contains(AVC.URI));
    }

    @Test
    public void testListFunctions() { // todo: for debug right now
        MapManager manager = Managers.getMapManager();
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
    }

}

package ru.avicomp.map.tests;

import org.apache.jena.graph.Graph;
import org.junit.Assert;
import org.junit.Test;
import ru.avicomp.map.Managers;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.spin.SystemModels;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.utils.Graphs;

import java.util.Comparator;
import java.util.Map;

/**
 * Created by @szuev on 05.04.2018.
 */
public class SystemModelsTest {

    @Test
    public void testInit() {
        Map<String, Graph> graphs = SystemModels.graphs();
        graphs.forEach((expected, g) -> Assert.assertEquals(expected, Graphs.getURI(g)));
        OntModelFactory.init();
        Assert.assertSame(graphs, SystemModels.graphs());
    }

    @Test
    public void testListFunctions() {
        Managers.getMapManager().functions()
                .sorted(Comparator.comparing((MapFunction f) -> !f.isTarget()).thenComparing(MapFunction::returnType).thenComparing(MapFunction::name))
                .forEach(System.out::println);
        System.out.println(Managers.getMapManager().functions().count());
    }

}

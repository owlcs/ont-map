package ru.avicomp.map.tests;

import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.junit.Assert;
import org.junit.Test;
import org.topbraid.spin.model.Argument;
import org.topbraid.spin.model.Function;
import org.topbraid.spin.model.Module;
import ru.avicomp.map.spin.MapFunctionImpl;
import ru.avicomp.map.spin.SystemModels;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.utils.Graphs;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

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
        Model m = MapFunctionImpl.createLibraryModel();
        System.out.println(Graphs.importsTreeAsString(m.getGraph()));

        Set<Function> functions = Iter.asStream(m.listSubjectsWithProperty(RDF.type))
                .filter(s -> s.canAs(Function.class))
                .map(s -> s.as(Function.class))
                .collect(Collectors.toSet());
        System.out.println(functions.size());
        functions.forEach(f -> {
            String returnType = f.getReturnType() != null ? m.shortForm(f.getReturnType().getURI()) : "void";
            String name = m.shortForm(f.getURI());
            Map<String, String> args = getArgumentsMap(f).entrySet()
                    .stream()
                    .collect(Collectors.toMap(e -> m.shortForm(e.getKey().getURI()),
                            e -> {
                                Resource t = e.getValue().getValueType();
                                return t == null ? "?" : m.shortForm(t.getURI());
                            }, throwingMerger(), LinkedHashMap::new));
            System.out.println(returnType + " [" + name + "](" + args + ")");
        });
    }

    public static Map<Property, Argument> getArgumentsMap(Module m) {
        Map<Property, Argument> res = new LinkedHashMap<>();
        for (Argument argument : m.getArguments(true)) {
            Property property = argument.getPredicate();
            if (property == null) continue;
            res.put(property, argument);
        }
        return res;
    }

    private static <T> BinaryOperator<T> throwingMerger() {
        return (u, v) -> {
            throw new IllegalStateException(String.format("Duplicate key %s", u));
        };
    }
}

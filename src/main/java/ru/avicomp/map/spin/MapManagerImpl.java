package ru.avicomp.map.spin;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.rdf.model.*;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.RDF;
import org.topbraid.spin.model.Function;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapJenaException;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.spin.model.TargetFunction;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.utils.Graphs;
import ru.avicomp.ontapi.jena.vocabulary.OWL;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A spin based implementation of {@link MapManager}.
 * <p>
 * Created by @szuev on 06.04.2018.
 */
public class MapManagerImpl implements MapManager {

    private final Model library;
    private Map<String, MapFunction> spinFunctions;

    public MapManagerImpl() {
        this.library = createLibraryModel();
        this.spinFunctions = loadFunctions(library);
    }

    public static Model createLibraryModel() {
        UnionGraph g = Graphs.toUnion(SystemModels.graphs().get(SystemModels.Resources.SPINMAPL.getURI()), SystemModels.graphs().values());
        // note: this graph is not included to the owl:imports
        g.addGraph(SystemModels.graphs().get(SystemModels.Resources.AVC.getURI()));
        return ModelFactory.createModel(g);
    }

    public static Map<String, MapFunction> loadFunctions(Model model) {
        return Iter.asStream(model.listSubjectsWithProperty(RDF.type))
                .filter(s -> s.canAs(Function.class) || s.canAs(TargetFunction.class))
                .map(s -> s.as(Function.class))
                // skip private:
                .filter(f -> !f.isPrivate())
                // skip abstract:
                .filter(f -> !f.isAbstract())
                // skip deprecated:
                .filter(f -> !f.hasProperty(RDF.type, OWL.DeprecatedClass))
                // skip hidden:
                .filter(f -> !f.hasProperty(AVC.hidden))
                .map(MapFunctionImpl::new)
                .collect(Collectors.toMap(MapFunctionImpl::name, java.util.function.Function.identity()));
    }

    @Override
    public Stream<MapFunction> functions() {
        return spinFunctions.values().stream();
    }

    @Override
    public PrefixMapping prefixes() {
        return library;
    }

    public Model library() {
        return library;
    }

    @Override
    public MapFunction getFunction(String name) throws MapJenaException {
        return MapJenaException.notNull(spinFunctions.get(name), "Can't find function " + name);
    }

    public static String getLangValue(Resource resource, Property predicate, String lang) {
        return Iter.asStream(resource.listProperties(predicate))
                .map(Statement::getObject)
                .filter(RDFNode::isLiteral)
                .map(RDFNode::asLiteral)
                .filter(l -> filterByLang(l, lang))
                .map(Literal::getString)
                .collect(Collectors.joining("\n"));
    }

    private static boolean filterByLang(Literal literal, String lang) {
        String other = literal.getLanguage();
        if (StringUtils.isEmpty(lang))
            return StringUtils.isEmpty(other);
        return lang.trim().equalsIgnoreCase(other);
    }
}

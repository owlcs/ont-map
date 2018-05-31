package ru.avicomp.map.utils;

import org.apache.jena.graph.Factory;
import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shared.PrefixMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapModel;
import ru.avicomp.map.tests.ClassPropertiesTest;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.impl.conf.OntModelConfig;
import ru.avicomp.ontapi.jena.model.*;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Created by @szuev on 13.04.2018.
 */
@SuppressWarnings("WeakerAccess")
public class TestUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestUtils.class);

    public static String asString(Model m) {
        StringWriter s = new StringWriter();
        m.write(s, "ttl");
        return s.toString();
    }

    public static void debug(Model m) {
        LOGGER.debug("\n{}\n==========", TestUtils.asString(m));
    }

    public static void debug(MapModel m) {
        debug(m.asOntModel());
    }

    public static void debug(MapFunction.Call func, PrefixMapping pm) {
        LOGGER.debug("Function: {}", pm.shortForm(func.getFunction().name()));
        func.asMap().forEach((arg, o) -> {
            String val = o instanceof String ? (String) o : ((MapFunction.Call) o).getFunction().name();
            LOGGER.debug("Argument: {} => '{}'", pm.shortForm(arg.name()), pm.shortForm(val));
        });
    }

    /**
     * Finds owl-entity by type and local name.
     * Priority for entities with the namespace matching ontology iri.
     * TODO: move to ONT-API Models ?
     *
     * @param m         {@link OntGraphModel} for search in
     * @param type      Class
     * @param localName String local name, not null
     * @param <E>       subclass of {@link OntEntity}
     * @return OntEntity
     */
    public static <E extends OntEntity> E findOntEntity(OntGraphModel m, Class<E> type, String localName) {
        Comparator<String> uriComparator = uriComparator(m.getID().getURI());
        return m.ontEntities(type)
                .filter(s -> s.getLocalName().equals(localName))
                .min((r1, r2) -> uriComparator.compare(r1.getNameSpace(), r2.getNameSpace()))
                .orElseThrow(() -> new AssertionError("Can't find [" + type.getSimpleName() + "] <...#" + localName + ">"));
    }

    /**
     * To compare strings.
     * The strings that match the argument (@code first) have the highest priority, i.e. go first.
     *
     * @param first String
     * @return Comparator for Strings
     */
    public static Comparator<String> uriComparator(String first) {
        Comparator<String> res = Comparator.comparing(first::equals);
        return res.reversed().thenComparing(String::compareTo);
    }

    public static Stream<OntStatement> plainAssertions(OntIndividual i) { // todo: move to ONT-API (already done) ?
        return i.statements().filter(st -> !Objects.equals(st.getPredicate(), RDF.type));
    }

    public static String getStringValue(OntIndividual i, OntNDP p) {
        return i.statement(p).map(Statement::getObject).map(RDFNode::asLiteral).map(Literal::getString).orElseThrow(AssertionError::new);
    }

    public static Stream<OntStatement> plainAssertions(OntGraphModel m) {
        return m.ontObjects(OntPE.class).filter(RDFNode::isURIResource)
                .map(p -> p.as(Property.class))
                .flatMap(p -> m.statements(null, p, null));
    }

    public static OntGraphModel load(String file, Lang format) throws IOException {
        Graph g = Factory.createGraphMem();
        try (InputStream in = ClassPropertiesTest.class.getResourceAsStream(file)) {
            RDFDataMgr.read(g, in, null, format);
        }
        return OntModelFactory.createModel(g, OntModelConfig.ONT_PERSONALITY_LAX);
    }

    public static String toString(PrefixMapping pm, Statement s) {
        return String.format("@%s <%s> ::: '%s'",
                pm.shortForm(s.getSubject().getURI()),
                pm.shortForm(s.getPredicate().getURI()),
                s.getObject());
    }

}

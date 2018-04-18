package ru.avicomp.map.tests;

import org.apache.jena.graph.Factory;
import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.OWL;
import org.junit.Test;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.impl.OntObjectImpl;
import ru.avicomp.ontapi.jena.impl.conf.OntModelConfig;
import ru.avicomp.ontapi.jena.model.OntCE;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntOPE;
import ru.avicomp.ontapi.jena.model.OntPE;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Created by @szuev on 18.04.2018.
 */
public class ClassPropertiesTest {

    private OntGraphModel load(String file, Lang format) throws IOException {
        Graph g = Factory.createGraphMem();
        try (InputStream in = ClassPropertiesTest.class.getResourceAsStream(file)) {
            RDFDataMgr.read(g, in, null, format);
        }
        return OntModelFactory.createModel(g, OntModelConfig.ONT_PERSONALITY_LAX);
    }

    @Test
    public void testPizza() throws Exception {
        OntGraphModel m = load("/pizza.ttl", Lang.TURTLE);
        m.setNsPrefix("pizza", m.getID().getURI() + "#");
        m.removeNsPrefix("");
        print(m);
    }

    @Test
    public void testFoaf() throws Exception {
        OntGraphModel m = load("/foaf.rdf", Lang.RDFXML);
        m.setNsPrefix("foaf", "http://xmlns.com/foaf/0.1/");
        print(m);
    }

    @Test
    public void testGoodrelations() throws Exception {
        OntGraphModel m = load("/goodrelations.rdf", Lang.RDFXML);
        m.write(System.out, "ttl");
        System.out.println("=============\n\n");
        m.getOWLThing().subClassOf().forEach(System.out::println);
        print(m);
    }

    public void print(OntGraphModel m) {
        ClassPropertiesMap c = new ClassPropertiesMap(m);
        m.ontObjects(OntCE.class).forEach(ce -> {
            System.out.println("[" + ((OntObjectImpl) ce).getActualClass().getSimpleName() + "]" + m.shortForm(ce.asNode().toString()));
            c.apply(ce)
                    .sorted(Comparator.comparing(Resource::getURI))
                    .forEach(property -> System.out.println("\t" + m.shortForm(property.getURI())));
        });
    }

    public static class ClassPropertiesMap implements Function<OntCE, Stream<Property>> {
        private final OntGraphModel model;

        private ClassPropertiesMap(OntGraphModel model) {
            this.model = model;
        }

        private static Property toNamed(OntPE p) {
            return (p.isAnon() ? p.as(OntOPE.class).getInverseOf() : p).as(Property.class);
        }

        @Override
        public Stream<Property> apply(OntCE ce) {
            return collect(ce, new HashSet<>());
        }

        public Stream<Property> collect(OntCE ce, Set<OntCE> visited) {
            if (visited.contains(checkCE(ce))) {
                return Stream.empty();
            }
            visited.add(ce);
            if (Objects.equals(ce, OWL.Thing)) {
                return Stream.of(model.getRDFSLabel());
            }
            Stream<OntCE> superClasses = ce.isAnon() ? ce.subClassOf() : Stream.concat(ce.subClassOf(), Stream.of(model.getOWLThing()));
            Stream<OntCE> equivalentClasses = ce.equivalentClass();
            Stream<OntCE> unionClasses = model.ontObjects(OntCE.UnionOf.class)
                    .filter(c -> c.components().anyMatch(_c -> Objects.equals(_c, ce))).map(OntCE.class::cast);

            Stream<OntCE> classes = Stream.of(superClasses, equivalentClasses, unionClasses).flatMap(Function.identity());
            classes = classes.distinct().filter(c -> !Objects.equals(c, ce));

            Stream<Property> properties = ce.properties().map(ClassPropertiesMap::toNamed);
            if (ce instanceof OntCE.ONProperty) {
                Property p = toNamed(((OntCE.ONProperty) ce).getOnProperty());
                properties = Stream.concat(properties, Stream.of(p));
            }
            if (ce instanceof OntCE.ONProperties) {
                Stream<? extends OntPE> props = ((OntCE.ONProperties<? extends OntPE>) ce).onProperties();
                properties = Stream.concat(properties, props.map(ClassPropertiesMap::toNamed));
            }
            return Stream.concat(classes.flatMap(c -> collect(c, visited)), properties)
                    .distinct();
        }

        private OntCE checkCE(OntCE ce) {
            if (Objects.requireNonNull(ce, "Null ce").getModel() != model) {
                throw new IllegalArgumentException("Wrong ce: " + ce);
            }
            return ce;
        }
    }
}

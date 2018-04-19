package ru.avicomp.map.spin;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDFS;
import ru.avicomp.map.ClassPropertyMap;
import ru.avicomp.ontapi.jena.model.OntCE;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntPE;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A class-property mapping implementation based on the same rules as in Tobraid Composer Diagram.
 * <p>
 * Created by @szuev on 19.04.2018.
 */
public class ClassPropertyMapImpl implements ClassPropertyMap {

    // any named class expression in Topbraid Composer has a rdfs:label as attached property.
    public static final Set<Property> OWL_THING_PROPERTIES = Collections.singleton(RDFS.label);

    @Override
    public Stream<Property> properties(OntCE ce) {
        return collect(ce, new HashSet<>());
    }

    public Stream<Property> collect(OntCE ce, Set<OntCE> visited) {
        if (visited.contains(Objects.requireNonNull(ce, "Null ce"))) {
            return Stream.empty();
        }
        visited.add(ce);
        OntGraphModel model = ce.getModel();
        if (Objects.equals(ce, OWL.Thing)) {
            return OWL_THING_PROPERTIES.stream().peek(p -> p.inModel(model));
        }
        Stream<OntCE> superClasses = ce.isAnon() ? ce.subClassOf() :
                Stream.concat(ce.subClassOf(), Stream.of(model.getOWLThing()));
        Stream<OntCE> equivalentClasses = ce.equivalentClass();
        Stream<OntCE> unionClasses =
                model.ontObjects(OntCE.UnionOf.class).filter(c -> c.components().anyMatch(_c -> Objects.equals(_c, ce))).map(OntCE.class::cast);

        Stream<OntCE> classes = Stream.of(superClasses, equivalentClasses, unionClasses).flatMap(Function.identity());
        classes = classes.distinct().filter(c -> !Objects.equals(c, ce));

        Stream<Property> properties = ce.properties().map(ClassPropertyMap::toNamed);
        if (ce instanceof OntCE.ONProperty) {
            Property p = ClassPropertyMap.toNamed(((OntCE.ONProperty) ce).getOnProperty());
            properties = Stream.concat(properties, Stream.of(p));
        }
        if (ce instanceof OntCE.ONProperties) {
            Stream<? extends OntPE> props = ((OntCE.ONProperties<? extends OntPE>) ce).onProperties();
            properties = Stream.concat(properties, props.map(ClassPropertyMap::toNamed));
        }
        return Stream.concat(classes.flatMap(c -> collect(c, visited)), properties)
                .distinct();
    }
}

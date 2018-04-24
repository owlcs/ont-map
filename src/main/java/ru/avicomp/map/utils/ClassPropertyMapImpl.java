package ru.avicomp.map.utils;

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
 * A class-property mapping implementation based on rules found empirically using Tobraid Composer Diagram.
 * These rules are not the standard, and definitely not fully covered OWL2 specification.
 * Moreover it does not seem to matter whether they are right: spin-api don't use it while inference context.
 * What we need - just something to draw class-property box in GUI.
 * <p>
 * Created by @szuev on 19.04.2018.
 */
@SuppressWarnings("WeakerAccess")
public class ClassPropertyMapImpl implements ClassPropertyMap {

    // any named class expression in Topbraid Composer has a rdfs:label as attached property.
    public static final Set<Property> OWL_THING_PROPERTIES = Collections.singleton(RDFS.label);

    @Override
    public Stream<Property> properties(OntCE ce) {
        return collect(ce, new HashSet<>());
    }

    protected Stream<Property> collect(OntCE ce, Set<OntCE> visited) {
        if (visited.contains(Objects.requireNonNull(ce, "Null ce"))) {
            return Stream.empty();
        }
        visited.add(ce);
        OntGraphModel model = ce.getModel();
        if (Objects.equals(ce, OWL.Thing)) {
            return OWL_THING_PROPERTIES.stream().peek(p -> p.inModel(model));
        }
        Stream<Property> res = directProperties(ce);

        Stream<OntCE> subClassOf = ce.isAnon() ? ce.subClassOf() : Stream.concat(ce.subClassOf(), Stream.of(model.getOWLThing()));

        Stream<OntCE> intersectionRestriction =
                ce instanceof OntCE.IntersectionOf ? ((OntCE.IntersectionOf) ce).components()
                        .filter(c -> OntCE.ONProperty.class.isInstance(c) || OntCE.ONProperties.class.isInstance(c))
                        : Stream.empty();
        Stream<OntCE> equivalentIntersections = ce.equivalentClass().filter(OntCE.IntersectionOf.class::isInstance);

        Stream<OntCE> unionClasses =
                model.ontObjects(OntCE.UnionOf.class)
                        .filter(c -> c.components().anyMatch(_c -> Objects.equals(_c, ce))).map(OntCE.class::cast);

        Stream<OntCE> classes = Stream.of(subClassOf, equivalentIntersections, intersectionRestriction, unionClasses)
                .flatMap(Function.identity())
                .distinct()
                .filter(c -> !Objects.equals(c, ce));

        return Stream.concat(classes.flatMap(c -> collect(c, visited)), res).distinct();
    }

    protected Stream<Property> directProperties(OntCE ce) {
        Stream<Property> res = ce.properties().map(ClassPropertyMap::toNamed);
        if (ce instanceof OntCE.ONProperty) {
            Property p = ClassPropertyMap.toNamed(((OntCE.ONProperty) ce).getOnProperty());
            res = Stream.concat(res, Stream.of(p));
        }
        if (ce instanceof OntCE.ONProperties) { // OWL2
            Stream<? extends OntPE> props = ((OntCE.ONProperties<? extends OntPE>) ce).onProperties();
            res = Stream.concat(res, props.map(ClassPropertyMap::toNamed));
        }
        return res;
    }
}

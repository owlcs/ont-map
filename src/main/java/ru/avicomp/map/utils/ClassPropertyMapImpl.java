package ru.avicomp.map.utils;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.vocabulary.RDFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.map.ClassPropertyMap;
import ru.avicomp.ontapi.jena.OntJenaException;
import ru.avicomp.ontapi.jena.model.*;
import ru.avicomp.ontapi.jena.vocabulary.OWL;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A class-property mapping implementation based on rules found empirically using Tobraid Composer Diagram.
 * It seems that these rules are not the standard, and right now definitely not fully covered OWL2 specification.
 * Moreover for SPIN-API it does not seem to matter whether they are right:
 * it does not use them directly while inference context.
 * But we deal only with OWL2 ontologies, so we need strict constraints to used while construct mapping.
 * Also we need something to draw class-property box in GUI.
 * <p>
 * Created by @szuev on 19.04.2018.
 */
@SuppressWarnings("WeakerAccess")
public class ClassPropertyMapImpl implements ClassPropertyMap {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClassPropertyMapImpl.class);

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
        Set<Property> res = directProperties(ce).collect(Collectors.toSet());
        // if one of the direct properties contains in propertyChain List in the first place,
        // then that propertyChain can be added to the result list as effective property
        listPropertyChains(model)
                .filter(p -> res.stream()
                        .filter(x -> x.canAs(OntOPE.class))
                        .map(x -> x.as(OntOPE.class))
                        .anyMatch(x -> containFirst(p, x)))
                .map(OntPE::asProperty).forEach(res::add);

        Stream<OntCE> subClassOf = ce.isAnon() ? ce.subClassOf() : Stream.concat(ce.subClassOf(), Stream.of(model.getOWLThing()));

        Stream<OntCE> intersectionRestriction =
                ce instanceof OntCE.IntersectionOf ? ((OntCE.IntersectionOf) ce).components()
                        .filter(c -> c instanceof OntCE.ONProperty || c instanceof OntCE.ONProperties)
                        : Stream.empty();
        Stream<OntCE> equivalentIntersections = ce.equivalentClass().filter(OntCE.IntersectionOf.class::isInstance);

        Stream<OntCE> unionClasses =
                model.ontObjects(OntCE.UnionOf.class)
                        .filter(c -> c.components().anyMatch(_c -> Objects.equals(_c, ce))).map(OntCE.class::cast);

        Stream<OntCE> classes = Stream.of(subClassOf, equivalentIntersections, intersectionRestriction, unionClasses)
                .flatMap(Function.identity())
                .distinct()
                .filter(c -> !Objects.equals(c, ce));

        return Stream.concat(classes.flatMap(c -> collect(c, visited)), res.stream()).distinct();
    }

    /**
     * Lists all direct class properties.
     * TODO: move to ONT-API ?
     *
     * @param ce {@link OntCE}
     * @return Stream of {@link Property properties}
     */
    protected static Stream<Property> directProperties(OntCE ce) {
        Stream<Property> res = ce.properties().map(OntPE::asProperty);
        try {
            if (ce instanceof OntCE.ONProperty) {
                Property p = ((OntCE.ONProperty) ce).getOnProperty().asProperty();
                res = Stream.concat(res, Stream.of(p));
            }
            if (ce instanceof OntCE.ONProperties) { // OWL2
                Stream<? extends OntPE> props = ((OntCE.ONProperties<? extends OntPE>) ce).onProperties();
                res = Stream.concat(res, props.map(OntPE::asProperty));
            }
        } catch (OntJenaException j) {
            // Ignore. Somebody can broke class expression manually, for example by deleting property declaration,
            // In that case ONT-API throws exception on calling method ONProperty#asProperty
            // TODO: (ONT-API hint) maybe need discard the restriction with the broken content
            LOGGER.warn("Can't find properties for restriction {}: {}", ce, j.getMessage());
        }
        return res;
    }

    /**
     * Lists all {@code owl:propertyChainAxiom} object properties.
     * TODO: move to ONT-API ?
     *
     * @param m {@link OntGraphModel}
     * @return Stream of {@link OntOPE}s
     */
    public static Stream<OntOPE> listPropertyChains(OntGraphModel m) {
        return m.statements(null, OWL.propertyChainAxiom, null)
                .map(OntStatement::getSubject)
                .filter(s -> s.canAs(OntOPE.class))
                .map(s -> s.as(OntOPE.class));
    }

    /**
     * Answers iff left argument has a {@code owl:propertyChainAxiom} list which contains right argument in the first position.
     * TODO: move to ONT-API ?
     *
     * @param left  {@link OntOPE}
     * @param right {@link OntOPE}
     * @return boolean
     */
    public static boolean containFirst(OntOPE left, OntOPE right) {
        return left.superPropertyOf().findFirst().map(right::equals).orElse(false);
    }

}

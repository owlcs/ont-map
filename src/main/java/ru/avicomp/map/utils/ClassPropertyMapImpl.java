/*
 * This file is part of the ONT MAP.
 * The contents of this file are subject to the Apache License, Version 2.0.
 * Copyright (c) 2018, Avicomp Services, AO
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.avicomp.map.utils;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.vocabulary.RDFS;
import ru.avicomp.map.ClassPropertyMap;
import ru.avicomp.ontapi.jena.model.OntCE;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntOPE;
import ru.avicomp.ontapi.jena.model.OntPE;
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
 * But we deal only with OWL2 ontologies, so we need strict constraints to used while construct mappings.
 * Also we need something to draw class-property box in GUI.
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
        Set<Property> res = ModelUtils.listProperties(ce).map(OntPE::asProperty).collect(Collectors.toSet());
        // if one of the direct properties contains in propertyChain List in the first place,
        // then that propertyChain can be added to the result list as effective property
        ModelUtils.listPropertyChains(model)
                .filter(p -> res.stream()
                        .filter(x -> x.canAs(OntOPE.class))
                        .map(x -> x.as(OntOPE.class))
                        .anyMatch(x -> ModelUtils.isHeadOfPropertyChain(p, x)))
                .map(OntPE::asProperty).forEach(res::add);

        Stream<OntCE> subClassOf = ce.isAnon() ? ce.subClassOf() :
                Stream.concat(ce.subClassOf(), Stream.of(model.getOWLThing()));

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
}

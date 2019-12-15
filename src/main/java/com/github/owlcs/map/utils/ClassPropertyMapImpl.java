/*
 * This file is part of the ONT MAP.
 * The contents of this file are subject to the Apache License, Version 2.0.
 * Copyright (c) 2019, The University of Manchester, owl.cs group.
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

package com.github.owlcs.map.utils;

import com.github.owlcs.map.ClassPropertyMap;
import com.github.owlcs.ontapi.jena.impl.OntObjectImpl;
import com.github.owlcs.ontapi.jena.model.OntClass;
import com.github.owlcs.ontapi.jena.model.OntModel;
import com.github.owlcs.ontapi.jena.model.OntObjectProperty;
import com.github.owlcs.ontapi.jena.model.OntProperty;
import com.github.owlcs.ontapi.jena.utils.Iter;
import com.github.owlcs.ontapi.jena.utils.OntModels;
import com.github.owlcs.ontapi.jena.vocabulary.OWL;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDFS;

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

    @Override
    public Stream<Property> properties(OntClass ce) {
        return collect(ce, new HashSet<>()).map(OntProperty::asProperty);
    }

    /**
     * Recursively collects all property expression that assumed to be belonged to the specified class.
     *
     * @param ce   {@link OntClass}, not {@code null}
     * @param seen a {@code Set} to control recursion
     * @return <b>distinct</b> {@code Stream} of {@link OntProperty property expression}s
     */
    public Stream<OntProperty> collect(OntClass ce, Set<OntClass> seen) {
        if (!seen.add(Objects.requireNonNull(ce, "Null ce"))) {
            return Stream.empty();
        }
        OntModel model = ce.getModel();
        if (OWL.Thing.equals(ce)) {
            // in Topbraid Composer owl:Thing implicitly has rdfs:label,
            // which is inherited by all other named class expressions
            return Stream.of(model.getRDFSLabel());
        }

        Set<OntProperty> res = ModelUtils.properties(ce)
                .flatMap(x -> relatedProperties(x, ce))
                .collect(Collectors.toSet());

        // if one of the direct properties contains in propertyChain Axiom List in the first place,
        // then that propertyChain can be added to the result list as effective property
        ModelUtils.propertyChains(model)
                .filter(p -> res.stream()
                        .filter(x -> x.canAs(OntObjectProperty.class))
                        .map(x -> x.as(OntObjectProperty.class))
                        .anyMatch(x -> ModelUtils.isHeadOfPropertyChain(p, x)))
                .forEach(res::add);

        Stream<OntProperty> fromSuperClasses = relatedClasses(ce).flatMap(c -> collect(c, seen));
        return Stream.concat(fromSuperClasses, res.stream()).distinct();
    }

    /**
     * Answers a {@code Stream} over class expressions
     * that relate with the given class in several relations which were found empirically using Topbraid.
     * These relations includes {@code rdfs:subClassOf}, {@code owl:equivalentClass} axioms and some others.
     *
     * @param ce {@link OntClass}, not {@code null}
     * @return <b>distinct</b> {@code Stream} of {@link OntClass class expression}s
     */
    protected Stream<OntClass> relatedClasses(OntClass ce) {
        OntModel model = ce.getModel();
        Stream<OntClass> superClasses = ce.isAnon() ? ce.superClasses() :
                Stream.concat(ce.superClasses(), Stream.of(model.getOWLThing()));

        Stream<OntClass> intersectionRestriction = ce instanceof OntClass.IntersectionOf ?
                ((OntClass.IntersectionOf) ce).getList().members().filter(c -> c instanceof OntClass.RestrictionCE)
                : Stream.empty();
        Stream<OntClass> equivalentIntersections = ce.equivalentClasses().filter(OntClass.IntersectionOf.class::isInstance);

        Stream<OntClass> unionClasses = model.ontObjects(OntClass.UnionOf.class)
                .filter(c -> c.getList().members().anyMatch(x -> Objects.equals(x, ce)))
                .map(OntClass.class::cast);

        return Stream.of(superClasses, equivalentIntersections, intersectionRestriction, unionClasses)
                .flatMap(Function.identity())
                .filter(c -> !Objects.equals(c, ce))
                .distinct();
    }

    /**
     * Answers a stream over all standalone sub properties for the given property.
     * The term 'standalone' here means that each property does not belong to any other class
     * with except the class given as second parameter.
     * The input property is also included into the stream.
     *
     * @param p      {@link OntProperty}, property to analyse, not {@code null}
     * @param domain {@link OntClass}, an allowed domain, not {@code null}
     * @return <b>distinct</b> {@code Stream} of {@link OntProperty property expression}s
     */
    protected Stream<OntProperty> relatedProperties(OntProperty p, OntClass domain) {
        Set<OntProperty> res = getSubProperties(p, domain);
        res.add(p);
        return res.stream();
    }

    /**
     * Returns a set of all standalone sub properties for the given property.
     * The term 'standalone' here means that each property does not belong to any other class
     * with except the class given as second parameter.
     *
     * @param p      {@link OntProperty}, property to analyse, not {@code null}
     * @param domain {@link OntClass}, an allowed domain, not {@code null}
     * @return {@code Set} of {@link OntProperty property expression}s
     */
    protected Set<OntProperty> getSubProperties(OntProperty p, OntClass domain) {
        Class<OntProperty> type = OntModels.getOntType(p);
        return OntObjectImpl.getHierarchy(p, o -> ((OntObjectImpl) o).listSubjects(RDFS.subPropertyOf, type)
                .filterKeep(x -> isStandalone(x, domain)), false);
    }

    /**
     * Answers {@code true} if the given property is standalone,
     * which means it has no {@code rdfs:domain}s with only one exclusion that is given as second parameter.
     *
     * @param p      {@link OntProperty}, property to test, not {@code null}
     * @param except {@link OntClass}, an allowed domain, not {@code null}
     * @return boolean
     */
    protected boolean isStandalone(OntProperty p, Resource except) {
        return !Iter.findFirst(p.listProperties(RDFS.domain).filterDrop(s -> except.equals(s.getObject()))).isPresent();
    }
}

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
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDFS;
import ru.avicomp.map.ClassPropertyMap;
import ru.avicomp.ontapi.jena.impl.OntObjectImpl;
import ru.avicomp.ontapi.jena.model.OntCE;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntOPE;
import ru.avicomp.ontapi.jena.model.OntPE;
import ru.avicomp.ontapi.jena.utils.Iter;
import ru.avicomp.ontapi.jena.utils.OntModels;
import ru.avicomp.ontapi.jena.vocabulary.OWL;

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
    public Stream<Property> properties(OntCE ce) {
        return collect(ce, new HashSet<>()).map(OntPE::asProperty);
    }

    /**
     * Recursively collects all property expression that assumed to be belonged to the specified class.
     *
     * @param ce   {@link OntCE}, not {@code null}
     * @param seen a {@code Set} to control recursion
     * @return <b>distinct</b> {@code Stream} of {@link OntPE property expression}s
     */
    public Stream<OntPE> collect(OntCE ce, Set<OntCE> seen) {
        if (!seen.add(Objects.requireNonNull(ce, "Null ce"))) {
            return Stream.empty();
        }
        OntGraphModel model = ce.getModel();
        if (OWL.Thing.equals(ce)) {
            // in Topbraid Composer owl:Thing implicitly has rdfs:label,
            // which is inherited by all other named class expressions
            return Stream.of(model.getRDFSLabel());
        }

        Set<OntPE> res = ModelUtils.properties(ce)
                .flatMap(x -> relatedProperties(x, ce))
                .collect(Collectors.toSet());

        // if one of the direct properties contains in propertyChain Axiom List in the first place,
        // then that propertyChain can be added to the result list as effective property
        ModelUtils.propertyChains(model)
                .filter(p -> res.stream()
                        .filter(x -> x.canAs(OntOPE.class))
                        .map(x -> x.as(OntOPE.class))
                        .anyMatch(x -> ModelUtils.isHeadOfPropertyChain(p, x)))
                .forEach(res::add);

        Stream<OntPE> fromSuperClasses = relatedClasses(ce).flatMap(c -> collect(c, seen));
        return Stream.concat(fromSuperClasses, res.stream()).distinct();
    }

    /**
     * Answers a {@code Stream} over class expressions
     * that relate with the given class in several relations which were found empirically using Topbraid.
     * These relations includes {@code rdfs:subClassOf}, {@code owl:equivalentClass} axioms and some others.
     *
     * @param ce {@link OntCE}, not {@code null}
     * @return <b>distinct</b> {@code Stream} of {@link OntCE class expression}s
     */
    protected Stream<OntCE> relatedClasses(OntCE ce) {
        OntGraphModel model = ce.getModel();
        Stream<OntCE> superClasses = ce.isAnon() ? ce.superClasses() :
                Stream.concat(ce.superClasses(), Stream.of(model.getOWLThing()));

        Stream<OntCE> intersectionRestriction = ce instanceof OntCE.IntersectionOf ?
                ((OntCE.IntersectionOf) ce).getList().members().filter(c -> c instanceof OntCE.RestrictionCE)
                : Stream.empty();
        Stream<OntCE> equivalentIntersections = ce.equivalentClasses().filter(OntCE.IntersectionOf.class::isInstance);

        Stream<OntCE> unionClasses = model.ontObjects(OntCE.UnionOf.class)
                .filter(c -> c.getList().members().anyMatch(x -> Objects.equals(x, ce)))
                .map(OntCE.class::cast);

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
     * @param p      {@link OntPE}, property to analyse, not {@code null}
     * @param domain {@link OntCE}, an allowed domain, not {@code null}
     * @return <b>distinct</b> {@code Stream} of {@link OntPE property expression}s
     */
    protected Stream<OntPE> relatedProperties(OntPE p, OntCE domain) {
        Set<OntPE> res = getSubProperties(p, domain);
        res.add(p);
        return res.stream();
    }

    /**
     * Returns a set of all standalone sub properties for the given property.
     * The term 'standalone' here means that each property does not belong to any other class
     * with except the class given as second parameter.
     *
     * @param p      {@link OntPE}, property to analyse, not {@code null}
     * @param domain {@link OntCE}, an allowed domain, not {@code null}
     * @return {@code Set} of {@link OntPE property expression}s
     */
    protected Set<OntPE> getSubProperties(OntPE p, OntCE domain) {
        Class<OntPE> type = OntModels.getOntType(p);
        return OntObjectImpl.getHierarchy(p, o -> ((OntObjectImpl) o).listSubjects(RDFS.subPropertyOf, type)
                .filterKeep(x -> isStandalone(x, domain)), false);
    }

    /**
     * Answers {@code true} if the given property is standalone,
     * which means it has no {@code rdfs:domain}s with only one exclusion that is given as second parameter.
     *
     * @param p      {@link OntPE}, property to test, not {@code null}
     * @param except {@link OntCE}, an allowed domain, not {@code null}
     * @return boolean
     */
    protected boolean isStandalone(OntPE p, Resource except) {
        return !Iter.findFirst(p.listProperties(RDFS.domain).filterDrop(s -> except.equals(s.getObject()))).isPresent();
    }
}

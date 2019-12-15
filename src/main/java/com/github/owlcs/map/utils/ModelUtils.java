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

import com.github.owlcs.ontapi.jena.model.*;
import com.github.owlcs.ontapi.jena.utils.Iter;
import com.github.owlcs.ontapi.jena.utils.Models;
import com.github.owlcs.ontapi.jena.vocabulary.OWL;
import com.github.owlcs.ontapi.jena.vocabulary.RDF;
import org.apache.jena.rdf.model.*;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.RDFS;
import org.topbraid.spin.util.JenaUtil;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Collection of utils additional to the {@link Models ONT-API Model Utils}.
 * NOTE: this class can be fully or partially moved to ONT-API.
 * <p>
 * Created by @szz on 25.12.2018.
 *
 * @see com.github.owlcs.ontapi.jena.utils.OntModels
 * @see Models
 */
@SuppressWarnings("WeakerAccess")
public class ModelUtils {

    /**
     * Represents the given resource as string.
     *
     * @param res {@link Resource}, not {@code null}
     * @return String, not {@code null}
     */
    public static String getResourceID(Resource res) {
        return res.isURIResource() ? res.getURI() : res.getId().getLabelString();
    }

    /**
     * Attempts to get a human readable name of the given resource if it is possible.
     * A local name is used in case the resource has it,
     * otherwise the method returns either a blank node id or some computed string,
     * which is always the same for a given resource.
     *
     * @param r {@link Resource}, not {@code null}
     * @return String, not {@code null} or empty
     */
    public static String getResourceName(Resource r) {
        if (Objects.requireNonNull(r).isAnon()) {
            return r.getId().getLabelString();
        }
        String res = r.getLocalName();
        if (res != null && !res.isEmpty()) {
            return res;
        }
        String uri = r.getURI();
        res = uri.replaceFirst("^[^\\w]+", "");
        if (res.isEmpty()) { // as a last attempt
            return r.getClass().getSimpleName() + "-" + Integer.toHexString(r.hashCode());
        }
        return res;
    }

    /**
     * List direct class-types,
     * i.e. all object resources from {@code rdf:type} statement for the specified subject resource in the model.
     * Notice that this is not recursive method: it does not consider {@code rdf:type}s of returned resources.
     *
     * @param individual {@link Resource}, individual, not {@code null}
     * @return {@link ExtendedIterator} of {@link Resource}s
     */
    public static ExtendedIterator<Resource> listDirectClasses(Resource individual) {
        return individual.listProperties(RDF.type)
                .mapWith(Statement::getObject)
                .filterKeep(RDFNode::isResource)
                .mapWith(RDFNode::asResource);
    }

    /**
     * Finds a range for the given object property.
     * Returns an empty result in case {@code rdfs:range} cannot be defined uniquely.
     *
     * @param p {@link OntObjectProperty}, not {@code null}
     * @return Optional around the {@link OntClass}
     * @see <a href='https://www.w3.org/TR/owl2-syntax/#Object_Property_Range'>9.2.6 Object Property Range</a>
     * @see OntObjectProperty#ranges()
     * @see ModelUtils#ranges(OntObjectProperty)
     */
    public static Optional<OntClass> range(OntObjectProperty p) {
        return findRange(p, x -> classRanges(x.as(OntObjectProperty.class)),
                OntProperty::superProperties, OntObjectProperty.class, OntClass.class, new HashSet<>());
    }

    /**
     * Finds a range for the given data property.
     * Returns an empty result in case {@code rdfs:range} cannot be defined uniquely.
     *
     * @param p {@link OntDataProperty}, not {@code null}
     * @return Optional around the {@link OntDataRange}
     * @see <a href='https://www.w3.org/TR/owl2-syntax/#Data_Property_Range'>9.3.5 Data Property Range</a>
     * @see OntDataProperty#ranges()
     * @see ModelUtils#ranges(OntDataProperty)
     */
    public static Optional<OntDataRange> range(OntDataProperty p) {
        return findRange(p, OntDataProperty.class, OntDataRange.class, new HashSet<>());
    }

    /**
     * Finds a range for the given annotation property.
     * Returns an empty result in case {@code rdfs:range} cannot be defined uniquely.
     *
     * @param p {@link OntAnnotationProperty}, not {@code null}
     * @return Optional around the {@link Property}
     * @see <a href='https://www.w3.org/TR/owl2-syntax/#Annotation_Property_Range'>10.2.4 Annotation Property Range</a>
     * @see OntAnnotationProperty#ranges()
     * @see ModelUtils#ranges(OntAnnotationProperty)
     */
    public static Optional<Property> range(OntAnnotationProperty p) {
        return findRange(p, OntAnnotationProperty.class, Property.class, new HashSet<>());
    }

    /**
     * List all {@code rdfs:domain} class expressions for the given {@link OntObjectProperty object property}
     * including inferred from property hierarchy.
     *
     * @param ope {@link OntObjectProperty}, not {@code null}
     * @return Stream
     * @see #range(OntObjectProperty)
     * @see #classRanges(OntObjectProperty)
     */
    public static Stream<OntClass> ranges(OntObjectProperty ope) {
        return resourceRanges(ope, p -> classRanges(p.as(OntObjectProperty.class)), OntProperty::superProperties,
                new HashSet<>()).filter(x -> x.canAs(OntClass.class)).map(x -> x.as(OntClass.class)).distinct();
    }

    /**
     * List all {@code rdfs:domain} data ranges for the given {@link OntDataProperty}
     * including inferred from property hierarchy.
     *
     * @param p {@link OntDataProperty}, not {@code null}
     * @return {@code Stream}
     * @see #range(OntDataProperty)
     */
    public static Stream<OntDataRange> ranges(OntDataProperty p) {
        return resourceRanges(p, new HashSet<>())
                .filter(x -> x.canAs(OntDataRange.class)).map(x -> x.as(OntDataRange.class));
    }

    /**
     * List all {@code rdfs:domain} IRIs for the given {@link OntAnnotationProperty annotation property}
     * including inferred from class hierarchy.
     *
     * @param p {@link OntAnnotationProperty}, not {@code null}
     * @return Stream
     * @see #range(OntAnnotationProperty)
     */
    public static Stream<Property> ranges(OntAnnotationProperty p) {
        return resourceRanges(p, new HashSet<>()).filter(RDFNode::isURIResource).map(x -> x.as(Property.class));
    }

    private static Stream<? extends Resource> resourceRanges(OntProperty p, Set<Resource> seen) {
        return resourceRanges(p, OntProperty::ranges, OntProperty::superProperties, seen);
    }

    /**
     * Finds a range for a given property.
     * First it looks direct {@code rdfs:range} declarations and returns an empty result,
     * if there are more then one ranges
     * Then, it looks at the property hierarchy.
     * Again, more then one {@code rdfs:range}s means ambiguous situation and the method returns empty result.
     *
     * @param property     {@link P}
     * @param propertyType class type of {@link P}
     * @param rangeType    class type of {@link R}
     * @param seen         Set to control possible recursions
     * @param <P>          any concrete subtype of {@link OntProperty}
     * @param <R>          any concrete subtype of {@link Resource}
     * @return Optional around {@link R}
     */
    private static <P extends OntProperty, R extends Resource> Optional<R> findRange(P property,
                                                                                     Class<P> propertyType,
                                                                                     Class<R> rangeType,
                                                                                     Set<Resource> seen) {
        return findRange(property, OntProperty::ranges, OntProperty::superProperties, propertyType, rangeType, seen);
    }

    private static <P extends OntProperty, R extends Resource> Optional<R> findRange(P property,
                                                                                     Function<OntProperty, Stream<? extends Resource>> ranges,
                                                                                     Function<OntProperty, Stream<? extends Resource>> superProperties,
                                                                                     Class<P> propertyType,
                                                                                     Class<R> rangeType,
                                                                                     Set<Resource> seen) {
        Set<R> res = ranges.apply(property)
                .filter(x -> x.canAs(rangeType)).map(x -> x.as(rangeType))
                .collect(Collectors.toSet());
        if (res.isEmpty()) { // inherit from some super property :
            res = superProperties.apply(property) // : direct super properties
                    .filter(x -> x.canAs(propertyType))
                    .map(x -> x.as(propertyType))
                    .filter(seen::add)
                    .map(x -> findRange(x, ranges, superProperties, propertyType, rangeType, seen)) // : recursion
                    .filter(Optional::isPresent).map(Optional::get)
                    .collect(Collectors.toSet());
        }
        if (res.size() != 1) { // : ambiguous situation
            return Optional.empty();
        }
        return Optional.of(res.iterator().next());
    }

    /**
     * Lists all {@code rdfs:range}s for the given {@link OntObjectProperty object property expression}.
     * The returned stream contains not only classes from the {@code rdfs:range} axioms,
     * but also from {@code rdfs:domain} axioms, if a property is inverse.
     *
     * @param p {@link OntObjectProperty}
     * @return {@code Stream} of {@link OntClass}s
     * @see <a href='https://www.w3.org/TR/owl2-syntax/#Inverse_Object_Properties'>6.1.1 Inverse Object Properties</a>
     * @see <a href='https://www.w3.org/TR/owl2-syntax/#Inverse_Object_Properties_2'>9.2.4 Inverse Object Properties</a>
     */
    private static Stream<OntClass> classRanges(OntObjectProperty p) {
        return Stream.concat(p.ranges(), p.inverseProperties().flatMap(OntRealProperty::domains));
    }

    private static Stream<? extends Resource> resourceRanges(OntProperty p,
                                                             Function<OntProperty, Stream<? extends Resource>> findRanges,
                                                             Function<OntProperty, Stream<? extends Resource>> findSuperProperties,
                                                             Set<Resource> seen) {
        if (!seen.add(p)) return Stream.empty();
        Stream<? extends Resource> res = findRanges.apply(p);
        return Stream.concat(res, findSuperProperties.apply(p)
                .flatMap(x -> resourceRanges(x.as(OntProperty.class), findRanges, findSuperProperties, seen)));
    }

    /**
     * Lists all properties that are related to the given class expression.
     * The class hierarchy are not taken into account.
     * The result includes the following cases:
     * <ul>
     * <li>right part of {@code rdfs:domain}, see {@link OntClass#properties()}</li>
     * <li>the subject of {@code owl:inverseOf}, if it is in {@code rdfs:range} relation with the given {@link OntClass}</li>
     * <li>{@code owl:onProperties} and {@code owl:onProperty} relations</li>
     * </ul>
     *
     * @param ce {@link OntClass}, not {@code null}
     * @return <b>distinct</b> {@code Stream} of {@link OntProperty properties}
     * @see OntClass#properties()
     * @see #classRanges(OntObjectProperty)
     */
    public static Stream<OntProperty> properties(OntClass ce) {
        // direct domains
        Stream<OntProperty> domains = ce.properties();
        // indirect domains (ranges for inverseOf object properties):
        Stream<OntProperty> ranges = ce.getModel().statements(null, OWL.inverseOf, null)
                .filter(x -> x.getSubject().canAs(OntObjectProperty.class) && ce.hasProperty(RDFS.range, x.getObject()))
                .map(x -> x.getSubject().as(OntProperty.class));
        // on properties for restrictions
        Stream<? extends OntProperty> onProps = Stream.empty();
        if (ce instanceof OntClass.RestrictionCE) {
            onProps = Stream.of(((OntClass.RestrictionCE<?>) ce).getProperty());
        }
        return Stream.of(domains, ranges, onProps).flatMap(Function.identity()).distinct();
    }

    /**
     * Returns a set consisting of a given class and all its superclasses.
     * Similar to {@code rdfs:subClassOf*}.
     *
     * @param clazz {@link Resource} the class to return with its superclasses
     * @return an {@link ExtendedIterator} of class resources
     */
    public static ExtendedIterator<Resource> listSuperClasses(Resource clazz) {
        return Iter.create(() -> JenaUtil.getAllSuperClassesStar(clazz).iterator());
    }

    /**
     * Checks whether a given Resource is an instance of a given type, or a subclass thereof.
     * Make sure that the {@code expectedType} parameter is associated
     * with the right {@link org.apache.jena.rdf.model.Model},
     * because the system will try to walk up the superclasses of {@code expectedType}.
     * The {@code expectedType} may have no Model, in which case the method will use the instance's Model.
     *
     * @param instance     the {@link Resource} to test
     * @param expectedType {@link Resource} the type that instance is expected to have
     * @return {@code true} if resource has {@code rdf:type} expectedType
     */
    public static boolean hasIndirectType(Resource instance, Resource expectedType) {
        return JenaUtil.hasIndirectType(instance, expectedType);
    }

    /**
     * Answers {@code true} if the given resource belongs to the specified {@code model} including all its content.
     * Always returns {@code true} if {@code test.getModel()} equals to the {@code model}.
     *
     * @param model {@link Model} which
     * @param test  {@link Resource} to test, not {@code null}
     * @return boolean, {@code true} if the given resource is contained in the model
     * @see Models#getAssociatedStatements(Resource)
     */
    public static boolean containsResource(Model model, final Resource test) {
        return Objects.requireNonNull(test.getModel(), "Unattached resource: " + test) == model
                || isEquivalent(test, test.inModel(model));
    }

    /**
     * Answers {@code true} if the given two resources are equivalent to each other.
     * This means that their differs only in b-node ids, all other nodes and structure are identical.
     * Note: it is a recursive method.
     * Example of equivalent resources:
     * {@code <x> rdf:type <t1> ; <p1> _:b0 . _:b0 rdf:type <t2> . } and
     * {@code <x> rdf:type <t1> ; <p1> _:b1 . _:b1 rdf:type <t2> . }
     *
     * @param left  {@link Resource}, must be in-model, not {@code null}
     * @param right {@link Resource}, must be in-model, not {@code null}
     * @return boolean, {@code true} if the given resources are equivalent
     */
    public static boolean isEquivalent(Resource left, Resource right) {
        if (left == right) return true;
        if (left.isURIResource() && !left.equals(right)) return false;
        Set<Statement> leftSet = left.listProperties().toSet();
        Set<Statement> rightSet = right.listProperties().toSet();
        if (leftSet.size() != rightSet.size()) return false;
        for (Statement s : leftSet) {
            Property p = s.getPredicate();
            Set<Statement> forPredicate = rightSet.stream()
                    .filter(x -> p.equals(x.getPredicate())).collect(Collectors.toSet());
            if (forPredicate.isEmpty()) {
                return false;
            }
            RDFNode o = s.getObject();
            if (!o.isAnon()) { // literal or uri
                if (forPredicate.stream().noneMatch(x -> o.equals(x.getObject()))) {
                    return false;
                }
                continue;
            }
            // anon:
            Resource r = o.asResource();
            if (forPredicate.stream().filter(x -> x.getObject().isAnon())
                    .map(Statement::getResource).noneMatch(x -> isEquivalent(r, x))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Adds all the content associated with the given resource (including sub-resource tree)
     * into the specified model.
     * Returns a resource that is in the model.
     *
     * @param m {@link Model} the graph to add content, not {@code null}
     * @param r {@link Resource}, not {@code null}
     * @return the same resource, but belonged to the specified model
     * @see Models#getAssociatedStatements(Resource)
     */
    public static Resource addResourceContent(Model m, Resource r) {
        Models.getAssociatedStatements(r).forEach(m::add);
        return r.inModel(m);
    }

    /**
     * Answers a {@code Stream} over all {@code owl:propertyChainAxiom} object properties.
     *
     * @param m {@link OntModel}
     * @return {@code Stream} of {@link OntObjectProperty}s
     */
    public static Stream<OntObjectProperty> propertyChains(OntModel m) {
        return Iter.asStream(listPropertyChains(m));
    }

    /**
     * Answers an {@code ExtendedIterator} over all {@code owl:propertyChainAxiom} object properties.
     *
     * @param m {@link OntModel}
     * @return {@link ExtendedIterator} of {@link OntObjectProperty}s
     */
    public static ExtendedIterator<OntObjectProperty> listPropertyChains(OntModel m) {
        return m.listStatements(null, OWL.propertyChainAxiom, (RDFNode) null)
                .mapWith(Statement::getSubject)
                .filterKeep(s -> s.canAs(OntObjectProperty.class))
                .mapWith(s -> s.as(OntObjectProperty.class));
    }

    /**
     * Answers {@code true} if the left argument has a {@code owl:propertyChainAxiom} list
     * that contains the right argument in the first position.
     *
     * @param superProperty {@link OntObjectProperty}, not {@code null}
     * @param candidate     {@link OntObjectProperty}
     * @return boolean
     */
    public static boolean isHeadOfPropertyChain(OntObjectProperty superProperty, OntObjectProperty candidate) {
        return superProperty.propertyChains()
                .map(OntList::first)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .anyMatch(p -> Objects.equals(p, candidate));
    }

}

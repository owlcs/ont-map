/*
 * This file is part of the ONT MAP.
 * The contents of this file are subject to the Apache License, Version 2.0.
 * Copyright (c) 2018, Avicomp Services, AO
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.avicomp.map.utils;

import org.apache.jena.rdf.model.*;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.util.iterator.WrappedIterator;
import org.topbraid.spin.util.JenaUtil;
import ru.avicomp.ontapi.jena.model.*;
import ru.avicomp.ontapi.jena.utils.Models;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Collection of utils additional to the {@link Models ONT-API Model Utils}.
 * NOTE: this class can be fully or partially moved to ONT-API.
 * <p>
 * Created by @szz on 25.12.2018.
 *
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
     * Gets the local name of the specified resource if it is an uri,
     * if it is blank - returns its b-node-id.
     *
     * @param res {@link Resource}, not {@code null}
     * @return String, not {@code null}
     */
    public static String getResourceName(Resource res) {
        return res.isURIResource() ? res.getLocalName() : res.getId().getLabelString();
    }

    /**
     * Gets all object resources from {@code rdf:type} statement for the specified subject resource in the model.
     *
     * @param individual {@link Resource}, individual, not {@code null}
     * @return Set of {@link Resource}, classes
     */
    public static Set<Resource> getClasses(Resource individual) {
        return individual.listProperties(RDF.type)
                .mapWith(Statement::getObject)
                .filterKeep(RDFNode::isResource)
                .mapWith(RDFNode::asResource)
                .toSet();
    }

    /**
     * Gets all types (classes) for an individual, taken into account class hierarchy.
     *
     * @param individual {@link OntIndividual}, not {@code null}
     * @return Set of {@link OntCE class expressions}
     */
    public static Set<OntCE> getClasses(OntIndividual individual) {
        Set<OntCE> res = new HashSet<>();
        individual.classes().forEach(c -> collectSuperClasses(c, res));
        return res;
    }

    private static void collectSuperClasses(OntCE ce, Set<OntCE> res) {
        if (!res.add(ce)) return;
        ce.subClassOf().forEach(c -> collectSuperClasses(c, res));
    }

    /**
     * Returns a set consisting of a given class and all its superclasses.
     * Similar to {@code rdfs:subClassOf*}.
     *
     * @param clazz {@link Resource} the class to return with its superclasses
     * @return an {@link ExtendedIterator} of class resources
     */
    public static ExtendedIterator<Resource> listSuperClasses(Resource clazz) {
        return WrappedIterator.create(JenaUtil.getAllSuperClassesStar(clazz).iterator());
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
     * Lists all {@code owl:propertyChainAxiom} object properties.
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
     * Answers {@code true} if the left argument has a {@code owl:propertyChainAxiom} list
     * that contains the right argument in the first position.
     *
     * @param superProperty {@link OntOPE}, not {@code null}
     * @param candidate     {@link OntOPE}
     * @return boolean
     */
    public static boolean isHeadOfPropertyChain(OntOPE superProperty, OntOPE candidate) {
        return superProperty.listPropertyChains()
                .map(OntList::first)
                .filter(Optional::isPresent)
                .map(Optional::get).anyMatch(p -> Objects.equals(p, candidate));
    }
}

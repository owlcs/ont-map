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

package ru.avicomp.map.spin.infer;

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.util.iterator.WrappedIterator;
import org.topbraid.spin.util.JenaUtil;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.ontapi.jena.model.OntCE;
import ru.avicomp.ontapi.jena.model.OntIndividual;
import ru.avicomp.ontapi.jena.utils.Graphs;
import ru.avicomp.ontapi.jena.utils.Iter;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Auxiliary class-helper to work with {@link Graph} or {@link org.apache.jena.rdf.model.Model}
 * (or its related objects such as {@link Resource}), that are belonged to {@link ru.avicomp.map.MapModel}
 * <p>
 * Created by @szz on 20.11.2018.
 */
@SuppressWarnings("WeakerAccess")
public class MapGraphUtils {

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
     * TODO: move to ONT-API?
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
     * Answers {@code true} if the {@code left} composite graph
     * contains all components from the {@code right} composite graph.
     *
     * @param left  {@link Graph}, not {@code null}
     * @param right {@link Graph}, not {@code null}
     * @return boolean
     */
    public static boolean containsAll(Graph left, Graph right) {
        Set<Graph> set = Graphs.flat(left).collect(Collectors.toSet());
        return containsAll(right, set);
    }

    /**
     * Answers {@code true} if all parts of the {@code test} graph are containing in the given graph collection.
     *
     * @param test {@link Graph} to test, not {@code null}
     * @param in   Collection of {@link Graph}s
     * @return boolean
     */
    private static boolean containsAll(Graph test, Collection<Graph> in) {
        return Graphs.flat(test).allMatch(in::contains);
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
     * @return true if resource has rdf:type expectedType
     */
    public static boolean hasIndirectType(Resource instance, Resource expectedType) {
        return JenaUtil.hasIndirectType(instance, expectedType);
    }

    /**
     * Answers {@code true} if the specified rule-resource derives {@code rdf:type} declaration.
     *
     * @param rule {@link Resource}
     * @return boolean
     */
    public static boolean isDeclarationMapping(Resource rule) {
        return rule.hasProperty(SPINMAP.targetPredicate1, RDF.type);
    }

    /**
     * Answers a {@code _:x rdf:type spinmap:Context} resource which is attached to the specified rule.
     *
     * @param rule {@link Resource}, rule, not null
     * @return Optional around the contexts resource declaration.
     */
    public static Optional<Resource> context(Resource rule) {
        return Iter.findFirst(rule.listProperties(SPINMAP.context)
                .mapWith(Statement::getObject)
                .filterKeep(RDFNode::isResource)
                .mapWith(RDFNode::asResource)
                .filterKeep(r -> r.hasProperty(RDF.type, SPINMAP.Context)));
    }
}

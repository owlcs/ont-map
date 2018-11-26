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

package ru.avicomp.map.spin;

import org.apache.jena.rdf.model.*;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;
import ru.avicomp.ontapi.jena.impl.UnionModel;
import ru.avicomp.ontapi.jena.utils.Iter;
import ru.avicomp.ontapi.jena.utils.Models;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A helper to work with {@link Model Jena Model}s
 * encapsulating spin/spinmap rules (in the form of {@link Resource Jena Resource}s).
 * <p>
 * Created by @szuev on 13.05.2018.
 */
@SuppressWarnings("WeakerAccess")
public class SpinModels {

    public static final Set<Resource> FUNCTION_TYPES = Stream.of(SPIN.Function,
            SPIN.MagicProperty,
            SPINMAP.TargetFunction).collect(Iter.toUnmodifiableSet());

    /**
     * Checks if the specified resource describes a self mapping context to produce {@code owl:NamedIndividual}s.
     *
     * @param context {@link Resource}
     * @return boolean
     */
    public static boolean isNamedIndividualSelfContext(Resource context) {
        return context.hasProperty(SPINMAP.targetClass, OWL.NamedIndividual) && Iter.asStream(context.listProperties(SPINMAP.target))
                .map(Statement::getObject)
                .filter(RDFNode::isAnon).map(RDFNode::asResource)
                .map(s -> s.hasProperty(RDF.type, SPINMAPL.self)).findFirst().isPresent();
    }

    public static Set<Statement> getLocalFunctionBody(Model m, Resource function) {
        if (m instanceof UnionModel) {
            m = ((UnionModel) m).getBaseModel();
        }
        Optional<Resource> res = Iter.findFirst(m.listStatements(function, RDF.type, SPIN.Function)
                .mapWith(Statement::getSubject)
                .filterKeep(r -> r.hasProperty(SPIN.body))
                .mapWith(r -> r.getRequiredProperty(SPIN.body))
                .mapWith(Statement::getObject)
                .filterKeep(RDFNode::isAnon)
                .mapWith(RDFNode::asResource));
        return res.map(Models::getAssociatedStatements).orElse(Collections.emptySet());
    }

    public static boolean isSourcePredicate(Property p) {
        return p.getLocalName().matches("^" + SPINMAP.SOURCE_PREDICATE + "\\d+$");
    }

    public static boolean isVariable(Resource inModel) {
        return inModel.hasProperty(RDF.type, SP.Variable);
    }

    public static boolean isSpinArgVariable(Resource isModel) {
        return isVariable(isModel)
                && SPIN.NS.equals(isModel.getNameSpace())
                && isModel.getLocalName().matches("^" + SPIN._ARG + "\\d+$");
    }

    /**
     * Lists all spin-api functions.
     * All functions must be named.
     * Auxiliary method.
     *
     * @param model {@link Model}
     * @return <b>distinct</b> Stream of {@link Resource}s
     */
    public static Stream<Resource> listSpinFunctions(Model model) {
        return Iter.asStream(model.listStatements(null, RDF.type, (RDFNode) null))
                .filter(s -> s.getSubject().isURIResource())
                .filter(s -> s.getObject().isURIResource())
                .filter(s -> FUNCTION_TYPES.contains(s.getObject().asResource()))
                .map(Statement::getSubject)
                .distinct();
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
     * @param left  {@link Resource}, not {@code null}
     * @param right {@link Resource}, not {@code null}
     * @return boolean, {@code true} if the given resources are equivalent
     */
    private static boolean isEquivalent(Resource left, Resource right) {
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
     * Prints the given resource-function "as is" into the specified graph.
     *
     * @param model    {@link Model} the graph to print, not {@code null}
     * @param function {@link Resource}, that is define spin-function, not {@code null}
     * @return the same resource, but belonged to the specified model
     */
    public static Resource printFunctionBody(Model model, Resource function) {
        // todo: also need to handle variables and other function-specific resources, which go independently
        return addResourceContent(model, function);
    }

    /**
     * Adds all the content associated with the given resource to the specified model.
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

}

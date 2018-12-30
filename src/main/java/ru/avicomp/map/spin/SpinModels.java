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
import org.apache.jena.vocabulary.RDFS;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import org.topbraid.spin.vocabulary.SPL;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;
import ru.avicomp.map.utils.ModelUtils;
import ru.avicomp.ontapi.jena.impl.UnionModel;
import ru.avicomp.ontapi.jena.utils.Iter;
import ru.avicomp.ontapi.jena.utils.Models;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A helper to work with {@link Model Jena Model}s
 * encapsulating spin/spinmap rules (in the form of {@link Resource Jena Resource}s).
 * <p>
 * Created by @szuev on 13.05.2018.
 *
 * @see ModelUtils
 */
@SuppressWarnings("WeakerAccess")
public class SpinModels {

    public static final Set<Resource> FUNCTION_TYPES = Stream.of(SPIN.Function,
            SPIN.MagicProperty,
            SPINMAP.TargetFunction).collect(Iter.toUnmodifiableSet());

    /**
     * Answers {@code true} if the given property is a mapping source predicate.
     *
     * @param p {@link Property}
     * @return boolean
     */
    public static boolean isSourcePredicate(Property p) {
        return p.getLocalName().matches("^" + SPINMAP.SOURCE_PREDICATE + "\\d+$");
    }

    /**
     * Answers {@code true} if the specified resource
     * represents a spin argument variable (e.g. {@code http://spinrdf.org/spin#_arg1}).
     *
     * @param inModel a {@link Resource} within a {@link Model}
     * @return boolean
     */
    public static boolean isSpinArgVariable(Resource inModel) {
        return isVariable(inModel)
                && SPIN.NS.equals(inModel.getNameSpace())
                && inModel.getLocalName().matches("^" + SPIN._ARG + "\\d+$");
    }

    /**
     * Answers {@code true} if the specified resource represents a spin variable.
     *
     * @param inModel a {@link Resource} within a {@link Model}
     * @return boolean
     */
    public static boolean isVariable(Resource inModel) {
        return inModel.hasProperty(RDF.type, SP.Variable);
    }

    /**
     * Returns index argument variable as resource with {@link SPIN#NS} namespace, e.g. {@code spin:_arg5}.
     *
     * @param m     {@link Model}, not {@code null}
     * @param index positive integer
     * @return {@link Resource}
     */
    public static Resource getSPINArgVariable(Model m, int index) {
        return getSpinVariable(m, SPIN.getArgVariable(index).getURI());
    }

    /**
     * Returns index argument property with {@link SP#NS} namespace, e.g. {@code sp:arg5}.
     *
     * @param m     {@link Model}, not {@code null}
     * @param index positive integer
     * @return {@link Property}
     */
    public static Property getSPArgProperty(Model m, int index) {
        return getSpinProperty(m, SP.getArgProperty(index).getURI());
    }

    /**
     * Finds or creates (if needed) a property with the given uri that has {@code rdfs:subPropertyOf == sp:arg}.
     *
     * @param m   {@link Model}, not {@code null}
     * @param uri String, not {@code null}
     * @return fresh or existing {@link Property}
     */
    public static Property getSpinProperty(Model m, String uri) {
        Property res = m.getProperty(uri);
        if (!m.contains(res, RDF.type, RDF.Property)) {
            m.createResource(uri, RDF.Property).addProperty(RDFS.subPropertyOf, SP.arg);
        }
        return res;
    }

    /**
     * Finds or creates (if needed) {@code sp:Variable} with the given uri.
     *
     * @param m   {@link Model}, not {@code null}
     * @param uri String, not {@code null}
     * @return fresh or existing {@link Resource}-variable description
     */
    public static Resource getSpinVariable(Model m, String uri) {
        Resource res = m.getResource(uri);
        if (!m.contains(res, RDF.type, SP.Variable)) {
            String name = res.getLocalName().replaceFirst("^_(.+)$", "$1");
            res.inModel(m).addProperty(RDF.type, SP.Variable)
                    .addProperty(SP.varName, name);
        }
        return res;
    }

    /**
     * Gets a {@link SPIN#body spin:body} as a set of statements.
     *
     * @param m        {@link Model}
     * @param function {@link Resource}
     * @return Set of {@link Statement}s
     */
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
     * Retrieves and lists all spin-api arguments as a stream.
     *
     * @param function {@link Resource}, must be in model, not {@code null}
     * @return Stream of URIs
     */
    public static Stream<String> listSpinArguments(Resource function) {
        return Iter.asStream(Iter.flatMap(function.listProperties(SPIN.constraint)
                        .filterKeep(s -> s.getObject().isAnon())
                        .mapWith(Statement::getResource),
                c -> c.listProperties(SPL.predicate)
                        .filterKeep(s -> s.getObject().isURIResource())
                        .mapWith(Statement::getResource)
                        .mapWith(Resource::getURI)));
    }

    /**
     * Prints the given spin resource-function "as it is" into the specified graph.
     *
     * @param model    {@link Model} the graph to print, not {@code null}
     * @param function {@link Resource}, that is define spin-function, not {@code null}
     * @return the same resource, but belonged to the specified model
     */
    public static Resource printSpinFunctionBody(Model model, Resource function) {
        listSpinArguments(function).forEach(s -> getSpinProperty(model, s));
        return ModelUtils.addResourceContent(model, function);
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
     * Checks if the specified resource describes a self mapping context to produce instances of the given type.
     *
     * @param context {@link Resource}
     * @param type    {@link Resource}
     * @return boolean
     */
    public static boolean isSelfContext(Resource context, Resource type) {
        return context.hasProperty(SPINMAP.targetClass, type) && Iter.findFirst(context.listProperties(SPINMAP.target)
                .mapWith(Statement::getObject).filterKeep(RDFNode::isAnon)
                .mapWith(RDFNode::asResource)
                .filterKeep(s -> s.hasProperty(RDF.type, SPINMAPL.self))).isPresent();
    }

    /**
     * Answers {@code true} if the specified resource
     * represents a {@link SPINMAP#Context spinmap:Context}.
     *
     * @param inModel a {@link Resource} within a {@link Model}
     * @return boolean
     */
    public static boolean isContext(Resource inModel) {
        return inModel.hasProperty(RDF.type, SPINMAP.Context);
    }

    /**
     * Answers a {@code _:x rdf:type spinmap:Context} resource that is attached to the specified rule.
     *
     * @param rule {@link Resource}, rule, not null
     * @return Optional around the contexts resource declaration.
     */
    public static Optional<Resource> context(Resource rule) {
        return Iter.findFirst(rule.listProperties(SPINMAP.context)
                .mapWith(Statement::getObject)
                .filterKeep(RDFNode::isResource)
                .mapWith(RDFNode::asResource)
                .filterKeep(SpinModels::isContext));
    }

}

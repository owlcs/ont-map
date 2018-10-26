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
import org.topbraid.spin.util.CommandWrapper;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;
import ru.avicomp.ontapi.jena.utils.Iter;
import ru.avicomp.ontapi.jena.utils.Models;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Utility to work with {@link org.apache.jena.rdf.model.Model}s encapsulating spin/spinmap rules.
 * Created by @szuev on 13.05.2018.
 */
@SuppressWarnings("WeakerAccess")
public class SpinModels {

    public static final Set<Resource> FUNCTION_TYPES = Stream.of(SPIN.Function,
            SPIN.MagicProperty,
            SPINMAP.TargetFunction).collect(Iter.toUnmodifiableSet());

    /**
     * Gets a {@code spinmap:rule} from CommandWrapper.
     *
     * @param cw {@link CommandWrapper}, not null
     * @return Optional around {@link Resource} describing rule
     */
    public static Optional<Resource> rule(CommandWrapper cw) {
        return Optional.ofNullable(cw.getStatement())
                .filter(s -> SPINMAP.rule.equals(s.getPredicate()))
                .map(Statement::getObject)
                .filter(RDFNode::isAnon)
                .map(RDFNode::asResource)
                .filter(r -> r.hasProperty(SPINMAP.context))
                .filter(r -> r.getRequiredProperty(SPINMAP.context).getObject().isURIResource());
    }

    /**
     * Answers if specified rule-resource derives {@code rdf:type} declaration.
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
     * @return Optional around contexts resource declaration.
     */
    public static Optional<Resource> context(Resource rule) {
        return Iter.asStream(rule.listProperties(SPINMAP.context))
                .map(Statement::getObject)
                .filter(RDFNode::isResource)
                .map(RDFNode::asResource)
                .filter(r -> r.hasProperty(RDF.type, SPINMAP.Context))
                .findFirst();
    }

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

    /**
     * Checks if the specified resource describes a self mapping rule to produce {@code owl:NamedIndividual}s.
     *
     * @param rule Resource
     * @return boolean
     */
    public static boolean isNamedIndividualSelfMapping(Resource rule) {
        return isDeclarationMapping(rule) && context(rule).filter(SpinModels::isNamedIndividualSelfContext).isPresent();
    }

    public static Set<Statement> getFunctionBody(Model m, Resource function) {
        return Iter.asStream(m.listStatements(function, RDF.type, SPIN.Function))
                .map(Statement::getSubject)
                .filter(r -> r.hasProperty(SPIN.body))
                .map(r -> r.getRequiredProperty(SPIN.body))
                .map(Statement::getObject)
                .filter(RDFNode::isAnon)
                .map(RDFNode::asResource)
                .map(Models::getAssociatedStatements)
                .findFirst()
                .orElse(Collections.emptySet());
    }

    public static String toString(CommandWrapper w) {
        if (w.getLabel() != null)
            return w.getLabel();
        if (w.getText() != null)
            return w.getText();
        return Optional.ofNullable(w.getStatement()).map(Object::toString).orElse("Unknown mapping");
    }

    public static boolean isSourcePredicate(Property p) {
        return p.getLocalName().matches("^" + SPINMAP.SOURCE_PREDICATE + "\\d+$");
    }

    public static boolean isVariable(Resource inModel) {
        return inModel.hasProperty(RDF.type, SP.Variable);
    }

    public static boolean isSpinArgVariable(Resource isModel) {
        return isVariable(isModel) && SPIN.NS.equals(isModel.getNameSpace()) && isModel.getLocalName().matches("^" + SPIN._ARG + "\\d+$");
    }

    /**
     * Lists all spin-api functions.
     * Auxiliary method.
     *
     * @param model {@link Model}
     * @return Stream of {@link Resource}s.
     */
    public static Stream<Resource> listSpinFunctions(Model model) {
        return Iter.asStream(model.listStatements(null, RDF.type, (RDFNode) null))
                .filter(s -> s.getObject().isURIResource())
                .filter(s -> FUNCTION_TYPES.contains(s.getObject().asResource()))
                .map(Statement::getSubject)
                .distinct();
    }

}

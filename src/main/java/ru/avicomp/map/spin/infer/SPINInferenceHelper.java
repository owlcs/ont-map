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

package ru.avicomp.map.spin.infer;

import org.apache.jena.graph.Triple;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.rdf.model.*;
import org.topbraid.spin.arq.ARQFactory;
import org.topbraid.spin.model.*;
import org.topbraid.spin.model.update.Update;
import org.topbraid.spin.system.SPINLabels;
import org.topbraid.spin.util.*;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.MapJenaException;
import ru.avicomp.map.spin.SpinModelConfig;
import ru.avicomp.map.spin.SpinModels;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * An ONT-MAP replacement for several Topbraid-SPIN common classes to conduct inference.
 *
 * @see org.topbraid.spin.util.SPINQueryFinder
 * @see org.topbraid.spin.inference.SPINInferences
 */
@SuppressWarnings({"UnusedReturnValue", "WeakerAccess"})
public class SPINInferenceHelper {

    protected final ARQFactory arqFactory;

    private static final String SELF_QUERY = "CONSTRUCT \n" +
            "  { \n" +
            "    ?target ?targetPredicate1 ?newValue .\n" +
            "  }\n" +
            "WHERE\n" +
            "  { ?this  a                     ?TYPE_CLASS\n" +
            "    BIND(<http://spinrdf.org/spin#eval>(?expression, <http://spinrdf.org/sp#arg1>, ?this) AS ?newValue)\n" +
            "    BIND(<http://spinrdf.org/spinmap#targetResource>(?this, ?context) AS ?target)\n" +
            "  }\n";

    public SPINInferenceHelper(ARQFactory factory) {
        this.arqFactory = Objects.requireNonNull(factory);
    }

    /**
     * Answers {@code true} if the given query is
     * for generating {@link OWL#NamedIndividual owl:NamedIndividuals} declarations.
     *
     * @param cw {@link QueryWrapper}, not {@code null}
     * @return boolean
     */
    public static boolean isNamedIndividualDeclaration(QueryWrapper cw) {
        return isDeclaration(cw, OWL.NamedIndividual);
    }

    /**
     * Answers {@code true} if the given query is for generating {@code rdf:type} declarations.
     * Such query corresponds the {@code spinmap:Mapping-0-1} with {@code spinmapl:self} target function.
     *
     * @param cw   {@link QueryWrapper}, not {@code null}
     * @param type {@link Resource} type to check
     * @return boolean
     */
    public static boolean isDeclaration(QueryWrapper cw, Resource type) {
        Map<String, RDFNode> input = cw.getTemplateBinding();
        return input.size() == 3
                && type.equals(input.get(SPINMAP.expression.getLocalName()))
                && RDF.type.equals(input.get(SPINMAP.targetPredicate1.getLocalName()))
                && isSelfQuery(cw.getQuery());
    }

    private static boolean isSelfQuery(org.apache.jena.query.Query query) {
        return query != null && SELF_QUERY.equals(query.toString());
    }

    /**
     * Creates a rule ({@code spin:rule}) comparator which sorts queries by contexts and puts declaration map rules
     * (i.e. {@code spinmap:rule} with {@code spinmap:targetPredicate1 rdf:type}) first
     * and dependent rules last.
     *
     * @return {@link Comparator} comparator for {@link CommandWrapper}s
     */
    public static Comparator<CommandWrapper> createMapComparator() {
        Comparator<Resource> mapRuleComparator = Comparator.comparing((Resource r) -> SpinModels.context(r)
                .map(String::valueOf).orElse("Unknown"))
                .thenComparing(Comparator.comparing(SpinModels::isDeclarationMapping).reversed());
        Comparator<CommandWrapper> res = (left, right) -> {
            java.util.Optional<Resource> r1 = rule(left);
            Optional<Resource> r2 = rule(right);
            return r1.isPresent() && r2.isPresent() ? mapRuleComparator.compare(r1.get(), r2.get()) : 10;
        };
        return res.thenComparing(Object::toString);
    }

    /**
     * Gets a {@code spinmap:rule} from this CommandWrapper.
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
     * @param triple    root rule triple, e.g. {@code C spinmap:rule _:x}
     * @param model     query model, must contain spin stuff (functions, templates, rules, etc),
     *                  built on top of {@link SpinModelConfig#LIB_PERSONALITY}
     * @param withClass if true {@code ?this a ?TYPE_CLASS} will be added to query
     * @param allowAsk  to allow ASK sparql
     * @return Stream of {@link CommandWrapper}s, possible empty
     * @see SPINQueryFinder#add(Map, Statement, Model, boolean, boolean)
     */
    public Stream<CommandWrapper> listCommands(Triple triple, Model model, boolean withClass, boolean allowAsk) {
        Statement statement = model.asStatement(triple);
        if (!statement.getObject().isResource()) return Stream.empty();
        TemplateCall templateCall = SPINFactory.asTemplateCall(statement.getResource());
        if (templateCall == null) {
            Command spinCommand = SPINFactory.asCommand(statement.getResource());
            if (spinCommand == null) return Stream.empty();
            CommandWrapper wrapper = createCommandWrapper(statement, withClass, allowAsk, null,
                    spinCommand.getComment(), spinCommand, spinCommand);
            if (wrapper == null) {
                return Stream.empty();
            }
            return Stream.of(wrapper);
        }
        Template baseTemplate = templateCall.getTemplate();
        if (baseTemplate == null) return Stream.empty();
        Map<String, RDFNode> bindings = templateCall.getArgumentsMapByVarNames();
        return JenaUtil.getAllSuperClassesStar(baseTemplate)
                .stream()
                .filter(r -> JenaUtil.hasIndirectType(r, SPIN.Template))
                .map(r -> r.as(Template.class))
                .filter(t -> hasAllNonOptionalArguments(t, bindings))
                .map(Module::getBody)
                .filter(body -> {
                    if (body instanceof Construct) return true;
                    if (body instanceof Update) return true;
                    return allowAsk && body instanceof Ask;
                }).map(body -> {
                    String spinQueryText = SPINLabels.get().getLabel(templateCall);
                    return createCommandWrapper(statement, withClass, allowAsk, spinQueryText, spinQueryText,
                            body, templateCall);
                })
                .filter(Objects::nonNull)
                .peek(c -> {
                    if (bindings.isEmpty()) return;
                    c.setTemplateBinding(bindings);
                });
    }

    /**
     * @param statement     the root {@link Statement} attached to the query model
     * @param withClass     boolean
     * @param allowAsk      boolean
     * @param spinQueryText String
     * @param label         String
     * @param spinCommand   {@link Command}
     * @param source        {@link Resource}
     * @return new {@link CommandWrapper}
     * @see SPINQueryFinder#createCommandWrapper(Map, Statement, boolean, boolean, String, String, Command, Resource)
     */
    protected CommandWrapper createCommandWrapper(Statement statement,
                                                  boolean withClass,
                                                  boolean allowAsk,
                                                  String spinQueryText,
                                                  String label,
                                                  Command spinCommand,
                                                  Resource source) {
        String queryString = arqFactory.createCommandString(spinCommand);
        boolean thisUnbound = spinCommand.hasProperty(SPIN.thisUnbound, JenaDatatypes.TRUE);
        if (spinQueryText == null) {
            spinQueryText = queryString;
        }
        if (spinCommand instanceof Query) {
            org.apache.jena.query.Query arqQuery = arqFactory.createQuery(queryString);
            if (arqQuery.isConstructType() || (allowAsk && arqQuery.isAskType())) {
                boolean thisDeep = NestedQueries.hasNestedBlocksUsingThis(arqQuery.getQueryPattern());
                if (isAddThisTypeClause(thisUnbound, withClass, thisDeep, spinCommand)) {
                    queryString = SPINUtil.addThisTypeClause(queryString);
                    arqQuery = arqFactory.createQuery(queryString);
                }
                return new QueryWrapper(arqQuery, source, spinQueryText,
                        (Query) spinCommand, label, statement, thisUnbound, thisDeep);
            }
        }
        if (spinCommand instanceof Update) {
            org.apache.jena.update.UpdateRequest updateRequest = arqFactory.createUpdateRequest(queryString);
            org.apache.jena.update.Update operation = updateRequest.getOperations().get(0);
            boolean thisDeep = NestedQueries.hasNestedBlocksUsingThis(operation);
            if (isAddThisTypeClause(thisUnbound, withClass, thisDeep, spinCommand)) {
                queryString = SPINUtil.addThisTypeClause(queryString);
                updateRequest = arqFactory.createUpdateRequest(queryString);
                operation = updateRequest.getOperations().get(0);
            }
            return new UpdateWrapper(operation, source, spinQueryText,
                    (Update) spinCommand, label, statement, thisUnbound, thisDeep);
        }
        return null;
    }

    private static boolean isAddThisTypeClause(boolean thisUnbound,
                                               boolean withClass,
                                               boolean thisDeep,
                                               Command command) {
        return !thisUnbound && withClass && !thisDeep && SPINUtil.containsThis((CommandWithWhere) command);
    }

    /**
     * @param template {@link Template}
     * @param bindings Map
     * @return true if template is good to use
     * @see SPINQueryFinder#hasAllNonOptionalArguments(Template, Map)
     */
    private static boolean hasAllNonOptionalArguments(Template template, Map<String, RDFNode> bindings) {
        return template.getArguments(false).stream().filter(a -> !a.isOptional())
                .map(Argument::getVarName).allMatch(bindings::containsKey);
    }

    /**
     * Runs a given Jena Query on a given individual and returns the inferred triples as a Model.
     *
     * @param query    {@link QueryWrapper} command to run
     * @param instance {@link Resource} individual to infer
     * @return a fresh in-memory {@link Model} with new triples
     * @see org.topbraid.spin.inference.SPINInferences#runQueryOnInstance(QueryWrapper, Model, Model, Resource, boolean)
     */
    public Model runQueryOnInstance(QueryWrapper query, Resource instance) {
        return runQueryOnInstance(query, instance, null);
    }

    /**
     * Runs a given Jena Query (wrapped as {@link QueryWrapper SPIN Query})
     * on a given individual (as a {@link Resource}) and
     * puts the inferred triples to the specified {@link Model} ({@code res}).
     *
     * @param query    {@link QueryWrapper} command to run, not {@code null}
     * @param instance {@link Resource} individual to infer, not {@code null}
     * @param res      {@link   Model} a storage to put new triples or {@code null} to create a fresh model
     * @return {@link Model} the same model as {@code res} or fresh one, if the {@code res} is {@code null}
     * @see org.topbraid.spin.inference.SPINInferences#runQueryOnInstance(QueryWrapper, Model, Model, Resource, boolean)
     */
    public Model runQueryOnInstance(QueryWrapper query, Resource instance, Model res) {
        if (res == null) {
            res = ModelFactory.createDefaultModel();
        }
        Model model = MapJenaException.notNull(query.getSPINQuery().getModel(), "Unattached query: " + query);
        Map<String, RDFNode> initialBindings = query.getTemplateBinding();
        QuerySolutionMap bindings = new QuerySolutionMap();
        if (initialBindings != null) {
            initialBindings.forEach(bindings::add);
        }
        bindings.add(SPIN.THIS_VAR_NAME, instance);
        QueryExecution qexec = arqFactory.createQueryExecution(query.getQuery(), model, bindings);
        return qexec.execConstruct(res);
    }

}

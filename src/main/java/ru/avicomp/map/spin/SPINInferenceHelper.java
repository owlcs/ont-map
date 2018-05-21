package ru.avicomp.map.spin;

import org.apache.jena.graph.Triple;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.sparql.function.FunctionRegistry;
import org.topbraid.spin.arq.ARQFactory;
import org.topbraid.spin.arq.SPINFunctionDrivers;
import org.topbraid.spin.model.*;
import org.topbraid.spin.model.update.Update;
import org.topbraid.spin.system.SPINLabels;
import org.topbraid.spin.util.*;
import org.topbraid.spin.vocabulary.SPIN;
import ru.avicomp.map.MapJenaException;
import ru.avicomp.map.spin.vocabulary.AVC;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An ONT-MAP replacement for several Topbraid-SPIN common classes to conduct inference.
 *
 * @see org.topbraid.spin.util.SPINQueryFinder
 * @see org.topbraid.spin.inference.SPINInferences
 */
@SuppressWarnings({"UnusedReturnValue", "WeakerAccess"})
public class SPINInferenceHelper {

    protected final ARQFactory spinARQFactory;
    protected final FunctionRegistry jenaFunctionRegistry;

    public SPINInferenceHelper(ARQFactory spinARQFactory, FunctionRegistry jenaFunctionRegistry) {
        this.spinARQFactory = spinARQFactory;
        this.jenaFunctionRegistry = jenaFunctionRegistry;
    }

    /**
     * @param triple    root rule triple, e.g. {@code C spinmap:rule _:x}
     * @param model     query model, must contain spin stuff (functions, templates, rules, etc), built on top of {@link SpinModelConfig#LIB_PERSONALITY}
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
            CommandWrapper wrapper = createCommandWrapper(statement, withClass, allowAsk, null, spinCommand.getComment(), spinCommand, spinCommand);
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
        String queryString = spinARQFactory.createCommandString(spinCommand);
        boolean thisUnbound = spinCommand.hasProperty(SPIN.thisUnbound, JenaDatatypes.TRUE);
        if (spinQueryText == null) {
            spinQueryText = queryString;
        }
        if (spinCommand instanceof Query) {
            org.apache.jena.query.Query arqQuery = spinARQFactory.createQuery(queryString);
            if (arqQuery.isConstructType() || (allowAsk && arqQuery.isAskType())) {
                boolean thisDeep = NestedQueries.hasNestedBlocksUsingThis(arqQuery.getQueryPattern());
                if (isAddThisTypeClause(thisUnbound, withClass, thisDeep, spinCommand)) {
                    queryString = SPINUtil.addThisTypeClause(queryString);
                    arqQuery = spinARQFactory.createQuery(queryString);
                }
                return new QueryWrapper(arqQuery, source, spinQueryText,
                        (Query) spinCommand, label, statement, thisUnbound, thisDeep);
            }
        }
        if (spinCommand instanceof Update) {
            org.apache.jena.update.UpdateRequest updateRequest = spinARQFactory.createUpdateRequest(queryString);
            org.apache.jena.update.Update operation = updateRequest.getOperations().get(0);
            boolean thisDeep = NestedQueries.hasNestedBlocksUsingThis(operation);
            if (isAddThisTypeClause(thisUnbound, withClass, thisDeep, spinCommand)) {
                queryString = SPINUtil.addThisTypeClause(queryString);
                updateRequest = spinARQFactory.createUpdateRequest(queryString);
                operation = updateRequest.getOperations().get(0);
            }
            return new UpdateWrapper(operation, source, spinQueryText,
                    (Update) spinCommand, label, statement, thisUnbound, thisDeep);
        }
        return null;
    }

    private static boolean isAddThisTypeClause(boolean thisUnbound, boolean withClass, boolean thisDeep, Command command) {
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
     * Runs a given Jena Query on a given instance and adds the inferred triples to a given Model.
     *
     * @param queryWrapper {@link CommandWrapper} command to run
     * @param newTriples   {@link Model} to add result of inference
     * @param instance     {@link Resource} individual to infer
     * @return true if changes were done
     * @see org.topbraid.spin.inference.SPINInferences#runQueryOnInstance(QueryWrapper, Model, Model, Resource, boolean)
     */
    public boolean runQueryOnInstance(QueryWrapper queryWrapper, Model newTriples, Resource instance) {
        Model res = runQueryOnInstance(queryWrapper, instance);
        res.listStatements().forEachRemaining(newTriples::add);
        return !res.isEmpty();
    }

    /**
     * Runs a given Jena Query on a given individual and returns the inferred triples as a Model.
     *
     * There is a difference with SPIN-API Inferences implementation:
     * in additional to passing {@code ?this} to top-level query binding (mapping construct) only
     * there is also a workaround ONT-MAP solution to place it deep in all sub-queries.
     * It is definitely leak of functionality, which severely limits the space of usage opportunities.
     * It seems that Topbraid Composer (checked version 5.5.1) has some magic solution for that leak in its deeps also:
     * testing shows that queries which handled by {@code spin:eval} may accept {@code ?this} in some conditions,
     * e.g. for original {@code spinmap:Mapping-1-1}, which has no been cloned to local mapping model.
     *
     * @param query    {@link QueryWrapper}
     * @param instance {@link Resource}
     * @return {@link Model} new triples
     * @see org.topbraid.spin.inference.SPINInferences#runQueryOnInstance(QueryWrapper, Model, Model, Resource, boolean)
     * @see AVC#currentIndividual
     */
    public Model runQueryOnInstance(QueryWrapper query, Resource instance) {
        Model model = MapJenaException.notNull(query.getSPINQuery().getModel(), "Unattached query: " + query);
        //ARQFactory.LOG_QUERIES = true;
        Resource get = AVC.currentIndividual.inModel(model);
        Map<Statement, Statement> vars = getThisVarReplacement(get, instance);
        try {
            vars.forEach((a, b) -> model.add(b).remove(a));
            if (!vars.isEmpty()) {
                spinARQFactory.clearCaches();
                jenaFunctionRegistry.put(get.getURI(), SPINFunctionDrivers.get().create(get));
            }
            Map<String, RDFNode> initialBindings = query.getTemplateBinding();
            QuerySolutionMap bindings = new QuerySolutionMap();
            if (initialBindings != null) {
                initialBindings.forEach(bindings::add);
            }
            bindings.add(SPIN.THIS_VAR_NAME, instance);
            QueryExecution qexec = spinARQFactory.createQueryExecution(query.getQuery(), model, bindings);
            return qexec.execConstruct();
        } finally {
            vars.forEach((a, b) -> model.add(a).remove(b));
        }
    }

    private static Map<Statement, Statement> getThisVarReplacement(Resource function, Resource instance) {
        Model m = function.getModel();
        return SpinModels.getFunctionBody(m, function).stream()
                .filter(s -> Objects.equals(s.getObject(), SPIN._this))
                .collect(Collectors.toMap(x -> x, x -> m.createStatement(x.getSubject(), x.getPredicate(), instance)));
    }

}

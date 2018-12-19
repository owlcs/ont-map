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

import org.apache.jena.atlas.io.IndentedWriter;
import org.apache.jena.enhanced.UnsupportedPolymorphismException;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.core.*;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.expr.ExprEvalException;
import org.apache.jena.sparql.expr.ExprList;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.function.FunctionEnv;
import org.apache.jena.sparql.function.FunctionFactory;
import org.apache.jena.sparql.function.FunctionRegistry;
import org.apache.jena.sparql.pfunction.PropertyFunctionFactory;
import org.apache.jena.sparql.pfunction.PropertyFunctionRegistry;
import org.apache.jena.sparql.serializer.SerializationContext;
import org.apache.jena.sparql.sse.SSE;
import org.apache.jena.sparql.util.Context;
import org.apache.jena.sparql.util.ExprUtils;
import org.apache.jena.sparql.util.FmtUtils;
import org.apache.jena.update.UpdateRequest;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPIN;
import ru.avicomp.map.MapJenaException;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An extended spin implementation, which is tightly bound with the {@link Context Jena Context}.
 * This approach is designed to split ARQ registers by managers
 * so that each manager has its own list of functions inside and cannot affects others.
 * <p>
 * Created by @szuev on 20.06.2018.
 *
 * @see org.topbraid.spin.arq.ARQFactory
 * @see FunctionRegistry
 * @see PropertyFunctionRegistry
 */
@SuppressWarnings({"WeakerAccess", "unused", "UnusedReturnValue"})
public class MapARQFactory extends org.topbraid.spin.arq.ARQFactory {
    private final Context context;

    public MapARQFactory(Context context) {
        this.context = Objects.requireNonNull(context, "Null context");
    }

    /**
     * Creates a fresh {@link MapARQFactory} with a new {@link Context} inside,
     * which contains all functions and property functions from the system-wide ARQ context
     * and also all available spin and spif functions.
     *
     * @return {@link MapARQFactory}
     */
    public static MapARQFactory createSPINARQFactory() {
        Context context = copyContext(ARQ.getContext());
        SPINRegistry.putAll(context);
        return new MapARQFactory(context);
    }

    /**
     * Creates a deep copy of the specified {@link Context}.
     *
     * @param from {@link Context} to copy from
     * @return {@link Context} new instance with the same content as in the given context
     */
    public static Context copyContext(Context from) {
        FunctionRegistry fr = copy(FunctionRegistry.get(from));
        PropertyFunctionRegistry pfr = copy(PropertyFunctionRegistry.get(from));
        Context res = new Context(from) {

            @Override
            public String toString() {
                return String.format("%s:::%s", MapARQFactory.class.getSimpleName(), super.toString());
            }
        };
        FunctionRegistry.set(res, fr);
        PropertyFunctionRegistry.set(res, pfr);
        return res;
    }

    /**
     * Copies a {@link FunctionRegistry} to a new one.
     *
     * @param from registry to copy from, not {@code null}
     * @return new instance with the same content
     */
    public static FunctionRegistry copy(FunctionRegistry from) {
        FunctionRegistry res = new FunctionRegistry();
        from.keys().forEachRemaining(k -> res.put(k, from.get(k)));
        return res;
    }

    /**
     * Copies a {@link PropertyFunctionRegistry} to a new one.
     *
     * @param from registry to copy from, not {@code null}
     * @return new instance with the same content
     */
    public static PropertyFunctionRegistry copy(PropertyFunctionRegistry from) {
        PropertyFunctionRegistry res = new PropertyFunctionRegistry();
        from.keys().forEachRemaining(k -> res.put(k, from.get(k)));
        return res;
    }

    /**
     * Returns the {@link Context Jena Context}, that is associated with this factory.
     *
     * @return {@link Context}, not {@code null}
     */
    public Context getContext() {
        return context;
    }

    /**
     * Returns the {@link FunctionRegistry Jena Function Registry}, that is associated with this factory.
     * @return {@link FunctionRegistry}
     */
    public FunctionRegistry getFunctionRegistry() {
        return FunctionRegistry.get(context);
    }

    /**
     * Returns the {@link PropertyFunctionRegistry Jena Property Function Registry},
     * that is associated with this factory.
     * @return {@link PropertyFunctionRegistry}
     */
    public PropertyFunctionRegistry getPropertyFunctionRegistry() {
        return PropertyFunctionRegistry.get(context);
    }

    @Override
    public Dataset getDataset(Model m) {
        DatasetGraph dg = new DatasetGraphMapLink(m.getGraph()) {
            @Override
            public Context getContext() {
                return context;
            }
        };
        return DatasetFactory.wrap(dg);
    }

    @SuppressWarnings("unchecked")
    private <O> O getPrivateField(String name) {
        try {
            Field f = org.topbraid.spin.arq.ARQFactory.class.getDeclaredField(name);
            f.setAccessible(true);
            return (O) f.get(this);
        } catch (NoSuchFieldException | IllegalAccessException | ClassCastException e) {
            throw new IllegalStateException("Can access field " + name, e);
        }
    }

    protected Map<Node, String> node2String() {
        return getPrivateField("node2String");
    }

    protected Map<String, Query> string2Query() {
        return getPrivateField("string2Query");
    }

    protected Map<String, UpdateRequest> string2Update() {
        return getPrivateField("string2Update");
    }

    /**
     * Auxiliary method to replace ARQ implementation in runtime.
     *
     * @param inModel {@link Resource} that has {@code rdf:type = spin:Function} in a model
     *                with {@link SpinModelConfig#LIB_PERSONALITY Spin Personality} attached.
     * @throws UnsupportedPolymorphismException in case of incompatible resource in model
     */
    public void replace(Resource inModel) throws UnsupportedPolymorphismException {
        org.topbraid.spin.model.Function func = inModel.as(org.topbraid.spin.model.Function.class);
        clearCaches();
        getFunctionRegistry().put(func.getURI(), asARQFunction(func));
    }

    /**
     * Registers the given {@link org.topbraid.spin.model.Function Spin Function} as an ARQ-function.
     * If the provided {@code Function} has an executable body (i.e. {@code spin:body}) and
     * it is not a magic property (i.e. has no type {@code spin:MagicProperty}), then
     * adds a new ARQ-function for it within the current {@link #getFunctionRegistry() FunctionRegistry}.
     * If there is already a registered function with the same URI,
     * and it is also a {@link org.topbraid.spin.arq.SPINFunctionFactory SPIN Function Factory} instance,
     * then replaces an existing ARQ-function with a new one.
     *
     * @param func {@link org.apache.jena.sparql.function.Function} the function to register
     * @return {@link FunctionFactory} or {@code null} if the function has not been registered
     * @see org.topbraid.spin.system.SPINModuleRegistry#registerARQFunction(org.topbraid.spin.model.Function)
     */
    @SuppressWarnings("JavadocReference")
    public FunctionFactory registerFunction(org.topbraid.spin.model.Function func) {
        FunctionRegistry reg = getFunctionRegistry();
        // notice that FunctionRegistry works in lazy manner: it can also init auto-loaded functions while get
        FunctionFactory old = reg.get(func.getURI());
        if (!func.hasProperty(SPIN.body) || func.isMagicProperty()) {
            return null;
        }
        // never overwrite native Jena ARQ java functions
        if (old != null && !(old instanceof org.topbraid.spin.arq.SPINFunctionFactory)) {
            return null;
        }
        FunctionFactory res = asARQFunction(func);
        reg.put(func.getURI(), res);
        return res;
    }

    /**
     * Registers the given {@link org.topbraid.spin.model.Function Spin Function} as an ARQ-property-function.
     * If the provided {@code Function} has an executable body (i.e. {@code spin:body}) and
     * it is a magic property (has type {@code spin:MagicProperty}),
     * then adds a new ARQ-property-function for it within
     * the current {@link #getPropertyFunctionRegistry() PropertyFunctionRegistry}.
     * If there is already a registered function with the same URI,
     * and it is also a {@link org.topbraid.spin.arq.SPINARQPFunction SPIN Property Function} instance,
     * then replaces an existing ARQ-property-function with a new one.
     *
     * @param func {@link org.topbraid.spin.model.Function} the property function to register
     * @return {@link PropertyFunctionFactory} or null if function was not registered
     * @see org.topbraid.spin.system.SPINModuleRegistry#registerARQPFunction(org.topbraid.spin.model.Function)
     */
    public PropertyFunctionFactory registerProperty(org.topbraid.spin.model.Function func) {
        PropertyFunctionRegistry reg = getPropertyFunctionRegistry();
        // notice that PropertyFunctionRegistry works in lazy manner:
        // it can also init auto-loaded property functions while get
        PropertyFunctionFactory old = reg.get(func.getURI());
        if (!func.hasProperty(SPIN.body) || !func.isMagicProperty()) {
            return null;
        }
        if (old != null && !(old instanceof org.topbraid.spin.arq.SPINARQPFunction)) {
            return null;
        }
        PropertyFunctionFactory res = asARQPropertyFunction(func);
        reg.put(func.getURI(), res);
        return res;
    }

    /**
     * Wraps a spin function as ARQ function-factory.
     *
     * @param func {@link org.topbraid.spin.model.Function}
     * @return {@link FunctionFactory}
     */
    public FunctionFactory asARQFunction(org.topbraid.spin.model.Function func) {
        return new ARQFunction(Objects.requireNonNull(func, "Null function"));
    }

    /**
     * Wraps the given spin function as ARQ property function-factory.
     * Since we, in ONT-MAP API, do not care much about (magic) property functions
     * (we just don't need them in bounds of ontology mapping terms),
     * there is no our own {@code ARQPropertyFunction} implementation.
     * But the original {@link org.topbraid.spin.arq.SPINARQPFunction SPIN ARQ Property Function}
     * implementation uses a global {@link org.topbraid.spin.arq.ARQFactory arq-factory} and other global things,
     * so when property functions will be really used,
     * this temporary solution must be replaced by the our implementation (TODO).
     *
     * @param func {@link org.topbraid.spin.model.Function}
     * @return {@link PropertyFunctionFactory}
     */
    public PropertyFunctionFactory asARQPropertyFunction(org.topbraid.spin.model.Function func) {
        if (!MapJenaException.notNull(func, "Null function").isMagicProperty())
            throw new MapJenaException.IllegalArgument("Function <" + func.getURI() + "> is not a magic property");
        return org.topbraid.spin.arq.SPINARQPFunctionFactory.get().create(func);
    }

    /**
     * Creates the "physical" Jena Query instance using the given string query representation and prefixes.
     * The returning {@code Query} is in {@link Syntax#syntaxARQ ARQ Syntax}, which is an extended SPARQL 1.1 syntax.
     *
     * @param query the parsable query string, not {@code null}
     * @param pm    {@link PrefixMapping prefixes}, possible {@code null}
     * @return {@link org.apache.jena.query.Query Jena Query}, not {@code null}
     */
    public org.apache.jena.query.Query createQuery(String query, PrefixMapping pm) {
        return doCreateQuery(query, pm);
    }

    /**
     * A modified copy-paste of {@link org.topbraid.spin.arq.SPINARQFunction}
     * in order to get rid of the use the global settings:
     * in the original impl (and everywhere throughout the spin api library) there are calls of static factories,
     * which is a bad architectural solution, though usual for spin-api.
     * In contrast to the spin implementation
     * it does not support {@code spin:cachable} and {@code spin:cachableForOntologies},
     * since that constructions are unused in spin-map library and it does not make sense in our case -
     * we'd better use our own caches if needed.
     *
     * @see org.topbraid.spin.arq.SPINARQFunction
     */
    @SuppressWarnings({"WeakerAccess", "unused"})
    public class ARQFunction implements org.apache.jena.sparql.function.Function,
            org.topbraid.shacl.arq.OptionalArgsFunction,
            org.topbraid.spin.arq.SPINFunctionFactory {

        protected org.apache.jena.query.Query query;
        protected List<org.topbraid.spin.model.Argument> args;
        protected String queryString;

        protected final org.topbraid.spin.model.Function spin;
        protected final org.topbraid.spin.system.SPINArgumentChecker argumentChecker;
        protected final org.topbraid.spin.statistics.SPINStatisticsManager statisticsManager;

        /**
         * Constructs a new ARQFunction based on a given SPIN Function.
         * The spinFunction model be associated with the Model containing the triples of its definition.
         *
         * @param spin the SPIN function
         */
        public ARQFunction(org.topbraid.spin.model.Function spin) {
            this(spin, org.topbraid.spin.system.SPINArgumentChecker.get(), org.topbraid.spin.statistics.SPINStatisticsManager.get());
        }

        public ARQFunction(org.topbraid.spin.model.Function spin,
                           org.topbraid.spin.system.SPINArgumentChecker argumentChecker,
                           org.topbraid.spin.statistics.SPINStatisticsManager statistics) {
            this.argumentChecker = argumentChecker;
            this.statisticsManager = statistics;
            this.spin = spin;
            this.args = spin.getArguments(true);
            if (args.stream().map(org.topbraid.spin.model.Argument::getVarName).anyMatch(Objects::isNull)) {
                throw new MapJenaException.IllegalArgument("Some of the function <" + spin.getURI() + "> " +
                        "arguments have not a valid predicate");
            }
            try {
                org.topbraid.spin.model.Query spinQuery = (org.topbraid.spin.model.Query) spin.getBody();
                queryString = MapARQFactory.this.createCommandString(spinQuery);
                query = MapARQFactory.this.createQuery(queryString);
            } catch (Exception ex) {
                throw new MapJenaException.IllegalArgument("Function <" + spin.getURI() + "> " +
                        "does not define a valid body", ex);
            }
        }

        @Override
        public void build(String uri, ExprList args) {
        }

        @Override
        public org.apache.jena.sparql.function.Function create(String uri) {
            return this;
        }

        @Override
        public NodeValue exec(Binding binding, ExprList args, String uri, FunctionEnv env) throws ExprEvalException {
            Graph activeGraph = env.getActiveGraph();
            Model model = activeGraph != null ? ModelFactory.createModelForGraph(activeGraph) : ModelFactory.createDefaultModel();

            QuerySolutionMap bindings = new QuerySolutionMap();
            Node t = binding.get(Var.alloc(SPIN.THIS_VAR_NAME));
            if (t != null) {
                bindings.add(SPIN.THIS_VAR_NAME, model.asRDFNode(t));
            }

            for (int i = 0; i < args.size(); i++) {
                Expr expr = args.get(i);
                if (expr == null || (expr.isVariable() && !binding.contains(expr.asVar()))) {
                    continue;
                }
                NodeValue x = expr.eval(binding, env);
                if (x == null) {
                    continue;
                }
                String argName;
                if (i < this.args.size()) {
                    argName = this.args.get(i).getVarName();
                } else {
                    argName = SP.ARG + (i + 1);
                }
                bindings.add(argName, model.asRDFNode(x.asNode()));
            }

            if (argumentChecker != null) {
                argumentChecker.check(spin, bindings);
            }
            Dataset dataset = DatasetImpl.wrap(env.getDataset());
            if (statisticsManager == null || !statisticsManager.isRecording() || !statisticsManager.isRecordingSPINFunctions()) {
                return executeBody(dataset, model, bindings);
            }
            StringBuilder sb = new StringBuilder();
            sb.append("SPIN Function ");
            sb.append(SSE.str(NodeFactory.createURI(uri), model));
            sb.append("(");
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                Expr expr = args.get(i);
                expr = Substitute.substitute(expr, binding);
                if (expr == null) {
                    sb.append("?unbound");
                } else {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    IndentedWriter iOut = new IndentedWriter(bos);
                    ExprUtils.fmtSPARQL(iOut, expr, new SerializationContext(model));
                    iOut.flush();
                    sb.append(bos.toString());
                }
            }
            sb.append(")");
            long startTime = System.currentTimeMillis();
            NodeValue result;
            try {
                result = executeBody(dataset, model, bindings);
                sb.append(" = ");
                sb.append(FmtUtils.stringForNode(result.asNode(), model));
            } catch (ExprEvalException ex) {
                sb.append(" : ");
                sb.append(ex.getLocalizedMessage());
                throw ex;
            } finally {
                long endTime = System.currentTimeMillis();
                org.topbraid.spin.statistics.SPINStatistics stats =
                        new org.topbraid.spin.statistics.SPINStatistics(sb.toString(),
                                queryString,
                                endTime - startTime,
                                startTime,
                                NodeFactory.createURI(uri));
                statisticsManager.addSilently(Collections.singleton(stats));
            }
            return result;
        }

        public NodeValue executeBody(Model model, QuerySolution bindings) {
            return executeBody(null, model, bindings);
        }

        protected QueryExecution createQueryExecution(Dataset dataset, Model model, QuerySolution bindings) {
            if (dataset == null) {
                return MapARQFactory.this.createQueryExecution(query, model, bindings);
            }
            Dataset newDataset = new org.topbraid.spin.arq.DatasetWithDifferentDefaultModel(model, dataset);
            return MapARQFactory.this.createQueryExecution(query, newDataset, bindings);
        }

        public NodeValue executeBody(Dataset dataset, Model defaultModel, QuerySolution bindings) throws ExprEvalException {
            try (QueryExecution qexec = createQueryExecution(dataset, defaultModel, bindings)) {
                if (query.isAskType()) {
                    return NodeValue.makeBoolean(qexec.execAsk());
                }
                if (!query.isSelectType()) {
                    throw new ExprEvalException("Body must be ASK or SELECT query");
                }
                ResultSet rs = qexec.execSelect();
                if (rs.hasNext()) {
                    QuerySolution s = rs.nextSolution();
                    List<String> resultVars = rs.getResultVars();
                    String varName = resultVars.get(0);
                    RDFNode resultNode = s.get(varName);
                    if (resultNode != null) {
                        return NodeValue.makeNode(resultNode.asNode());
                    }
                }
                throw new ExprEvalException("Empty result set for SPIN function");
            }
        }

        /**
         * Gets the Jena Query object for execution.
         *
         * @return the Jena Query
         */
        public org.apache.jena.query.Query getBodyQuery() {
            return query;
        }

        public org.topbraid.spin.model.Function getSPINFunction() {
            return spin;
        }

        @Override
        public boolean isOptionalArg(int index) {
            return args.get(index).isOptional();
        }

        @Override
        public String toString() {
            return String.format("%s{func=<%s>, query='%s'}", getClass().getSimpleName(), spin.getURI(), queryString);
        }
    }
}

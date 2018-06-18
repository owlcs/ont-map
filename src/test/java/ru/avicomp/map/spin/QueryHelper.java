package ru.avicomp.map.spin;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.Query;
import org.apache.jena.query.SortCondition;
import org.apache.jena.rdf.model.*;
import org.apache.jena.sparql.algebra.table.TableData;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.core.VarExprList;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.expr.*;
import org.apache.jena.sparql.expr.aggregate.AggGroupConcat;
import org.apache.jena.sparql.expr.aggregate.AggGroupConcatDistinct;
import org.apache.jena.sparql.expr.aggregate.Aggregator;
import org.apache.jena.sparql.modify.request.*;
import org.apache.jena.sparql.path.*;
import org.apache.jena.sparql.serializer.SerializationContext;
import org.apache.jena.sparql.syntax.Element;
import org.apache.jena.sparql.syntax.*;
import org.apache.jena.sparql.syntax.ElementGroup;
import org.apache.jena.sparql.syntax.Template;
import org.apache.jena.update.UpdateRequest;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.topbraid.spin.arq.ARQFactory;
import org.topbraid.spin.arq.AbstractElementVisitor;
import org.topbraid.spin.model.*;
import org.topbraid.spin.model.update.*;
import org.topbraid.spin.query.UpdateFactoryFilter;
import org.topbraid.spin.system.ExtraPrefixes;
import org.topbraid.spin.system.SPINPreferences;
import org.topbraid.spin.util.JenaDatatypes;
import org.topbraid.spin.util.JenaUtil;
import org.topbraid.spin.util.SPTextUtil;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPIN;
import ru.avicomp.ontapi.jena.utils.Iter;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A copy-pasted TopBraid-SPIN class to avoid stupid static initialization.
 * Takes a ARQ SPARQL Query as input and creates a corresponding SPIN RDF data structure from it.
 *
 * @author Holger Knublauch
 * @see org.topbraid.spin.arq.ARQ2SPIN
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class QueryHelper {

    private static Map<String, List<Resource>> symbolsMap = loadSymbols();
    private boolean addPrefixes;
    private Model model;
    private String varNamespace = SP.NS;
    private Map<String, Resource> var2Resource = new HashMap<String, Resource>();

    /**
     * Constructs a new ARQ2SPIN engine for a given Model,
     * equivalent with <code>ARQ2SPIN(model, true)</code>.
     *
     * @param model the Model to operate on
     */
    public QueryHelper(Model model) {
        this(model, true);
    }

    /**
     * Constructs a new ARQ2SPIN engine for a given Model.
     *
     * @param model       the Model to operate on
     * @param addPrefixes true to also let the system add missing
     *                    prefixes mentioned in SPARQL expressions
     *                    (e.g. the afn namespace if afn:now() is used)
     */
    public QueryHelper(Model model, boolean addPrefixes) {
        this.model = model;
        this.addPrefixes = addPrefixes;

        // Pre-populate with named variables
        JenaUtil.setGraphReadOptimization(true);
        try {
            StmtIterator it = model.listStatements(null, SP.varName, (RDFNode) null);
            while (it.hasNext()) {
                Resource variable = it.nextStatement().getSubject();
                if (variable.isURIResource()) {
                    if (SPINPreferences.get().isCreateURIVariables() ||
                            variable.getURI().startsWith(SP.NS + "arg") ||
                            SPIN.NS.equals(variable.getNameSpace())) {
                        Variable var = variable.as(Variable.class);
                        String name = var.getName();
                        var2Resource.put(name, var);
                    }
                }
            }
        } finally {
            JenaUtil.setGraphReadOptimization(false);
        }
    }

    public static Map<String, List<Resource>> loadSymbols() {
        Map<String, List<Resource>> res = new HashMap<>();
        Model m = SpinModelConfig.createSpinModel(SPINLibrary.SPL.getGraph());
        Iter.asStream(m.listStatements(null, SPIN.symbol, (RDFNode) null))
                .filter(s -> s.getObject().isLiteral())
                .filter(s -> s.getSubject().isURIResource())
                .forEach(s -> {
                    String symbol = s.getLiteral().getLexicalForm().toLowerCase();
                    res.computeIfAbsent(symbol, k -> new ArrayList<>(1)).add(s.getSubject());
                });
        Stream.of("not in", "not exists").filter(res::containsKey).forEach(s -> res.put(s.replace(" ", ""), res.get(s)));
        return res;
    }

    public static String getTextOnly(Resource spinCommand) {
        // Return sp:text if this is the only property of the command apart from the rdf:type triple
        Statement s = spinCommand.getProperty(SP.text);
        if (s != null) {
            if (SPTextUtil.hasSPINRDF(spinCommand)) {
                return null;
            } else {
                return s.getString();
            }
        }
        return null;
    }

    /**
     * Parses a given partial query string and converts it into a SPIN structure
     * inside a given Model.
     *
     * @param str   the partial query string
     * @param model the Model to operate on
     * @return the new SPIN Query
     */
    public static org.topbraid.spin.model.Query parseQuery(String str, Model model) {
        Query arq = ARQFactory.get().createQuery(model, str);
        QueryHelper a2s = new QueryHelper(model);
        return a2s.createQuery(arq, null);
    }

    /**
     * Parses a given partial UPDATE string and converts it into a SPIN structure
     * inside a given Model.
     *
     * @param str   the partial UPDATE string
     * @param model the Model to operate on
     * @return the new SPIN Query
     */
    public static org.topbraid.spin.model.update.Update parseUpdate(String str, Model model) {
        String prefixes = ARQFactory.get().createPrefixDeclarations(model);
        UpdateRequest request = UpdateFactoryFilter.get().create(prefixes + str);
        QueryHelper a2s = new QueryHelper(model);
        return a2s.createUpdate(request.getOperations().get(0), null);
    }

    private void addClearOrDropProperties(UpdateDropClear arqClear, Update spinUpdate) {
        Target target = arqClear.getTarget();
        if (target.isAll()) {
            spinUpdate.addProperty(SP.all, JenaDatatypes.TRUE);
        } else if (target.isAllNamed()) {
            spinUpdate.addProperty(SP.named, JenaDatatypes.TRUE);
        } else if (target.isDefault()) {
            spinUpdate.addProperty(SP.default_, JenaDatatypes.TRUE);
        } else if (target.isOneNamedGraph()) {
            spinUpdate.addProperty(SP.graphIRI, model.asRDFNode(target.getGraph()));
        }
        if (arqClear.isSilent()) {
            spinUpdate.addProperty(SP.silent, JenaDatatypes.TRUE);
        }
    }

    private void addDescribeProperties(Query arq, Resource spinQuery) {
        if (!arq.isQueryResultStar()) {
            List<Resource> members = new LinkedList<>();
            for (String varName : arq.getResultVars()) {
                Resource variable = getVariable(varName);
                members.add(variable);
            }
            for (Node uriNode : arq.getResultURIs()) {
                members.add(model.getResource(uriNode.getURI()));
            }
            spinQuery.addProperty(SP.resultNodes, model.createList(members.iterator()));
        }
    }

    private void addGroupBy(Query arq, Resource spinQuery) {
        VarExprList namedExprs = arq.getGroupBy();
        Iterator<Var> vit = namedExprs.getVars().iterator();
        if (vit.hasNext()) {
            List<RDFNode> members = new LinkedList<>();
            while (vit.hasNext()) {
                Var var = vit.next();
                Expr expr = namedExprs.getExpr(var);
                if (expr == null) {
                    String varName = var.getName();
                    Resource variable = getVariable(varName);
                    members.add(variable);
                } else {
                    throw new IllegalArgumentException("Expressions not supported in GROUP BY");
                }
            }
            spinQuery.addProperty(SP.groupBy, model.createList(members.iterator()));
        }
    }

    private void addNamedGraphClauses(Query arq, Resource spinQuery) {
        for (String graphURI : arq.getGraphURIs()) {
            spinQuery.addProperty(SP.from, model.getResource(graphURI));
        }
        for (String namedGraphURI : arq.getNamedGraphURIs()) {
            spinQuery.addProperty(SP.fromNamed, model.getResource(namedGraphURI));
        }
    }

    private void addSelectProperties(Query arq, Resource spinQuery) {
        if (arq.isDistinct()) {
            spinQuery.addProperty(SP.distinct, model.createTypedLiteral(true));
        }
        if (arq.isReduced()) {
            spinQuery.addProperty(SP.reduced, model.createTypedLiteral(true));
        }
        if (arq.hasHaving()) {
            List<Expr> havings = arq.getHavingExprs();
            List<RDFNode> spinExprs = new LinkedList<>();
            for (Expr expr : havings) {
                RDFNode e = createExpression(expr);
                spinExprs.add(e);
            }
            spinQuery.addProperty(SP.having, model.createList(spinExprs.iterator()));
        }
        if (!arq.isQueryResultStar()) {
            List<RDFNode> members = new LinkedList<>();
            VarExprList namedExprs = arq.getProject();
            for (Var var : namedExprs.getVars()) {
                Expr expr = namedExprs.getExpr(var);
                if (expr == null) {
                    String varName = var.getName();
                    Resource variable = getVariable(varName);
                    members.add(variable);
                } else {
                    RDFNode e = createExpression(expr);
                    if (var.isAllocVar()) {
                        members.add(e);
                    } else {
                        // Create a new blank node variable wrapping the sp:expression
                        String varName = var.getName();
                        Variable variable = SPINFactory.createVariable(model, varName);
                        variable.addProperty(SP.expression, e);
                        members.add(variable);
                    }
                }
            }
            spinQuery.addProperty(SP.resultVariables, model.createList(members.iterator()));
        }
        addSolutionModifiers(arq, spinQuery);
    }

    private void addSolutionModifiers(Query arq, Resource query) {
        long limit = arq.getLimit();
        if (limit != Query.NOLIMIT) {
            query.addProperty(SP.limit, query.getModel().createTypedLiteral(limit));
        }
        long offset = arq.getOffset();
        if (offset != Query.NOLIMIT) {
            query.addProperty(SP.offset, query.getModel().createTypedLiteral(offset));
        }
        List<SortCondition> orderBy = arq.getOrderBy();
        if (orderBy != null && !orderBy.isEmpty()) {
            List<RDFNode> criteria = new LinkedList<>();
            for (SortCondition sortCondition : orderBy) {
                Expr expr = sortCondition.getExpression();
                RDFNode node = createExpression(expr);
                if (sortCondition.getDirection() == Query.ORDER_ASCENDING) {
                    Resource asc = query.getModel().createResource(SP.Asc);
                    asc.addProperty(SP.expression, node);
                    criteria.add(asc);
                } else if (sortCondition.getDirection() == Query.ORDER_DESCENDING) {
                    Resource desc = query.getModel().createResource(SP.Desc);
                    desc.addProperty(SP.expression, node);
                    criteria.add(desc);
                } else {
                    criteria.add(node);
                }
            }
            query.addProperty(SP.orderBy, query.getModel().createList(criteria.iterator()));
        }
    }

    private void addValues(Query arq, Resource spinQuery) {
        if (arq.hasValues()) {
            List<Var> vars = arq.getValuesVariables();
            List<Binding> bindings = arq.getValuesData();
            Values values = SPINFactory.createValues(model, new TableData(vars, bindings), true);
            spinQuery.addProperty(SP.values, values);
        }
    }

    private Resource createAggregation(Var var, String str, Resource type) {
        Resource agg = model.createResource(type);
        int start = str.indexOf('(');
        str = str.substring(start + 1);
        if (str.toLowerCase().startsWith("distinct")) {
            agg.addProperty(SP.distinct, model.createTypedLiteral(true));
            str = str.substring(8).trim(); // Bypass distinct
        }
        if (!str.equals("*)")) {
            str = str.substring(0, str.length() - 1);
            RDFNode expr = Expressions.parseExpression(str, model);
            agg.addProperty(SP.expression, expr);
        }
        if (!var.isAllocVar()) {
            agg.addProperty(SP.as, getVariable(var.getName()));
        }
        return agg;
    }

    private Clear createClear(UpdateClear arqClear, String uri) {
        Clear spinClear = model.createResource(uri, SP.Clear).as(Clear.class);
        addClearOrDropProperties(arqClear, spinClear);
        return spinClear;
    }

    private Create createCreate(UpdateCreate arqCreate, String uri) {
        Create spinCreate = model.createResource(uri, SP.Create).as(Create.class);
        if (arqCreate.isSilent()) {
            spinCreate.addProperty(SP.silent, JenaDatatypes.TRUE);
        }
        Node graph = arqCreate.getGraph();
        spinCreate.addProperty(SP.graphIRI, model.asRDFNode(graph));
        return spinCreate;
    }

    private DeleteData createDeleteData(UpdateDataDelete arq, String uri) {
        DeleteData spin = model.createResource(uri, SP.DeleteData).as(DeleteData.class);
        spin.addProperty(SP.data, createQuadsList(arq.getQuads()));
        return spin;
    }

    private DeleteWhere createDeleteWhere(UpdateDeleteWhere arqDeleteWhere, String uri) {
        DeleteWhere spinDeleteWhere = model.createResource(uri, SP.DeleteWhere).as(DeleteWhere.class);
        Resource where = createQuadsList(arqDeleteWhere.getQuads());
        spinDeleteWhere.addProperty(SP.where, where);
        return spinDeleteWhere;
    }

    private Drop createDrop(UpdateDrop arqDrop, String uri) {
        Drop spinDrop = model.createResource(uri, SP.Drop).as(Drop.class);
        addClearOrDropProperties(arqDrop, spinDrop);
        return spinDrop;
    }

    /**
     * Creates a SPIN ElementList from a given ARQ Element pattern.
     *
     * @param pattern the ARQ pattern to convert to SPIN
     * @return a SPIN ElementList
     */
    public ElementList createElementList(Element pattern) {
        final List<Resource> members = new LinkedList<>();
        if (pattern != null) {
            pattern.visit(new AbstractElementVisitor() {

                private boolean first = true;

                @Override
                public void visit(ElementAssign assign) {
                    RDFNode expression = createExpression(assign.getExpr());
                    Variable variable = getVariable(assign.getVar().getName()).as(Variable.class);
                    members.add(SPINFactory.createBind(model, variable, expression));
                }

                @Override
                public void visit(ElementBind bind) {
                    RDFNode expression = createExpression(bind.getExpr());
                    Variable variable = getVariable(bind.getVar().getName()).as(Variable.class);
                    members.add(SPINFactory.createBind(model, variable, expression));
                }


                @Override
                public void visit(ElementData data) {
                    members.add(SPINFactory.createValues(model, data.getTable(), false));
                }

                @Override
                public void visit(ElementExists exists) {
                    Element element = exists.getElement();
                    ElementList body = createElementList(element);
                    Exists e = SPINFactory.createExists(model, body);
                    members.add(e);
                }


                @Override
                public void visit(ElementFilter filter) {
                    RDFNode expression = createExpression(filter.getExpr());
                    members.add(SPINFactory.createFilter(model, expression));
                }


                @Override
                public void visit(ElementGroup group) {
                    if (first) {
                        first = false;
                        super.visit(group);
                    } else {
                        ElementList list = createElementList(group);
                        members.add(list);
                    }
                }


                @Override
                public void visit(ElementMinus minus) {
                    Element element = minus.getMinusElement();
                    ElementList body = createElementList(element);
                    Minus spinMinus = SPINFactory.createMinus(model, body);
                    members.add(spinMinus);
                }


                @Override
                public void visit(ElementNamedGraph namedGraph) {
                    Resource graphNameNode;
                    Node nameNode = namedGraph.getGraphNameNode();
                    if (nameNode.isVariable()) {
                        graphNameNode = getVariable(nameNode.getName());
                    } else {
                        graphNameNode = model.getResource(nameNode.getURI());
                    }
                    Element element = namedGraph.getElement();
                    RDFList elements = createElementList(element);
                    NamedGraph ng = SPINFactory.createNamedGraph(model, graphNameNode, elements);
                    members.add(ng);
                }


                @Override
                public void visit(ElementNotExists notExists) {
                    Element element = notExists.getElement();
                    ElementList body = createElementList(element);
                    NotExists ne = SPINFactory.createNotExists(model, body);
                    members.add(ne);
                }


                @Override
                public void visit(ElementOptional optional) {
                    Element element = optional.getOptionalElement();
                    ElementList body = createElementList(element);
                    org.topbraid.spin.model.Optional o = SPINFactory.createOptional(model, body);
                    members.add(o);
                }


                @Override
                public void visit(ElementPathBlock block) {
                    visitElements(block.patternElts());
                }


                @Override
                public void visit(ElementService service) {
                    Node node = service.getServiceNode();
                    Resource uri;
                    if (node.isVariable()) {
                        uri = getVariable(node.getName());
                    } else {
                        uri = model.getResource(node.getURI());
                    }
                    Element element = service.getElement();
                    ElementList body = createElementList(element);
                    members.add(SPINFactory.createService(model, uri, body));
                }


                @Override
                public void visit(ElementSubQuery subQuery) {
                    Query arq = subQuery.getQuery();
                    org.topbraid.spin.model.Query spinQuery = createQuery(arq, null);
                    members.add(SPINFactory.createSubQuery(model, spinQuery));
                }


                @Override
                public void visit(ElementTriplesBlock el) {
                    visitElements(el.patternElts());
                }


                @Override
                public void visit(ElementUnion arqUnion) {
                    List<Element> arqElements = arqUnion.getElements();
                    List<RDFNode> elements = new LinkedList<>();
                    for (Element arqElement : arqElements) {
                        RDFList element = createElementList(arqElement);
                        elements.add(element);
                    }
                    Union union = model.createResource(SP.Union).as(Union.class);
                    union.addProperty(SP.elements, model.createList(elements.iterator()));
                    members.add(union);
                }


                @SuppressWarnings("rawtypes")
                private void visitElements(Iterator it) {
                    while (it.hasNext()) {
                        Object next = it.next();
                        if (next instanceof org.apache.jena.sparql.core.TriplePath) {
                            org.apache.jena.sparql.core.TriplePath path = (org.apache.jena.sparql.core.TriplePath) next;
                            if (path.isTriple()) {
                                next = path.asTriple();
                            } else {
                                Path p = path.getPath();
                                Resource pathResource = createPath(p);
                                Resource subject = (Resource) getNode(path.getSubject());
                                RDFNode object = getNode(path.getObject());
                                org.topbraid.spin.model.TriplePath triplePath = SPINFactory.createTriplePath(model, subject, pathResource, object);
                                members.add(triplePath);
                            }
                        }
                        if (next instanceof org.apache.jena.graph.Triple) {
                            org.apache.jena.graph.Triple triple = (org.apache.jena.graph.Triple) next;
                            RDFNode subject = getNode(triple.getSubject());
                            Resource predicate = (Resource) getNode(triple.getPredicate());
                            RDFNode object = getNode(triple.getObject());
                            members.add(SPINFactory.createTriplePattern(model, subject, predicate, object));
                        }
                    }
                }
            });
        }
        return model.createList(members.iterator()).as(ElementList.class);
    }

    public RDFNode createExpression(Expr expr) {
        NodeValue constant = expr.getConstant();
        if (constant != null) {
            Node node = constant.asNode();
            return model.asRDFNode(node);
        } else {
            if (expr instanceof ExprAggregator) {
                return createAggregation((ExprAggregator) expr);
            }
            ExprVar var = expr.getExprVar();
            if (var != null) {
                String varName = var.getVarName();
                return getVariable(varName);
            } else {
                return createFunctionCall(expr);
            }
        }
    }

    private RDFNode createAggregation(ExprAggregator agg) {
        String str = agg.asSparqlExpr(new SerializationContext());
        int opening = str.indexOf('(');
        if (opening > 0) {
            String name = str.substring(0, opening).toUpperCase().trim();
            Resource aggType = SPINAggregations.getType(name);
            if (aggType != null) {
                if (agg.getAggregator() instanceof AggGroupConcat || agg.getAggregator() instanceof AggGroupConcatDistinct) {
                    String separator = getGroupConcatSeparator(agg.getAggregator());
                    if (separator != null) {
                        int semi = str.indexOf(';');
                        String sub = str.substring(0, semi) + ")";
                        Resource result = createAggregation(agg.getAggVar().asVar(), sub, aggType);
                        result.addProperty(SP.separator, model.createTypedLiteral(separator));
                        return result;
                    }
                }
                return createAggregation(agg.getAggVar().asVar(), str, aggType);
            } else {
                throw new IllegalArgumentException("Expected aggregation");
            }
        } else {
            throw new IllegalArgumentException("Malformed aggregation");
        }
    }

    private RDFNode createFunctionCall(Expr expr) {
        ExprFunction function = expr.getFunction();
        Resource f = getFunction(function);
        if (f == null) throw new IllegalStateException("Null func resource for expr " + expr);
        FunctionCall call = SPINFactory.createFunctionCall(model, f);
        if (addPrefixes) {
            String ns = f.getNameSpace();
            if (ns != null && model.getNsURIPrefix(ns) == null) {
                Map<String, String> extras = ExtraPrefixes.getExtraPrefixes();
                for (String prefix : extras.keySet()) {
                    if (ns.equals(extras.get(prefix))) {
                        model.setNsPrefix(prefix, ns);
                    }
                }
            }
        }
        List<RDFNode> params = createParameters(function);
        List<Argument> args = f.as(Function.class).getArguments(true);
        for (int i = 0; i < params.size(); i++) {
            RDFNode arg = params.get(i);
            Property predicate = i < args.size() ?
                    args.get(i).getPredicate() :
                    model.getProperty(SP.NS + "arg" + (i + 1));
            call.addProperty(predicate, arg);
        }
        if (function instanceof ExprFunctionOp) {
            Element element = ((ExprFunctionOp) function).getElement();
            ElementList elements = createElementList(element);
            call.addProperty(SP.elements, elements);
        }
        return call;
    }

    private InsertData createInsertData(UpdateDataInsert arq, String uri) {
        InsertData spin = model.createResource(uri, SP.InsertData).as(InsertData.class);
        spin.addProperty(SP.data, createQuadsList(arq.getQuads()));
        return spin;
    }

    private Load createLoad(UpdateLoad arqLoad, String uri) {
        Load spinLoad = model.createResource(uri, SP.Load).as(Load.class);
        String documentURI = arqLoad.getSource();
        spinLoad.addProperty(SP.document, model.getResource(documentURI));
        Node graphName = arqLoad.getDest();
        if (graphName != null) {
            spinLoad.addProperty(SP.into, model.asRDFNode(graphName));
        }
        return spinLoad;
    }

    private Resource createPath(Path path) {
        if (path instanceof P_Link) {
            P_Link link = (P_Link) path;
            Node node = link.getNode();
            return (Resource) model.asRDFNode(node);
        } else if (path instanceof P_ZeroOrMore1) {
            return createMod((P_ZeroOrMore1) path, 0, -2);
        } else if (path instanceof P_ZeroOrMoreN) {
            return createMod((P_ZeroOrMoreN) path, 0, -2);
        } else if (path instanceof P_ZeroOrOne) {
            return createMod((P_ZeroOrOne) path, 0, -1);
        } else if (path instanceof P_OneOrMore1) {
            return createMod((P_OneOrMore1) path, 1, -2);
        } else if (path instanceof P_OneOrMoreN) {
            return createMod((P_OneOrMoreN) path, 1, -2);
        } else if (path instanceof P_FixedLength) {
            P_FixedLength mod = (P_FixedLength) path;
            long count = mod.getCount();
            return createMod((P_FixedLength) path, count, count);
        } else if (path instanceof P_Mod) {
            P_Mod mod = (P_Mod) path;
            long min = mod.getMin();
            long max = mod.getMax();
            return createMod((P_Mod) path, min, max);
        } else if (path instanceof P_Alt) {
            P_Alt alt = (P_Alt) path;
            Resource path1 = createPath(alt.getLeft());
            Resource path2 = createPath(alt.getRight());
            Resource r = model.createResource(SP.AltPath);
            r.addProperty(SP.path1, path1);
            r.addProperty(SP.path2, path2);
            return r;
        } else if (path instanceof P_Inverse) {
            P_Inverse reverse = (P_Inverse) path;
            Resource r = model.createResource(SP.ReversePath);
            Resource path1 = createPath(reverse.getSubPath());
            r.addProperty(SP.subPath, path1);
            return r;
        } else if (path instanceof P_Seq) {
            P_Seq seq = (P_Seq) path;
            Resource path1 = createPath(seq.getLeft());
            Resource path2 = createPath(seq.getRight());
            Resource r = model.createResource(SP.SeqPath);
            r.addProperty(SP.path1, path1);
            r.addProperty(SP.path2, path2);
            return r;
        } else if (path instanceof P_ReverseLink) {
            P_ReverseLink rl = (P_ReverseLink) path;
            Resource r = model.createResource(SP.ReverseLinkPath);
            r.addProperty(SP.node, model.asRDFNode(rl.getNode()));
            return r;
        } else {
            throw new IllegalArgumentException("Unsupported Path element: " + path + " of type " + path.getClass());
        }
    }

    private Resource createMod(P_Path1 path, long min, long max) {
        Resource subR = createPath(path.getSubPath());
        Resource r = model.createResource(SP.ModPath);
        r.addProperty(SP.subPath, subR);
        r.addProperty(SP.modMax, model.createTypedLiteral(max, XSD.integer.getURI()));
        r.addProperty(SP.modMin, model.createTypedLiteral(min, XSD.integer.getURI()));
        return r;
    }

    private List<RDFNode> createParameters(ExprFunction function) {
        List<RDFNode> params = new LinkedList<>();
        List<Expr> args = function.getArgs();
        for (Expr argExpr : args) {
            RDFNode param = createExpression(argExpr);
            params.add(param);
        }
        return params;
    }

    private Resource createHead(Template template) {
        final List<Resource> members = new LinkedList<>();
        for (Triple triple : template.getTriples()) {
            Resource tripleTemplate = model.createResource(); // No SP.TripleTemplate needed
            tripleTemplate.addProperty(SP.subject, getNode(triple.getSubject()));
            tripleTemplate.addProperty(SP.predicate, getNode(triple.getPredicate()));
            tripleTemplate.addProperty(SP.object, getNode(triple.getObject()));
            members.add(tripleTemplate);
        }
        return model.createList(members.iterator());
    }

    /**
     * Takes a list of Quads and turns it into an rdf:List consisting of plain
     * sp:Triples or GRAPH { ... } blocks for those adjacent Quads with the same named graph.
     *
     * @param quads the Quads to convert
     * @return a SPIN RDFList
     */
    private RDFList createQuadsList(List<Quad> quads) {
        List<Resource> members = new LinkedList<>();
        Node nestedGraph = null;
        List<Resource> nested = null;
        Iterator<Quad> it = quads.iterator();
        while (it.hasNext()) {
            Quad quad = it.next();
            if (nestedGraph != null && !nestedGraph.equals(quad.getGraph())) {
                members.add(createNestedNamedGraph(nestedGraph, nested));
                nestedGraph = null;
            }
            Resource triple = createTriple(quad);
            if (quad.isDefaultGraph()) {
                members.add(triple);
            } else {
                if (!quad.getGraph().equals(nestedGraph)) {
                    nested = new LinkedList<>();
                    nestedGraph = quad.getGraph();
                }
                nested.add(triple);
                if (!it.hasNext()) {
                    members.add(createNestedNamedGraph(nestedGraph, nested));
                }
            }
        }
        return model.createList(members.iterator());
    }

    private Resource createNestedNamedGraph(Node nestedGraph, List<Resource> nested) {
        RDFList nestedMembers = model.createList(nested.iterator());
        Resource graphNode =
                nestedGraph.isVariable() ?
                        getVariable(nestedGraph.getName()) :
                        (Resource) model.asRDFNode(nestedGraph);
        return SPINFactory.createNamedGraph(model, graphNode, nestedMembers);
    }

    private Resource createTriple(Quad quad) {
        Resource triple = model.createResource(); // No rdf:type needed
        triple.addProperty(SP.subject, getNode(quad.getSubject()));
        triple.addProperty(SP.predicate, getNode(quad.getPredicate()));
        triple.addProperty(SP.object, getNode(quad.getObject()));
        return triple;
    }

    /**
     * Constructs a new SPIN Query from a given ARQ query, possibly
     * with a URI.
     *
     * @param arq the ARQ query
     * @param uri the URI of the new Query resource or null for a blank node
     * @return the Query
     */
    public org.topbraid.spin.model.Query createQuery(Query arq, String uri) {
        Resource spinQuery = model.createResource(uri);
        addNamedGraphClauses(arq, spinQuery);
        Resource where = createElementList(arq.getQueryPattern());
        spinQuery.addProperty(SP.where, where);
        if (arq.isAskType()) {
            spinQuery.addProperty(RDF.type, SP.Ask);
            addValues(arq, spinQuery);
            return spinQuery.as(Ask.class);
        } else if (arq.isConstructType()) {
            Resource head = createHead(arq.getConstructTemplate());
            spinQuery.addProperty(RDF.type, SP.Construct);
            spinQuery.addProperty(SP.templates, head);
            addSolutionModifiers(arq, spinQuery);
            addValues(arq, spinQuery);
            return spinQuery.as(Construct.class);
        } else if (arq.isSelectType()) {
            spinQuery.addProperty(RDF.type, SP.Select);
            Select select = spinQuery.as(Select.class);
            addSelectProperties(arq, spinQuery);
            addGroupBy(arq, spinQuery);
            addValues(arq, spinQuery);
            return select;
        } else if (arq.isDescribeType()) {
            spinQuery.addProperty(RDF.type, SP.Describe);
            Describe describe = spinQuery.as(Describe.class);
            addDescribeProperties(arq, spinQuery);
            addSolutionModifiers(arq, spinQuery);
            addValues(arq, spinQuery);
            return describe;
        }
        throw new IllegalArgumentException("Unsupported SPARQL query type");
    }

    private Modify createModify(UpdateModify arq, String uri) {
        Modify result = model.createResource(uri, SP.Modify).as(Modify.class);
        Node withIRI = arq.getWithIRI();
        if (withIRI != null) {
            result.addProperty(SP.with, model.asRDFNode(withIRI));
        }

        if (arq.hasDeleteClause()) {
            List<Quad> deletes = arq.getDeleteQuads();
            result.addProperty(SP.deletePattern, createQuadsList(deletes));
        }
        if (arq.hasInsertClause()) {
            List<Quad> inserts = arq.getInsertQuads();
            result.addProperty(SP.insertPattern, createQuadsList(inserts));
        }
        Element where = arq.getWherePattern();
        if (where != null) {
            Resource spinWhere = createElementList(where);
            result.addProperty(SP.where, spinWhere);
        }
        for (Node using : arq.getUsing()) {
            result.addProperty(SP.using, model.asRDFNode(using));
        }
        for (Node usingNamed : arq.getUsingNamed()) {
            result.addProperty(SP.usingNamed, model.asRDFNode(usingNamed));
        }
        return result;
    }

    public Update createUpdate(org.apache.jena.update.Update arq, String uri) {
        if (arq instanceof UpdateModify) {
            return createModify((UpdateModify) arq, uri);
        } else if (arq instanceof UpdateClear) {
            return createClear((UpdateClear) arq, uri);
        } else if (arq instanceof UpdateCreate) {
            return createCreate((UpdateCreate) arq, uri);
        } else if (arq instanceof UpdateDeleteWhere) {
            return createDeleteWhere((UpdateDeleteWhere) arq, uri);
        } else if (arq instanceof UpdateDrop) {
            return createDrop((UpdateDrop) arq, uri);
        } else if (arq instanceof UpdateLoad) {
            return createLoad((UpdateLoad) arq, uri);
        } else if (arq instanceof UpdateDataDelete) {
            return createDeleteData((UpdateDataDelete) arq, uri);
        } else if (arq instanceof UpdateDataInsert) {
            return createInsertData((UpdateDataInsert) arq, uri);
        } else {
            throw new IllegalArgumentException("Unsupported SPARQL Update type for " + arq);
        }
    }

    private Resource getFunction(ExprFunction function) {
        String symbol = function.getOpName();
        if (symbol == null) {
            symbol = function.getFunctionSymbol().getSymbol();
        }
        if (symbol != null) {
            List<Resource> list = symbolsMap.get(symbol.toLowerCase());
            if (list != null) {
                if (list.size() == 1) {
                    return list.get(0);
                } else {
                    // Disambiguate functions with same symbol (+ and -)
                    for (Resource f : list) {
                        int count = 0;
                        StmtIterator dit = f.listProperties(SPIN.constraint);
                        while (dit.hasNext()) {
                            dit.next();
                            count++;
                        }
                        int argsCount = function.getArgs().size();
                        if (argsCount == count) {
                            return f;
                        }
                    }
                }
            }
        }
        String iri = function.getFunctionIRI();
        if (iri != null) {
            return model.getResource(iri);
        } else if ("uuid".equals(symbol)) {
            return model.getResource(SP.NS + "UUID");
        } else if ("struuid".equals(symbol)) {
            return model.getResource(SP.NS + "struuid");
        } else if (symbol != null) {
            // Case if fn: functions are entered without prefix
            return model.getResource("http://www.w3.org/2005/xpath-functions#" + symbol);
        } else {
            return null;
        }
    }

    private String getGroupConcatSeparator(Aggregator agg) {
        if (agg instanceof AggGroupConcat) {
            return ((AggGroupConcat) agg).getSeparator();
        } else {
            return ((AggGroupConcatDistinct) agg).getSeparator();
        }
    }

    private RDFNode getNode(Node node) {
        if (node.isVariable()) {
            String name = node.getName();
            return getVariable(name);
        } else {
            return model.asRDFNode(node);
        }
    }

    private Resource getVariable(String name) {
        Resource old = var2Resource.get(name);
        if (old != null) {
            return old;
        } else if (SPINPreferences.get().isCreateURIVariables()) {
            String uri = varNamespace + "_" + name;
            Resource var = model.createResource(uri, SP.Variable);
            var.addProperty(SP.varName, model.createTypedLiteral(name));
            var2Resource.put(name, var);
            return var;
        } else {
            Variable var = SPINFactory.createVariable(model, name);
            if (SPINPreferences.get().isReuseLocalVariables()) {
                var2Resource.put(name, var);
            }
            return var;
        }
    }

    /**
     * Gets the (optional) variable namespace.
     *
     * @return the variable namespace
     */
    public String getVarNamespace() {
        return varNamespace;
    }

    /**
     * Sets the variable namespace which is used to prevent the
     * creation of too many blank nodes.
     *
     * @param value the new namespace (might be null)
     */
    public void setVarNamespace(String value) {
        this.varNamespace = value;
    }

    /**
     * Manages the registered SPARQL aggregations (such as SUM).
     * These are loaded from the sp system ontology.
     *
     * @author Holger Knublauch
     * @see org.topbraid.spin.arq.Aggregations
     */
    public static class SPINAggregations {
        private static Map<String, Resource> name2Type = aggregations()
                .collect(Collectors.toMap(r -> r.getProperty(RDFS.label).getString(), java.util.function.Function.identity()));
        private static Map<Resource, String> type2Name = aggregations()
                .collect(Collectors.toMap(java.util.function.Function.identity(), r -> r.getProperty(RDFS.label).getString()));

        public static Stream<Resource> aggregations() {
            return Iter.asStream(SpinModelConfig.createSpinModel(SPINLibrary.SP.getGraph()).listSubjectsWithProperty(RDFS.subClassOf, SP.Aggregation));
        }

        /**
         * If registered, returns the display name of a given aggregation type.
         *
         * @param aggType the aggregation type, e.g. sp:Sum
         * @return the name (e.g., "SUM") or null if not registered
         */
        public static String getName(Resource aggType) {
            return type2Name.get(aggType);
        }

        /**
         * If registered, returns the aggregation Resource for a given display name.
         *
         * @param name the name (e.g., "SUM")
         * @return the type or null if not registered
         */
        public static Resource getType(String name) {
            return name2Type.get(name);
        }
    }
}

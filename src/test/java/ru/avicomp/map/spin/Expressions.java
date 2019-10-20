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

package ru.avicomp.map.spin;

import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.shared.impl.PrefixMappingImpl;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.function.FunctionRegistry;
import org.apache.jena.sparql.syntax.ElementBind;
import org.apache.jena.sparql.syntax.ElementGroup;
import org.apache.jena.sparql.util.FmtUtils;
import org.topbraid.spin.arq.ARQFactory;
import org.topbraid.spin.model.Aggregation;
import org.topbraid.spin.model.FunctionCall;
import org.topbraid.spin.model.SPINFactory;
import org.topbraid.spin.model.Variable;
import org.topbraid.spin.model.impl.AbstractSPINResourceImpl;
import org.topbraid.spin.model.print.PrintContext;
import org.topbraid.spin.model.print.StringPrintContext;
import org.topbraid.spin.system.SPINModuleRegistry;
import org.topbraid.spin.util.SPINUtil;
import org.topbraid.spin.vocabulary.SP;

/**
 * A copy-pasted TopBraid-SPIN class to avoid stupid static initialization.
 * Static utilities on SPIN Expressions.
 *
 * @author Holger Knublauch
 * @see org.topbraid.spin.util.SPINExpressions
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class Expressions {
    public static final PrefixMapping emptyPrefixMapping = new PrefixMappingImpl();

    public static String checkExpression(String str, Model model) {
        String queryString = "ASK WHERE { LET (?xqoe := (" + str + ")) }";
        try {
            ARQFactory.get().createQuery(model, queryString);
            return null;
        } catch (QueryParseException ex) {
            String s = ex.getMessage();
            int startIndex = s.indexOf("at line ");
            if (startIndex >= 0) {
                int endIndex = s.indexOf('.', startIndex);
                return s.substring(0, startIndex) +
                        "at column " +
                        (ex.getColumn() - 27) +
                        s.substring(endIndex);
            } else {
                return s;
            }
        }
    }

    /**
     * Evaluates a given SPIN expression.
     * Prior to calling this, the caller must make sure that the expression has the
     * most specific Java type, e.g. using SPINFactory.asExpression().
     *
     * @param expression the expression (must be cast into the best possible type)
     * @param queryModel the Model to query
     * @param bindings   the initial bindings
     * @return the result RDFNode or null
     */
    public static RDFNode evaluate(Resource expression, Model queryModel, QuerySolution bindings) {
        return evaluate(expression, ARQFactory.get().getDataset(queryModel), bindings);
    }

    public static RDFNode evaluate(Resource expression, Dataset dataset, QuerySolution bindings) {
        if (expression instanceof Variable) {
            // Optimized case if the expression is just a variable
            String varName = ((Variable) expression).getName();
            return bindings.get(varName);
        } else if (expression.isURIResource()) {
            return expression;
        } else {
            Query arq = ARQFactory.get().createExpressionQuery(expression);
            try (QueryExecution qexec = ARQFactory.get().createQueryExecution(arq, dataset, bindings)) {
                return SPINUtil.getFirstResult(qexec);
            }
        }
    }

    public static String getExpressionString(RDFNode expression) {
        return getExpressionString(expression, true);
    }

    public static String getExpressionString(RDFNode expression, boolean usePrefixes) {
        if (usePrefixes) {
            StringPrintContext p = new StringPrintContext();
            p.setUsePrefixes(true);
            org.topbraid.spin.util.SPINExpressions.printExpressionString(p, expression, false, false, expression.getModel().getGraph().getPrefixMapping());
            return p.getString();
        } else {
            return ARQFactory.get().createExpressionString(expression);
        }
    }


    /**
     * Checks whether a given RDFNode is an expression.
     * In order to be regarded as expression it must be a well-formed
     * function call, aggregation or variable.
     *
     * @param node the RDFNode
     * @return true if node is an expression
     */
    public static boolean isExpression(RDFNode node) {
        if (!(node instanceof Resource) || !SP.exists(node.getModel())) {
            return false;
        }
        RDFNode expr = SPINFactory.asExpression(node);
        if (expr instanceof Variable) {
            return true;
        } else if (!node.isAnon()) {
            return false;
        }
        if (expr instanceof FunctionCall) {
            Resource function = ((FunctionCall) expr).getFunction();
            if (function.isURIResource()) {
                if (SPINModuleRegistry.get().getFunction(function.getURI(), node.getModel()) != null) {
                    return true;
                }
                return FunctionRegistry.get().isRegistered(function.getURI());
            }
        } else {
            return expr instanceof Aggregation;
        }
        return false;
    }

    public static Expr parseARQExpression(String str, Model model) {
        String queryString = "ASK WHERE { BIND ((" + str + ") AS ?xqoe) }";
        Query arq = ARQFactory.get().createQuery(model, queryString);
        ElementGroup group = (ElementGroup) arq.getQueryPattern();
        ElementBind assign = (ElementBind) group.getElements().get(0);
        return assign.getExpr();
    }

    public static RDFNode parseExpression(String str, Model model) {
        Expr expr = parseARQExpression(str, model);
        return parseExpression(expr, model);
    }

    public static RDFNode parseExpression(Expr expr, Model model) {
        QueryHelper a2s = new QueryHelper(model);
        return a2s.createExpression(expr);
    }

    public static void printExpressionString(PrintContext p, RDFNode node, boolean nested, boolean force, PrefixMapping prefixMapping) {
        if (node instanceof Resource && SPINFactory.asVariable(node) == null) {
            Resource resource = (Resource) node;
            Aggregation aggr = SPINFactory.asAggregation(resource);
            if (aggr != null) {
                PrintContext pc = p.clone();
                pc.setNested(nested);
                aggr.print(pc);
                return;
            }
            FunctionCall call = SPINFactory.asFunctionCall(resource);
            if (call != null) {
                PrintContext pc = p.clone();
                pc.setNested(nested);
                call.print(pc);
                return;
            }
        }
        if (force) {
            p.print("(");
        }
        if (node instanceof Resource) {
            AbstractSPINResourceImpl.printVarOrResource(p, (Resource) node);
        } else {
            PrefixMapping pm = p.getUsePrefixes() ? prefixMapping : emptyPrefixMapping;
            String str = FmtUtils.stringForNode(node.asNode(), pm);
            p.print(str);
        }
        if (force) {
            p.print(")");
        }
    }
}

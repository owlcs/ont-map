/*
 * This file is part of the ONT MAP.
 * The contents of this file are subject to the Apache License, Version 2.0.
 * Copyright (c) 2019, Avicomp Services, AO
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

package ru.avicomp.map.spin.functions.spin;

import org.apache.jena.graph.Node;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetImpl;
import org.apache.jena.sparql.expr.ExprEvalException;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.function.FunctionEnv;
import org.apache.jena.sparql.function.FunctionFactory;
import org.topbraid.spin.arq.AbstractFunction;
import org.topbraid.spin.arq.DatasetWithDifferentDefaultModel;
import org.topbraid.spin.model.SPINFactory;
import org.topbraid.spin.util.SPINExpressions;
import ru.avicomp.map.spin.SpinModelConfig;

import java.util.Arrays;
import java.util.Objects;

/**
 * Re-implementation of {@code spin:eval}.
 * <p>
 * Created by @ssz on 12.06.2019.
 *
 * @see org.topbraid.spin.vocabulary.SPIN#eval
 * @see org.topbraid.spin.arq.functions.EvalFunction
 */
public class eval extends AbstractFunction implements FunctionFactory {

    @Override
    public eval create(String uri) {
        return this;
    }

    @Override
    public NodeValue exec(Node[] nodes, FunctionEnv env) {
        if (nodes.length == 0) {
            throw new ExprEvalException("Missing arguments");
        }
        if ((nodes.length - 1) % 2 != 0) {
            throw new ExprEvalException("Wrong arguments list: " + Arrays.toString(nodes));
        }
        Node exprNode = nodes[0];
        if (exprNode == null) {
            throw new ExprEvalException("No expression specified");
        }
        if (exprNode.isLiteral()) {
            return NodeValue.makeNode(exprNode);
        }

        Model model = SpinModelConfig.createSpinModel(env.getActiveGraph());
        return exec(model.wrapAsResource(exprNode), getBindings(nodes, model), env.getDataset());
    }

    public NodeValue exec(RDFNode exprNode, QuerySolutionMap bindings, DatasetGraph dsg) {
        if (exprNode.isLiteral()) {
            return NodeValue.makeNode(exprNode.asNode());
        }
        Model model = Objects.requireNonNull(exprNode.getModel());
        Dataset ds = new DatasetWithDifferentDefaultModel(model, DatasetImpl.wrap(dsg));

        // The following code is commented out
        // since ONT-MAP does not support the direct query execution.
        // No functions can accept queries. Moreover, the function <spin:eval> is marked as <spin:private>.
        /*
        org.topbraid.spin.model.Query spin = SPINFactory.asQuery(exprNode.asResource());
        if (spin instanceof org.topbraid.spin.model.Select || spin instanceof org.topbraid.spin.model.Ask) {
            org.topbraid.spin.arq.ARQFactory factory = org.topbraid.spin.arq.ARQFactory.get();
            org.apache.jena.query.Query query = factory.createQuery(spin);
            try (org.apache.jena.query.QueryExecution qexec = factory.createQueryExecution(query, ds, bindings)) {
                if (query.isAskType()) {
                    return NodeValue.makeBoolean(qexec.execAsk());
                }
                org.apache.jena.query.ResultSet rs = qexec.execSelect();
                java.util.List<String> vars = rs.getResultVars();
                if (!vars.isEmpty() && rs.hasNext()) {
                    RDFNode res = rs.next().get(vars.get(0));
                    if (res != null) {
                        return NodeValue.makeNode(res.asNode());
                    }
                }
            }
            throw new ExprEvalException(spin + ":: no result");
        }
        */

        RDFNode expr = SPINFactory.asExpression(exprNode.asResource());
        RDFNode res = SPINExpressions.evaluate((Resource) expr, ds, bindings);
        if (res != null) {
            return NodeValue.makeNode(res.asNode());
        }
        throw new ExprEvalException("Expression has no result");
    }

    private QuerySolutionMap getBindings(Node[] nodes, Model model) {
        QuerySolutionMap res = new QuerySolutionMap();
        for (int i = 1; i < nodes.length - 1; i += 2) {
            Node property = nodes[i];
            Node value = nodes[i + 1];
            if (value != null) {
                res.add(property.getLocalName(), model.asRDFNode(value));
            }
        }
        return res;
    }
}

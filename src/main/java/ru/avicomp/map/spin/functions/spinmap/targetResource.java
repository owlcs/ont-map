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

package ru.avicomp.map.spin.functions.spinmap;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.sparql.expr.ExprEvalException;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.function.FunctionEnv;
import org.topbraid.spin.arq.AbstractFunction2;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.spin.SpinModelConfig;
import ru.avicomp.map.spin.functions.spin.eval;
import ru.avicomp.ontapi.jena.utils.Iter;

/**
 * An ARQ impl-optimization for a {@link org.topbraid.spin.vocabulary.SPINMAP#targetResource spinmap:targetResource}.
 * The body query:
 * <pre>{@code
 * SELECT (spin:eval(?targetExpr, spinmap:source, ?arg1) AS ?result)
 * WHERE {
 *     BIND (spl:object(?context, spinmap:target) AS ?targetExpr) .
 * }
 * }</pre>
 * Created by @ssz on 12.06.2019.
 *
 * @see ru.avicomp.map.spin.vocabulary.AVC#optimize
 */
public class targetResource extends AbstractFunction2 {
    private static final String SPINMAP_SOURCE_NAME = SPINMAP.source.getLocalName();
    private static final Node SPINMAP_TARGET_NODE = SPINMAP.target.asNode();

    private final eval evalFunction = new eval();

    @Override
    protected NodeValue exec(Node arg1, Node arg2, FunctionEnv env) {
        Node source = requireResource(arg1, "arg1");
        Node context = requireResource(arg2, "context");
        Graph g = env.getActiveGraph();
        Model m = SpinModelConfig.createSpinModel(g);
        QuerySolutionMap map = new QuerySolutionMap();
        map.add(SPINMAP_SOURCE_NAME, m.asRDFNode(source));
        return Iter.findFirst(g.find(context, SPINMAP_TARGET_NODE, Node.ANY)
                .mapWith(t -> evalFunction.exec(m.getRDFNode(t.getObject()), map, env.getDataset())))
                .orElseThrow(() -> new ExprEvalException(String.format("No spinmap:targetResource is derived " +
                        "for source=%s and context=%s", source, context)));
    }

    private static Node requireResource(Node arg, String name) {
        if (arg == null) {
            throw new ExprEvalException("Null argument " + name);
        } else if (!arg.isURI() && !arg.isBlank()) {
            throw new ExprEvalException("The argument (" + name + ") must be resource: " + arg);
        }
        return arg;
    }
}

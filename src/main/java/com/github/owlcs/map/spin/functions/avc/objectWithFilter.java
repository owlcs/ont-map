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

package com.github.owlcs.map.spin.functions.avc;

import com.github.owlcs.map.spin.functions.spl.object;
import com.github.owlcs.map.spin.vocabulary.AVC;
import com.github.owlcs.ontapi.jena.utils.Iter;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.sparql.expr.ExprEvalException;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.function.FunctionEnv;
import org.topbraid.spin.arq.AbstractFunction4;

/**
 * An ARQ impl-optimization for a
 * {@link AVC#objectWithFilter avc:objectWithFilter}.
 * This function has two mandatory arguments (arg1, arg2) and two optional (?arg3, arg4).
 * The query:
 * <pre>{@code
 * SELECT ?object
 * WHERE {
 *     OPTIONAL {
 *         BIND (?arg4 AS ?value) .
 *     } .
 *     OPTIONAL {
 *         BIND (?arg3 AS ?property) .
 *     } .
 *     ?arg1 ?arg2 ?object .
 *     FILTER EXISTS {
 *         ?object ?property ?value .
 *     } .
 * }
 * }</pre>
 * Created by @ssz on 13.06.2019.
 *
 * @see AVC#optimize
 * @see object
 */
public class objectWithFilter extends AbstractFunction4 {

    @Override
    protected NodeValue exec(Node arg1, Node arg2, Node arg3, Node arg4, FunctionEnv env) {
        Node subject, predicate, filterPredicate, filterObject;
        if (arg1 == null) {
            throw new ExprEvalException("The first argument is mandatory");
        } else if (arg1.isURI() || arg1.isBlank()) {
            subject = arg1;
        } else {
            throw new ExprEvalException("The first argument must be a resource (uri or blank node)");
        }
        if (arg2 == null) {
            throw new ExprEvalException("The second argument is mandatory");
        } else if (arg2.isURI()) {
            predicate = arg2;
        } else {
            throw new ExprEvalException("The second argument must be a property (uri node)");
        }
        if (arg3 == null) {
            filterPredicate = Node.ANY;
        } else if (arg3.isURI()) {
            filterPredicate = arg3;
        } else {
            throw new ExprEvalException("The third argument must be a property (uri node)");
        }
        if (arg4 == null) {
            filterObject = Node.ANY;
        } else if (arg4.isConcrete()) {
            filterObject = arg4;
        } else {
            throw new ExprEvalException("The fourth argument must be a RDF node");
        }

        // TODO: need to select only the query graph
        Graph g = env.getActiveGraph();

        return Iter.findFirst(g.find(subject, predicate, Node.ANY)
                .filterKeep(x -> g.contains(x.getObject(), filterPredicate, filterObject))
                .mapWith(x -> NodeValue.makeNode(x.getObject())))
                .orElseThrow(() -> new ExprEvalException(String.format("No object found for ?subject = %s, " +
                                "?predicate = %s, ?filterPredicate = %s, ?filterObject = %s",
                        subject, predicate, filterPredicate, filterObject)));
    }
}

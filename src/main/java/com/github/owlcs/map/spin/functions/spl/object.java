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

package com.github.owlcs.map.spin.functions.spl;

import com.github.owlcs.map.spin.vocabulary.AVC;
import com.github.owlcs.ontapi.jena.utils.Iter;
import org.apache.jena.graph.Node;
import org.apache.jena.sparql.expr.ExprEvalException;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.function.FunctionEnv;
import org.topbraid.spin.arq.AbstractFunction2;

/**
 * An ARQ impl-optimization for a {@link org.topbraid.spin.vocabulary.SPL#object spl:object}.
 * The body query:
 * <pre>{@code
 * SELECT ?object
 * WHERE {
 *     ?arg1 ?arg2 ?object .
 * }
 * }</pre>
 * Created by @szz on 23.05.2019.
 *
 * @see AVC#optimize
 */
public class object extends AbstractFunction2 {
    @Override
    protected NodeValue exec(Node arg1, Node arg2, FunctionEnv env) {
        Node subject, predicate;
        if (arg1 == null) {
            subject = Node.ANY;
        } else if (arg1.isURI() || arg1.isBlank()) {
            subject = arg1;
        } else {
            throw new ExprEvalException("The first argument must be a resource (uri or blank node)");
        }
        if (arg2 == null) {
            predicate = Node.ANY;
        } else if (arg2.isURI()) {
            predicate = arg2;
        } else {
            throw new ExprEvalException("The second argument must be a property (uri node)");
        }
        // since this function is used by spin API
        // always use the whole graph which contains everything: all libraries, source and target inside.
        // todo: it is better to use search (source) graph in case
        //  the function is called not by SPIN, but from ONT-MAP, but currently don't know how to achieve this
        return Iter.findFirst(env.getActiveGraph().find(subject, predicate, Node.ANY)
                .mapWith(x -> NodeValue.makeNode(x.getObject())))
                .orElseThrow(() -> new ExprEvalException("No object found for ?subject = " +
                        subject + " and ?predicate = " + predicate));
    }

}

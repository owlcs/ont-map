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

package ru.avicomp.map.spin.functions.avc;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.sparql.expr.*;
import org.apache.jena.sparql.function.FunctionEnv;
import org.topbraid.spin.arq.AbstractFunction1;
import ru.avicomp.map.spin.MapARQFactory;

import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * An ARQ impl-optimization for a
 * {@link ru.avicomp.map.spin.vocabulary.AVC#UUID avc:UUID}, which is a target function.
 * The query:
 * <pre>{@code
 * SELECT (IRI(?uri))
 * WHERE {
 *     BIND (IF(isBlank(?source), afn:bnode(?source), str(?source)) AS ?s) .
 *     BIND (MD5(?s) AS ?value) .
 *     BIND (CONCAT("urn:uuid:", ?value) AS ?uri) .
 * }
 * }</pre>
 * Created by @ssz on 30.12.2018.
 *
 * @see ru.avicomp.map.spin.vocabulary.AVC#optimize
 */
@SuppressWarnings("WeakerAccess")
public class UUID extends AbstractFunction1 {
    public static final String URI_PREFIX = "urn:uuid:";
    public static final ExprDigest MD5_CALCULATOR = new E_MD5(new ExprVar(UUID.class.getName()));
    public static final UnaryOperator<String> URI_MAKER =
            s -> URI_PREFIX + MD5_CALCULATOR.eval(NodeValue.makeString(s)).getString();

    @Override
    protected NodeValue exec(Node source, FunctionEnv env) {
        if (source == null) {
            throw new ExprEvalException("Null source node is given");
        }
        if (!source.isBlank() && !source.isURI()) {
            throw new ExprEvalException("?source must be either b-node or uri resource");
        }
        Map<Node, NodeValue> cache = env.getContext().get(MapARQFactory.NODE_TO_VALUE_CACHE);
        return cache.computeIfAbsent(source, s -> {
            String res = s.isBlank() ? s.getBlankNodeId().getLabelString() : s.getURI();
            return NodeValue.makeNode(NodeFactory.createURI(URI_MAKER.apply(res)));
        });
    }
}

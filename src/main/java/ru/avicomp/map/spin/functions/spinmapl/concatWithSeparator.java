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

package ru.avicomp.map.spin.functions.spinmapl;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.graph.Node;
import org.apache.jena.sparql.expr.ExprEvalException;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.expr.nodevalue.XSDFuncOp;
import org.apache.jena.sparql.function.CastXSD;
import org.apache.jena.sparql.function.FunctionEnv;
import org.topbraid.spin.arq.AbstractFunction3;

import java.util.ArrayList;
import java.util.List;

/**
 * An ARQ impl-optimization for a
 * {@link ru.avicomp.map.spin.vocabulary.SPINMAPL#concatWithSeparator spinmapl:concatWithSeparator}.
 * The query:
 * <pre>{@code
 * SELECT (xsd:string(?untyped) AS ?result)
 * WHERE {
 *     BIND (CONCAT(xsd:string(?arg1), ?separator, xsd:string(?arg2)) AS ?untyped) .
 * }
 * }</pre>
 * Created by @ssz on 30.12.2018.
 *
 * @see ru.avicomp.map.spin.vocabulary.AVC#optimize
 */
public class concatWithSeparator extends AbstractFunction3 {
    @Override
    protected NodeValue exec(Node arg1, Node arg2, Node separator, FunctionEnv env) {
        List<NodeValue> args = new ArrayList<>(3);
        args.add(parse(arg1, "sp:arg1", false));
        args.add(parse(separator, "spinmapl:separator", true));
        args.add(parse(arg2, "sp:arg2", false));
        return XSDFuncOp.strConcat(args);
    }

    private static NodeValue parse(Node node, String name, boolean requireString) throws ExprEvalException {
        if (node == null) { // a null value is a valid case, that means a variable is given
            throw new ExprEvalException("a null node is given for " + name);
        }
        if (!node.isLiteral()) {
            throw new ExprEvalException(name + " must be a literal, but found " + node);
        }
        if (requireString && !XSDDatatype.XSDstring.equals(node.getLiteralDatatype())) {
            throw new ExprEvalException(name + " must be a string literal, but found " + node);
        }
        NodeValue res = NodeValue.makeNode(node);
        return requireString ? res : CastXSD.cast(res, XSDDatatype.XSDstring);
    }
}

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

package com.github.owlcs.map.spin.functions.spif;

import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFList;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.function.FunctionEnv;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.topbraid.spin.arq.AbstractFunction3;

/**
 * Created by @ssz on 04.12.2018.
 */
public class buildStringFromRDFList extends AbstractFunction3 {
    private static final String MEMBER_VARIABLE = "member";

    @Override
    protected NodeValue exec(Node arg1, Node arg2, Node arg3, FunctionEnv env) {
        Model model = ModelFactory.createModelForGraph(env.getActiveGraph());
        RDFList list = model.asRDFNode(arg1).as(RDFList.class);
        String template = arg2.getLiteralLexicalForm();
        String separator = arg3 != null ? arg3.getLiteralLexicalForm() : ""; // optional arg

        StringBuilder res = new StringBuilder();
        ExtendedIterator<RDFNode> it = list.iterator();
        while (it.hasNext()) {
            RDFNode node = it.next();
            String str = AbstractBuildFunction.format(template, var -> {
                if (!MEMBER_VARIABLE.equals(var)) {
                    return null;
                }
                if (node.isURIResource()) {
                    return model.createTypedLiteral("<" + node.asResource().getURI() + ">");
                }
                if (node.isLiteral()) {
                    return node;
                }
                return null;
            }, AbstractBuildFunction.DEFAULT_INSERTION_STRATEGY);
            res.append(str);
            if (it.hasNext()) {
                res.append(separator);
            }
        }
        return NodeValue.makeNodeString(res.toString());
    }
}

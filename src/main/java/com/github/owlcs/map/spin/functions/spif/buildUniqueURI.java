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

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.expr.NodeValue;

import java.util.function.Function;

/**
 * Created by @ssz on 04.12.2018.
 */
public class buildUniqueURI extends AbstractBuildFunction {

    @Override
    protected NodeValue exec(String template, PrefixMapping pm, Function<String, RDFNode> variables, Graph graph) {
        String uri = buildURI(template, pm, variables);
        Model model = ModelFactory.createModelForGraph(graph);
        Node res = NodeFactory.createURI(uri);
        for (int i = 0; model.containsResource(model.asRDFNode(res)); ++i) {
            res = NodeFactory.createURI(uri + i);
        }
        return NodeValue.makeNode(res);
    }
}

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

import org.apache.jena.ext.xerces.util.XMLChar;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.expr.ExprEvalException;
import org.apache.jena.sparql.expr.ExprList;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.function.FunctionEnv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * A base abstraction and helper for functions that accepts a string template with variable placeholders.
 * Examples of such templates: {@code http://test.com#Instance-{?index}}, {@code "Hello-{?1}-{?2}"}.
 * <p>
 * Created by @ssz on 04.12.2018.
 */
@SuppressWarnings("WeakerAccess")
abstract class AbstractBuildFunction implements org.apache.jena.sparql.function.Function {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractBuildFunction.class);

    public static final Function<RDFNode, String> DEFAULT_INSERTION_STRATEGY = node -> {
        if (node.isLiteral()) {
            return node.asLiteral().getLexicalForm();
        }
        Resource res = node.asResource();
        if (res.isURIResource()) {
            String qname = res.getModel().qnameFor(res.getURI());
            return qname != null ? qname : "<" + res.getURI() + ">";
        }
        return "<@" + res.getId() + ">";
    };
    public static final Function<RDFNode, String> URI_INSERTION_STRATEGY = node -> {
        String str;
        if (node.isLiteral()) {
            str = node.asLiteral().getLexicalForm();
        } else {
            Resource res = node.asResource();
            if (res.isURIResource()) {
                String qname = res.getModel().qnameFor(res.getURI());
                if (qname == null) {
                    throw new ExprEvalException("Cannot build qname for URI " + res.getURI());
                }
                str = qname;
            } else {
                str = res.toString().replace(':', '_');
            }
        }
        str = str.replaceAll(" ", "_");
        StringBuilder res = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (isValidURIChar(c)) {
                res.append(c);
            }
        }
        return res.toString();
    };

    /**
     * Answers {@code true} if the given char is good enough to be a part of URI.
     *
     * @param c {@code char} to test
     * @return boolean
     */
    private static boolean isValidURIChar(char c) {
        return XMLChar.isNCName(c)
                || c == '$'
                || c == '*'
                || c == '~'
                || c == '.'
                || c == '!'
                || c == '('
                || c == ')'
                || c == '\''
                || c == '/'
                || c == ':'
                || c == '#'
                || c == '?'
                || c == '='
                || c == '['
                || c == ']'
                || c == '&';
    }

    /**
     * Formats the given {@code template} to a new form using the {@code bindings} and {@code strategy} to translate variables.
     *
     * @param template String, not {@code null}
     * @param bindings variable mapping, to translate String (var) to {@link RDFNode RDF Node}, not {@code null}
     * @param strategy strategy mapping, to translate back - {@link RDFNode} to String, not {@code null}
     * @return String
     */
    public static String format(String template,
                                Function<String, RDFNode> bindings,
                                Function<RDFNode, String> strategy) {
        Objects.requireNonNull(template, "Null template");
        Objects.requireNonNull(bindings, "Null variable binding");
        Objects.requireNonNull(strategy, "Null insertion strategy");
        return format(template, var -> {
            RDFNode node = bindings.apply(var);
            if (node == null) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("String insertions with unknown variable: {}. Initial input: {}", var, template);
                }
                return "";
            }
            return strategy.apply(node);
        });
    }

    /**
     * Formats the given {@code template} to a new form using the {@code mapping} to translate variables.
     *
     * @param template String, not {@code null}
     * @param mapping  variable mapping, to translate String (var) to another form, not {@code null}
     * @return String
     */
    public static String format(String template, UnaryOperator<String> mapping) {
        Objects.requireNonNull(template, "Null template");
        Objects.requireNonNull(mapping, "Null variable mapping");
        List<Integer> starts = new LinkedList<>();
        for (int i = template.length() - 2; i > 0; i--) {
            if (template.charAt(i) == '?' && template.charAt(i - 1) == '{') {
                starts.add(i - 1);
            }
        }
        if (starts.isEmpty())
            return template;
        String res = template;
        for (int start : starts) {
            int ends = template.indexOf('}', start + 2);
            if (ends < 0) {
                continue;
            }
            String var = template.substring(start + 2, ends);
            if (!isVariable(var) && (var.isEmpty() || !Character.isDigit(var.charAt(0)))) {
                continue;
            }
            String str = mapping.apply(var);
            String prefix = res.substring(0, start);
            String suffix = res.substring(ends + 1);
            res = prefix + str + suffix;
        }
        return res;
    }

    /**
     * Answers {@code true} if the given string is a good variable name.
     *
     * @param str String to test
     * @return boolean
     */
    private static boolean isVariable(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        if (!Character.isJavaIdentifierStart(str.charAt(0))) {
            return false;
        }
        for (int i = 1; i < str.length(); ++i) {
            char c = str.charAt(i);
            if (!Character.isJavaIdentifierPart(c)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Builds an URI using the specified parameters.
     *
     * @param template   String, not {@code null}
     * @param pm         {@link PrefixMapping}, not {@code null}
     * @param variables, not {@code null}, String (variable) to {@link RDFNode} mapping
     * @return String, URI
     */
    public static String buildURI(String template,
                                  PrefixMapping pm,
                                  Function<String, RDFNode> variables) {
        String name = format(template, variables, URI_INSERTION_STRATEGY);
        String res;
        if (name.startsWith("<") && name.endsWith(">")) {
            res = name.substring(1, name.length() - 1);
        } else if (name.startsWith(":")) {
            res = name.substring(1);
            String ns = pm.getNsPrefixURI("");
            if (ns != null) {
                res = ns + res;
            }
        } else {
            res = pm.expandPrefix(name);
        }
        return res;
    }

    /**
     * Executes a template-based function body.
     *
     * @param template  String, not {@code null}
     * @param pm        {@link PrefixMapping}
     * @param variables not {@code null}, String (variable) to {@link RDFNode} binding
     * @param graph     {@link Graph}, not {@code null}
     * @return {@link NodeValue}
     * @throws ExprEvalException if something is wrong
     */
    protected abstract NodeValue exec(String template,
                                      PrefixMapping pm,
                                      Function<String, RDFNode> variables,
                                      Graph graph);

    @Override
    public void build(String uri, ExprList args) {
        // nothing
    }

    @Override
    public NodeValue exec(Binding binding, ExprList args, String func, FunctionEnv env) {
        if (args.isEmpty()) {
            throw new ExprEvalException("Missing template argument for function " + func);
        }
        Expr e = args.get(0);
        String template = e.eval(binding, env).asNode().getLiteralLexicalForm();
        Model model = ModelFactory.createModelForGraph(env.getActiveGraph());
        Function<String, RDFNode> variables = var -> {
            Node node = binding.get(Var.alloc(var));
            if (node != null) {
                return model.asRDFNode(node);
            }
            if (!Character.isDigit(var.charAt(0))) {
                return null;
            }
            int index = Integer.parseInt(var);
            if (index <= 0 || index >= args.size()) {
                return null;
            }
            Expr expr = args.get(index);
            if (expr == null) return null;
            try {
                NodeValue value = expr.eval(binding, env);
                if (value != null) {
                    node = value.asNode();
                    return ModelFactory.createModelForGraph(env.getActiveGraph()).asRDFNode(node);
                }
            } catch (ExprEvalException ex) {
                throw new ExprEvalException("Cannot exec " + func + ": '" + ex.getMessage() + "'", ex);
            }
            return null;
        };
        PrefixMapping pm = env.getActiveGraph().getPrefixMapping();
        return exec(template, pm, variables, env.getActiveGraph());
    }

}

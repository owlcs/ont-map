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

package ru.avicomp.map.utils;

import org.apache.jena.ext.xerces.util.XMLChar;
import org.apache.jena.graph.*;
import org.apache.jena.rdf.model.impl.Util;
import org.apache.jena.shared.PrefixMapping;
import ru.avicomp.ontapi.jena.UnionGraph;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * A {@link GraphListener Graph Listener}, which takes care about the prefixes,
 * in order to produce good looking model, that only has the necessary prefixes.
 * Note: it is always better to control prefixes manually,
 * especially for large graphs or in case of intensive mutation operations on it.
 * <p>
 * Created by @szuev on 12.04.2018.
 */
@SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
public class AutoPrefixListener extends BaseGraphListener {

    private final PrefixMapping prefixes;

    private final Function<Node, String> namespaceExtractor;
    private final UnaryOperator<String> prefixExtractor;

    private Map<String, AtomicLong> usages = new HashMap<>();

    /**
     * Creates an auto-prefix listener.
     *
     * @param prefixes     {@link PrefixMapping} to write computed prefix map, not {@code null}
     * @param getNamespace {@link Function}, cannot be {@code null},
     *                     this function accepts {@link Node} and returns its namespace or {@code null}
     * @param getPrefix    {@link UnaryOperator}, cannot be {@code null},
     *                     this function accepts {@code String} namespace and returns a prefix for it
     */
    protected AutoPrefixListener(PrefixMapping prefixes,
                                 Function<Node, String> getNamespace,
                                 UnaryOperator<String> getPrefix) {
        this.prefixes = Objects.requireNonNull(prefixes);
        this.namespaceExtractor = Objects.requireNonNull(getNamespace);
        this.prefixExtractor = Objects.requireNonNull(getPrefix);
    }

    /**
     * Registers a default {@link AutoPrefixListener auto-prefix listener} implementation,
     * which is based on prefix library and custom uri-parsing mechanism.
     * All registered before auto-prefix-listeners will be detached.
     *
     * @param g       {@link UnionGraph} source graph
     * @param library {@link PrefixMapping} the library
     * @return {@link AutoPrefixListener}
     */
    public static AutoPrefixListener addAutoPrefixListener(UnionGraph g, PrefixMapping library) {
        UnionGraph.OntEventManager evm = g.getEventManager();
        // clear all auto-prefixes listeners that were previously registered
        evm.listeners()
                .filter(x -> AutoPrefixListener.class.isAssignableFrom(x.getClass()))
                .forEach(evm::unregister);
        AutoPrefixListener res = createAutoPrefixesListener(g, library);
        evm.register(res);
        return res;
    }

    /**
     * Creates a {@link AutoPrefixListener} instance for the given {@code graph}.
     * @param graph {@link Graph}, not {@code null}
     * @param library {@link PrefixMapping} prefixes library, not {@code null}
     * @return {@link AutoPrefixListener}
     */
    public static AutoPrefixListener createAutoPrefixesListener(Graph graph, PrefixMapping library) {
        return createAutoPrefixesListener(graph, library, uri -> needPrefix(uri) ? calculatePrefix(uri) : null);
    }

    /**
     * Creates a {@link AutoPrefixListener} instance for the given {@code graph}.
     * Once attached, the listener will control all {@code graph}'s mutations:
     * each {@link Node} added to the graph will have its prefix in {@code graph}'s {@link PrefixMapping}.
     *
     * @param graph   {@link Graph} for which the returned listener is intended, not {@code null}
     * @param library {@link PrefixMapping} that is used to find existing prefix, not {@code null}
     * @param mapper  {@code UnaryOperator} that is used as the last attempt to calculate the prefix,
     *                if it was not found in the graph itself or in the library, not {@code null}
     * @return {@link AutoPrefixListener}
     */
    public static AutoPrefixListener createAutoPrefixesListener(Graph graph,
                                                                PrefixMapping library,
                                                                UnaryOperator<String> mapper) {
        return createAutoPrefixesListener(graph.getPrefixMapping(), library, mapper);
    }

    /**
     * Creates a {@link AutoPrefixListener} instance for the given graph prefix mapping ({@code pm}),
     * the library prefix mapper ({@code library}) and the {@code mapper}.
     *
     * @param pm      {@link PrefixMapping} to be modified, not {@code null}
     * @param library {@link PrefixMapping} that is used to find existing prefix, not {@code null}
     * @param mapper  {@code UnaryOperator} that is used as the last attempt to calculate the prefix,
     *                if it was not found in the graph itself or in the library, not {@code null}
     * @return {@link AutoPrefixListener}
     */
    public static AutoPrefixListener createAutoPrefixesListener(PrefixMapping pm,
                                                                PrefixMapping library,
                                                                UnaryOperator<String> mapper) {
        Function<Node, String> namespaceExtractor = node -> {
            if (node.isURI()) {
                return nonEmpty(node.getNameSpace());
            }
            if (node.isLiteral()) {
                String uri = node.getLiteralDatatypeURI();
                return uri == null ? null : nonEmpty(NodeFactory.createURI(uri).getNameSpace());
            }
            return null;
        };
        UnaryOperator<String> prefixExtractor = ns -> {
            String res = pm.getNsURIPrefix(ns);
            if (!isEmpty(res)) return res;
            res = library.getNsURIPrefix(ns);
            if (!isEmpty(res)) return res;
            return mapper.apply(ns);
        };
        return new AutoPrefixListener(pm, namespaceExtractor, prefixExtractor);
    }

    /**
     * Auxiliary method,
     * that attempts to compute a good-looking prefix from the given URI.
     *
     * @param uri String, not {@code null}
     * @return String or {@code null}
     */
    public static String calculatePrefix(String uri) {
        return calculatePrefix(uri, 10);
    }

    /**
     * Tries to compute a good-looking prefix from the given URI with length restriction.
     *
     * @param uri   String, not {@code null}
     * @param limit maximum allowed length of the prefix
     * @return String or {@code null}
     */
    public static String calculatePrefix(String uri, int limit) {
        String body = stripURI(getNameSpace(uri));
        String name = getLocalName(body);
        if (name != null && name.length() <= limit) {
            return name.toLowerCase();
        }
        return abbreviate(body, limit);
    }

    /**
     * Abbreviates the URI by choosing only the first letter from the every significant parts of it.
     * @param uri String, not {@code null}
     * @param limit int
     * @return String or {@code null}
     */
    public static String abbreviate(String uri, int limit) {
        StringBuilder res = new StringBuilder();
        for (String part : uri.split("[.\\-/\\d]")) {
            char c = firstNCName(part, res.length() == 0);
            if (c != 0)
                res.append(c);
            if (res.length() > limit) {
                break;
            }
        }
        return res.length() == 0 ? null : res.toString();
    }

    /**
     * Finds and returns the valid NCName character from the given string and converts it to lower case.
     *
     * @param s     String
     * @param start boolean
     * @return char or {@code 0}
     */
    private static char firstNCName(String s, boolean start) {
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (start ? XMLChar.isNCNameStart(ch) : XMLChar.isNCName(ch)) {
                return Character.toLowerCase(ch);
            }
        }
        return 0;
    }

    /**
     * Strips the URI to the body part, removing the schema part and the end symbols ({@code #} or {@code /}).
     *
     * @param uri String, not {@code null}
     * @return String, not {@code null} (possibly, the same)
     */
    private static String stripURI(String uri) {
        return uri.replaceFirst("^[^:]+:[/]*(.+[^#/])[#/]*$", "$1");
    }

    /**
     * Gets a namespace part from URI.
     *
     * @param uri String, not {@code null}
     * @return String, not {@code null}
     */
    private static String getNameSpace(String uri) {
        int i = Util.splitNamespaceXML(uri);
        return i == uri.length() ? uri : uri.substring(0, i);
    }

    /**
     * Gets a local part from URI of {@code null}.
     *
     * @param uri String, not {@code null}
     * @return String or {@code null}
     */
    private static String getLocalName(String uri) {
        int i = Util.splitNamespaceXML(uri);
        return i == uri.length() || i <= 1 ? null : uri.substring(i);
    }

    /**
     * Decides whether the given uri can have the prefix.
     *
     * @param uri String, not {@code null}
     * @return boolean
     */
    private static boolean needPrefix(String uri) {
        return uri.endsWith("#") && !uri.endsWith("/#");
    }

    private static String nonEmpty(String s) {
        return isEmpty(s) ? null : s;
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    @Override
    protected void addEvent(Triple t) {
        addPrefix(t.getSubject());
        addPrefix(t.getPredicate());
        addPrefix(t.getObject());
    }

    @Override
    protected void deleteEvent(Triple t) {
        removePrefix(t.getSubject());
        removePrefix(t.getPredicate());
        removePrefix(t.getObject());
    }

    protected void addPrefix(Node n) {
        String ns = namespaceExtractor.apply(n);
        if (ns == null) {
            return;
        }
        String pref = prefixExtractor.apply(ns);
        if (pref == null) {
            return;
        }
        addPrefix(pref, ns);
    }

    protected void removePrefix(Node n) {
        String ns = namespaceExtractor.apply(n);
        if (ns == null) {
            return;
        }
        String pref = prefixExtractor.apply(ns);
        if (pref == null) {
            return;
        }
        removePrefix(pref, ns);
    }

    protected void addPrefix(String pref, String ns) {
        usages.computeIfAbsent(ns, i -> new AtomicLong()).incrementAndGet();
        String prevPref = prefixes.getNsURIPrefix(ns);
        if (prevPref != null) {
            prefixes.removeNsPrefix(prevPref);
        }
        prefixes.setNsPrefix(pref, ns);
    }

    protected void removePrefix(String pref, String ns) {
        if (usages.computeIfAbsent(ns, i -> new AtomicLong()).decrementAndGet() > 0) {
            // still have usages -> cannot remove prefix
            return;
        }
        usages.remove(ns);
        prefixes.removeNsPrefix(pref);
    }
}

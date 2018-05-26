package ru.avicomp.map.utils;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.graph.GraphListener;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.shared.PrefixMapping;
import ru.avicomp.ontapi.jena.UnionGraph;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * {@link GraphListener Graph Listener} to produce good looking model.
 * Not thread-safe.
 * Note: it is always better to control prefixes manually.
 * <p>
 * Created by @szuev on 12.04.2018.
 */
@SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
public class AutoPrefixListener extends BaseGraphListener {
    private final PrefixMapping prefixes;
    private final NodePrefixExtractor extractor;

    private Map<String, AtomicLong> map = new HashMap<>();

    protected AutoPrefixListener(PrefixMapping prefixes, NodePrefixExtractor extractor) {
        this.prefixes = prefixes;
        this.extractor = extractor;
    }

    @Override
    protected void addEvent(Triple t) {
        asIterable(t).forEach(n -> {
            String pref = extractor.extract(n);
            if (pref == null) return;
            if (map.computeIfAbsent(pref, i -> new AtomicLong()).incrementAndGet() != 1)
                return;
            prefixes.setNsPrefix(pref, extractor.getNameSpace(n));
        });
    }

    @Override
    protected void deleteEvent(Triple t) {
        asIterable(t).forEach(n -> {
            String pref = extractor.extract(n);
            if (pref == null) return;
            if (map.computeIfAbsent(pref, i -> new AtomicLong()).decrementAndGet() > 0)
                return;
            map.remove(pref);
            prefixes.removeNsPrefix(pref);
        });
    }

    public static Iterable<Node> asIterable(Triple t) {
        Set<Node> res = new HashSet<>(3);
        res.add(t.getSubject());
        res.add(t.getPredicate());
        res.add(t.getObject());
        return res;
    }

    /**
     * Registers a default {@link AutoPrefixListener auto-prefix listener} implementation,
     * which is based on prefix library and custom uri-parsing mechanism.
     *
     * @param g       {@link UnionGraph} source graph
     * @param library {@link PrefixMapping} the library
     * @return a fresh {@link NodePrefixExtractor node prefix extractor}
     */
    public static NodePrefixExtractor addAutoPrefixListener(UnionGraph g, PrefixMapping library) {
        NodePrefixExtractor extractor = new NodePrefixExtractor(library, ns -> {
            ns = ns.replaceFirst("#$", "");
            //if (ns.contains("#")) return null;
            //if (ns.endsWith("/")) return null;
            String res = NodeFactory.createURI(ns).getLocalName();
            if (StringUtils.isEmpty(res)) return null;
            return res.replace(".", "-").toLowerCase();
        });
        attachAutoPrefixListener(g, extractor);
        return extractor;
    }

    /**
     * Creates an {@link AutoPrefixListener auto-prefix listener} and attaches it to the specified graph.
     * All registered before auto-prefix-listeners will be detached.
     *
     * @param g         {@link UnionGraph}
     * @param extractor {@link NodePrefixExtractor}, a function to map {@link Node} -&gt; String (prefix)
     */
    public static void attachAutoPrefixListener(UnionGraph g, NodePrefixExtractor extractor) {
        PrefixMapping pm = g.getPrefixMapping();
        UnionGraph.OntEventManager m = g.getEventManager();
        // clear all registered before
        m.listeners()
                .map(GraphListener::getClass)
                .filter(AutoPrefixListener.class::equals)
                .map(AutoPrefixListener.class::cast)
                .forEach(m::unregister);
        m.register(new AutoPrefixListener(pm, extractor));
    }

    /**
     * A helper to handle graph (node) prefixes.
     * <p>
     * Created by @szuev on 13.04.2018.
     */
    public static class NodePrefixExtractor {
        protected final PrefixMapping library;
        protected final Function<String, String> nsMapper;

        /**
         * Constructor.
         *
         * @param library         {@link PrefixMapping} collection of prefixes,
         *                        which is used as a library to search most suitable prefixes
         * @param namespaceMapper a Function to create prefix from a namespace
         *                        as last attempt in case there is no corresponding prefix in the first argument
         */
        public NodePrefixExtractor(PrefixMapping library, Function<String, String> namespaceMapper) {
            this.library = PrefixMapping.Factory.create().setNsPrefixes(library);
            this.nsMapper = namespaceMapper;
        }

        /**
         * Gets a namespace from a node.
         *
         * @param node {@link Node}
         * @return String or null
         */
        public String getNameSpace(Node node) {
            if (node.isLiteral()) {
                node = NodeFactory.createURI(node.getLiteralDatatypeURI());
            }
            if (!node.isURI()) {
                return null;
            }
            return node.getNameSpace();
        }

        /**
         * Adds additional prefixes to the internal collection.
         *
         * @param prefixes {@link PrefixMapping}
         */
        public void addPrefixes(PrefixMapping prefixes) {
            this.library.setNsPrefixes(prefixes);
        }

        /**
         * Extracts a prefix from a {@link Node graph node}.
         * For a node that cannot be mapped to string the function will return null.
         *
         * @param node {@link Node}
         * @return String or null
         */
        public String extract(Node node) {
            String ns = getNameSpace(node);
            if (StringUtils.isEmpty(ns)) return null;
            String res = library.getNsURIPrefix(ns);
            if (!StringUtils.isEmpty(res)) return res;
            return nsMapper.apply(ns);
        }

    }
}

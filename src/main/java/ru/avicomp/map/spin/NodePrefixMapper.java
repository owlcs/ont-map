package ru.avicomp.map.spin;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.shared.PrefixMapping;

/**
 * A helper to handle graph (node) prefixes.
 * <p>
 * Created by @szuev on 13.04.2018.
 */
@SuppressWarnings("WeakerAccess")
public class NodePrefixMapper {
    private final PrefixMapping library;

    /**
     * Constructor.
     * An external prefix mapping is used as a library to search most suitable prefixes.
     * In case there is no prefix in library the class will try to create it.
     *
     * @param library {@link PrefixMapping}
     */
    public NodePrefixMapper(PrefixMapping library) {
        this.library = PrefixMapping.Factory.create().setNsPrefixes(library);
    }

    /**
     * Adds prefixes to the internal collection.
     *
     * @param prefixes {@link PrefixMapping}
     */
    public void addPrefixes(PrefixMapping prefixes) {
        this.library.setNsPrefixes(prefixes);
    }

    /**
     * Gets a namespace for a node.
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
        ns = ns.replaceFirst("#$", "");
        //if (ns.contains("#")) return null;
        //if (ns.endsWith("/")) return null;
        res = NodeFactory.createURI(ns).getLocalName();
        if (StringUtils.isEmpty(res)) return null;
        return res.replace(".", "-").toLowerCase();
    }

}

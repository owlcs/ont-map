package ru.avicomp.map.spin;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.shared.PrefixMapping;
import org.topbraid.spin.vocabulary.*;

/**
 * A helper to handle graph (node) prefixes.
 * <p>
 * Created by @szuev on 13.04.2018.
 */
@SuppressWarnings("WeakerAccess")
public class NodePrefixMapper {
    public static final PrefixMapping LIBRARY = PrefixMapping.Factory.create()
            .setNsPrefixes(PrefixMapping.Extended)
            .setNsPrefix(SP.PREFIX, SP.NS)
            .setNsPrefix(SPIN.PREFIX, SPIN.NS)
            .setNsPrefix(SPL.PREFIX, SPL.NS)
            .setNsPrefix(SPIF.PREFIX, SPIF.NS)
            .setNsPrefix(SPINMAP.PREFIX, SPINMAP.NS)
            .setNsPrefix(SPINMAPL.PREFIX, SPINMAPL.NS)
            .lock();

    /**
     * Gets a namespace from a node.
     *
     * @param node {@link Node}
     * @return String or null.
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
        String res = LIBRARY.getNsURIPrefix(ns);
        if (!StringUtils.isEmpty(res)) return res;
        ns = ns.replaceFirst("#$", "");
        //if (ns.contains("#")) return null;
        //if (ns.endsWith("/")) return null;
        res = NodeFactory.createURI(ns).getLocalName();
        if (StringUtils.isEmpty(res)) return null;
        return res.replace(".", "-").toLowerCase();
    }
}

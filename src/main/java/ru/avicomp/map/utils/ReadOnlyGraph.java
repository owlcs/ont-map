package ru.avicomp.map.utils;

import org.apache.jena.graph.*;
import org.apache.jena.shared.AccessDeniedException;
import org.apache.jena.shared.AddDeniedException;
import org.apache.jena.shared.DeleteDeniedException;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.graph.UnmodifiableGraph;

/**
 * Read-only graph implementation.
 * No modification (including changing prefixes) is not allowed for this graph.
 * <p>
 * Created by @szuev on 21.06.2018.
 */
public class ReadOnlyGraph extends UnmodifiableGraph {

    @SuppressWarnings("deprecation")
    private static final Capabilities READ_ONLY_CAPABILITIES = new Capabilities() {
        @Override
        public boolean sizeAccurate() {
            return true;
        }

        @Override
        public boolean addAllowed() {
            return addAllowed(false);
        }

        @Override
        public boolean addAllowed(boolean every) {
            return false;
        }

        @Override
        public boolean deleteAllowed() {
            return deleteAllowed(false);
        }

        @Override
        public boolean deleteAllowed(boolean every) {
            return false;
        }

        @Override
        public boolean canBeEmpty() {
            return true;
        }

        @Override
        public boolean iteratorRemoveAllowed() {
            return false;
        }

        @Override
        public boolean findContractSafe() {
            return true;
        }

        @Override
        public boolean handlesLiteralTyping() {
            return true;
        }
    };

    protected PrefixMapping pm;

    public ReadOnlyGraph(Graph base) {
        super(base);
        this.pm = base.getPrefixMapping();
    }

    @Override
    public void add(Triple t) {
        throw new AddDeniedException("Read only graph: can't add triple " + t);
    }

    @Override
    public void remove(Node s, Node p, Node o) {
        GraphUtil.remove(this, s, p, o);
    }

    @Override
    public void delete(Triple t) {
        throw new DeleteDeniedException("Read only graph: can't delete triple " + t);
    }

    @Override
    public void clear() {
        throw new AccessDeniedException("Read only graph: can't clear");
    }

    @Override
    public Capabilities getCapabilities() {
        return READ_ONLY_CAPABILITIES;
    }

    @Override
    public PrefixMapping getPrefixMapping() {
        return PrefixMapping.Factory.create().setNsPrefixes(pm).lock();
    }

}

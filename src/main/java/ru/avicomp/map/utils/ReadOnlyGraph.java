package ru.avicomp.map.utils;

import org.apache.jena.graph.Capabilities;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.graph.UnmodifiableGraph;

/**
 * Read-only graph implementation.
 * <p>
 * Created by @szuev on 21.06.2018.
 */
public class ReadOnlyGraph extends UnmodifiableGraph {

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

    public ReadOnlyGraph(Graph base) {
        super(base);
    }

    @Override
    public void add(Triple t) {
        throw new UnsupportedOperationException("Read only graph: can't add triple " + t);
    }

    @Override
    public void remove(Node s, Node p, Node o) {
        delete(Triple.createMatch(s, p, o));
    }

    @Override
    public void delete(Triple t) {
        throw new UnsupportedOperationException("Read only graph: can't delete triple " + t);
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("Read only graph: can't clear");
    }

    @Override
    public Capabilities getCapabilities() {
        return READ_ONLY_CAPABILITIES;
    }

}

package ru.avicomp.map.utils;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.graph.UnmodifiableGraph;

/**
 * Read-only graph implementation.
 * <p>
 * Created by @szuev on 21.06.2018.
 */
public class ReadOnlyGraph extends UnmodifiableGraph {

    public ReadOnlyGraph(Graph base) {
        super(base);
    }

    @Override
    public void add(Triple t) {
        throw new UnsupportedOperationException("Read only graph: can't add triple " + t);
    }

    @Override
    public void delete(Triple t) {
        throw new UnsupportedOperationException("Read only graph: can't delete triple " + t);
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("Read only graph: can't clear");
    }
}

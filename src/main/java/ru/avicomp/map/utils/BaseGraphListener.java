package ru.avicomp.map.utils;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.GraphListener;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.util.graph.GraphListenerBase;

/**
 * An abstract Graph Listener that handle any change on graph.
 * Created by @szuev on 26.05.2018.
 *
 * @see GraphListenerBase
 */
public abstract class BaseGraphListener extends GraphListenerBase implements GraphListener {

    @Override
    protected abstract void addEvent(Triple t);

    @Override
    protected abstract void deleteEvent(Triple t);

    @Override
    public void notifyAddGraph(Graph g, Graph other) {
        other.find(Triple.ANY).forEachRemaining(this::addEvent);
    }

    @Override
    public void notifyDeleteGraph(Graph g, Graph other) {
        other.find(Triple.ANY).forEachRemaining(this::deleteEvent);
    }
}

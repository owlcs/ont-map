package ru.avicomp.map.utils;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.util.graph.GraphListenerBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by @szuev on 21.05.2018.
 */
public class DebugLogListener extends GraphListenerBase {
    private static final Logger LOGGER = LoggerFactory.getLogger(DebugLogListener.class);

    @Override
    public void notifyAddGraph(Graph g, Graph other) {
        other.find(Triple.ANY).forEachRemaining(this::addEvent);
    }

    @Override
    public void notifyDeleteGraph(Graph g, Graph other) {
        other.find(Triple.ANY).forEachRemaining(this::deleteEvent);
    }

    @Override
    protected void addEvent(Triple t) {
        LOGGER.debug("ADD: {}", t);
    }

    @Override
    protected void deleteEvent(Triple t) {
        LOGGER.debug("DELETE: {}", t);
    }
}

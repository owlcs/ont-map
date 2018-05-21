package ru.avicomp.map.utils;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.util.graph.GraphListenerBase;

import java.util.function.BiConsumer;

/**
 * Created by @szuev on 21.05.2018.
 */
public class GraphLogListener extends GraphListenerBase {

    private final BiConsumer<String, Triple> logger;

    public GraphLogListener(BiConsumer<String, Triple> logger) {
        this.logger = logger;
    }

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
        logger.accept("ADD: {}", t);
    }

    @Override
    protected void deleteEvent(Triple t) {
        logger.accept("DELETE: {}", t);
    }
}

package ru.avicomp.map.utils;

import org.apache.jena.graph.Triple;

import java.util.function.BiConsumer;

/**
 * Created by @szuev on 21.05.2018.
 */
public class GraphLogListener extends BaseGraphListener {

    private final BiConsumer<String, Triple> logger;

    public GraphLogListener(BiConsumer<String, Triple> logger) {
        this.logger = logger;
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

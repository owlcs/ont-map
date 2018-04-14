package ru.avicomp.map;

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.shared.PrefixMapping;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * A Map Manager.
 * This should be the only place to provide everything that required to build and conduct OWL2 mapping
 * including spin-inferencing and class-properties hierarchy.
 * TODO: not ready: everything can change
 * Created by @szuev on 06.04.2018.
 */
public interface MapManager {

    /**
     * Returns all available functions.
     *
     * @return Stream of {@link MapFunction}s
     */
    Stream<MapFunction> functions();

    /**
     * TODO: do we need it here?
     *
     * @return {@link PrefixMapping}
     */
    PrefixMapping prefixes();

    default MapFunction getFunction(String name) throws MapJenaException {
        return functions()
                .filter(f -> Objects.equals(name, f.name()))
                .findFirst()
                .orElseThrow(() -> new MapJenaException("Function " + name + " not found."));
    }

    /**
     * Creates a fresh mapping model.
     * @return {@link MapModel}
     */
    MapModel createModel();

    /**
     * Creates an engine to inference mappings.
     * @return {@link InferenceEngine}
     */
    InferenceEngine getInferenceEngine();

    interface InferenceEngine {
        /**
         * Runs inference process.
         *
         * @param mapping a mapping instructions in form of {@link MapModel}
         * @param source  a graph with data to map.
         * @param target  a graph to write mapping results.
         * @throws MapJenaException in case of something is wrong.
         */
        void run(MapModel mapping, Graph source, Graph target) throws MapJenaException;

        default void run(MapModel mapping, Model source, Model target) throws MapJenaException {
            run(mapping, source.getGraph(), target.getGraph());
        }
    }

}

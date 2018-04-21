package ru.avicomp.map;

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.shared.PrefixMapping;
import ru.avicomp.ontapi.jena.model.OntGraphModel;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * A Map Manager.
 * This should be the only place to provide everything that required to build and conduct OWL2 mapping
 * including map inferencing, functions, and tools to create new functions.
 * TODO: not ready: everything can change
 * Created by @szuev on 06.04.2018.
 */
public interface MapManager {

    /**
     * Returns a collection of all know prefixes from an underling library.
     * This method is just for convenience.
     *
     * @return Unmodifiable {@link PrefixMapping PrefixMapping}
     */
    PrefixMapping prefixes();

    /**
     * Returns all available functions.
     *
     * @return Stream of {@link MapFunction}s
     */
    Stream<MapFunction> functions();

    /**
     * Gets function by name, which is an iri in our single implementation.
     *
     * @param name String, not null
     * @return {@link MapFunction}
     * @throws MapJenaException if no function found.
     */
    default MapFunction getFunction(String name) throws MapJenaException {
        return functions()
                .filter(f -> Objects.equals(name, f.name()))
                .findFirst()
                .orElseThrow(() -> new MapJenaException("Function " + name + " not found."));
    }

    /**
     * Creates a fresh mapping model.
     *
     * @return {@link MapModel}
     */
    MapModel createModel();


    /**
     * Provides a class-properties mapping.
     *
     * Basically, this is for GUI's drawing and there is no direct usage in the API.
     * Please note: spin-map (an API default implementation) does not require a property to be "belonged" to a class,
     * i.e. it allows to perform mapping even a property has no domain with a class from a context.
     *
     * @param model {@link OntGraphModel OWL model}
     * @return {@link ClassPropertyMap class properties mapping object}
     */
    ClassPropertyMap getClassProperties(OntGraphModel model);

    /**
     * Creates an engine to inference mappings.
     *
     * @return {@link InferenceEngine}
     */
    InferenceEngine getInferenceEngine();

    /**
     * An inference engine.
     * In our (currently single) implementation it is SPIN.
     */
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

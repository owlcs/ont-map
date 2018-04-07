package ru.avicomp.map;

import org.apache.jena.shared.PrefixMapping;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * A Map Manager.
 * This should be the only place to provide everything that required to build and conduct OWL2 mapping
 * including spin-inferencing and class-properties hierarchy.
 * TODO: not ready.
 * Created by @szuev on 06.04.2018.
 */
public interface MapManager {

    Stream<MapFunction> functions();

    PrefixMapping prefixes();

    default MapFunction getFunction(String name) throws MapJenaException {
        return functions()
                .filter(f -> Objects.equals(name, f.name()))
                .findFirst()
                .orElseThrow(() -> new MapJenaException("Function " + name + " not found."));
    }
}

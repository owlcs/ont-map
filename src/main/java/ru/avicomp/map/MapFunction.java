package ru.avicomp.map;

import java.util.stream.Stream;

/**
 * This is going to be a wrapper for {@link org.topbraid.spin.model.Function} and
 * common interface for any possible functions used while OWL2 -> OWL2 mappings.
 * It is not a part of jena model graph system (i.e. not a {@link org.apache.jena.rdf.model.RDFNode rdf-node}).
 * TODO: not ready.
 * <p>
 * Created by @szuev on 06.04.2018.
 */
public interface MapFunction {

    String name();

    String returnType();

    Stream<Arg> args();

    interface Arg {
        String name();

        String type();
    }
}

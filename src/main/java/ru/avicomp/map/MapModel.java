package ru.avicomp.map;

import org.apache.jena.rdf.model.Model;

/**
 * An extended {@link Model jena model} with mapping instructions.
 * <p>
 * Created by @szuev on 10.04.2018.
 */
public interface MapModel extends Model {

    MapManager getManager();

    default void runInferences(Model source, Model target) throws MapJenaException {
        getManager().getInferenceEngine().run(this, source.getGraph(), target.getGraph());
    }

}

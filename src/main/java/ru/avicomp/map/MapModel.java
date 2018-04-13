package ru.avicomp.map;

import org.apache.jena.rdf.model.Model;
import ru.avicomp.ontapi.jena.model.OntID;

/**
 * An extended {@link Model jena model} with mapping instructions.
 * Note: it does not have to be OWL2 ontology.
 * Moreover, a spin implementation is not OWL2-, but RDFS-ontology.
 * <p>
 * Created by @szuev on 10.04.2018.
 */
public interface MapModel extends Model {

    OntID getID();

    OntID setID(String uri);

    /**
     * TODO: do we need it here?
     *
     * @return
     */
    MapManager getManager();

    default void runInferences(Model source, Model target) throws MapJenaException {
        getManager().getInferenceEngine().run(this, source.getGraph(), target.getGraph());
    }

}

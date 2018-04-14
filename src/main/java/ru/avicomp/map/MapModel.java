package ru.avicomp.map;

import org.apache.jena.rdf.model.Model;
import ru.avicomp.ontapi.jena.model.OntCE;
import ru.avicomp.ontapi.jena.model.OntID;

import java.util.stream.Stream;

/**
 * An extended {@link Model jena model} with mapping instructions.
 * Note: it does not have to be OWL2 ontology.
 * Moreover, a spin implementation is not OWL2-, but rather a RDFS-ontology.
 * Nevertheless it have to be compatible with OWL2 as possible,
 * e.g. ontology id must be the only one as it is required by OWL2.
 * <p>
 * Created by @szuev on 10.04.2018.
 */
public interface MapModel extends Model {

    OntID getID();

    OntID setID(String uri);

    Stream<Context> contexts();

    /**
     * Creates or finds context.
     * Specified class expressions can be anonymous,
     * since OWL2 allows individuals to be attached on any class expression.
     *
     * @param source {@link OntCE} a source class expression
     * @param target {@link OntCE} a target class expression
     * @return {@link Context} existing or fresh context.
     */
    Context createContext(OntCE source, OntCE target);
}

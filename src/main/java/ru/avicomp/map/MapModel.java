package ru.avicomp.map;

import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.model.OntCE;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntID;

import java.util.stream.Stream;

/**
 * A graph model with mapping instructions to perform data transfer from one OWL2 ontology to another.
 * Note: it does not have to be OWL2 ontology.
 * Moreover, a spin implementation is not OWL2-, but rather a RDFS-ontology.
 * Nevertheless it have to be compatible with OWL2 as possible,
 * e.g. ontology id must be the only one as it is required by OWL2.
 * <p>
 * Created by @szuev on 10.04.2018.
 */
public interface MapModel {

    /**
     * Returns an ontology id.
     *
     * @return {@link OntID}, not null
     * @see ru.avicomp.ontapi.jena.model.OntGraphModel#getID()
     */
    OntID getID();

    /**
     * Sets a new ontology iri.
     *
     * @param uri String iri or null for anonymous ontology
     * @return {@link OntID}, not null
     * @see ru.avicomp.ontapi.jena.model.OntGraphModel#setID(String)
     */
    OntID setID(String uri);

    /**
     * Lists all linked ontologies, i.e. actual imports with exclusion of library.
     * Note: the result models have {@link ru.avicomp.ontapi.jena.impl.conf.OntModelConfig#ONT_PERSONALITY_LAX} inside.
     *
     * @return Stream of imports in form of {@link OntGraphModel OWL2 jena model}.
     */
    Stream<OntGraphModel> imports();

    /**
     * Lists all contexts.
     *
     * @return Stream of {@link Context}
     */
    Stream<Context> contexts();

    /**
     * Creates or finds context.
     * Specified class expressions can be anonymous,
     * since OWL2 allows individuals to be attached to any class expression.
     *
     * @param source {@link OntCE} a source class expression
     * @param target {@link OntCE} a target class expression
     * @return {@link Context} existing or fresh context.
     */
    Context createContext(OntCE source, OntCE target);


    /**
     * Removes the specified context and all related triples including property bindings.
     *
     * @param context {@link Context}
     * @return this model
     */
    MapModel removeContext(Context context);


    /**
     * Answers the Graph which this Model is presenting.
     *
     * @return {@link UnionGraph}
     */
    UnionGraph getGraph();
}

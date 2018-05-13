package ru.avicomp.map;

import ru.avicomp.ontapi.jena.model.OntCE;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntID;
import ru.avicomp.ontapi.jena.model.OntOPE;

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
     * @see OntGraphModel#getID()
     */
    OntID getID();

    /**
     * Sets a new ontology iri.
     *
     * @param uri String iri or null for anonymous ontology
     * @return {@link OntID}, not null
     * @see OntGraphModel#setID(String)
     */
    OntID setID(String uri);

    /**
     * Lists all linked (OWL-) ontologies,
     * i.e. all actual imports with exclusion of library plus this mapping model itself if it has its own OWL-declarations.
     *
     * @return Stream of linked ontologies in form of {@link OntGraphModel OWL2 jena model}.
     * @see OntGraphModel#imports()
     * @see #asOntModel()
     */
    Stream<OntGraphModel> ontologies();

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
     * Binds contexts together.
     * Both contexts must have the same source class expression,
     * context target class expressions should be linked together using an object property (i.e. through domain/range relation).
     * Bound contexts will produce object property assertions between individuals on inference.
     * If contexts target classes are not linked to each other or contexts sources classes are different, an exception are expected.
     *
     * @param left  {@link Context}
     * @param right {@link Context}
     * @return this model
     * @throws MapJenaException if something goes wrong
     * @see Context#attachContext(Context, OntOPE)
     */
    MapModel bindContexts(Context left, Context right) throws MapJenaException;

    /**
     * Answers the OWL2 model which wraps the same mapping graph.
     *
     * @return {@link OntGraphModel OWL2 jena model}
     */
    OntGraphModel asOntModel();

    /**
     * Creates a ready to use context, i.e. a class expression binding with an target rule expression.
     *
     * @param source             {@link OntCE} a source class expression
     * @param target             {@link OntCE} a target class expression
     * @param targetFunctionCall {@link MapFunction.Call}
     * @return {@link Context} a new context instance.
     * @throws MapJenaException if something goes wrong (e.g. not target function specified)
     */
    default Context createContext(OntCE source, OntCE target, MapFunction.Call targetFunctionCall) throws MapJenaException {
        return createContext(source, target).addClassBridge(targetFunctionCall);
    }
}

package ru.avicomp.map;

import org.apache.jena.graph.Graph;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import ru.avicomp.ontapi.OntologyManager;
import ru.avicomp.ontapi.jena.model.OntGraphModel;

import java.util.stream.Stream;

/**
 * A manager which combines the functionality from {@link OntologyManager} and {@link MapManager}.
 * An {@link OntologyManager} interface is intended to manage OWL2 ontologies (graphs and axioms) and relationship between them,
 * while {@link MapManager} interface is for managing {@link MapFunction functions} and {@link MapModel mappings}.
 * Created by @szuev on 21.06.2018.
 */
public interface OWLMapManager extends OntologyManager, MapManager {

    /**
     * Lists all ontologies inside manager.
     * Note: this method will not return system ontologies (i.e. library graphs).
     * They are hidden but it is possible to access them, e.g. using the method {@link OntologyManager#getOntology(IRI)}.
     *
     * @return Stream of {@link OWLOntology}
     */
    @Override
    Stream<OWLOntology> ontologies();

    /**
     * Creates a fresh mapping model <b>inside</b> the manager.
     * The return model will be attached to this manager,
     * and therefore may contain axioms,
     * can be casted to the {@link ru.avicomp.ontapi.OntologyModel} interface and so on.
     *
     * @return {@link MapModel}
     */
    @Override
    MapModel createMapModel();

    /**
     * Wraps a given model as {@link MapModel}.
     * This method does <b>not</b> add the given model to the manager,
     * for such purposes there are {@link OntologyManager#addOntology(Graph)} or {@link OntologyManager#loadOntology(IRI)} methods.
     * Instead, it registers in the manager all custom functions from specified graph
     * and returns a {@link MapModel mapping} interface aroud the specified graph.
     *
     * @param model {@link OntGraphModel}
     * @return {@link MapModel}
     * @throws MapJenaException if the given model cannot be casted to the mapping interface.
     * @see MapManager#isMapModel(OntGraphModel)
     */
    @Override
    MapModel asMapModel(OntGraphModel model) throws MapJenaException;

    /**
     * Lists all mapping models from the manager.
     *
     * @return Stream of {@link MapModel}s
     * @see OntologyManager#models()
     */
    default Stream<MapModel> mappings() {
        return models().filter(this::isMapModel).map(this::asMapModel);
    }

}

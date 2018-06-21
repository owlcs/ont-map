package ru.avicomp.map;

import org.apache.jena.shared.PrefixMapping;
import org.semanticweb.owlapi.model.OWLOntology;
import ru.avicomp.ontapi.OntologyManager;
import ru.avicomp.ontapi.jena.model.OntGraphModel;

import java.util.stream.Stream;

/**
 * TODO: description and explanation.
 * Created by @szuev on 21.06.2018.
 */
public interface OWLMapManager extends OntologyManager, MapManager {

    @Override
    PrefixMapping prefixes();

    @Override
    Stream<OWLOntology> ontologies();

    @Override
    MapModel createMapModel();

    @Override
    MapModel asMapModel(OntGraphModel model) throws MapJenaException;

    default Stream<MapModel> mappings() {
        return models().filter(this::isMapModel).map(this::asMapModel);
    }

}

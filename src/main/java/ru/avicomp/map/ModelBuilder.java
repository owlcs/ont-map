package ru.avicomp.map;

import org.apache.jena.rdf.model.Property;
import ru.avicomp.ontapi.jena.model.OntCE;
import ru.avicomp.ontapi.jena.model.OntPE;

/**
 * TODO: developing.
 * Created by @szuev on 10.04.2018.
 */
public interface ModelBuilder {

    /**
     * Build a rdf-ontology mapping model.
     *
     * @return
     * @throws MapJenaException
     */
    MapModel build() throws MapJenaException;

    /**
     * Adds ontology iri
     *
     * @param iri
     * @return
     */
    ModelBuilder addName(String iri);

    /**
     * @param src
     * @param dst
     * @param rule
     * @return
     * @throws MapJenaException
     */
    Context addClassBridge(OntCE src, OntCE dst, MapFunction.Call rule) throws MapJenaException;

    interface Context {

        <P extends Property & OntPE> Context addPropertyBridge(P src, P dst, MapFunction.Call rule) throws MapJenaException;

        ModelBuilder back();
    }
}

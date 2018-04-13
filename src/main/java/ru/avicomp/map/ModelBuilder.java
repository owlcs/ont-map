package ru.avicomp.map;

import org.apache.jena.rdf.model.Property;
import ru.avicomp.ontapi.jena.model.OntCE;
import ru.avicomp.ontapi.jena.model.OntPE;

/**
 * A builder for a rdf-ontology mapping model.
 * TODO: developing.
 * Created by @szuev on 10.04.2018.
 */
@Deprecated
public interface ModelBuilder extends Builder<MapModel> {

    /**
     * Adds ontology iri
     *
     * @param iri
     * @return
     */
    ModelBuilder addName(String iri);

    Context addClassBridge(OntCE src, OntCE dst, FunctionBuilder rule) throws MapJenaException;

    /**
     * @param src
     * @param dst
     * @param rule
     * @return
     * @throws MapJenaException
     */
    default Context addClassBridge(OntCE src, OntCE dst, MapFunction.Call rule) throws MapJenaException {
        return addClassBridge(src, dst, rule.asUnmodifiableBuilder());
    }

    interface Context {

        <P extends Property & OntPE> Context addPropertyBridge(P src, P dst, FunctionBuilder rule) throws MapJenaException;

        default <P extends Property & OntPE> Context addPropertyBridge(P src, P dst, MapFunction.Call rule) throws MapJenaException {
            return addPropertyBridge(src, dst, rule.asUnmodifiableBuilder());
        }

        ModelBuilder back();
    }
}

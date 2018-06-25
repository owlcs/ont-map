package ru.avicomp.map;

import org.apache.jena.rdf.model.Property;
import ru.avicomp.ontapi.jena.model.*;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * An interface to provide mapping between class expression and properties, which supposed to be belonged to that expression.
 * <p>
 * Created by @szuev on 19.04.2018.
 */
public interface ClassPropertyMap {

    /**
     * Lists all properties by class.
     *
     * @param ce {@link OntCE} with a model inside
     * @return <b>distinct</b> Stream of {@link Property properties}
     */
    Stream<Property> properties(OntCE ce);

    /**
     * Lists all classes by property.
     * A reverse operation to the {@link #properties(OntCE)}.
     *
     * @param pe {@link OntPE} - an property, which in OWL2 can be either {@link OntNDP}, {@link OntNAP} or {@link OntOPE}
     * @return <b>distinct</b> Stream of {@link OntCE class-expressions}
     */
    default Stream<OntCE> classes(OntPE pe) {
        return pe.getModel().ontObjects(OntCE.class)
                .filter(c -> properties(c).anyMatch(p -> Objects.equals(p, pe.asProperty())))
                .distinct();
    }

}

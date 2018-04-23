package ru.avicomp.map;

import org.apache.jena.rdf.model.Property;

import java.util.stream.Stream;

/**
 * This class represents a properties binding, which is a component of context.
 * To tie together OWL2 annotation ({@code owl:AnnotationProperty}) and data properties ({@code owl:DatatypeProperty})
 * from source and target.
 * Only such OWL2 entities can have assertions with literals as object, which can be attached to an individual.
 * <p>
 * Created by @szuev on 16.04.2018.
 */
public interface PropertyBridge {

    Stream<Property> sources();

    Property getTarget();

    MapFunction.Call getExpression();

    Context getContext();

}

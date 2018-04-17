package ru.avicomp.map.spin.impl;

import org.apache.jena.enhanced.EnhGraph;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.impl.ResourceImpl;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.spin.MapModelImpl;
import ru.avicomp.map.spin.model.MapProperties;

import java.util.stream.Stream;

/**
 * Implementation of properties binding rule.
 * <p>
 * Created by @szuev on 16.04.2018.
 */
public class MapPropertiesImpl extends ResourceImpl implements MapProperties {

    public MapPropertiesImpl(Node n, EnhGraph m) {
        super(n, m);
    }

    @Override
    public MapModelImpl getModel() {
        return (MapModelImpl) super.getModel();
    }

    @Override
    public Stream<Property> sources() {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public Property getTarget() {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public MapFunction.Call getExpression() {
        throw new UnsupportedOperationException("TODO");
    }
}

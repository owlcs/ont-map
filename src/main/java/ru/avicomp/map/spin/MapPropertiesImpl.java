package ru.avicomp.map.spin;

import org.apache.jena.enhanced.EnhGraph;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.shared.PrefixMapping;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.MapContext;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.PropertyBridge;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.ontapi.jena.impl.OntObjectImpl;
import ru.avicomp.ontapi.jena.model.OntObject;
import ru.avicomp.ontapi.jena.utils.Iter;

import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of properties binding rule (for resource with type {@code spinmap:rule} related to properties map).
 * <p>
 * Created by @szuev on 16.04.2018.
 */
public class MapPropertiesImpl extends OntObjectImpl implements PropertyBridge {

    public MapPropertiesImpl(Node n, EnhGraph m) {
        super(n, m);
    }

    @Override
    public OntObject asResource() {
        return this;
    }

    @Override
    public MapModelImpl getModel() {
        return (MapModelImpl) super.getModel();
    }

    @Override
    public Stream<Property> sources() {
        return Iter.asStream(listProperties())
                .filter(s -> SpinModels.isSourcePredicate(s.getPredicate()))
                .map(Statement::getObject)
                .map(s -> s.as(Property.class));
    }

    @Override
    public Property getTarget() {
        return getRequiredProperty(SPINMAP.targetPredicate1).getObject().as(Property.class);
    }

    @Override
    public MapFunction.Call getMapping() {
        return getModel().parseExpression(this, getRequiredProperty(SPINMAP.expression).getObject());
    }

    @Override
    public MapFunction.Call getFilter() {
        if (!hasProperty(AVC.filter)) return null;
        return getModel().parseExpression(this, getPropertyResourceValue(AVC.filter), true);
    }

    @Override
    public MapContext getContext() {
        return getModel().asContext(getRequiredProperty(SPINMAP.context).getObject().asResource());
    }

    @Override
    public String toString() {
        return toString(getModel());
    }

    public String toString(PrefixMapping pm) {
        return toString(p -> pm.shortForm(p.getURI()));
    }

    public String toString(Function<Property, String> map) {
        return String.format("Properties{%s => %s}",
                sources().map(map).collect(Collectors.joining(", ", "[", "]")),
                map.apply(getTarget()));
    }

}

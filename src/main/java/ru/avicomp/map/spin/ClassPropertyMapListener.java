package ru.avicomp.map.spin;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.apache.jena.graph.GraphListener;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.sparql.util.graph.GraphListenerBase;
import ru.avicomp.map.ClassPropertyMap;
import ru.avicomp.ontapi.jena.model.OntCE;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An implementation of {@link GraphListener} to provide cached {@link ClassPropertyMap class-property-map}.
 * Any changes in a graph to which this listener is attached will reset that cache.
 * <p>
 * Created by @szuev on 19.04.2018.
 */
public class ClassPropertyMapListener extends GraphListenerBase {

    protected final LoadingCache<OntCE, Set<Property>> properties;

    public ClassPropertyMapListener(ClassPropertyMap noCache) {
        this.properties = Caffeine.newBuilder()
                .maximumSize(2048)
                //.softValues()
                .build(key -> noCache.properties(key).collect(Collectors.toSet()));
    }

    @Override
    protected void addEvent(Triple triple) {
        properties.invalidateAll();
    }

    @Override
    protected void deleteEvent(Triple triple) {
        properties.invalidateAll();
    }

    private Set<Property> getProperties(OntCE ce) {
        return Objects.requireNonNull(properties.get(ce), "Null property set for " + ce);
    }

    public ClassPropertyMap get() {
        return c -> getProperties(c).stream();
    }
}

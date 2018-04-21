package ru.avicomp.map.spin;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.apache.jena.graph.GraphListener;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.sparql.util.graph.GraphListenerBase;
import ru.avicomp.map.ClassPropertyMap;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.model.OntCE;

import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * An implementation of {@link GraphListener} to provide a cached {@link ClassPropertyMap class-property-map}.
 * Any changes in a graph to which this listener is attached on will reset that cache.
 * <p>
 * Created by @szuev on 19.04.2018.
 */
@SuppressWarnings("WeakerAccess")
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

    /**
     * Creates or finds a cached class-properties mapping which is attached to the specified graph.
     *
     * @param graph    {@link UnionGraph} a graph to attache listener
     * @param internal a factory to provide a new class-property mapping to be cached
     * @return {@link ClassPropertyMap} an existing or a new class-property mapping, not null.
     */
    public static ClassPropertyMap getCachedClassPropertyMap(UnionGraph graph, Supplier<ClassPropertyMap> internal) {
        UnionGraph.OntEventManager manager = graph.getEventManager();
        return manager.listeners()
                .filter(l -> ClassPropertyMapListener.class.equals(l.getClass()))
                .map(ClassPropertyMapListener.class::cast)
                .findFirst()
                .orElseGet(() -> {
                    ClassPropertyMapListener res = new ClassPropertyMapListener(internal.get());
                    manager.register(res);
                    return res;
                }).get();
    }

}

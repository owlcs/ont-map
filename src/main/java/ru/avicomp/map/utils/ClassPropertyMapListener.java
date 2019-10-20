/*
 * This file is part of the ONT MAP.
 * The contents of this file are subject to the Apache License, Version 2.0.
 * Copyright (c) 2019, The University of Manchester, owl.cs group.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.avicomp.map.utils;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.GraphListener;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Property;
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
 * Based on caffeine, sine it is used by OWL-API
 * <p>
 * Created by @szuev on 19.04.2018.
 */
@SuppressWarnings("WeakerAccess")
public class ClassPropertyMapListener extends BaseGraphListener {

    protected final LoadingCache<OntCE, Set<Property>> properties;

    public ClassPropertyMapListener(ClassPropertyMap noCache) {
        this.properties = buildCache(key -> noCache.properties(key).collect(Collectors.toSet()));
    }

    /**
     * Builds caffeine loading cache.
     *
     * @param loader {@link CacheLoader}
     * @param <K>    any key type
     * @param <V>    any value type
     * @return {@link LoadingCache}
     * @see ru.avicomp.ontapi.internal.CacheDataFactory
     */
    static <K, V> LoadingCache<K, V> buildCache(CacheLoader<K, V> loader) {
        return Caffeine.newBuilder()
                // a magic number from OWL-API
                .maximumSize(2048)
                //.softValues()
                .build(loader);
    }

    protected void invalidate() {
        properties.invalidateAll();
    }

    @Override
    protected void addEvent(Triple triple) {
        invalidate();
    }

    @Override
    protected void deleteEvent(Triple triple) {
        invalidate();
    }

    @Override
    public void notifyAddGraph(Graph g, Graph other) {
        invalidate();
    }

    @Override
    public void notifyDeleteGraph(Graph g, Graph other) {
        invalidate();
    }

    protected Set<Property> getProperties(OntCE ce) {
        return Objects.requireNonNull(properties.get(Objects.requireNonNull(ce, "Null class")), "Null property set for " + ce);
    }

    public ClassPropertyMap get() {
        return ce -> ClassPropertyMapListener.this.getProperties(ce).stream();
    }

    /**
     * Creates or finds a cached class-properties mapping which is attached to the specified graph through {@link ClassPropertyMapListener map listener}.
     *
     * @param graph    {@link UnionGraph} a graph to attache listener
     * @param internal a factory to provide a new class-property mapping to be cached
     * @return {@link ClassPropertyMap} an existing or a new class-property mapping, not null.
     */
    public static ClassPropertyMap getCachedClassPropertyMap(UnionGraph graph, Supplier<ClassPropertyMap> internal) {
        UnionGraph.OntEventManager events = graph.getEventManager();
        return events.listeners()
                .filter(l -> ClassPropertyMapListener.class.equals(l.getClass()))
                .map(ClassPropertyMapListener.class::cast)
                .findFirst()
                .orElseGet(() -> {
                    ClassPropertyMapListener res = new ClassPropertyMapListener(internal.get());
                    events.register(res);
                    return res;
                }).get();
    }

}

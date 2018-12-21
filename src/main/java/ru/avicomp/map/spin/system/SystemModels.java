/*
 * This file is part of the ONT MAP.
 * The contents of this file are subject to the Apache License, Version 2.0.
 * Copyright (c) 2018, Avicomp Services, AO
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

package ru.avicomp.map.spin.system;

import org.apache.jena.graph.Graph;
import org.apache.jena.mem.GraphMem;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.system.stream.LocationMapper;
import org.apache.jena.riot.system.stream.StreamManager;
import org.apache.jena.sparql.function.Function;
import org.apache.jena.sparql.pfunction.PropertyFunction;
import org.apache.jena.sys.JenaSubsystemLifecycle;
import org.apache.jena.sys.JenaSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.map.utils.ReadOnlyGraph;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * A Jena module and spin library loader.
 * <p>
 * Created by @szuev on 05.04.2018.
 */
public class SystemModels implements JenaSubsystemLifecycle {
    private static final Logger LOGGER = LoggerFactory.getLogger(SystemModels.class);

    private static volatile Map<String, Graph> graphs;
    private static volatile Map<String, Class<? extends Function>> functions;
    private static volatile Map<String, Class<? extends PropertyFunction>> properties;


    /**
     * Returns all library models from the system resources.
     * Singleton.
     *
     * @return Unmodifiable Map with {@link ReadOnlyGraph unmodifiable graph}s as values and ontology iris as keys.
     */
    public static Map<String, Graph> graphs() {
        return get(graphs);
    }

    /**
     * Selects all graphs which belong or not to the spin-family, depending on input parameter {@code spin}.
     *
     * @param spin boolean, a filter parameter
     * @return Stream of {@link Graph}s
     */
    public static Stream<Graph> graphs(boolean spin) {
        return SystemModels.graphs().entrySet().stream()
                .filter(e -> spin == Resources.SPIN_FAMILY.contains(e.getKey()))
                .map(Map.Entry::getValue);
    }

    /**
     * Returns all ARQ functions to be registered in a manager.
     *
     * @return Unmodifiable Map with uri as a key and {@link Function}-impl class java body as a value
     */
    public static Map<String, Class<? extends Function>> functions() {
        return get(functions);
    }

    /**
     * Returns all ARQ property functions to be registered in a manager.
     * A {@link PropertyFunction} has no direct usage in the API, but it can be used to construct real functions.
     *
     * @return Unmodifiable Map with uri as a key and {@link PropertyFunction}-impl class java body as a value
     */
    public static Map<String, Class<? extends PropertyFunction>> properties() {
        return get(properties);
    }

    private static <R> R get(R val) {
        if (val == null) {
            init();
        }
        if (val == null) {
            throw new IllegalStateException("Can't init.");
        }
        return val;
    }

    public static void init() {
        JenaSystem.init();
    }

    @Override
    public int level() {
        return 799;
    }

    @Override
    public void start() {
        LOGGER.debug("START");
        // The following code is just in case.
        // E.g. to prevent possible internet trips from calls of "model.read(http:..)"
        // or something like that in the depths of topbraid API.
        // A standard jena Locator (org.apache.jena.riot.system.stream.LocatorClassLoader) is used implicitly here.
        LocationMapper mapper = StreamManager.get().getLocationMapper();
        for (Resources r : Resources.values()) {
            // the resource name should not begin with '/' if java.lang.ClassLoader#getResourceAsStream is called
            mapper.addAltEntry(r.uri, r.path.replaceFirst("^/", ""));
        }
        Map<String, Graph> graphMap = new HashMap<>(Loader.GRAPHS);
        Map<String, Class<? extends Function>> functionMap = new HashMap<>(SPIFFunctions.FUNCTIONS);
        Map<String, Class<? extends PropertyFunction>> propertyMap = new HashMap<>(SPIFFunctions.PROPERTY_FUNCTIONS);
        // process all extensions:
        JenaSystem.get().snapshot().stream()
                .filter(Extension.class::isInstance).map(Extension.class::cast)
                .forEach(ext -> {
                    LOGGER.debug("Load {}", ext);
                    Map<String, Graph> eg = ext.graphs();
                    Map<String, Class<? extends Function>> ef = ext.functions();
                    Map<String, Class<? extends PropertyFunction>> epf = ext.properties();
                    if (eg != null) graphMap.putAll(eg);
                    if (ef != null) functionMap.putAll(ef);
                    if (epf != null) propertyMap.putAll(epf);
                });
        LOGGER.debug("[INIT]Graphs: {}, Functions: {}, PropertyFunctions: {}",
                graphMap.size(), functionMap.size(), propertyMap.size());
        functions = Collections.unmodifiableMap(functionMap);
        properties = Collections.unmodifiableMap(propertyMap);
        graphs = Collections.unmodifiableMap(graphMap);
    }

    @Override
    public void stop() {
        LOGGER.debug("STOP");
        graphs = null;
        functions = null;
        properties = null;
    }

    /**
     * A helper to load system resources.
     */
    private static class Loader {
        private final static Map<String, Graph> GRAPHS = load();

        private static Map<String, Graph> load() throws UncheckedIOException {
            Map<String, Graph> res = new HashMap<>();
            for (Resources f : Resources.values()) {
                Graph g = new GraphMem();
                try (InputStream in = SystemModels.class.getResourceAsStream(f.path)) {
                    RDFDataMgr.read(g, in, null, Lang.TURTLE);
                } catch (IOException e) {
                    throw new UncheckedIOException("Can't load " + f.path, e);
                }
                LOGGER.debug("Graph {} is loaded, size: {}", f.uri, g.size());
                res.put(f.uri, ReadOnlyGraph.wrap(g));
            }
            return Collections.unmodifiableMap(res);
        }
    }

}

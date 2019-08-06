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
import org.apache.jena.riot.system.stream.LocationMapper;
import org.apache.jena.riot.system.stream.StreamManager;
import org.apache.jena.sparql.function.Function;
import org.apache.jena.sparql.pfunction.PropertyFunction;
import org.apache.jena.sys.JenaSubsystemLifecycle;
import org.apache.jena.sys.JenaSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.map.utils.ReadOnlyGraph;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A Jena module and spin libraries loader,
 * that also provides access to all system {@link Graph}s with spin-descriptions,
 * {@link Function ARQ-Functions} and {@link PropertyFunction ARQ-Properties}.
 * <p>
 * Created by @szuev on 05.04.2018.
 *
 * @see Extension
 * @see JenaSystem
 */
public class SystemLibraries implements JenaSubsystemLifecycle {
    private static final Logger LOGGER = LoggerFactory.getLogger(SystemLibraries.class);

    private static volatile Map<String, Class<? extends Function>> functions;
    private static volatile Map<String, Class<? extends PropertyFunction>> properties;
    private static volatile Map<String, Supplier<Graph>> loaders;
    private static volatile Map<String, Graph> graphs;

    /**
     * Initializes the Jena System.
     * By default, initialization happens by using {@code ServiceLoader.load} to
     * find {@link JenaSubsystemLifecycle} objects.
     * ONT-MAP also uses this mechanism, but every its module must implement {@link Extension} interface.
     */
    public static void init() {
        JenaSystem.init();
    }

    /**
     * Returns all ARQ functions to be registered in a manager.
     * Singleton.
     *
     * @return Unmodifiable Map with uri as a key and {@link Function}-impl class java body as a value
     */
    public static Map<String, Class<? extends Function>> functions() {
        if (functions != null) return functions;
        init();
        if (functions == null) {
            throw new IllegalStateException("Initialization problem: can't get functions.");
        }
        return functions;
    }

    /**
     * Returns all ARQ property functions to be registered in a manager.
     * A {@link PropertyFunction} has no direct usage in the API, but it can be used to construct real functions.
     * Singleton.
     *
     * @return Unmodifiable Map with uri as a key and {@link PropertyFunction}-impl class java body as a value
     */
    public static Map<String, Class<? extends PropertyFunction>> properties() {
        if (properties != null) return properties;
        init();
        if (properties == null) {
            throw new IllegalStateException("Initialization problem: can't get properties.");
        }
        return properties;
    }

    /**
     * Returns all library models from the system resources.
     * Singleton (by class instance).
     *
     * @return Unmodifiable Map with {@link ReadOnlyGraph unmodifiable graph}s as values and ontology IRIs as keys
     */
    public static Map<String, Graph> graphs() {
        if (graphs != null) return graphs;
        if (loaders == null) {
            init();
            if (loaders == null) {
                throw new IllegalStateException("Initialization problem: can't get graph loaders.");
            }
        }
        synchronized (SystemLibraries.class) {
            if (graphs != null) return graphs;
            Map<String, Graph> res = new HashMap<>();
            LOGGER.debug("Load all system graphs (libraries).");
            loaders.forEach((k, v) -> res.put(k, v.get()));
            return graphs = Collections.unmodifiableMap(res);
        }
    }

    /**
     * Selects all graphs which belong or not to the spin-family, depending on input parameter {@code spin}.
     *
     * @param spin boolean, a filter parameter
     * @return Stream of {@link Graph}s
     */
    public static Stream<Graph> graphs(boolean spin) {
        return graphs().entrySet().stream()
                .filter(e -> spin == Resources.SPIN_FAMILY.contains(e.getKey()))
                .map(Map.Entry::getValue);
    }

    @Override
    public int level() {
        return 799;
    }

    @Override
    public void start() {
        LOGGER.debug("[START]");
        // The following code is just in case.
        // E.g. to prevent possible internet trips from calls of "model.read(http:..)"
        // or something like that in the depths of topbraid API.
        // A standard jena Locator (org.apache.jena.riot.system.stream.LocatorClassLoader) is used implicitly here.
        LocationMapper mapper = StreamManager.get().getLocationMapper();
        for (Resources r : Resources.values()) {
            // the resource name should not begin with '/' if java.lang.ClassLoader#getResourceAsStream is called
            mapper.addAltEntry(r.uri, r.path.replaceFirst("^/", ""));
        }

        // The main initialization.
        // graphs:
        Map<String, Supplier<Graph>> graphMap = new HashMap<>();
        Arrays.stream(Resources.values())
                .forEach(v -> graphMap.put(v.getURI(), () -> Resources.Loader.GRAPHS.get(v.getURI())));

        // functions:
        Map<String, Class<? extends Function>> functionMap = new HashMap<>();
        functionMap.putAll(StandardFunctions.FUNCTIONS);
        functionMap.putAll(SPIFFunctions.FUNCTIONS);
        functionMap.putAll(OptimizedFunctions.FUNCTIONS);
        // property functions:
        Map<String, Class<? extends PropertyFunction>> propertyMap = new HashMap<>();
        propertyMap.putAll(StandardFunctions.PROPERTY_FUNCTIONS);
        propertyMap.putAll(SPIFFunctions.PROPERTY_FUNCTIONS);
        // process all extensions:
        JenaSystem.get().snapshot().stream()
                .filter(Extension.class::isInstance).map(Extension.class::cast)
                .forEach(ext -> {
                    LOGGER.debug("Load extension '{}'", ext);
                    Map<String, Supplier<Graph>> eg = ext.graphs();
                    Map<String, Class<? extends Function>> ef = ext.functions();
                    Map<String, Class<? extends PropertyFunction>> epf = ext.properties();
                    if (eg != null) {
                        eg.forEach((k, v) -> graphMap.put(k, () -> ReadOnlyGraph.wrap(v.get())));
                    }
                    if (ef != null) functionMap.putAll(ef);
                    if (epf != null) propertyMap.putAll(epf);
                });
        LOGGER.debug("[INIT]Graphs: {}, Functions: {}, PropertyFunctions: {}",
                graphMap.size(), functionMap.size(), propertyMap.size());
        functions = Collections.unmodifiableMap(functionMap);
        properties = Collections.unmodifiableMap(propertyMap);
        loaders = Collections.unmodifiableMap(graphMap);
    }

    @Override
    public void stop() {
        LOGGER.debug("[STOP]");
        // reset all caches:
        graphs = null;
        loaders = null;
        functions = null;
        properties = null;
    }

}

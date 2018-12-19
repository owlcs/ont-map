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
import org.apache.jena.sys.JenaSubsystemLifecycle;
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

    /**
     * Returns all library models from the system resources.
     * Singleton.
     *
     * @return Unmodifiable Map with {@link ReadOnlyGraph unmodifiable graph}s as values and ontology iris as keys.
     */
    public static Map<String, Graph> graphs() {
        return Loader.GRAPHS;
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
    }

    @Override
    public void stop() {
        LOGGER.debug("STOP");
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

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

package com.github.owlcs.map.spin.system;

import com.github.owlcs.map.utils.ReadOnlyGraph;
import org.apache.jena.graph.Graph;
import org.apache.jena.mem.GraphMem;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A collection of system resources (placed in the {@code /etc} folder).
 * <p>
 * Created by @ssz on 19.12.2018.
 */
public enum Resources {
    AVC_SPIN("/etc/avc.spin.ttl", "https://github.com/avicomp/spin", false),
    AVC_LIB("/etc/avc.lib.ttl", "https://github.com/avicomp/lib", false),
    AVC_MATH("/etc/avc.math.ttl", "https://github.com/avicomp/math", false),
    AVC_FN("/etc/avc.fn.ttl", "https://github.com/avicomp/fn", false),
    AVC_XSD("/etc/avc.xsd.ttl", "https://github.com/avicomp/xsd", false),
    SP("/etc/sp.ttl", "http://spinrdf.org/sp"),
    SPIN("/etc/spin.ttl", "http://spinrdf.org/spin"),
    SPL("/etc/spl.spin.ttl", "http://spinrdf.org/spl"),
    SPIF("/etc/spif.ttl", "http://spinrdf.org/spif"),
    SPINMAP("/etc/spinmap.spin.ttl", "http://spinrdf.org/spinmap"),
    SMF("/etc/functions-smf.ttl", "http://topbraid.org/functions-smf"),
    FN("/etc/functions-fn.ttl", "http://topbraid.org/functions-fn"),
    AFN("/etc/functions-afn.ttl", "http://topbraid.org/functions-afn"),
    SMF_BASE("/etc/sparqlmotionfunctions.ttl", "http://topbraid.org/sparqlmotionfunctions"),
    SPINMAPL("/etc/spinmapl.spin.ttl", "http://topbraid.org/spin/spinmapl");

    static final Set<String> SPIN_FAMILY = Arrays.stream(values())
            .filter(x -> x.spin).map(Resources::getURI).collect(Collectors.toSet());

    final String path;
    final String uri;
    final boolean spin;

    Resources(String path, String uri) {
        this(path, uri, true);
    }

    Resources(String path, String uri, boolean spin) {
        this.path = path;
        this.uri = uri;
        this.spin = spin;
    }

    /**
     * Gets the library graph name (Ontology IRI).
     *
     * @return String, not {@code null}
     */
    public String getURI() {
        return uri;
    }

    /**
     * Gets a system in-memory graph for this library.
     *
     * @return {@link Graph}, not {@code null}
     */
    public Graph getGraph() {
        return Loader.GRAPHS.get(getURI());
    }

    /**
     * A helper to load system resources.
     */
    static class Loader {
        private static final Logger LOGGER = LoggerFactory.getLogger(Loader.class);
        final static Map<String, Graph> GRAPHS = load();

        private static Map<String, Graph> load() throws UncheckedIOException {
            Map<String, Graph> res = new HashMap<>();
            for (Resources f : values()) {
                Graph g = new GraphMem();
                try (InputStream in = Loader.class.getResourceAsStream(f.path)) {
                    RDFDataMgr.read(g, in, null, Lang.TURTLE);
                } catch (IOException e) {
                    throw new UncheckedIOException("Can't load " + f.path, e);
                }
                LOGGER.debug("Graph {} is loaded, size: {}", f.uri, g.size());
                res.put(f.uri, ReadOnlyGraph.wrap(g));
            }
            return Collections.unmodifiableMap(res);
        }
    }}

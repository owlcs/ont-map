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

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A collection of system resources (placed in the {@code /etc} folder).
 * <p>
 * Created by @ssz on 19.12.2018.
 */
public enum Resources {
    AVC_SPIN("/etc/avc.spin.ttl", "http://avc.ru/spin", false),
    AVC_LIB("/etc/avc.lib.ttl", "http://avc.ru/lib", false),
    AVC_MATH("/etc/avc.math.ttl", "http://avc.ru/math", false),
    AVC_FN("/etc/avc.fn.ttl", "http://avc.ru/fn", false),
    AVC_XSD("/etc/avc.xsd.ttl", "http://avc.ru/xsd", false),
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

    public String getURI() {
        return uri;
    }

    public Graph getGraph() {
        return SystemModels.graphs().get(getURI());
    }
}
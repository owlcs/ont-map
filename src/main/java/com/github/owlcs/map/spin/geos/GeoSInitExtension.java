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

package com.github.owlcs.map.spin.geos;

import com.github.owlcs.map.spin.geos.vocabulary.SPATIAL;
import com.github.owlcs.map.spin.system.Extension;
import com.github.owlcs.ontapi.jena.utils.Graphs;
import org.apache.jena.geosparql.spatial.filter_functions.AzimuthDegreesFF;
import org.apache.jena.geosparql.spatial.filter_functions.AzimuthFF;
import org.apache.jena.geosparql.spatial.filter_functions.ConvertLatLonFF;
import org.apache.jena.geosparql.spatial.filter_functions.DistanceFF;
import org.apache.jena.graph.Factory;
import org.apache.jena.graph.Graph;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.function.Function;
import org.apache.sis.internal.system.DataDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A GeoSparql initializer.
 * <p>
 * Created by @ssz on 05.07.2019.
 */
public class GeoSInitExtension implements Extension {
    private static final Logger LOGGER = LoggerFactory.getLogger(GeoSInitExtension.class);

    public static final String AVC_GEO_URI = "https://github.com/owlcs/map/geosparql";
    private static final String AVC_GEO_PATH = "/etc/avc.geo.ttl";

    @SuppressWarnings("WeakerAccess")
    public static Graph loadGraph() {
        Graph res = Factory.createGraphMem();
        try (InputStream in = GeoSInitExtension.class.getResourceAsStream(AVC_GEO_PATH)) {
            RDFDataMgr.read(res, in, null, Lang.TURTLE);
        } catch (IOException e) {
            throw new UncheckedIOException("Can't load graph", e);
        }
        LOGGER.debug("Graph {} is loaded, size: {}", Graphs.getName(res), res.size());
        return res;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    @Override
    public Map<String, Supplier<Graph>> graphs() {
        return Collections.singletonMap(AVC_GEO_URI, GeoSInitExtension::loadGraph);
    }

    @Override
    public Map<String, Class<? extends Function>> functions() {
        return Collections.unmodifiableMap(new HashMap<String, Class<? extends Function>>() {
            {
                put(SPATIAL.convertLatLon.getURI(), ConvertLatLonFF.class);
                put(SPATIAL.distance.getURI(), DistanceFF.class);

                put(SPATIAL.azimuth.getURI(), AzimuthFF.class);
                put(SPATIAL.azimuthDeg.getURI(), AzimuthDegreesFF.class);
            }
        });
    }

    @Override
    public void start() {
        // no SQL, turn off warning:
        DataDirectory.quiet();
    }
}

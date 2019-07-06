/*
 * This file is part of the ONT MAP.
 * The contents of this file are subject to the Apache License, Version 2.0.
 * Copyright (c) 2019, Avicomp Services, AO
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

package ru.avicomp.map.spin.geos.vocabulary;

import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 * Describes spatial GeoSPARQL filter functions.
 */
public class SPATIAL {
    public static final String PREFIX = "spatial";
    public static final String BASE_URI = "http://jena.apache.org/function/spatial";
    public static final String NS = BASE_URI + "#";

    public static String getURI() {
        return NS;
    }

    public static final Resource distance = resource("distance");
    public static final Resource convertLatLon = resource("convertLatLon");

    protected static Resource resource(String local) {
        return ResourceFactory.createResource(NS + local);
    }
}

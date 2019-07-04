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

package ru.avicomp.map.spin.vocabulary;

import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 * The vocabulary for ARQ functions.
 * The namespace is deprecated, but it is used by spin-family (see {@code file://resources/etc/functions-afn.ttl}),
 * so we can either put up with it, or create our own library with the same functions but with minor customization changes.
 * The second way is easier.
 * Customization is delegated to AVC library (see file://resources/etc/avc.spin.ttl, ontology https://github.com/avicomp/spin).
 * Created by @szuev on 11.06.2018.
 *
 * @see org.apache.jena.sparql.ARQConstants#ARQFunctionLibraryURI
 * @see org.apache.jena.sparql.ARQConstants#ARQFunctionLibraryURI_Jena2
 * @see <a href='https://jena.apache.org/documentation/query/library-function.html#function-library'>Function Library</a>
 */
public class ARQ {
    // old ns:
    public static final String BASE_URI = "http://jena.hpl.hp.com/ARQ/function";
    public static final String URI = BASE_URI + "/";
    public static final String NS = BASE_URI + "#";

    /**
     * Can handle only numeric literals.
     *
     * @see org.apache.jena.sparql.function.library.max
     */
    public static final Resource max = resource("max");
    /**
     * Can handle only numeric literals.
     *
     * @see org.apache.jena.sparql.function.library.min
     */
    public static final Resource min = resource("min");

    public static Resource resource(String local) {
        return ResourceFactory.createResource(NS + local);
    }
}

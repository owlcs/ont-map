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
 * The vocabulary which describes https://www.w3.org/2005/xpath-functions/math/.
 * An addition to the spin-family in order to provide access to math functions through spin-api.
 * See file://resources/etc/avc.math.ttl
 * <p>
 * Created by @szuev on 22.05.2018.
 *
 * @see org.apache.jena.sparql.function.StandardFunctions
 * @see org.apache.jena.sparql.ARQConstants#mathPrefix
 */
public class MATH {
    public static final String BASE_URI = "http://www.w3.org/2005/xpath-functions/math";
    public static final String URI = BASE_URI + "/";
    public static final String NS = BASE_URI + "#";

    public static Resource acos = resource("acos");
    public static Resource asin = resource("asin");
    public static Resource atan = resource("atan");
    public static Resource atan2 = resource("atan2");
    public static Resource cos = resource("cos");
    public static Resource exp = resource("exp");
    public static Resource exp10 = resource("exp10");
    public static Resource log = resource("log");
    public static Resource log10 = resource("log10");
    public static Resource pi = resource("pi");
    public static Resource pow = resource("pow");
    public static Resource sin = resource("sin");
    public static Resource sqrt = resource("sqrt");
    public static Resource tan = resource("tan");

    public static Resource resource(String local) {
        return ResourceFactory.createResource(NS + local);
    }
}

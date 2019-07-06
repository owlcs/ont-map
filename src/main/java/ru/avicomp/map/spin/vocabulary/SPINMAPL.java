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

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import ru.avicomp.map.spin.system.Resources;

/**
 * Vocabulary of the Topbraid SPINMAPL library.
 * <p>
 * Created by @szuev on 10.04.2018.
 * @see <a href='http://topbraid.org/spin/spinmapl#'>spinmapl</a>
 */
public class SPINMAPL {
    public static final String BASE_URI = Resources.SPINMAPL.getURI();
    public static final String NS = BASE_URI + "#";

    // functions:
    public static final Resource self = resource("self");
    public static final Resource concatWithSeparator = resource("concatWithSeparator");
    public static final Resource buildURI1 = resource("buildURI1");
    public static final Resource buildURI2 = resource("buildURI2");
    public static final Resource buildURI3 = resource("buildURI3");
    public static final Resource buildURI4 = resource("buildURI4");
    public static final Resource buildURI5 = resource("buildURI5");
    public static final Resource relatedSubjectContext = resource("relatedSubjectContext");
    public static final Resource relatedObjectContext = resource("relatedObjectContext");
    public static final Resource changeNamespace = resource("changeNamespace");
    public static final Resource composeURI = resource("composeURI");

    // properties:
    public static final Property separator = property("separator");
    public static final Property template = property("template");
    public static final Property context = property("context");
    public static final Property predicate = property("predicate");
    public static final Property targetNamespace = property("targetNamespace");

    public static String getURI() {
        return NS;
    }

    public static Resource resource(String local) {
        return ResourceFactory.createResource(NS + local);
    }

    public static Property property(String local) {
        return ResourceFactory.createProperty(NS + local);
    }
}

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

package com.github.owlcs.map.spin.vocabulary;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 * Vocabulary of the OWL-RL SPIN Vocabulary,
 * that is a collection of rules specified by the OWL RL specification in SPIN format.
 * It is here just in case and for convenience: there are no direct use of this library in the API.
 * <p>
 * Created by @ssz on 20.12.2018.
 *
 * @see <a href='http://topbraid.org/spin/owlrl#'>owlrl</a>
 */
public class OWLRL {
    public static final String NS = "http://topbraid.org/spin/owlrl#";
    public static final Resource propertyChainHelper = resource("propertyChainHelper");

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

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

package ru.avicomp.map;

import org.apache.jena.rdf.model.Property;

import java.util.stream.Stream;

/**
 * A representation of a property mapping.
 * It is a component of {@link MapContext context} and
 * a connection (property bridge) between source and target properties, which, in OWL2,
 * can be either {@link ru.avicomp.ontapi.jena.model.OntNAP annotation property}
 * ({@code rdf:type} equals to{@code owl:AnnotationProperty})
 * or {@link ru.avicomp.ontapi.jena.model.OntNDP data property}
 * ({@code rdf:type} equals to {@code owl:DatatypeProperty}),
 * since only those properties can have a literal property assertion for an individual.
 * <p>
 * Created by @szuev on 16.04.2018.
 */
public interface PropertyBridge extends MapResource {

    /**
     * Lists all source properties.
     *
     * @return Stream of {@link Property}s
     */
    Stream<Property> sources();

    /**
     * Gets a target property.
     *
     * @return {@link Property}
     */
    Property getTarget();

    /**
     * Gets context, in which this property bridge is built.
     *
     * @return {@link MapContext}
     */
    MapContext getContext();

}

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

package ru.avicomp.map;

import org.apache.jena.rdf.model.Property;

import java.util.stream.Stream;

/**
 * This class represents a properties binding, which is a component of context.
 * To tie together OWL2 annotation ({@code owl:AnnotationProperty}) and data properties ({@code owl:DatatypeProperty})
 * from source and target.
 * Only such OWL2 entities can have assertions with literals as object, which can be attached to an individual.
 * <p>
 * Created by @szuev on 16.04.2018.
 */
public interface PropertyBridge extends MapResource {

    Stream<Property> sources();

    Property getTarget();

    MapContext getContext();

}

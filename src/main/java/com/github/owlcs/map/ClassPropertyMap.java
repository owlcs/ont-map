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

package com.github.owlcs.map;

import com.github.owlcs.ontapi.jena.model.*;
import org.apache.jena.rdf.model.Property;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * An interface to provide mapping between class expression and properties,
 * that are supposed to be belonged to that expression.
 * <p>
 * Created by @szuev on 19.04.2018.
 */
public interface ClassPropertyMap {

    /**
     * Lists all properties by a class.
     *
     * @param ce {@link OntCE} with a model inside
     * @return <b>distinct</b> Stream of {@link Property properties}
     */
    Stream<Property> properties(OntCE ce);

    /**
     * Lists all classes by a property.
     * A reverse operation to the {@link #properties(OntCE)}.
     *
     * @param pe {@link OntPE} - an property, which in OWL2 can be either {@link OntNDP}, {@link OntNAP} or {@link OntOPE}
     * @return <b>distinct</b> Stream of {@link OntCE class-expressions}
     */
    default Stream<OntCE> classes(OntPE pe) {
        return pe.getModel().ontObjects(OntCE.class)
                .filter(c -> properties(c).anyMatch(p -> Objects.equals(p, pe.asProperty())))
                .distinct();
    }

}

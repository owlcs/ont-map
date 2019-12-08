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

package com.github.owlcs.map.spin;

/**
 * The mapping configuration settings.
 * Created by @ssz on 10.06.2019.
 */
public interface MapConfig {
    /**
     * Answers {@code true},
     * if a mapping rule, that derives individual, must also take care about
     * its {@link com.github.owlcs.ontapi.jena.vocabulary.OWL#NamedIndividual owl:NamedIndividual} declaration.
     * Such a declaration is optional, but well-formed ontology should have all named individuals declared explicitly.
     * By default {@code false}.
     *
     * @return boolean
     */
    boolean generateNamedIndividuals();

    /**
     * Answers {@code true}, if inference must be running with query and function optimizations.
     * In case of {@code false}, a standard SPARQL ARQ is used.
     * By default {@code true}.
     *
     * @return boolean
     */
    boolean useAllOptimizations();
}

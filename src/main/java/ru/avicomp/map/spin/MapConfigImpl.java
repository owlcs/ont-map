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

package ru.avicomp.map.spin;

/**
 * A container to store all configuration options that are used during inference or while building mappings.
 * Currently it is a part of internal impl, not for general use.
 * <p>
 * Created by @ssz on 19.11.2018.
 */
@SuppressWarnings("WeakerAccess")
public class MapConfigImpl {

    final static MapConfigImpl INSTANCE = new MapConfigImpl();

    /**
     * Answers {@code true} if a target individuals must be a {@code owl:NamedIndividuals} also.
     *
     * @return boolean
     */
    public boolean generateNamedIndividuals() {
        return true;
    }

    /**
     * Answers {@code true}
     * if inference must be optimized, that includes replacing some spin queries with direct operations on a graph,
     * which are significantly faster than SPARQL.
     * <p>
     * An example of such optimization is runtime replacement processing
     * {@link ru.avicomp.map.spin.vocabulary.SPINMAPL#self spinmapl:self} map-instructions,
     * that produces {@link ru.avicomp.ontapi.jena.vocabulary.OWL#NamedIndividual owl:NamedIndividuals} declarations,
     * with direct writing a corresponding triple into a graph.
     * Please note: the result of inference must not be differ depending on whether this option is enabled or not.
     *
     * @return boolean
     */
    public boolean optimizeQueries() {
        return true;
    }

    /**
     * Answers {@code true} if the system must use ARQ-bodies instead of SPARQL-bodies
     * while expression evaluation if possible.
     * This is a part of optimization, since direct execution is expected to be faster than analysing and execution of SPARQL queries.
     *
     * @return boolean
     * @see ru.avicomp.map.spin.vocabulary.AVC#optimize
     */
    public boolean optimizeFunctions() {
        return true;
    }
}

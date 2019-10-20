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

package ru.avicomp.map.spin;

/**
 * A container to store all configuration options that are used during inference or while building mappings.
 * Currently it is a part of internal impl, not for general use.
 * <p>
 * Created by @ssz on 19.11.2018.
 */
@SuppressWarnings("WeakerAccess")
public class MapConfigImpl implements MapConfig {

    public final static MapConfigImpl INSTANCE = new MapConfigImpl(true, true, false);

    private final boolean namedIndividuals;
    private final boolean queriesOptimization;
    private final boolean functionsOptimization;

    private MapConfigImpl(boolean withFuncOpt, boolean withQueryOpt, boolean withNIDeclaration) {
        this.namedIndividuals = withNIDeclaration;
        this.queriesOptimization = withQueryOpt;
        this.functionsOptimization = withFuncOpt;
    }

    /**
     * Answers {@code true} if a target individuals must be a {@code owl:NamedIndividuals} also.
     *
     * @return boolean
     */
    @Override
    public boolean generateNamedIndividuals() {
        return namedIndividuals;
    }

    @Override
    public boolean useAllOptimizations() {
        return optimizeFunctions() && optimizeQueries();
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
        return queriesOptimization;
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
        return functionsOptimization;
    }

    /**
     * Creates a config with disabled/enabled optimization depending to the parameter.
     *
     * @param b boolean
     * @return new instance
     */
    public MapConfigImpl setAllOptimizations(boolean b) {
        return new MapConfigImpl(b, b, namedIndividuals);
    }

    /**
     * Creates a config with disabled/enabled generation individuals depending to the parameter.
     *
     * @param b boolean
     * @return new instance
     */
    public MapConfigImpl setGenerateNamedIndividuals(boolean b) {
        return new MapConfigImpl(functionsOptimization, queriesOptimization, b);
    }

    @Override
    public String toString() {
        return String.format("MappingConfiguration{namedIndividuals=%s, queriesOptimization=%s, functionsOptimization=%s}",
                namedIndividuals, queriesOptimization, functionsOptimization);
    }
}

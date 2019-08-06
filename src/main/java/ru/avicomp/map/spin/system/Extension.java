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

package ru.avicomp.map.spin.system;

import org.apache.jena.graph.Graph;
import org.apache.jena.sparql.function.Function;
import org.apache.jena.sparql.pfunction.PropertyFunction;
import org.apache.jena.sparql.system.InitARQ;
import org.apache.jena.sys.JenaSubsystemLifecycle;

import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

/**
 * An ONT-AMP API extension.
 * Provides additional libraries of spin-functions,
 * that will be represented as {@link ru.avicomp.map.MapFunction}s by ONT-MAP.
 * A concrete plugin may provide only ARQ function bodies as optimization for existing {@code MapFunction}s,
 * or a SPARQL-based functions without java implementation,
 * or both - a SPIN description and an ARQ implementation.
 * To make it workable an implementation class-path must be added into
 * the {@code META-INF/services/org.apache.jena.sys.JenaSubsystemLifecycle} system resource file.
 * <p>
 * Created by @ssz on 20.12.2018.
 *
 * @see org.apache.jena.sys.JenaSystem
 * @see SystemLibraries
 */
public interface Extension extends JenaSubsystemLifecycle {

    /**
     * Returns library graph loaders as a {@code Map},
     * where key is a graph name (i.e. an ontology IRI), and value is a facility to load it.
     * Each of the graphs should contain a spin-description of the functions,
     * which can be either ARQ-based (and must be provided separately by the {@link #functions()} method)
     * or SPARQL-based (i.e. must include {@link org.topbraid.spin.vocabulary.SPIN#body spin:body}
     * in their RDF description.
     *
     * @return {@code Map} of name-loader pairs
     */
    Map<String, Supplier<Graph>> graphs();

    /**
     * Returns a {@code Map} of {@link Function}s, provided by this module.
     * Each of the functions from the {@code Map} must have a spin-description in a graph
     * provided by the {@link #graphs()} method.
     *
     * @return {@code Map} of uri-class pairs
     */
    Map<String, Class<? extends Function>> functions();

    /**
     * Returns a {@code Map} of {@link PropertyFunction}s, provided by this module.
     * ONT-MAP does not use property-functions directly, but they can be used by other (real) functions.
     * Each of the property-functions from the {@code Map} must have a spin-description in a graph
     * provided by the {@link #graphs()} method.
     *
     * @return {@code Map} of uri-class pairs
     */
    default Map<String, Class<? extends PropertyFunction>> properties() {
        return Collections.emptyMap();
    }

    /**
     * Starts the module.
     */
    @Override
    default void start() {
        // no-op by default
    }

    /**
     * Stops the module.
     */
    @Override
    default void stop() {
        // no-op by default
    }

    /**
     * Provides a marker as to the level to order initialization: 40, 50, ...
     * Level must be between {@link InitARQ#level() 30} and {@link SystemLibraries#level() 799} exclusive.
     *
     * @return positive int
     */
    @Override
    default int level() {
        return 699;
    }
}

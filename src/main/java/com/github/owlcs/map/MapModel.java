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

import com.github.owlcs.ontapi.jena.model.OntClass;
import com.github.owlcs.ontapi.jena.model.OntID;
import com.github.owlcs.ontapi.jena.model.OntModel;
import com.github.owlcs.ontapi.jena.model.OntObjectProperty;
import org.apache.jena.graph.Graph;

import java.util.stream.Stream;

/**
 * A graph model containing mapping instructions for performing
 * transformation and transferring data from one OWL2 ontology to another.
 * It consists of {@link MapContext Class Mapping}s, that are instructions to handle individuals,
 * which, in turn, can include {@link PropertyBridge property bridge}s,
 * that are instructions to handle property assertions for context's individuals.
 * Please note: this graph model does not have to be an OWL2 ontology
 * (and, moreover, a spin implementation is not an OWL2-, but rather a RDFS-ontology),
 * but it have to be compatible with OWL2 as far as it's possible,
 * at least, an {@link OntID Ontology ID} must be the only one in the {@code Graph}
 * as it is required by the OWL2 specification.
 * <p>
 * Created by @szuev on 10.04.2018.
 */
public interface MapModel {

    /**
     * Answers the OWL2 model which wraps the same mapping graph.
     *
     * @return {@link OntModel OWL2 jena model}
     */
    OntModel asGraphModel();

    /**
     * Lists all linked OWL2 ontologies,
     * i.e. all actual imports with exclusion of library
     * plus this mapping model itself if it has its own OWL-declarations.
     *
     * @return Stream of linked ontologies in form of {@link OntModel OWL2 jena model}s
     * @see OntModel#imports()
     * @see #asGraphModel()
     */
    Stream<OntModel> ontologies();

    /**
     * Returns the manager with which this mapping model is associated.
     *
     * @return {@link MapManager}, not {@code null}
     */
    MapManager getManager();

    /**
     * Lists all mapping contexts.
     *
     * @return Stream of {@link MapContext}
     */
    Stream<MapContext> contexts();

    /**
     * Creates or finds a context.
     * The specified class expressions can be anonymous,
     * since OWL2 allows individuals to be attached to any class expression.
     *
     * @param source {@link OntClass} a source class expression
     * @param target {@link OntClass} a target class expression
     * @return {@link MapContext} existing or fresh context
     */
    MapContext createContext(OntClass source, OntClass target);

    /**
     * Deletes the specified context and all related triples including property bindings.
     *
     * @param context {@link MapContext}
     * @return this model
     * @throws MapJenaException in case context cannot be deleted
     */
    MapModel deleteContext(MapContext context) throws MapJenaException;

    /**
     * Binds contexts together.
     * Both contexts must have the same source class expression,
     * context target class expressions should be linked together
     * using an object property (e.g. through domain/range relation).
     * Bound contexts will produce object property assertions between individuals on inference.
     * If contexts target classes are not linked to each other
     * or contexts sources classes are different, an exception is expected.
     *
     * @param left  {@link MapContext}
     * @param right {@link MapContext}
     * @return this model
     * @throws MapJenaException if something goes wrong
     * @see MapContext#attachContext(MapContext, OntObjectProperty)
     */
    MapModel bindContexts(MapContext left, MapContext right) throws MapJenaException;

    /**
     * Validates the given {@link MapFunction.Call function-call} against this mapping model.
     * Throws {@link MapJenaException} in case the function-call has wrong arguments.
     *
     * @param func {@link MapFunction.Call} the expression to test
     * @throws MapJenaException iif the given function is not good enough to be used in this mapping
     * @see MapContext#validate(MapFunction.Call)
     */
    void validate(MapFunction.Call func) throws MapJenaException;

    /**
     * Returns the name of this mapping, that is expected to be an ontological IRI.
     *
     * @return String, not {@code null}
     */
    default String name() {
        return asGraphModel().getID().getURI();
    }

    /**
     * Lists all rules ({@link MapResource Mapping Resources}).
     * A MapResource can be either {@link MapContext} or {@link PropertyBridge}.
     * Incomplete contexts are not included to the result.
     *
     * @return Stream of {@link MapResource}s
     * @see MapContext#isValid()
     */
    default Stream<MapResource> rules() {
        return contexts().flatMap(c -> c.isValid() ? Stream.concat(Stream.of(c), c.properties()) : c.properties());
    }

    /**
     * Creates a ready to use context.
     *
     * @param source         {@link OntClass} a source class expression, not {@code null}
     * @param target         {@link OntClass} a target class expression, not {@code null}
     * @param targetFunction {@link MapFunction.Builder}, not {@code null}
     * @return {@link MapContext} a new context instance
     * @throws MapJenaException if something goes wrong (e.g. not target function specified)
     */
    default MapContext createContext(OntClass source,
                                     OntClass target,
                                     MapFunction.Builder targetFunction) throws MapJenaException {
        return createContext(source, target, targetFunction.build());
    }


    /**
     * Creates a ready to use context, i.e. a class expression binding with an target rule expression.
     *
     * @param source         {@link OntClass} a source class expression, not {@code null}
     * @param target         {@link OntClass} a target class expression, not {@code null}
     * @param targetFunction {@link MapFunction.Call}, not {@code null}
     * @return {@link MapContext} a new context instance
     * @throws MapJenaException if something goes wrong (e.g. not target function specified)
     */
    default MapContext createContext(OntClass source,
                                     OntClass target,
                                     MapFunction.Call targetFunction) throws MapJenaException {
        return createContext(source, target).addClassBridge(targetFunction);
    }

    /**
     * Performs an inference operation over the given {@code source} graph,
     * putting the result into the specified {@code target} graph.
     *
     * @param source {@link Graph}, not {@code null}
     * @param target {@link Graph}, not {@code null}
     * @throws MapJenaException unable to perform inference
     * @see MapManager.InferenceEngine#run(Graph, Graph)
     */
    default void runInference(Graph source, Graph target) throws MapJenaException {
        getManager().getInferenceEngine(this).run(source, target);
    }
}

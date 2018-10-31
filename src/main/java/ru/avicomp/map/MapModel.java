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

import org.apache.jena.graph.Graph;
import ru.avicomp.ontapi.jena.model.OntCE;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntID;
import ru.avicomp.ontapi.jena.model.OntOPE;

import java.util.stream.Stream;

/**
 * A graph model with mapping instructions to perform data transfer from one OWL2 ontology to another.
 * Note: it does not have to be OWL2 ontology.
 * Moreover, a spin implementation is not OWL2-, but rather a RDFS-ontology.
 * Nevertheless it have to be compatible with OWL2 as possible,
 * e.g. ontology id must be the only one as it is required by OWL2.
 * <p>
 * Created by @szuev on 10.04.2018.
 */
public interface MapModel {

    /**
     * Returns an ontology id.
     *
     * @return {@link OntID}, not null
     * @see OntGraphModel#getID()
     */
    OntID getID();

    /**
     * Sets a new ontology iri.
     *
     * @param uri String iri or null for anonymous ontology
     * @return {@link OntID}, not null
     * @see OntGraphModel#setID(String)
     */
    OntID setID(String uri);

    /**
     * Lists all linked (OWL-) ontologies,
     * i.e. all actual imports with exclusion of library plus this mapping model itself if it has its own OWL-declarations.
     *
     * @return Stream of linked ontologies in form of {@link OntGraphModel OWL2 jena model}s.
     * @see OntGraphModel#imports()
     * @see #asGraphModel()
     */
    Stream<OntGraphModel> ontologies();

    /**
     * Returns manager to which this mapping model is associated.
     *
     * @return {@link MapManager}
     */
    MapManager getManager();

    /**
     * Lists all contexts.
     *
     * @return Stream of {@link MapContext}
     */
    Stream<MapContext> contexts();

    /**
     * Creates or finds context.
     * Specified class expressions can be anonymous,
     * since OWL2 allows individuals to be attached to any class expression.
     *
     * @param source {@link OntCE} a source class expression
     * @param target {@link OntCE} a target class expression
     * @return {@link MapContext} existing or fresh context.
     */
    MapContext createContext(OntCE source, OntCE target);

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
     * context target class expressions should be linked together using an object property (i.e. through domain/range relation).
     * Bound contexts will produce object property assertions between individuals on inference.
     * If contexts target classes are not linked to each other or contexts sources classes are different, an exception are expected.
     *
     * @param left  {@link MapContext}
     * @param right {@link MapContext}
     * @return this model
     * @throws MapJenaException if something goes wrong
     * @see MapContext#attachContext(MapContext, OntOPE)
     */
    MapModel bindContexts(MapContext left, MapContext right) throws MapJenaException;

    /**
     * Answers the OWL2 model which wraps the same mapping graph.
     *
     * @return {@link OntGraphModel OWL2 jena model}
     */
    OntGraphModel asGraphModel();

    /**
     * Lists all rules ({@link MapResource Mapping Resources}).
     * A MapResource can be either {@link MapContext} or {@link PropertyBridge}.
     * Incomplete contexts are not included to the result.
     *
     * @return Stream of {@link MapResource}s.
     * @see MapContext#isValid()
     */
    default Stream<MapResource> rules() {
        return contexts().flatMap(c -> c.isValid() ? Stream.concat(Stream.of(c), c.properties()) : c.properties());
    }

    /**
     * Creates a ready to use context, i.e. a class expression binding with an target rule expression.
     *
     * @param source             {@link OntCE} a source class expression
     * @param target             {@link OntCE} a target class expression
     * @param targetFunctionCall {@link MapFunction.Call}
     * @return {@link MapContext} a new context instance.
     * @throws MapJenaException if something goes wrong (e.g. not target function specified)
     */
    default MapContext createContext(OntCE source, OntCE target, MapFunction.Call targetFunctionCall) throws MapJenaException {
        return createContext(source, target).addClassBridge(targetFunctionCall);
    }

    /**
     * TODO: description!
     *
     * @param source {@link Graph}
     * @param target {@link Graph}
     */
    default void runInference(Graph source, Graph target) {
        getManager().getInferenceEngine().run(this, source, target);
    }
}

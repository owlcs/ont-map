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

import org.apache.jena.graph.Factory;
import org.apache.jena.graph.Graph;
import org.semanticweb.owlapi.model.IRI;
import ru.avicomp.map.spin.MapManagerImpl;
import ru.avicomp.map.spin.OWLMapManagerImpl;
import ru.avicomp.map.spin.SpinModels;
import ru.avicomp.map.spin.system.SystemLibraries;
import ru.avicomp.ontapi.*;
import ru.avicomp.ontapi.jena.utils.Graphs;
import ru.avicomp.ontapi.transforms.GraphTransformers;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.stream.Collectors;

/**
 * The main (static) access point to {@link MapManager} instances.
 * <p>
 * Created by @szuev on 07.04.2018.
 */
@SuppressWarnings("WeakerAccess")
public class Managers {

    static { // force initialization
        SystemLibraries.init();
    }

    /**
     * The {@link OntologyManager.DocumentSourceMapping} to handle resources from {@code file://resources/etc}.
     * Must be attached to any {@link OntologyManager} if you plan to work with library graphs.
     */
    public static final OntologyManager.DocumentSourceMapping MAP_RESOURCES_MAPPING = id -> id.getOntologyIRI()
            .map(IRI::getIRIString)
            .map(uri -> SystemLibraries.graphs().get(uri))
            .map(g -> new OntGraphDocumentSource() {
                @Override
                public Graph getGraph() {
                    return g;
                }

                @Override
                public boolean withTransforms() {
                    return false;
                }
            }).orElse(null);
    /**
     * The filter to skip transformations on system library graphs (from {@code file://resources/etc})
     * and on any incoming spin-rdf mapping file.
     */
    public static final GraphTransformers.Filter TRANSFORM_FILTER = g -> {
        if (SystemLibraries.graphs().containsKey(Graphs.getURI(g))) return false;
        return Graphs.getImports(g).stream().noneMatch(SpinModels::isTopSpinURI);
    };

    /**
     * Creates a non-concurrent spin-based {@link MapManager}.
     *
     * @return {@link MapManager}, not {@code null}
     */
    public static MapManager createMapManager() {
        return createMapManager(Factory.createGraphMem());
    }

    /**
     * Creates a non-concurrent spin-based {@link MapManager} with given graph as primary.
     * Please note that the current {@link MapManager} implementation has a restriction:
     * any changes in the specified graph that occur after calling this method,
     * will not affect the returned manager content -
     * the state of functions register is initialized while manager's construction.
     *
     * @param graph {@link Graph}, not {@code null}
     * @return {@link MapManager}, not {@code null}
     */
    public static MapManager createMapManager(Graph graph) {
        return new MapManagerImpl(graph);
    }

    /**
     * Creates a default SPIN-based {@link OWLMapManager OWL Mapping Manager},
     * that is simultaneously an instance of interfaces {@link OntologyManager} and {@link MapManager}.
     * The returning manager is not thread-safe.
     *
     * @return {@link OWLMapManager}, not {@code null}
     */
    public static OWLMapManager createOWLMapManager() {
        return createOWLMapManager(Factory.createGraphMem());
    }

    /**
     * Creates an {@link OWLMapManager} instance for the given graph.
     *
     * @param graph {@link Graph}, to store user-defined stuff, not {@code null}
     * @return {@link OWLMapManager}, not {@code null}
     * @see #createMapManager(Graph) see the note about the graph-parameter
     */
    public static OWLMapManager createOWLMapManager(Graph graph) {
        return createOWLMapManager(graph, NoOpReadWriteLock.NO_OP_RW_LOCK);
    }

    /**
     * Creates a concurrent version of {@link OWLMapManager}.
     *
     * @param primary {@link Graph} to store user-defined stuff, not {@code null}
     * @param lock    {@link ReadWriteLock}, not {@code null}
     * @return {@link OWLMapManager}
     * @see OntManagers.ONTAPIProfile#create(boolean)
     * @see #createMapManager(Graph) see the note about the graph-parameter
     */
    public static OWLMapManager createOWLMapManager(Graph primary, ReadWriteLock lock) {
        OntologyFactory.Builder builder = OntManagers.DEFAULT_PROFILE.createOntologyBuilder();
        OntologyFactory ontologyFactory = OntManagers.DEFAULT_PROFILE.createOntologyFactory(builder);
        DataFactory dataFactory = OntManagers.DEFAULT_PROFILE.dataFactory();

        OWLMapManagerImpl res = new OWLMapManagerImpl(primary, dataFactory, ontologyFactory, lock);
        res.getOntologyStorers().set(OWLLangRegistry.storerFactories().collect(Collectors.toSet()));
        res.getOntologyParsers().set(OWLLangRegistry.parserFactories().collect(Collectors.toSet()));
        res.getDocumentSourceMappers().set(MAP_RESOURCES_MAPPING);
        GraphTransformers.Store store = res.getOntologyConfigurator().getGraphTransformers();
        res.getOntologyConfigurator().setGraphTransformers(store.addFilter(TRANSFORM_FILTER));
        return res;
    }

}

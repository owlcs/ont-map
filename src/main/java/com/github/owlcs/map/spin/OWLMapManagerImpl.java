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

import com.github.owlcs.map.*;
import com.github.owlcs.map.spin.system.SystemLibraries;
import com.github.owlcs.ontapi.*;
import com.github.owlcs.ontapi.jena.impl.OntGraphModelImpl;
import com.github.owlcs.ontapi.jena.model.OntModel;
import com.github.owlcs.ontapi.jena.utils.Graphs;
import org.apache.jena.graph.Graph;
import org.apache.jena.shared.PrefixMapping;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyID;

import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * A SPIN-based implementation of {@link OWLMapManager}
 * which is a {@link OntologyManager} and {@link MapManager} simultaneously.
 * <p>
 * Created by @ssz on 21.06.2018.
 */
@SuppressWarnings({"NullableProblems", "WeakerAccess"})
public class OWLMapManagerImpl extends OntologyManagerImpl implements OWLMapManager, HasConfig {
    // todo: handle in serialization (see issue #10):
    private final transient MapManagerImpl manager;
    private transient Set<String> libs;

    public OWLMapManagerImpl(Graph primary,
                             DataFactory dataFactory,
                             OntologyFactory ontologyFactory,
                             ReadWriteLock lock) {
        this(primary, dataFactory, ontologyFactory, MapConfigImpl.INSTANCE, lock);
    }

    /**
     * Creates a ready-to use {@link OWLMapManager} instance.
     *
     * @param primary         {@link Graph} to store user-defined functions and other mapping-manager-specific stuff,
     *                        cannot be {@code null}
     * @param dataFactory     {@link DataFactory} a facility to construct OWL-API objects, cannot be {@code null}
     * @param ontologyFactory {@link OntologyFactory} a facility to create and load {@link Ontology} instances,
     *                        cannot be {@code null}
     * @param config          {@link MapConfigImpl} a mapping manager config settings, cannot be {@code null}
     * @param lock            {@link ReadWriteLock} to ensure thread-safety,
     *                        possible {@code null} for one-thread environment
     */
    public OWLMapManagerImpl(Graph primary,
                             DataFactory dataFactory,
                             OntologyFactory ontologyFactory,
                             MapConfigImpl config,
                             ReadWriteLock lock) {
        super(dataFactory, ontologyFactory, lock);
        this.manager = createMapManager(
                primary
                , () -> {
                    throw new MapJenaException.IllegalState("Direct model creation is not allowed here.");
                }
                , getLock()
                , config
                , m -> ontology(m.getBaseGraph()).map(Ontology::asGraphModel).orElse(m));
    }

    /**
     * Creates a concurrent version of {@link MapManagerImpl}.
     * Since only {@link MapManager#asMapModel(OntModel)} can change the state of manager
     * (if specified model has custom functions inside),
     * and only {@link MapManager#functions()} returns that state (a list of functions),
     * this two methods must be synchronized.
     *
     * @param primary {@link Graph} to store user-defined functions and other manager-specific stuff
     * @param factory to produce fresh {@link Graph}s instances
     * @param lock    {@link ReadWriteLock}, not {@code null}
     * @return {@link MapManager} instance with lock inside
     */
    public static MapManagerImpl createMapManager(Graph primary, Supplier<Graph> factory, ReadWriteLock lock) {
        return createMapManager(primary, factory, lock, MapConfigImpl.INSTANCE, UnaryOperator.identity());
    }

    protected static MapManagerImpl createMapManager(Graph library,
                                                     Supplier<Graph> factory,
                                                     ReadWriteLock lock,
                                                     MapConfigImpl config,
                                                     UnaryOperator<OntModel> map) {
        boolean noOp = !NoOpReadWriteLock.isConcurrent(lock);
        library = Graphs.asNonConcurrent(Objects.requireNonNull(library, "Null primary graph"));
        if (!noOp) {
            library = Graphs.asConcurrent(library, lock);
        }
        return new MapManagerImpl(library
                , factory
                , noOp ? new HashMap<>() : new ConcurrentHashMap<>()
                , config) {
            @Override
            protected boolean filter(FunctionImpl f) {
                lock.readLock().lock();
                try {
                    return super.filter(f);
                } finally {
                    lock.readLock().unlock();
                }
            }

            @Override
            public MapModelImpl asMapModel(OntModel m) throws MapJenaException {
                lock.writeLock().lock();
                try {
                    return super.asMapModel(m);
                } finally {
                    lock.writeLock().unlock();
                }
            }

            @Override
            public Stream<OntModel> relatedModels(OntModel model) {
                return super.relatedModels(model).map(map);
            }
        };
    }

    /**
     * Post-processes for the ontology.
     * This method is called after each creation or loading.
     *
     * @param ont {@link Ontology}
     */
    @Override
    public void ontologyCreated(OWLOntology ont) {
        lock.writeLock().lock();
        try {
            OntModel g = ((Ontology) ont).asGraphModel();
            if (manager.isMapModel(g)) {
                // register custom functions
                manager.asMapModel(g);
            }
            content.add(new OntInfo((Ontology) ont));
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Stream<OWLOntology> ontologies() {
        lock.readLock().lock();
        try {
            return content.values().filter(x -> !isLibraryModel(x.getOntologyID())).map(OntInfo::get);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Stream<MapModel> mappings() {
        lock.readLock().lock();
        try {
            return content.values()
                    .map(OntInfo::get)
                    .map(Ontology::asGraphModel)
                    .filter(this::isMapModel)
                    .map(manager::newMapModelImpl);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public PrefixMapping prefixes() {
        return manager.prefixes();
    }

    @Override
    public Stream<MapFunction> functions() {
        return manager.functions();
    }

    @Override
    public Graph getGraph() {
        lock.readLock().lock();
        try {
            return manager.getGraph();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void addGraph(Graph g) {
        lock.writeLock().lock();
        try {
            manager.addGraph(g);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public MapModel createMapModel() {
        lock.writeLock().lock();
        try {
            OntGraphModelImpl m = (OntGraphModelImpl) createGraphModel(null);
            manager.setupMapModel(m);
            return manager.newMapModelImpl(m);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public MapModel asMapModel(OntModel m) throws MapJenaException {
        lock.readLock().lock();
        try {
            if (contains(m)) {
                // already added -> no need to re-register the same functions (was handled by #ontologyCreated),
                // therefore the state of the manager is not changed -> no need in write lock
                return manager.newMapModelImpl(m);
            }
        } finally {
            lock.readLock().unlock();
        }
        lock.writeLock().lock();
        try {
            // external ontology -> just registers functions and return the map-model without any lock
            return manager.asMapModel(m);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Answers iff the model is present inside manager.
     * The check is performed by comparing the base graphs not ontology ids.
     *
     * @param m {@link OntModel} to test
     * @return boolean
     * @see OntologyManager#contains(OWLOntology)
     */
    public boolean contains(OntModel m) {
        return ontology(m.getGraph()).isPresent();
    }

    @Override
    public boolean isMapModel(OntModel m) {
        lock.readLock().lock();
        try {
            return manager.isMapModel(m);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Answers {@code true} if the given ontology id is reserved as system.
     *
     * @param id {@link OWLOntologyID} to test
     * @return boolean
     */
    public boolean isLibraryModel(OWLOntologyID id) {
        lock.readLock().lock();
        try {
            if (libs == null)
                libs = SystemLibraries.graphs().keySet();
            return id.getOntologyIRI()
                    .map(IRI::getIRIString)
                    .filter(libs::contains)
                    .isPresent();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public ClassPropertyMap getClassProperties(OntModel model) {
        lock.readLock().lock();
        try {
            return manager.getClassProperties(model);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public InferenceEngine getInferenceEngine(MapModel mapping) {
        return new OWLInferenceEngineImpl(mapping);
    }

    @Override
    public MapConfigImpl getMappingConfiguration() {
        return manager.getMappingConfiguration();
    }

    public class OWLInferenceEngineImpl implements InferenceEngine {
        protected final InferenceEngine delegate;
        protected final Lock mappingLock;

        public OWLInferenceEngineImpl(MapModel mapping) {
            lock.readLock().lock();
            try {
                // Use a write lock in case the given mapping belongs to the manager,
                // since inference engine may add temporary triples into the mapping main graph
                this.mappingLock = ontology(Objects.requireNonNull(mapping, "Null mapping").asGraphModel().getGraph())
                        .isPresent() ? lock.writeLock() : NoOpReadWriteLock.NO_OP_LOCK;
                this.delegate = manager.getInferenceEngine(mapping);
            } finally {
                lock.readLock().unlock();
            }
        }

        /**
         * Runs an inference process on the given mapping, source and target.
         * Uses manager's write lock, if the target graph or the mapping belongs to the manager;
         * or manager's read lock, if the the source graph belongs to the manager,
         * otherwise uses no-op lock instance.
         *
         * @param source a graph with data to map.
         * @param target a graph to write mapping results.
         */
        @Override
        public void run(Graph source, Graph target) {
            Lock inferLock;
            lock.readLock().lock();
            try {
                Optional<Ontology> dst = ontology(target);
                // clears the cache (just in case): new axioms will be added to that ontology
                dst.ifPresent(Ontology::clearCache);
                if (mappingLock != NoOpReadWriteLock.NO_OP_LOCK) { // then write lock (mapping is in the manager)
                    inferLock = mappingLock;
                } else {
                    if (dst.isPresent()) { // the target belongs to the manager
                        inferLock = lock.writeLock();
                    } else if (ontology(source).isPresent()) {  // the source belongs to manager
                        inferLock = lock.readLock();
                    } else { // all - the mapping, the source and the target - are external to the manager
                        inferLock = NoOpReadWriteLock.NO_OP_LOCK;
                    }
                }
            } finally {
                lock.readLock().unlock();
            }
            inferLock.lock();
            try {
                delegate.run(source, target);
            } finally {
                inferLock.unlock();
            }
        }

    }
}

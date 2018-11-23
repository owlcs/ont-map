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

import org.apache.jena.graph.Factory;
import org.apache.jena.graph.Graph;
import org.apache.jena.shared.PrefixMapping;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyID;
import ru.avicomp.map.*;
import ru.avicomp.ontapi.*;
import ru.avicomp.ontapi.jena.RWLockedGraph;
import ru.avicomp.ontapi.jena.impl.OntGraphModelImpl;
import ru.avicomp.ontapi.jena.model.OntGraphModel;

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
 * A SPIN-based implementation of {@link OWLMapManager} which is a {@link OntologyManager} and {@link MapManager} simultaneously.
 * Created by @ssz on 21.06.2018.
 */
@SuppressWarnings({"NullableProblems", "WeakerAccess"})
public class OWLMapManagerImpl extends OntologyManagerImpl implements OWLMapManager {
    private final MapManagerImpl manager;

    public OWLMapManagerImpl(DataFactory dataFactory, OntologyFactory ontologyFactory, ReadWriteLock lock) {
        super(dataFactory, ontologyFactory, lock);
        this.manager = createMapManager(
                () -> {
                    throw new MapJenaException.IllegalState("Direct model creation is not allowed");
                }
                , lock
                , m -> ontology(m.getBaseGraph()).map(OntologyModel::asGraphModel).orElse(m));
    }

    /**
     * Creates a concurrent version of {@link MapManagerImpl}.
     * Since only {@link MapManager#asMapModel(OntGraphModel)} can change the state of manager
     * (if specified model has custom functions inside),
     * and only {@link MapManager#functions()} returns that state (a list of functions),
     * this two methods must be synchronized.
     *
     * @param factory to produce fresh {@link Graph}s
     * @param lock    {@link ReadWriteLock}, not null
     * @return {@link MapManager} instance with lock inside
     */
    public static MapManagerImpl createMapManager(Supplier<Graph> factory, ReadWriteLock lock) {
        return createMapManager(factory, lock, UnaryOperator.identity());
    }

    private static MapManagerImpl createMapManager(Supplier<Graph> factory,
                                                   ReadWriteLock lock,
                                                   UnaryOperator<OntGraphModel> map) {
        boolean noOp = Objects.requireNonNull(lock, "Null lock").getClass()
                .equals(NoOpReadWriteLock.NO_OP_RW_LOCK.getClass());
        Graph library = Factory.createGraphMem();
        if (!noOp)
            library = new RWLockedGraph(library, lock);
        return new MapManagerImpl(library, factory, noOp ? new HashMap<>() : new ConcurrentHashMap<>(), MapConfigImpl.INSTANCE) {
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
            public MapModelImpl asMapModel(OntGraphModel m) throws MapJenaException {
                lock.writeLock().lock();
                try {
                    return super.asMapModel(m);
                } finally {
                    lock.writeLock().unlock();
                }
            }

            @Override
            public Stream<OntGraphModel> listRelatedModels(OntGraphModel model) {
                return super.listRelatedModels(model).map(map);
            }
        };
    }

    /**
     * Post-processes for the ontology.
     * This method is called after each creation or loading.
     *
     * @param ont {@link OntologyModel}
     */
    @Override
    public void ontologyCreated(OWLOntology ont) {
        lock.writeLock().lock();
        try {
            OntGraphModel g = ((OntologyModel) ont).asGraphModel();
            if (manager.isMapModel(g)) {
                // register custom functions
                manager.asMapModel(g);
            }
            content.add((OntologyModel) ont);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Stream<OWLOntology> ontologies() {
        lock.readLock().lock();
        try {
            return content.values().filter(x -> !isLibraryModel(x.id())).map(OntInfo::get);
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
                    .map(OntologyModel::asGraphModel)
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
    public MapModel asMapModel(OntGraphModel m) throws MapJenaException {
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
     * @param m {@link OntGraphModel} to test
     * @return boolean
     * @see OntologyManager#contains(OWLOntology)
     */
    public boolean contains(OntGraphModel m) {
        return ontology(m.getGraph()).isPresent();
    }

    @Override
    public boolean isMapModel(OntGraphModel m) {
        lock.readLock().lock();
        try {
            return manager.isMapModel(m);
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean isLibraryModel(OWLOntologyID id) {
        lock.readLock().lock();
        try {
            Set<String> libs = SystemModels.graphs().keySet();
            return id.getOntologyIRI()
                    .map(IRI::getIRIString)
                    .filter(libs::contains)
                    .isPresent();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public ClassPropertyMap getClassProperties(OntGraphModel model) {
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

    public class OWLInferenceEngineImpl implements InferenceEngine {
        protected final InferenceEngine delegate;
        protected final Lock mappingLock;

        public OWLInferenceEngineImpl(MapModel mapping) {
            lock.readLock().lock();
            try {
                // Use a write lock in case the given mapping belongs to the manager,
                // since inference engine may add temporary triples into the mapping main graph
                this.mappingLock = ontology(Objects.requireNonNull(mapping, "Null mapping").asGraphModel().getGraph()).isPresent() ?
                        lock.writeLock() : NoOpReadWriteLock.NO_OP_LOCK;
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
         * @param source  a graph with data to map.
         * @param target  a graph to write mapping results.
         */
        @Override
        public void run(Graph source, Graph target) {
            Lock inferLock;
            lock.readLock().lock();
            try {
                Optional<OntologyModel> dst = ontology(target);
                // clears the cache (just in case): new axioms will be added to that ontology
                dst.ifPresent(OntologyModel::clearCache);
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

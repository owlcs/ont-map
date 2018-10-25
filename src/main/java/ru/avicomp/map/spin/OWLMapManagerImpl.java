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
                    throw new IllegalStateException("Direct model creation is not allowed");
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

    private static MapManagerImpl createMapManager(Supplier<Graph> factory, ReadWriteLock lock, UnaryOperator<OntGraphModel> map) {
        boolean noOp = Objects.equals(Objects.requireNonNull(lock, "Null lock").getClass(), NoOpReadWriteLock.NO_OP_RW_LOCK.getClass());
        Graph library = Factory.createGraphMem();
        if (!noOp)
            library = new RWLockedGraph(library, lock);
        return new MapManagerImpl(library, factory, noOp ? new HashMap<>() : new ConcurrentHashMap<>()) {
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
    public InferenceEngine getInferenceEngine() {
        return new OWLInferenceEngineImpl(manager.getInferenceEngine());
    }

    public class OWLInferenceEngineImpl implements InferenceEngine {
        private final InferenceEngine delegate;

        public OWLInferenceEngineImpl(InferenceEngine delegate) {
            this.delegate = delegate;
        }

        /**
         * Runs inference process on the given mapping, source and target.
         * Uses managers write lock, if the target graph belongs to the manager,
         * otherwise, if the mapping or the source graph belong to the manager,
         * uses read lock (manager does not change its state).
         *
         * @param mapping a mapping instructions in form of {@link MapModel}
         * @param source  a graph with data to map.
         * @param target  a graph to write mapping results.
         */
        @Override
        public void run(MapModel mapping, Graph source, Graph target) {
            Lock lock = NoOpReadWriteLock.NO_OP_LOCK;
            ReadWriteLock rwLock = getLock();
            rwLock.readLock().lock();
            try {
                if (isConcurrent()) {
                    Optional<OntologyModel> dst = ontology(target);
                    if (dst.isPresent()) {
                        dst.get().clearCache(); // just in case
                        lock = rwLock.writeLock();
                    } else if (ontology(mapping.asGraphModel().getGraph()).isPresent() || ontology(source).isPresent()) {
                        lock = rwLock.readLock();
                    }
                }
            } finally {
                rwLock.readLock().unlock();
            }
            lock.lock();
            try {
                delegate.run(mapping, source, target);
            } finally {
                lock.unlock();
            }
        }
    }
}

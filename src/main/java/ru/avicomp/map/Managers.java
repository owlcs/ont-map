package ru.avicomp.map;

import org.apache.jena.graph.Factory;
import org.apache.jena.graph.Graph;
import org.apache.jena.shared.PrefixMapping;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDocumentFormat;
import org.semanticweb.owlapi.model.OWLOntology;
import ru.avicomp.map.spin.MapManagerImpl;
import ru.avicomp.map.spin.MapModelImpl;
import ru.avicomp.map.spin.SystemModels;
import ru.avicomp.ontapi.*;
import ru.avicomp.ontapi.jena.ConcurrentGraph;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.impl.OntGraphModelImpl;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.utils.Graphs;
import ru.avicomp.owlapi.NoOpReadWriteLock;

import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The main (static) access point to {@link MapManager} instances.
 * <p>
 * Created by @szuev on 07.04.2018.
 */
@SuppressWarnings("WeakerAccess")
public class Managers {

    private static final ReadWriteLock NO_OP = new NoOpReadWriteLock();

    /**
     * The {@link OntologyManager.DocumentSourceMapping} to handle resources from file://resources/etc.
     * Must be attached to any {@link OntologyManager} if you plan to work with library graphs.
     */
    public static final OntologyManager.DocumentSourceMapping MAP_RESOURCES_MAPPING = id -> id.getOntologyIRI()
            .map(IRI::getIRIString)
            .map(uri -> SystemModels.graphs().get(uri))
            .map(g -> new OntGraphDocumentSource() {
                @Override
                public Graph getGraph() {
                    return g;
                }
            }).orElse(null);

    /**
     * Creates a spin-based {@link MapManager}.
     * The result instance is not tread-safe.
     *
     * @return {@link MapManager}
     */
    public static MapManager createMapManager() {
        return new MapManagerImpl();
    }

    /**
     * Creates a concurrent version of {@link MapManagerImpl}.
     * Since only {@link MapManager#asMapModel(OntGraphModel)} can change the state of manager
     * (if specified model has custom functions inside),
     * and only {@link MapManager#functions()} returns that state (a list of functions),
     * this two methods must be synchronized.
     *
     * @param lock {@link ReadWriteLock}
     * @return {@link MapManager} instance with lock inside
     */
    public static MapManager createMapManager(ReadWriteLock lock) {
        boolean noOp = Objects.equals(Objects.requireNonNull(lock, "Null lock").getClass(), NO_OP.getClass());
        Supplier<Graph> factory = Factory::createGraphMem;
        Graph library = noOp ? factory.get() : new ConcurrentGraph(factory.get(), lock);
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
        };
    }

    /**
     * Creates an {@link OWLMapManager} instance,
     * which implements {@link OntologyManager} and {@link MapManager} at the same time.
     * The result manager is not thread-safe.
     *
     * @return {@link OWLMapManager}
     */
    public static OWLMapManager createOWLMapManager() {
        return createOWLMapManager(NO_OP);
    }

    /**
     * Creates a concurrent version of {@link OWLMapManager}.
     *
     * @param lock {@link ReadWriteLock}
     * @return {@link OWLMapManager}
     * @see OntManagers.ONTManagerProfile#create(boolean)
     */
    public static OWLMapManager createOWLMapManager(ReadWriteLock lock) {
        MapManager map = createMapManager(lock);
        OWLDataFactory df = OntManagers.DEFAULT_PROFILE.dataFactory();
        OWLMapManagerImpl res = new OWLMapManagerImpl(map, df, lock);
        res.setOntologyStorers(OWLLangRegistry.storerFactories().collect(Collectors.toSet()));
        res.setOntologyParsers(OWLLangRegistry.parserFactories().collect(Collectors.toSet()));
        res.addDocumentSourceMapper(MAP_RESOURCES_MAPPING);
        // TODO: this is a temporary solution:
        // TODO: in ONT-API need to provide a general mechanism to disable transformations for a whole graph family.
        res.getOntologyConfigurator().setPerformTransformation(false);
        return res;
    }

    /**
     * The implementation of {@link OWLMapManager} which is a {@link OntologyManager} and {@link MapManager} simultaneously.
     * Created by @szuev on 21.06.2018.
     */
    @SuppressWarnings({"NullableProblems", "WeakerAccess"})
    public static class OWLMapManagerImpl extends OntologyManagerImpl implements OWLMapManager {
        private final MapManager delegate;

        public OWLMapManagerImpl(MapManager manager, OWLDataFactory dataFactory, ReadWriteLock readWriteLock) {
            super(dataFactory, readWriteLock);
            this.delegate = manager;
        }

        @Override
        public void ontologyCreated(OWLOntology ont) {
            getLock().writeLock().lock();
            try {
                OntGraphModel g = ((OntologyModel) ont).asGraphModel();
                if (delegate.isMapModel(g)) {
                    // register custom functions
                    delegate.asMapModel(g);
                }
                content.add((OntologyModel) ont);
            } finally {
                getLock().writeLock().unlock();
            }
        }

        @Override
        public Stream<OWLOntology> ontologies() {
            // TODO: (ONT-API) make OntologyCollection#values -> public
            return super.ontologies().filter(x -> !isLibraryModel(x));
        }

        @Override
        public PrefixMapping prefixes() {
            return delegate.prefixes();
        }

        @Override
        public Stream<MapFunction> functions() {
            return delegate.functions();
        }

        @Override
        public MapModel createMapModel() {
            getLock().writeLock().lock();
            try {
                MapModel res = delegate.createMapModel();
                OntGraphModel m = res.asGraphModel();
                // `newOntologyModel` will wrap graph as ConcurrentGraph in case it is a concurrent manager
                OntologyModel o = newOntologyModel(m.getGraph(), getOntologyLoaderConfiguration());
                OWLDocumentFormat format = OntFormat.TURTLE.createOwlFormat();
                m.getNsPrefixMap().forEach(format.asPrefixOWLDocumentFormat()::setPrefix);
                OntologyManagerImpl.setDefaultPrefix(format.asPrefixOWLDocumentFormat(), o);
                super.ontologyCreated(o);
                super.setOntologyFormat(o, format);
                return res;
            } finally {
                getLock().writeLock().unlock();
            }
        }

        @Override
        public MapModel asMapModel(OntGraphModel m) throws MapJenaException {
            getLock().readLock().lock();
            try {
                if (delegate instanceof MapManagerImpl && contains(m)) {
                    // already added -> no need to re-register the same functions,
                    // therefore the state of manager is not changed -> no need in write lock
                    return new MapModelImpl((UnionGraph) m.getGraph(),
                            ((OntGraphModelImpl) m).getPersonality(),
                            (MapManagerImpl) delegate);
                }
            } finally {
                getLock().readLock().unlock();
            }
            getLock().writeLock().lock();
            try {
                return delegate.asMapModel(m);
            } finally {
                getLock().writeLock().unlock();
            }
        }

        /**
         * Answers iff model is present inside manager.
         * The check is performed by comparing the base graphs not ontology ids.
         *
         * @param m {@link OntGraphModel} to test
         * @return boolean
         * @see OntologyManager#contains(OWLOntology)
         */
        public boolean contains(OntGraphModel m) {
            return findOntology(m.getGraph()).isPresent();
        }

        /**
         * Finds ontology by the given graph.
         *
         * @param graph {@link Graph}
         * @return Optional around {@link OntologyModel}
         */
        public Optional<OntologyModel> findOntology(Graph graph) {
            return this.ontologies()
                    .map(OntologyModel.class::cast)
                    .filter(m -> sameBase(graph, m.asGraphModel().getGraph()))
                    .findFirst();
        }

        private static boolean sameBase(Graph left, Graph right) {
            return Objects.equals(getBaseGraph(left), getBaseGraph(right));
        }

        /**
         * Gets a base graph from any composite graph or concurrent wrapper.
         * @param graph {@link Graph} to unclothe
         * @return the base {@link Graph} or {@code null} if argument is also {@code null}
         * @see Graphs#getBase(Graph)
         * @see ConcurrentGraph
         */
        public static Graph getBaseGraph(Graph graph) {
            if (graph == null) return null;
            Graph res = Graphs.getBase(graph);
            return res instanceof ConcurrentGraph ? ((ConcurrentGraph) res).get() : res;
        }

        @Override
        public boolean isMapModel(OntGraphModel m) {
            getLock().readLock().lock();
            try {
                return delegate.isMapModel(m);
            } finally {
                getLock().readLock().unlock();
            }
        }

        public boolean isLibraryModel(OWLOntology o) {
            getLock().readLock().lock();
            try {
                Set<String> libs = SystemModels.graphs().keySet();
                return o.getOntologyID()
                        .getOntologyIRI()
                        .map(IRI::getIRIString)
                        .filter(libs::contains)
                        .isPresent();
            } finally {
                getLock().readLock().unlock();
            }
        }

        @Override
        public ClassPropertyMap getClassProperties(OntGraphModel model) {
            getLock().readLock().lock();
            try {
                return delegate.getClassProperties(model);
            } finally {
                getLock().readLock().unlock();
            }
        }

        @Override
        public InferenceEngine getInferenceEngine() {
            return new OWLInferenceEngineImpl(delegate.getInferenceEngine());
        }

        public class OWLInferenceEngineImpl implements InferenceEngine {
            private final InferenceEngine delegate;

            public OWLInferenceEngineImpl(InferenceEngine delegate) {
                this.delegate = delegate;
            }

            @Override
            public void run(MapModel mapping, Graph source, Graph target) {
                ReadWriteLock lock;
                Optional<OntologyModel> dst;
                getLock().readLock().lock();
                try {
                    dst = findOntology(target);
                    // if target graph does not belong to the manager -> there is no need in lock
                    lock = dst.isPresent() ? getLock() : NO_OP;
                } finally {
                    getLock().readLock().unlock();
                }
                lock.writeLock().lock();
                try {
                    dst.ifPresent(OntologyModel::clearCache);
                    delegate.run(mapping, source, target);
                } finally {
                    lock.writeLock().unlock();
                }
            }
        }
    }
}

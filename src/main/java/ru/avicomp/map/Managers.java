package ru.avicomp.map;

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

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The main (static) access point to {@link MapManager} instances.
 * <p>
 * Created by @szuev on 07.04.2018.
 */
@SuppressWarnings("WeakerAccess")
public class Managers {
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
     * TODO: description
     *
     * @return
     */
    public static MapManager createMapManager() {
        return new MapManagerImpl();
    }

    /**
     * TODO: description
     *
     * @return
     */
    public static OWLMapManager createOWLMapManager() {
        return createOWLMapManager(new NoOpReadWriteLock());
    }

    /**
     * TODO: description
     *
     * @param lock
     * @return
     * @see OntManagers.ONTManagerProfile#create(boolean)
     */
    public static OWLMapManager createOWLMapManager(ReadWriteLock lock) {
        MapManager map = createMapManager();
        OWLDataFactory df = OntManagers.DEFAULT_PROFILE.dataFactory();
        OWLMapManagerImpl res = new OWLMapManagerImpl(map, df, lock);
        res.setOntologyStorers(OWLLangRegistry.storerFactories().collect(Collectors.toSet()));
        res.setOntologyParsers(OWLLangRegistry.parserFactories().collect(Collectors.toSet()));
        res.addDocumentSourceMapper(MAP_RESOURCES_MAPPING);
        // todo:
        res.getOntologyConfigurator().setPerformTransformation(false);
        return res;
    }

    /**
     * TODO: description and explanation
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
                if (delegate.isMapModel(g)) { // register custom functions
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
                OntGraphModel m = res.asOntModel();
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
                if (contains(m) && delegate instanceof MapManagerImpl) {
                    // already added -> no need to re-register the same functions
                    return new MapModelImpl((UnionGraph) m.getGraph(), ((OntGraphModelImpl) m).getPersonality(), (MapManagerImpl) delegate);
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

        public boolean contains(OntGraphModel m) {
            return findOntology(m.getGraph()).isPresent();
        }

        public Optional<OntologyModel> findOntology(Graph graph) {
            return this.ontologies()
                    .map(OntologyModel.class::cast)
                    .filter(m -> sameBase(graph, m.asGraphModel().getGraph()))
                    .findFirst();
        }

        public static boolean sameBase(Graph left, Graph right) {
            return Objects.equals(getBaseGraph(left), getBaseGraph(right));
        }

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
                Optional<OntologyModel> dst = findOntology(target);
                ReadWriteLock lock = dst.isPresent() ? getLock() : new NoOpReadWriteLock();
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

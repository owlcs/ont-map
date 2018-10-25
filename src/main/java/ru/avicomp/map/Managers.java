package ru.avicomp.map;

import org.apache.jena.graph.Graph;
import org.semanticweb.owlapi.model.IRI;
import ru.avicomp.map.spin.MapManagerImpl;
import ru.avicomp.map.spin.OWLMapManagerImpl;
import ru.avicomp.map.spin.SystemModels;
import ru.avicomp.ontapi.*;
import ru.avicomp.ontapi.jena.OntModelFactory;
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
        OntModelFactory.init();
    }

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

                @Override
                public boolean withTransforms() {
                    return false;
                }
            }).orElse(null);
    /**
     * The filter to skip transformations on system library graphs (from file://resources/etc)
     */
    public static final GraphTransformers.Filter TRANSFORM_FILTER = g -> !SystemModels.graphs().containsKey(Graphs.getURI(g));

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
     * Creates a SPIN-based {@link OWLMapManager} instance,
     * which implements {@link OntologyManager} and {@link MapManager} at the same time.
     * The result manager is not thread-safe.
     *
     * @return {@link OWLMapManager}
     */
    public static OWLMapManager createOWLMapManager() {
        return createOWLMapManager(NoOpReadWriteLock.NO_OP_RW_LOCK);
    }

    /**
     * Creates a concurrent version of {@link OWLMapManager}.
     *
     * @param lock {@link ReadWriteLock}
     * @return {@link OWLMapManager}
     * @see OntManagers.ONTAPIProfile#create(boolean)
     */
    public static OWLMapManager createOWLMapManager(ReadWriteLock lock) {
        OntologyFactory.Builder builder = new OntologyBuilderImpl();
        OntologyFactory.Loader loader = new OntologyLoaderImpl(builder, new OWLLoaderImpl(builder));
        OntologyFactory ontologyFactory = new OntologyFactoryImpl(builder, loader);
        DataFactory dataFactory = OntManagers.DEFAULT_PROFILE.dataFactory();
        OWLMapManagerImpl res = new OWLMapManagerImpl(dataFactory, ontologyFactory, lock);
        res.getOntologyStorers().set(OWLLangRegistry.storerFactories().collect(Collectors.toSet()));
        res.getOntologyParsers().set(OWLLangRegistry.parserFactories().collect(Collectors.toSet()));
        res.getDocumentSourceMappers().set(MAP_RESOURCES_MAPPING);
        GraphTransformers.Store store = res.getOntologyConfigurator().getGraphTransformers();
        res.getOntologyConfigurator().setGraphTransformers(store.addFilter(TRANSFORM_FILTER));
        return res;
    }

}

package ru.avicomp.map.spin;

import org.apache.jena.graph.Graph;
import org.apache.jena.mem.GraphMem;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.system.stream.LocationMapper;
import org.apache.jena.riot.system.stream.StreamManager;
import org.apache.jena.sparql.graph.UnmodifiableGraph;
import org.apache.jena.sys.JenaSubsystemLifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.map.utils.ReadOnlyGraph;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A Jena module and spin library loader.
 * <p>
 * Created by @szuev on 05.04.2018.
 */
public class SystemModels implements JenaSubsystemLifecycle {
    private static final Logger LOGGER = LoggerFactory.getLogger(SystemModels.class);

    /**
     * Returns all spin models from system resources.
     * Singleton.
     *
     * @return Unmodifiable Map with {@link UnmodifiableGraph unmodifiable graphs} as values and ontology iris as keys.
     */
    public static Map<String, Graph> graphs() {
        return Loader.GRAPHS;
    }

    public static Graph get(Resources resource) {
        return graphs().get(resource.getURI());
    }

    @Override
    public void start() {
        LOGGER.debug("START");
        // The following code is just in case.
        // E.g. to prevent possible internet trips from calls of "model.read(http:..)" or something like that in the depths of topbraid API.
        // A standard jena Locator (org.apache.jena.riot.system.stream.LocatorClassLoader) is used implicitly here.
        LocationMapper mapper = StreamManager.get().getLocationMapper();
        for (Resources r : Resources.values()) {
            // the resource name should not begin with '/' if java.lang.ClassLoader#getResourceAsStream is called
            mapper.addAltEntry(r.uri, r.path.replaceFirst("^/", ""));
        }
    }

    @Override
    public void stop() {
        LOGGER.debug("STOP");
    }

    private static class Loader {
        private final static Map<String, Graph> GRAPHS = load();

        private static Map<String, Graph> load() throws UncheckedIOException {
            Map<String, Graph> res = new HashMap<>();
            for (Resources f : Resources.values()) {
                Graph g = new GraphMem();
                try (InputStream in = SystemModels.class.getResourceAsStream(f.path)) {
                    RDFDataMgr.read(g, in, null, Lang.TURTLE);
                } catch (IOException e) {
                    throw new UncheckedIOException("Can't load " + f.path, e);
                }
                LOGGER.debug("Graph {} is loaded, size: {}", f.uri, g.size());
                res.put(f.uri, new ReadOnlyGraph(g));
            }
            return Collections.unmodifiableMap(res);
        }
    }

    public enum Resources {
        AVC("/etc/avc.spin.ttl", "http://avc.ru/spin"),
        AVC_LIB("/etc/avc.lib.ttl", "http://avc.ru/lib"),
        AVC_MATH("/etc/avc.math.ttl", "http://avc.ru/math"),
        AVC_FN("/etc/avc.fn.ttl", "http://avc.ru/fn"),
        SP("/etc/sp.ttl", "http://spinrdf.org/sp"),
        SPIN("/etc/spin.ttl", "http://spinrdf.org/spin"),
        SPL("/etc/spl.spin.ttl", "http://spinrdf.org/spl"),
        SPIF("/etc/spif.ttl", "http://spinrdf.org/spif"),
        SPINMAP("/etc/spinmap.spin.ttl", "http://spinrdf.org/spinmap"),
        SMF("/etc/functions-smf.ttl", "http://topbraid.org/functions-smf"),
        FN("/etc/functions-fn.ttl", "http://topbraid.org/functions-fn"),
        AFN("/etc/functions-afn.ttl", "http://topbraid.org/functions-afn"),
        SMF_BASE("/etc/sparqlmotionfunctions.ttl", "http://topbraid.org/sparqlmotionfunctions"),
        SPINMAPL("/etc/spinmapl.spin.ttl", "http://topbraid.org/spin/spinmapl");

        private final String path;
        private final String uri;

        Resources(String path, String uri) {
            this.path = path;
            this.uri = uri;
        }

        public String getURI() {
            return uri;
        }
    }
}

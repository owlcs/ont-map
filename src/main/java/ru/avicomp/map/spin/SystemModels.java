package ru.avicomp.map.spin;

import org.apache.jena.graph.Graph;
import org.apache.jena.mem.GraphMem;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.graph.UnmodifiableGraph;
import org.apache.jena.system.JenaSubsystemLifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    @Override
    public void start() {
        LOGGER.info("START");
        //noinspection ResultOfMethodCallIgnored
        graphs();
    }

    @Override
    public void stop() {
        LOGGER.info("STOP");
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
                LOGGER.info("Graph {} is loaded, size: {}", f.uri, g.size());
                res.put(f.uri, new UnmodifiableGraph(g));
            }
            return Collections.unmodifiableMap(res);
        }
    }

    public enum Resources {
        AVC("/spin/avc.spin.ttl", "http://avc.ru/spin"),
        SP("/spin/sp.ttl", "http://spinrdf.org/sp"),
        SPIN("/spin/spin.ttl", "http://spinrdf.org/spin"),
        SPL("/spin/spl.spin.ttl", "http://spinrdf.org/spl"),
        SPIF("/spin/spif.ttl", "http://spinrdf.org/spif"),
        SPINMAP("/spin/spinmap.spin.ttl", "http://spinrdf.org/spinmap"),
        SMF("/spin/functions-smf.ttl", "http://topbraid.org/functions-smf"),
        FN("/spin/functions-fn.ttl", "http://topbraid.org/functions-fn"),
        AFN("/spin/functions-afn.ttl", "http://topbraid.org/functions-afn"),
        SMF_BASE("/spin/sparqlmotionfunctions.ttl", "http://topbraid.org/sparqlmotionfunctions"),
        SPINMAPL("/spin/spinmapl.spin.ttl", "http://topbraid.org/spin/spinmapl");

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

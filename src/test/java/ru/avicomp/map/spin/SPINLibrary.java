package ru.avicomp.map.spin;

import org.apache.jena.graph.Graph;
import org.apache.jena.mem.GraphMem;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.graph.UnmodifiableGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.ontapi.jena.utils.Graphs;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by @szuev on 14.06.2018.
 *
 * @see ru.avicomp.map.spin.SystemModels
 */
public enum SPINLibrary {
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
    private static final Logger LOGGER = LoggerFactory.getLogger(SPINLibrary.class);
    private final static Map<String, Graph> GRAPHS = load();

    SPINLibrary(String path, String uri) {
        this.path = path;
        this.uri = uri;
    }

    public Graph getBaseGraph() {
        return GRAPHS.get(uri);
    }

    public Graph getGraph() {
        return Graphs.toUnion(getBaseGraph(), GRAPHS.values());
    }

    public static PrefixMapping prefixes() {
        return Graphs.collectPrefixes(GRAPHS.values());
    }

    private static Map<String, Graph> load() throws UncheckedIOException {
        Map<String, Graph> res = new HashMap<>();
        for (SPINLibrary lib : values()) {
            Graph g = new GraphMem();
            try (InputStream in = SPINLibrary.class.getResourceAsStream(lib.path)) {
                RDFDataMgr.read(g, in, null, Lang.TURTLE);
            } catch (IOException e) {
                throw new UncheckedIOException("Can't load " + lib.path, e);
            }
            LOGGER.debug("Graph {} is loaded, size: {}", lib.uri, g.size());
            res.put(lib.uri, new UnmodifiableGraph(g));
        }
        return Collections.unmodifiableMap(res);
    }

    public String getURI() {
        return uri;
    }
}

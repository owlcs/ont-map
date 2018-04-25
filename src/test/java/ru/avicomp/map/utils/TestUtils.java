package ru.avicomp.map.utils;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.shared.PrefixMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapModel;
import ru.avicomp.ontapi.jena.model.OntGraphModel;

import java.io.StringWriter;

/**
 * Created by @szuev on 13.04.2018.
 */
public class TestUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestUtils.class);

    public static String asString(Model m) {
        StringWriter s = new StringWriter();
        m.write(s, "ttl");
        return s.toString();
    }

    public static void debug(Model m) {
        LOGGER.debug("\n{}\n==========", TestUtils.asString(m));
    }

    public static void debug(MapModel m) {
        debug((OntGraphModel) m);
    }

    public static void debug(MapFunction.Call func, PrefixMapping pm) {
        LOGGER.debug("Function: {}", pm.shortForm(func.getFunction().name()));
        func.asMap().forEach((arg, o) -> {
            String val = o instanceof String ? (String) o : ((MapFunction.Call) o).getFunction().name();
            LOGGER.debug("Argument: {} => '{}'", pm.shortForm(arg.name()), pm.shortForm(val));
        });
    }
}

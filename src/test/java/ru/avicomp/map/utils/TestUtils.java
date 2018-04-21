package ru.avicomp.map.utils;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.shared.PrefixMapping;
import org.slf4j.Logger;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapModel;
import ru.avicomp.ontapi.jena.model.OntGraphModel;

import java.io.StringWriter;

/**
 * Created by @szuev on 13.04.2018.
 */
public class TestUtils {

    public static String asString(Model m) {
        StringWriter s = new StringWriter();
        m.write(s, "ttl");
        return s.toString();
    }

    public static void debug(Logger logger, Model m) {
        logger.debug("\n{}\n==========", TestUtils.asString(m));
    }

    public static void debug(Logger logger, MapModel m) {
        debug(logger, (OntGraphModel) m);
    }

    public static void debug(Logger logger, MapFunction.Call func, PrefixMapping pm) {
        logger.debug("Function: {}", pm.shortForm(func.getFunction().name()));
        func.asMap().forEach((arg, o) -> {
            String val = o instanceof String ? (String) o : ((MapFunction.Call) o).getFunction().name();
            logger.debug("Argument: {} => '{}'", pm.shortForm(arg.name()), pm.shortForm(val));
        });
    }
}

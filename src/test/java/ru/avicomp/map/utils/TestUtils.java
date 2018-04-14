package ru.avicomp.map.utils;

import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;

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
}

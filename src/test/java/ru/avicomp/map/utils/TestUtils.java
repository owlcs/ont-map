package ru.avicomp.map.utils;

import org.apache.jena.rdf.model.Model;

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
}

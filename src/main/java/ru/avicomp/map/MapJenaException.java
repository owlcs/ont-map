package ru.avicomp.map;

import org.apache.jena.shared.JenaException;

/**
 * A base map exception.
 * <p>
 * Created by @szuev on 06.04.2018.
 */
public class MapJenaException extends JenaException {

    public MapJenaException() {
    }

    public MapJenaException(String message) {
        super(message);
    }

    public MapJenaException(Throwable cause) {
        super(cause);
    }

    public MapJenaException(String message, Throwable cause) {
        super(message, cause);
    }

    public static <T> T notNull(T obj, String message) {
        if (obj == null)
            throw message == null ? new MapJenaException() : new MapJenaException(message);
        return obj;
    }
}

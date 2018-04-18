package ru.avicomp.map.spin;

import ru.avicomp.map.MapJenaException;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * An exception builder.
 * <p>
 * Created by @szuev on 18.04.2018.
 */
public enum Exceptions {
    CONTEXT_REQUIRE_TARGET_FUNCTION,
    CONTEXT_WRONG_TARGET_PROPERTY,
    CONTEXT_WRONG_EXPRESSION_ARGUMENT,

    FUNCTION_NONEXISTENT_ARGUMENT,
    FUNCTION_WRONG_ARGUMENT,
    FUNCTION_SELF_CALL,
    FUNCTION_NO_REQUIRED_ARG,;

    public Builder create() {
        return new Builder();
    }

    private String message() {
        return name();
    }

    public class Builder {
        private final EnumMap<Key, Object> map;

        Builder() {
            this.map = new EnumMap<>(Key.class);
        }

        public Builder add(Key k, String v) {
            map.put(k, v);
            return this;
        }

        public SpinMapException build() {
            return build(null);
        }

        public SpinMapException build(Throwable cause) {
            return new SpinMapException(Exceptions.this, map, Exceptions.this.message(), cause);
        }
    }

    public enum Key {
        CONTEXT_SOURCE,
        CONTEXT_TARGET,
        FUNCTION,
        TARGET_PROPERTY,
        ARG,
        ARG_TYPE,
        ARG_VALUE,
    }

    public final class SpinMapException extends MapJenaException {
        private final Map<Key, Object> map;
        private final Exceptions code;

        SpinMapException(Exceptions code, Map<Key, Object> map, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
            this.map = Collections.unmodifiableMap(map);
        }

        public Map<Key, Object> getInfo() {
            return map;
        }

        public Exceptions getCode() {
            return code;
        }
    }
}

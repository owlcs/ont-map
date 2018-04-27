package ru.avicomp.map.spin;

import ru.avicomp.map.MapJenaException;

import java.util.*;

/**
 * An exception builder.
 * <p>
 * Created by @szuev on 18.04.2018.
 */
public enum Exceptions {
    CONTEXT_REQUIRE_TARGET_FUNCTION,
    CONTEXT_WRONG_TARGET_PROPERTY,
    CONTEXT_WRONG_EXPRESSION_ARGUMENT,
    CONTEXT_NOT_BOOLEAN_FILTER_FUNCTION,

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
        private final EnumMap<Key, List<String>> map;

        Builder() {
            this.map = new EnumMap<>(Key.class);
        }

        public Builder add(Key key, String v) {
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(v);
            return this;
        }

        public boolean has(Key key) {
            return map.containsKey(key);
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
        private final Map<Key, List<String>> map;
        private final Exceptions code;

        SpinMapException(Exceptions code, Map<Key, List<String>> map, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
            this.map = Collections.unmodifiableMap(map);
        }

        public String getString(Key k) {
            return map.isEmpty() ? null : map.get(k).get(0);
        }

        public List<String> getList(Key k) {
            return map.get(k);
        }

        public Map<Key, List<String>> getInfo() {
            return map;
        }

        public Exceptions getCode() {
            return code;
        }
    }
}

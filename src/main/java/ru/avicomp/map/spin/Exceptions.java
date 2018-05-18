package ru.avicomp.map.spin;

import org.apache.jena.rdf.model.RDFNode;
import ru.avicomp.map.Context;
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
    CONTEXT_CANNOT_BE_DELETED_DUE_TO_DEPENDENCIES,

    RELATED_CONTEXT_SOURCES_CLASS_NOT_LINKED,
    RELATED_CONTEXT_AMBIGUOUS_CLASS_LINK,
    ATTACHED_CONTEXT_SELF_CALL,
    ATTACHED_CONTEXT_DIFFERENT_SOURCES,
    ATTACHED_CONTEXT_TARGET_CLASS_NOT_LINKED,
    ATTACHED_CONTEXT_AMBIGUOUS_CLASS_LINK,

    FUNCTION_NONEXISTENT_ARGUMENT,
    FUNCTION_WRONG_ARGUMENT,
    FUNCTION_SELF_CALL,
    FUNCTION_NO_REQUIRED_ARG,;

    public Builder create() {
        return new Builder();
    }

    public class Builder {
        private final EnumMap<Key, List<String>> map;

        Builder() {
            this.map = new EnumMap<>(Key.class);
        }

        public Builder addContext(Context context) {
            return add(Key.CONTEXT, context.getURI())
                    .add(Key.CONTEXT_SOURCE, context.getSource())
                    .add(Key.CONTEXT_TARGET, ((MapContextImpl) context).target());
        }

        public Builder add(Key key, RDFNode n) {
            return add(key, n.asNode().toString());
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
            return new SpinMapException(Exceptions.this, map, message(), cause);
        }

        public String message() {
            return name() + ": " + map;
        }
    }

    public enum Key {
        CONTEXT,
        CONTEXT_SOURCE,
        CONTEXT_TARGET,
        FUNCTION,
        TARGET_PROPERTY,
        LINK_PROPERTY,
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

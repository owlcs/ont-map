/*
 * This file is part of the ONT MAP.
 * The contents of this file are subject to the Apache License, Version 2.0.
 * Copyright (c) 2019, The University of Manchester, owl.cs group.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.owlcs.map.spin;

import com.github.owlcs.map.MapContext;
import com.github.owlcs.map.MapFunction;
import com.github.owlcs.map.MapJenaException;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;

import java.util.*;
import java.util.function.Supplier;

import static com.github.owlcs.map.spin.Exceptions.Key.*;

/**
 * An exception builder and store for error codes.
 * <p>
 * Created by @szuev on 18.04.2018.
 */
public enum Exceptions {
    // in mapping, either while validation or operations with contexts:
    MAPPING_FUNCTION_VALIDATION_FAIL,
    MAPPING_CONTEXT_CANNOT_BE_DELETED_DUE_TO_DEPENDENCIES,
    MAPPING_ATTACHED_CONTEXT_TARGET_CLASS_NOT_LINKED,
    MAPPING_ATTACHED_CONTEXT_AMBIGUOUS_CLASS_LINK,

    // while building context or function validation:
    CONTEXT_FUNCTION_VALIDATION_FAIL,
    CONTEXT_REQUIRE_TARGET_FUNCTION,
    CONTEXT_WRONG_MAPPING_FUNCTION,
    CONTEXT_WRONG_FILTER_FUNCTION,
    CONTEXT_NOT_BOOLEAN_FILTER_FUNCTION,

    // while making context references
    CONTEXT_RELATED_CONTEXT_SOURCES_CLASS_NOT_LINKED,
    CONTEXT_RELATED_CONTEXT_AMBIGUOUS_CLASS_LINK,
    CONTEXT_ATTACHED_CONTEXT_SELF_CALL,
    CONTEXT_ATTACHED_CONTEXT_DIFFERENT_SOURCES,
    CONTEXT_ATTACHED_CONTEXT_TARGET_CLASS_NOT_LINKED,

    // while building property bridge:
    PROPERTY_BRIDGE_WRONG_TARGET_PROPERTY,
    PROPERTY_BRIDGE_REQUIRE_NONTARGET_FUNCTION,
    PROPERTY_BRIDGE_WRONG_MAPPING_FUNCTION,
    PROPERTY_BRIDGE_WRONG_FILTER_FUNCTION,
    PROPERTY_BRIDGE_NOT_BOOLEAN_FILTER_FUNCTION,

    // while validation call:
    FUNCTION_CALL_INCOMPATIBLE_NESTED_FUNCTION,
    FUNCTION_CALL_WRONG_ARGUMENT_STRING_VALUE,
    FUNCTION_CALL_WRONG_ARGUMENT_FUNCTION_VALUE,
    FUNCTION_CALL_WRONG_ARGUMENT_UNHANDLED_CASE,
    FUNCTION_CALL_WRONG_ARGUMENT_OBJECT_PROPERTY,
    FUNCTION_CALL_WRONG_ARGUMENT_NONEXISTENT_PROPERTY,
    FUNCTION_CALL_WRONG_ARGUMENT_NON_CONTEXT_PROPERTY,
    FUNCTION_CALL_WRONG_ARGUMENT_INCOMPATIBLE_RANGE,

    // while assemble call:
    FUNCTION_CALL_ILLEGAL_ARG_NOT_ASSIGNABLE,
    FUNCTION_CALL_ILLEGAL_ARG_SELF_REF,
    FUNCTION_CALL_ILLEGAL_ARG_CANNOT_BE_NESTED,
    FUNCTION_CALL_ILLEGAL_ARG_CANNOT_HAVE_NESTED,
    // while build call:
    FUNCTION_CALL_BUILD_FAIL,
    FUNCTION_CALL_BUILD_NO_REQUIRED_ARG,
    FUNCTION_CALL_BUILD_MISSED_OPTIONAL_ARG,
    FUNCTION_CALL_BUILD_MUST_BE_ONE_OF,

    // while inference:
    INFERENCE_NO_CONTEXTS,
    INFERENCE_NO_RULES,
    INFERENCE_FAIL,
    ;

    public Builder create() {
        return new Builder();
    }

    public class Builder {
        private final EnumMap<Key, List<String>> map;

        private Builder() {
            this.map = new EnumMap<>(Key.class);
        }

        Builder addFunction(MapFunction.Call func) {
            return addFunction(func.getFunction());
        }

        Builder addFunction(MapFunction func) {
            return add(FUNCTION, func.name());
        }

        Builder addContext(MapContext context) {
            return add(CONTEXT, context.name());
        }

        Builder addArg(MapFunction.Arg arg) {
            return addFunction(arg.getFunction()).add(ARG_NAME, arg.name()).add(ARG_TYPE, arg.type());
        }

        Builder addProperty(Property property) {
            return add(PROPERTY, property);
        }

        Builder add(Key key, RDFNode n) {
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
            return build(buildMessage(), cause);
        }

        public SpinMapException build(String message, Throwable cause) {
            return new SpinMapException(Exceptions.this, map, message, cause);
        }

        String buildMessage() {
            return name() + ": " + map;
        }
    }

    /**
     * Commonly used key-codes to build exception.
     */
    public enum Key {
        MAPPING,
        CONTEXT,
        FUNCTION,
        CLASS,
        PROPERTY,
        ARG_NAME,
        ARG_TYPE,
        ARG_VALUE,
        QUERY,
        INSTANCE,
    }

    /**
     * MapException impl, a result of {@link Builder}, immutable.
     */
    public final class SpinMapException extends MapJenaException {
        private final Map<Key, List<String>> details;
        private final Exceptions code;

        private SpinMapException(Exceptions code, Map<Key, List<String>> map, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
            this.details = immutableMap(() -> new EnumMap<>(Key.class), map);
        }

        /**
         * Gets the details for the given key.
         *
         * @param k {@link Key}
         * @return String or {@code null}
         */
        public String getDetails(Key k) {
            List<String> values;
            return details.isEmpty() ? null : (values = details.get(k)).isEmpty() ? null : values.get(0);
        }

        public Map<Key, List<String>> getDetails() {
            return details;
        }

        public Exceptions getCode() {
            return code;
        }
    }

    private static <K, V> Map<K, List<V>> immutableMap(Supplier<Map<K, List<V>>> create, Map<K, List<V>> from) {
        Map<K, List<V>> tmp = create.get();
        from.forEach((key, values) -> tmp.put(key, Collections.unmodifiableList(values)));
        return Collections.unmodifiableMap(tmp);
    }
}

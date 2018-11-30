/*
 * This file is part of the ONT MAP.
 * The contents of this file are subject to the Apache License, Version 2.0.
 * Copyright (c) 2018, Avicomp Services, AO
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

package ru.avicomp.map;

import ru.avicomp.ontapi.jena.model.OntObject;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * Common interface for any mapping rule: class bridge (MapContext) and property bridge.
 * <p>
 * Created by @szuev on 05.06.2018.
 */
public interface MapResource {

    /**
     * Wraps this map-resource as ont-resource.
     * This may be useful if you want to add annotations to the mapping rule.
     *
     * @return {@link OntObject}
     */
    OntObject asResource();

    /**
     * Returns a function call that describes this bridge.
     *
     * @return {@link MapFunction.Call}
     */
    MapFunction.Call getMapping();

    /**
     * Returns a filter function call.
     * Usually a mapping (class or property bridge) does not contain any filter and method returns {@code null}.
     * A filter function must be boolean (i.e. {@code this.getFilter().isBoolean() = true}).
     *
     * @return {@link MapFunction.Call} or {@code null}
     * @see MapFunction#isBoolean()
     */
    MapFunction.Call getFilter();

    /**
     * Return the map-model associated with this {@code MapResource}.
     * The model cannot be null.
     *
     * @return {@link MapModel}, not {@code null}
     */
    MapModel getModel();

    /**
     * Lists all functions which are used by this {@code MapResource}.
     *
     * @return <b>distinct</b> Stream of {@link MapFunction}s
     * @see MapFunction#dependencies()
     */
    default Stream<MapFunction> functions() {
        return Stream.of(getMapping(), getFilter())
                .filter(Objects::nonNull)
                .flatMap(f -> Stream.concat(Stream.of(f), f.functions()))
                .map(MapFunction.Call::getFunction)
                .distinct();
    }
}

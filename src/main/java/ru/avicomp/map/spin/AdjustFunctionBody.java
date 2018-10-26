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

package ru.avicomp.map.spin;

import org.apache.jena.rdf.model.Model;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapJenaException;

import java.util.function.BiFunction;

/**
 * A common interface to allow accepting MapFunction arguments directly into function body,
 * not only in form of function-call expression.
 * Created by @szuev on 21.05.2018.
 *
 * @see ru.avicomp.map.spin.vocabulary.AVC#runtime
 */
public interface AdjustFunctionBody extends BiFunction<Model, MapFunction.Call, Boolean> {

    /**
     * Modifies a spin function body.
     *
     * @param model {@link Model} model containing spin-function body
     * @param args  {@link MapFunction.Call} function call to get arguments
     * @return true if map model graph has been changed
     * @throws MapJenaException if something is wrong with arguments or during operation
     */
    Boolean apply(Model model, MapFunction.Call args) throws MapJenaException;

}

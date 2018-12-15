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
 * A common interface that allows accepting {@link MapFunction} arguments directly into a function body.
 * Usually arguments go into a function-call expression,
 * but sometimes it is not enough due to SPIN limitation and
 * to achieve desired function behaviour it is need to modify a function body itself.
 * This mechanism serves for such a purpose:
 * before the execution of a function, the arguments from the expression (function-call) fall directly
 * into the function body with the help of this class implementation,
 * than function is re-registered and executed while inference.
 *
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

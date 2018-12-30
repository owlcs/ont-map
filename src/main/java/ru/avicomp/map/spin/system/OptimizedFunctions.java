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

package ru.avicomp.map.spin.system;

import org.apache.jena.sparql.function.Function;
import ru.avicomp.map.spin.functions.avc.UUID;
import ru.avicomp.map.spin.functions.spinmapl.concatWithSeparator;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A collection of the SPARQL-based functions that have ARQ optimizations.
 * <p>
 * Created by @ssz on 30.12.2018.
 */
class OptimizedFunctions {
    static final Map<String, Class<? extends Function>> FUNCTIONS =
            Collections.unmodifiableMap(new HashMap<String, Class<? extends Function>>() {
                {
                    put(SPINMAPL.concatWithSeparator.getURI(), concatWithSeparator.class);
                    put(AVC.UUID.getURI(), UUID.class);
                }
            });
}

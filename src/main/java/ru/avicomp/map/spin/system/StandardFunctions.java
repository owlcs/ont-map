/*
 * This file is part of the ONT MAP.
 * The contents of this file are subject to the Apache License, Version 2.0.
 * Copyright (c) 2019, Avicomp Services, AO
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
import org.apache.jena.sparql.pfunction.PropertyFunction;
import org.topbraid.spin.arq.PropertyChainHelperPFunction;
import org.topbraid.spin.arq.functions.*;
import org.topbraid.spin.vocabulary.SPIN;
import ru.avicomp.map.spin.vocabulary.OWLRL;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by @ssz on 12.06.2019.
 */
class StandardFunctions {

    static final Map<String, Class<? extends Function>> FUNCTIONS =
            Collections.unmodifiableMap(new HashMap<String, Class<? extends Function>>() {
                {
                    put(SPIN.ask.getURI(), AskFunction.class);
                    put(SPIN.eval.getURI(), EvalFunction.class);
                    put(SPIN.evalInGraph.getURI(), EvalInGraphFunction.class);
                    put(SPIN.violatesConstraints.getURI(), ViolatesConstraintsFunction.class);
                }
            });

    static final Map<String, Class<? extends PropertyFunction>> PROPERTY_FUNCTIONS =
            Collections.unmodifiableMap(new HashMap<String, Class<? extends PropertyFunction>>() {
                {
                    put(SPIN.construct.getURI(), ConstructPFunction.class);
                    put(SPIN.constructViolations.getURI(), ConstructViolationsPFunction.class);
                    put(SPIN.select.getURI(), SelectPFunction.class);
                    put(OWLRL.propertyChainHelper.getURI(), PropertyChainHelperPFunction.class);
                }
            });

}

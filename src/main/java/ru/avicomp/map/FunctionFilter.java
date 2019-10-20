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

package ru.avicomp.map;

import org.apache.jena.vocabulary.RDFS;
import org.topbraid.spin.vocabulary.SPL;
import ru.avicomp.map.spin.MapFunctionImpl;
import ru.avicomp.map.spin.vocabulary.AVC;

import java.util.function.Predicate;

/**
 * A helper to conduct functions filtering by some attribute.
 * Created by @szuev on 21.06.2018.
 *
 * @see MapManager#functions()
 */
public enum FunctionFilter implements Predicate<MapFunction> {
    BOOLEAN {
        @Override
        public boolean test(MapFunction f) {
            return f.isBoolean();
        }
    },
    DATE {
        @Override
        public boolean test(MapFunction f) {
            return f instanceof MapFunctionImpl && ((MapFunctionImpl) f).isInheritedOfClass(SPL.DateFunctions);
        }
    },
    MATH {
        @Override
        public boolean test(MapFunction f) {
            return f instanceof MapFunctionImpl && ((MapFunctionImpl) f).isInheritedOfClass(SPL.MathematicalFunctions);
        }
    },
    MISC {
        @Override
        public boolean test(MapFunction f) {
            return f instanceof MapFunctionImpl &&
                    (((MapFunctionImpl) f).isInheritedOfClass(SPL.MiscFunctions) || !((MapFunctionImpl) f).asResource().hasProperty(RDFS.subClassOf));
        }
    },
    URI {
        @Override
        public boolean test(MapFunction f) {
            return f instanceof MapFunctionImpl && ((MapFunctionImpl) f).isInheritedOfClass(SPL.URIFunctions);
        }
    },
    ONTOLOGY {
        @Override
        public boolean test(MapFunction f) {
            return f instanceof MapFunctionImpl && ((MapFunctionImpl) f).isInheritedOfClass(SPL.OntologyFunctions);
        }
    },
    STRING {
        @Override
        public boolean test(MapFunction f) {
            return f instanceof MapFunctionImpl && ((MapFunctionImpl) f).isInheritedOfClass(SPL.StringFunctions);
        }
    },
    AGGREGATE {
        @Override
        public boolean test(MapFunction f) {
            return f instanceof MapFunctionImpl && ((MapFunctionImpl) f).isInheritedOfClass(AVC.AggregateFunctions);
        }
    },
    VARARG {
        @Override
        public boolean test(MapFunction f) {
            return f.isVararg();
        }
    },
    TARGET {
        @Override
        public boolean test(MapFunction f) {
            return f.isTarget();
        }
    },
    USER_DEFINED {
        @Override
        public boolean test(MapFunction f) {
            return f.isUserDefined();
        }
    },
    ;

    @Override
    public abstract boolean test(MapFunction function);
}

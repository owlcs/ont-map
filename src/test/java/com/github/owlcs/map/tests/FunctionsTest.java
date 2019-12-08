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

package com.github.owlcs.map.tests;

import com.github.owlcs.map.FunctionFilter;
import com.github.owlcs.map.Managers;
import com.github.owlcs.map.MapFunction;
import com.github.owlcs.map.MapManager;
import com.github.owlcs.map.spin.MapFunctionImpl;
import com.github.owlcs.map.utils.TestUtils;
import org.apache.jena.rdf.model.Resource;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SPINMAP;
import org.topbraid.spin.vocabulary.SPL;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Created by @szuev on 04.06.2018.
 */
@RunWith(Parameterized.class)
public class FunctionsTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(FunctionsTest.class);
    private final FuncTypeFilter data;
    private static MapManager manager;

    @BeforeClass
    public static void before() {
        manager = Managers.createMapManager();
    }

    public FunctionsTest(FuncTypeFilter data) {
        this.data = data;
    }

    @Parameterized.Parameters(name = "{0}")
    public static FuncTypeFilter[] data() {
        return FuncTypeFilter.values();
    }

    @Test
    public void testList() {
        List<MapFunction> functions = manager.functions()
                .sorted(Comparator.comparing(MapFunction::type).thenComparing(MapFunction::name))
                .filter(data.filter).collect(Collectors.toList());
        functions.forEach(f -> LOGGER.debug("{}", f));
        LOGGER.info("{} : {}", data.name(), functions.size());
        Assert.assertEquals(data.count, functions.size());
    }

    /**
     * @see MapFunctionImpl#isInheritedOfClass(Resource)
     */
    enum FuncTypeFilter {
        ALL(186, f -> true, TestUtils.withGeoSparql()),
        BOOLEAN_TYPE(35, FunctionFilter.BOOLEAN),
        BOOLEAN_CLASS(24, f -> ((MapFunctionImpl) f).isInheritedOfClass(SPL.BooleanFunctions)),
        DATE(13, FunctionFilter.DATE),
        MATH(33, FunctionFilter.MATH, TestUtils.withGeoSparql()),
        MISC(26, FunctionFilter.MISC),
        URI(5, FunctionFilter.URI),
        ONTOLOGY(14, FunctionFilter.ONTOLOGY),
        STRING(55, FunctionFilter.STRING),
        AGGREGATE(1, FunctionFilter.AGGREGATE),
        VARARG(4, FunctionFilter.VARARG),
        TARGET_TYPE(10, FunctionFilter.TARGET),
        TARGET_CLASS(TARGET_TYPE.count, f -> ((MapFunctionImpl) f).isInheritedOfClass(SPINMAP.TargetFunctions));

        private static final int GEO_SPARQL_FUNCTIONS = 4;

        // TODO: this count number will change during the development process
        private final int count;
        private final Predicate<MapFunction> filter;

        FuncTypeFilter(int count, Predicate<MapFunction> filter) {
            this(count, filter, false);
        }

        FuncTypeFilter(int count, Predicate<MapFunction> filter, boolean geoSparql) {
            this.count = geoSparql ? count + GEO_SPARQL_FUNCTIONS : count;
            this.filter = filter;
        }

    }
}


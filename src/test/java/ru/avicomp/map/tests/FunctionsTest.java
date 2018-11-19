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

package ru.avicomp.map.tests;

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
import ru.avicomp.map.FunctionFilter;
import ru.avicomp.map.Managers;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.spin.MapFunctionImpl;

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
     * @see ru.avicomp.map.spin.MapFunctionImpl#isInheritedOfClass(Resource)
     */
    enum FuncTypeFilter {
        ALL(187, f -> true),
        BOOLEAN_TYPE(39, FunctionFilter.BOOLEAN),
        BOOLEAN_CLASS(25, f -> ((MapFunctionImpl) f).isInheritedOfClass(SPL.BooleanFunctions)),
        DATE(13, FunctionFilter.DATE),
        MATH(35, FunctionFilter.MATH),
        MISC(26, FunctionFilter.MISC),
        URI(5, FunctionFilter.URI),
        ONTOLOGY(16, FunctionFilter.ONTOLOGY),
        STRING(58, FunctionFilter.STRING),
        AGGREGATE(1, FunctionFilter.AGGREGATE),
        VARARG(3, FunctionFilter.VARARG),
        TARGET_TYPE(10, FunctionFilter.TARGET),
        TARGET_CLASS(TARGET_TYPE.count, f -> ((MapFunctionImpl) f).isInheritedOfClass(SPINMAP.TargetFunctions));

        // TODO: this count number will change during the development process
        private final int count;
        private final Predicate<MapFunction> filter;

        FuncTypeFilter(int count, Predicate<MapFunction> filter) {
            this.count = count;
            this.filter = filter;
        }

    }
}


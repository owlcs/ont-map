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

import org.apache.jena.shared.PrefixMapping;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.Managers;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapJenaException;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.spin.Exceptions;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;
import ru.avicomp.map.utils.TestUtils;

import java.util.Arrays;

/**
 * Created by @ssz on 15.12.2018.
 */
public class FunctionBuilderTest {

    private static MapManager manager;

    @BeforeClass
    public static void before() {
        manager = Managers.createMapManager();
    }

    @Test
    public void testAddRecursiveArg() {
        PrefixMapping pm = manager.prefixes();
        MapFunction.Builder a = manager.getFunction(pm.expandPrefix("sp:uri")).create();
        MapFunction.Builder b = manager.getFunction(pm.expandPrefix("sp:encode_for_uri")).create();
        b.addFunction(SP.arg1, a);
        try {
            a.addFunction(SP.arg1, b);
            Assert.fail("Possible create chained recursion");
        } catch (MapJenaException j) {
            TestUtils.debug(j);
        }
        MapFunction.Builder c = manager.getFunction(SPINMAPL.buildURI1).create();
        try {
            c.addFunction(SP.arg1, c);
            Assert.fail("Possible create straightforward recursion");
        } catch (MapJenaException j) {
            TestUtils.debug(j);
        }
    }

    @Test
    public void testAddNonAssignableArg() {
        MapFunction.Builder b = manager.getFunction(SPINMAPL.buildURI1).create()
                .addLiteral(SPINMAPL.template, "x")
                .add(SP.arg1.getURI(), "y");
        try {
            b.add(SPINMAP.source.getURI(), "z");
            Assert.fail("Possible add spinmap:source");
        } catch (MapJenaException j) {
            TestUtils.debug(j);
        }
    }

    @Test
    public void testAddUnknownArg() {
        MapFunction f = manager.getFunction(SPINMAPL.buildURI1);
        String p = "http://unknown-prefix.org";
        try {
            f.create().add(p, "xxx");
            Assert.fail("Expected error");
        } catch (MapJenaException.IllegalArgument e) { // non existent arg
            TestUtils.debug(e);
        }
        try {
            manager.getFunction(SP.resource("contains"))
                    .create()
                    .addLiteral(SP.arg1, "a")
                    .addLiteral(SP.arg2, "b")
                    .addLiteral(SPINMAPL.template, "target:xxx");
            Assert.fail("Expected error");
        } catch (MapJenaException.IllegalArgument e) {
            TestUtils.debug(e);
        }
    }

    @Test(expected = MapJenaException.IllegalArgument.class)
    public void testAddTargetFunctionAsNested() {
        manager.getFunction(SP.resource("contains"))
                .create()
                .addLiteral(SP.arg1, "a")
                .addFunction(SP.arg2, manager.getFunction(SPINMAPL.buildURI1).create());
    }

    @Test(expected = MapJenaException.IllegalArgument.class)
    public void testAddPropertyFunctionWithNestedCall() {
        manager.getFunction(AVC.withDefault)
                .create()
                .add(SP.arg1.getURI(), manager.getFunction(manager.prefixes().expandPrefix("afn:now")).create())
                .add(SP.arg2.getURI(), "val");
    }

    @Test
    public void testRemoveAndClear() {
        MapFunction.Builder f = manager.getFunction(manager.prefixes().expandPrefix("spl:hasValueOfType")).create();
        f.add(SP.arg1.getURI(), "x").add(SP.arg2.getURI(), "t").remove(SP.arg1.getURI()).remove(SP.arg3.getURI());
        try {
            f.build();
            Assert.fail("Can build");
        } catch (MapJenaException j) {
            TestUtils.debug(j);
            Assert.assertEquals(2, j.getSuppressed().length);
        }
        f.clear();
        try {
            f.build();
            Assert.fail("Can build");
        } catch (MapJenaException j) {
            TestUtils.debug(j);
            Assert.assertEquals(3, j.getSuppressed().length);
        }
    }

    @Test
    public void testBuildNoRequiredArg() {
        MapFunction f = manager.getFunction(manager.prefixes().expandPrefix("spinmapl:concatWithSeparator"));
        try {
            f.create().build();
            Assert.fail("Expected error");
        } catch (MapJenaException j) {
            MappingErrorsTest.assertCode(j, Exceptions.FUNCTION_BUILD_FAIL);
            Assert.assertEquals(3, j.getSuppressed().length);
            Assert.assertEquals(f.name(), ((Exceptions.SpinMapException) j).getString(Exceptions.Key.FUNCTION));
            Arrays.stream(j.getSuppressed()).map(Exceptions.SpinMapException.class::cast)
                    .forEach(x -> MappingErrorsTest.assertCode(x, Exceptions.FUNCTION_BUILD_NO_REQUIRED_ARG));
        }
        try {
            f.create().add(manager.prefixes().expandPrefix("sp:arg1"), "x").build();
            Assert.fail("Expected error");
        } catch (MapJenaException j) { // no required arg
            MappingErrorsTest.assertCode(j, Exceptions.FUNCTION_BUILD_FAIL);
            Assert.assertEquals(2, j.getSuppressed().length);
            Assert.assertEquals(f.name(), ((Exceptions.SpinMapException) j).getString(Exceptions.Key.FUNCTION));
        }
        try {
            manager.getFunction(SPINMAPL.buildURI1).create().addLiteral(SPINMAPL.template, "x").build();
            Assert.fail("Expected error");
        } catch (MapJenaException j) {
            MappingErrorsTest.assertCode(j, Exceptions.FUNCTION_BUILD_NO_REQUIRED_ARG);
        }
    }
}

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

package com.github.owlcs.map.tests.geos;

import com.github.owlcs.map.Managers;
import com.github.owlcs.map.MapFunction;
import com.github.owlcs.map.MapJenaException;
import com.github.owlcs.map.MapManager;
import com.github.owlcs.map.spin.Exceptions;
import com.github.owlcs.map.spin.geos.vocabulary.SPATIAL;
import com.github.owlcs.map.utils.TestUtils;
import org.junit.Assert;
import org.junit.Test;
import org.topbraid.spin.vocabulary.SP;

/**
 * Created by @ssz on 06.07.2019.
 */
public class ErrorsTest {

    @Test
    public void testIncorrectDistanceUnit() {
        MapManager manager = Managers.createMapManager();
        MapFunction distance = manager.getFunction(SPATIAL.distance);

        try {
            distance.create()
                    .add(SP.arg1, "http://p1")
                    .add(SP.arg2, "http://p2")
                    .add(SP.arg3, "x").build();
            Assert.fail("Expected fail");
        } catch (MapJenaException j) {
            TestUtils.assertCode(j, Exceptions.FUNCTION_CALL_BUILD_MUST_BE_ONE_OF);
        }
    }
}

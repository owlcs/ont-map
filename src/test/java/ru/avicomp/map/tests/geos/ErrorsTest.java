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

package ru.avicomp.map.tests.geos;

import org.junit.Assert;
import org.junit.Test;
import org.topbraid.spin.vocabulary.SP;
import ru.avicomp.map.Managers;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapJenaException;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.spin.Exceptions;
import ru.avicomp.map.spin.geos.vocabulary.SPATIAL;
import ru.avicomp.map.utils.TestUtils;

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

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

package ru.avicomp.map.tests;

import org.junit.Test;

/**
 * Created by @ssz on 30.12.2018.
 */
public class InfrOptimizationTest {
    private static InfrPerfTester tester = new InfrPerfTester(5);

    @Test
    public void testInferenceWithOptimization() {
        tester.testInference();
    }

    @Test
    public void testInferenceWithoutOptimization() {
        tester.testInferenceNoOptimization();
    }
}

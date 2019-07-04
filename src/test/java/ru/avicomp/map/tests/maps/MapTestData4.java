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

package ru.avicomp.map.tests.maps;

import org.apache.jena.riot.Lang;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.model.OntGraphModel;

import java.io.IOException;

/**
 * Created by @szuev on 17.05.2018.
 */
abstract class MapTestData4 extends AbstractMapTest {

    @Override
    public OntGraphModel assembleSource() {
        try {
            return TestUtils.load("/iswc.ttl", Lang.TURTLE);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public OntGraphModel assembleTarget() {
        return new RelatedContextMapTest().assembleTarget();
    }

}

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

package com.github.owlcs.map.tests.maps;

import com.github.owlcs.map.utils.TestUtils;
import com.github.owlcs.ontapi.jena.model.OntClass;
import com.github.owlcs.ontapi.jena.model.OntDT;
import com.github.owlcs.ontapi.jena.model.OntGraphModel;
import com.github.owlcs.ontapi.jena.model.OntNDP;
import org.apache.jena.riot.Lang;
import org.apache.jena.vocabulary.XSD;

import java.io.IOException;
import java.util.stream.Stream;

/**
 * Created by @szuev on 30.05.2018.
 */
abstract class MapTestData6 extends AbstractMapTest {

    // Test Data:
    static final String SHIP_1_NAME = "Bismarck";
    static final String SHIP_2_NAME = "Yamashiro";
    static final String SHIP_3_NAME = "King George V";
    static final double[] SHIP_1_COORDINATES = new double[]{43.296492, 12.3234};
    static final double[] SHIP_2_COORDINATES = new double[]{-32, 151.56};
    static final double[] SHIP_3_COORDINATES = new double[]{46.34542, 28.674692};

    @Override
    public OntGraphModel assembleSource() {
        OntGraphModel m;
        try {
            m = TestUtils.load("/ex-sup-test.ttl", Lang.TURTLE);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        m.setID(getDataNameSpace() + "/source");
        m.setNsPrefix("data", m.getID().getURI() + "/data#");
        return m;
    }


    @Override
    public OntGraphModel assembleTarget() {
        OntGraphModel m = createDataModel("result");
        String ns = m.getID().getURI() + "#";
        OntClass res = m.createOntEntity(OntClass.class, ns + "Res");
        OntDT string = m.getOntEntity(OntDT.class, XSD.xstring);
        Stream.of("name", "latitude", "longitude", "message").forEach(s -> {
            OntNDP p = m.createOntEntity(OntNDP.class, ns + s);
            p.addRange(string);
            p.addDomain(res);
        });
        return m;
    }
}

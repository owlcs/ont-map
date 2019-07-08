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

import org.apache.jena.vocabulary.XSD;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import ru.avicomp.map.Managers;
import ru.avicomp.map.MapModel;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntDT;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntNDP;

import java.util.function.Supplier;

/**
 * Created by @ssz on 05.07.2019.
 */
@RunWith(Parameterized.class)
@SuppressWarnings("WeakerAccess")
public class PoleDistanceMapTest {

    static final String CITY_NAME = "City";
    static final String POLE_NAME = "ToPole";
    static final String POINT_NAME = "GeoPoint";
    static final String LATITUDE_NAME = "Latitude";
    static final String LONGITUDE_NAME = "Longitude";

    private final DistanceMappings testData;

    public PoleDistanceMapTest(DistanceMappings testData) {
        this.testData = testData;
    }

    @Parameterized.Parameters(name = "{0}")
    public static DistanceMappings[] getTestData() {
        return DistanceMappings.values();
    }

    @Test
    public void testInference() {
        OntGraphModel src = createSource(OntModelFactory::createModel);
        OntGraphModel dst = testData.target(OntModelFactory::createModel);
        MapModel map = testData.mapping(src, dst, Managers.createMapManager());
        TestUtils.debug(map);
        map.runInference(src.getGraph(), dst.getGraph());
        TestUtils.debug(dst);
        testData.validate(dst);
    }

    public static OntGraphModel createSource(Supplier<OntGraphModel> factory) {
        String uri = "http://geo.source.test";
        String ns = uri + "#";
        OntGraphModel base = createBase(factory);
        OntGraphModel res = factory.get().setNsPrefixes(OntModelFactory.STANDARD).setNsPrefix("src", ns);
        res.setID(uri);
        res.addImport(base);

        OntClass point = TestUtils.findOntEntity(res, OntClass.class, POINT_NAME);
        OntNDP lat = TestUtils.findOntEntity(res, OntNDP.class, LATITUDE_NAME);
        OntNDP lon = TestUtils.findOntEntity(res, OntNDP.class, LONGITUDE_NAME);
        OntClass city = res.createOntClass(ns + "City").addSuperClass(point);
        OntDT xdouble = res.getDatatype(XSD.xdouble);

        // coords M1:  55,45,21 n.lat. 37,37,04 e.lon.
        city.createIndividual(ns + "M1")
                .addAssertion(lat, xdouble.createLiteral(50. + 45. / 60 + 21. / 3600))
                .addAssertion(lon, xdouble.createLiteral(37. + 37. / 60 + 04. / 3600));

        // coords M2: 59,34 n.lat. 150,48 e.lon.
        city.createIndividual(ns + "M2")
                .addAssertion(lat, xdouble.createLiteral(059.0 + 34. / 60))
                .addAssertion(lon, xdouble.createLiteral(150.0 + 48. / 60));

        // coords B1: 13,45 n.lat. 100,31 e.lon.
        city.createIndividual(ns + "B1")
                .addAssertion(lat, xdouble.createLiteral(013.0 + 45. / 60))
                .addAssertion(lon, xdouble.createLiteral(100.0 + 31. / 60));

        // coords K1:  21,09,38 n.lat. 86,50,51 w.lon.
        city.createIndividual(ns + "K1")
                .addAssertion(lat, xdouble.createLiteral(21.0 + 9. / 60 + 38. / 3600))
                .addAssertion(lon, xdouble.createLiteral(-(86.0 + 50. / 60 + 51. / 3600)));

        return res;
    }

    public static OntGraphModel createTarget(Supplier<OntGraphModel> factory, String dataPropertyLocalName) {
        return createTarget(factory, POLE_NAME, dataPropertyLocalName);
    }

    public static OntGraphModel createTarget(Supplier<OntGraphModel> factory, String classLocalName, String dataPropertyLocalName) {
        String uri = "http://geo.target.test";
        String ns = uri + "#";
        OntGraphModel res = factory.get().setNsPrefixes(OntModelFactory.STANDARD).setNsPrefix("dst", ns);
        res.setID(uri);
        res.createDataProperty(ns + dataPropertyLocalName)
                .addDomain(res.createOntClass(ns + classLocalName)).addRange(XSD.xdouble);
        return res;
    }

    public static OntGraphModel createBase(Supplier<OntGraphModel> factory) {
        String uri = "http://geo.base.test";
        String ns = uri + "#";
        OntGraphModel res = factory.get().setNsPrefixes(OntModelFactory.STANDARD);
        res.setID(uri);
        OntClass point = res.createOntClass(ns + POINT_NAME);
        res.getDatatype(XSD.xdouble);
        res.createDataProperty(ns + LATITUDE_NAME).addDomain(point).addRange(XSD.xdouble);
        res.createDataProperty(ns + LONGITUDE_NAME).addDomain(point).addRange(XSD.xdouble);
        return res;
    }

}

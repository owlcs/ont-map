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

import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.junit.Assert;
import org.junit.Test;
import org.topbraid.spin.vocabulary.SP;
import ru.avicomp.map.Managers;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.MapModel;
import ru.avicomp.map.spin.geos.vocabulary.SPATIAL;
import ru.avicomp.map.spin.geos.vocabulary.UOM;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.spin.vocabulary.SPIF;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntDT;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntNDP;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Created by @ssz on 05.07.2019.
 */
@SuppressWarnings("WeakerAccess")
public class PoleDistanceMapTest {

    private static final String CITY_NAME = "City";
    private static final String POLE_NAME = "ToPole";
    private static final String LATITUDE_NAME = "Latitude";
    private static final String LONGITUDE_NAME = "Longitude";
    private static final String DISTANCE_NAME = "Km";

    @Test
    public void testInference() {
        OntGraphModel src = createSource(OntModelFactory::createModel);
        OntGraphModel dst = createTarget(OntModelFactory::createModel);
        MapModel map = createMapping(src, dst, Managers.createMapManager());
        map.runInference(src.getGraph(), dst.getGraph());
        TestUtils.debug(dst);
        validate(dst);
    }

    public static void validate(OntGraphModel m) {
        Map<String, Integer> expected = new HashMap<>();
        expected.put("M1", 4363);
        expected.put("M2", 3384);
        expected.put("B1", 8478);
        expected.put("K1", 7654);

        OntNDP dis = TestUtils.findOntEntity(m, OntNDP.class, DISTANCE_NAME);
        expected.forEach((k, v) -> m.listResourcesWithProperty(RDFS.label, k)
                .mapWith(x -> m.getRequiredProperty(x, dis).getLiteral().getInt())
                .forEachRemaining(i -> Assert.assertEquals(v, i)));
    }

    public static MapModel createMapping(OntGraphModel source, OntGraphModel target, MapManager manager) {
        MapFunction convertLatLon = manager.getFunction(SPATIAL.convertLatLon);
        MapFunction distance = manager.getFunction(SPATIAL.distance);
        MapFunction localName = manager.getFunction(SPIF.localName);

        MapFunction.Call north = convertLatLon.create()
                .addLiteral(SP.arg1, 90d).addLiteral(SP.arg2, 0d).build();
        MapFunction.Call thisIndividual = manager.getFunction(AVC.currentIndividual).create().build();
        MapFunction.Call uuid = manager.getFunction(AVC.UUID).create().build();

        OntClass city = TestUtils.findOntEntity(source, OntClass.class, CITY_NAME);
        OntClass pole = TestUtils.findOntEntity(target, OntClass.class, POLE_NAME);
        OntNDP lat = TestUtils.findOntEntity(source, OntNDP.class, LATITUDE_NAME);
        OntNDP lon = TestUtils.findOntEntity(source, OntNDP.class, LONGITUDE_NAME);
        OntNDP dis = TestUtils.findOntEntity(target, OntNDP.class, DISTANCE_NAME);

        MapModel res = manager.createMapModel();
        res.createContext(city, pole, uuid)
                .addPropertyBridge(distance.create()
                        .addFunction(SP.arg1, convertLatLon.create()
                                .addProperty(SP.arg1, lat).addProperty(SP.arg2, lon))
                        .addFunction(SP.arg2, north)
                        .add(SP.arg3, UOM.URN.kilometer.getURI()), dis)
                .getContext()
                .addPropertyBridge(localName.create()
                        .addFunction(SP.arg1, thisIndividual), RDFS.label);

        TestUtils.debug(res);
        return res;
    }

    public static OntGraphModel createSource(Supplier<OntGraphModel> factory) {
        String uri = "http://geo.source.test";
        String ns = uri + "#";
        OntGraphModel base = createBase(factory);
        OntGraphModel res = factory.get().setNsPrefixes(OntModelFactory.STANDARD).setNsPrefix("src", ns);
        res.setID(uri);
        res.addImport(base);

        OntClass point = TestUtils.findOntEntity(res, OntClass.class, "GeoPoint");
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

    public static OntGraphModel createTarget(Supplier<OntGraphModel> factory) {
        String uri = "http://geo.target.test";
        String ns = uri + "#";
        OntGraphModel res = factory.get().setNsPrefixes(OntModelFactory.STANDARD).setNsPrefix("dst", ns);
        res.setID(uri);
        res.createDataProperty(ns + DISTANCE_NAME)
                .addDomain(res.createOntClass(ns + POLE_NAME)).addRange(XSD.xdouble);
        return res;
    }

    public static OntGraphModel createBase(Supplier<OntGraphModel> factory) {
        String uri = "http://geo.base.test";
        String ns = uri + "#";
        OntGraphModel res = factory.get().setNsPrefixes(OntModelFactory.STANDARD);
        res.setID(uri);
        OntClass point = res.createOntClass(ns + "GeoPoint");
        res.getDatatype(XSD.xdouble);
        res.createDataProperty(ns + LATITUDE_NAME).addDomain(point).addRange(XSD.xdouble);
        res.createDataProperty(ns + LONGITUDE_NAME).addDomain(point).addRange(XSD.xdouble);
        return res;
    }
}

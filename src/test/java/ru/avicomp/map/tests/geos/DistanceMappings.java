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
import org.junit.Assert;
import org.topbraid.spin.vocabulary.SP;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.MapModel;
import ru.avicomp.map.spin.geos.vocabulary.SPATIAL;
import ru.avicomp.map.spin.geos.vocabulary.UOM;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.spin.vocabulary.SPIF;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntNDP;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Created by @ssz on 06.07.2019.
 */
enum DistanceMappings {
    DISTANCE_IN_KMS {
        @Override
        String getDistanceDataPropertyLocalName() {
            return "DistanceInKm";
        }

        @Override
        MapModel assembleMapping(MapManager manager,
                                 OntClass city, OntNDP lat, OntNDP lon,
                                 OntClass pole, OntNDP dis,
                                 MapFunction.Call north) {
            MapFunction convertLatLon = manager.getFunction(SPATIAL.convertLatLon);
            MapFunction distance = manager.getFunction(SPATIAL.distance);
            MapFunction localName = manager.getFunction(SPIF.localName);
            MapFunction.Call uuid = manager.getFunction(AVC.UUID).create().build();

            MapFunction.Call thisIndividual = manager.getFunction(AVC.currentIndividual).create().build();
            return manager.createMapModel()
                    .createContext(city, pole, uuid)
                    .addPropertyBridge(distance.create()
                            .addFunction(SP.arg1, convertLatLon.create()
                                    .addProperty(SP.arg1, lat).addProperty(SP.arg2, lon))
                            .addFunction(SP.arg2, north)
                            .add(SP.arg3, UOM.URN.kilometer.getURI()), dis)
                    .getContext()
                    .addPropertyBridge(localName.create()
                            .addFunction(SP.arg1, thisIndividual), RDFS.label)
                    .getModel();

        }

        @Override
        Map<String, Integer> expectedValues() {
            Map<String, Integer> res = new HashMap<>();
            res.put("M1", 4363);
            res.put("M2", 3384);
            res.put("B1", 8478);
            res.put("K1", 7654);
            return res;
        }
    },
    DISTANCE_IN_DEFAULT_METERS {
        @Override
        String getDistanceDataPropertyLocalName() {
            return "DistanceInM";
        }

        @Override
        MapModel assembleMapping(MapManager manager,
                                 OntClass city, OntNDP lat, OntNDP lon,
                                 OntClass pole, OntNDP dis,
                                 MapFunction.Call north) {
            MapFunction convertLatLon = manager.getFunction(SPATIAL.convertLatLon);
            MapFunction distance = manager.getFunction(SPATIAL.distance);
            MapFunction localName = manager.getFunction(SPIF.localName);
            MapFunction.Call uuid = manager.getFunction(AVC.UUID).create().build();

            MapFunction.Call thisIndividual = manager.getFunction(AVC.currentIndividual).create().build();
            return manager.createMapModel()
                    .createContext(city, pole, uuid)
                    .addPropertyBridge(distance.create()
                            .addFunction(SP.arg1, convertLatLon.create()
                                    .addProperty(SP.arg1, lat).addProperty(SP.arg2, lon))
                            .addFunction(SP.arg2, north), dis)
                    .getContext()
                    .addPropertyBridge(localName.create()
                            .addFunction(SP.arg1, thisIndividual), RDFS.label)
                    .getModel();

        }

        @Override
        Map<String, Integer> expectedValues() {
            Map<String, Integer> res = new HashMap<>();
            res.put("M1", 4363758);
            res.put("M2", 3384036);
            res.put("B1", 8478624);
            res.put("K1", 7654607);
            return res;
        }
    },

    DISTANCE_IN_MINUTES {
        @Override
        String getDistanceDataPropertyLocalName() {
            return "DistanceInMinutes";
        }

        @Override
        Map<String, Integer> expectedValues() {
            Map<String, Integer> res = new HashMap<>();
            res.put("M1", 3717);
            res.put("M2", 3600);
            res.put("B1", 4704);
            res.put("K1", 4424);
            return res;
        }

        @Override
        MapModel assembleMapping(MapManager manager,
                                 OntClass city, OntNDP lat, OntNDP lon,
                                 OntClass pole, OntNDP dis,
                                 MapFunction.Call north) {
            MapFunction convertLatLon = manager.getFunction(SPATIAL.convertLatLon);
            MapFunction distance = manager.getFunction(SPATIAL.distance);
            MapFunction.Call self = manager.getFunction(SPINMAPL.self).create().build();

            return manager.createMapModel()
                    .createContext(city, pole, self)
                    .addPropertyBridge(distance.create()
                            .addFunction(SP.arg1, convertLatLon.create()
                                    .addProperty(SP.arg1, lat).addProperty(SP.arg2, lon))
                            .addFunction(SP.arg2, north)
                            .add(SP.arg3, UOM.minute.getURI()), dis)
                    .getModel();
        }

        void validate(OntGraphModel m) {
            OntNDP dis = TestUtils.findOntEntity(m, OntNDP.class, getDistanceDataPropertyLocalName());
            OntClass pole = TestUtils.findOntEntity(m, OntClass.class, PoleDistanceMapTest.POLE_NAME);
            Map<String, Integer> expected = expectedValues();
            pole.individuals().forEach(i -> {
                Integer ex = expected.get(i.getLocalName());
                Assert.assertNotNull(ex);
                Assert.assertEquals(ex.intValue(), i.getRequiredProperty(dis).getLiteral().getInt());
            });
        }
    };

    abstract String getDistanceDataPropertyLocalName();

    abstract Map<String, Integer> expectedValues();

    abstract MapModel assembleMapping(MapManager manager,
                                      OntClass city, OntNDP lat, OntNDP lon,
                                      OntClass pole, OntNDP dis,
                                      MapFunction.Call north);

    OntGraphModel target(Supplier<OntGraphModel> factory) {
        return PoleDistanceMapTest.createTarget(factory, getDistanceDataPropertyLocalName());
    }

    MapModel mapping(OntGraphModel source, OntGraphModel target, MapManager manager) {
        MapFunction.Call north = manager.getFunction(SPATIAL.convertLatLon).create()
                .addLiteral(SP.arg1, 90d).addLiteral(SP.arg2, 0d).build();

        OntClass city = TestUtils.findOntEntity(source, OntClass.class, PoleDistanceMapTest.CITY_NAME);
        OntClass pole = TestUtils.findOntEntity(target, OntClass.class, PoleDistanceMapTest.POLE_NAME);
        OntNDP lat = TestUtils.findOntEntity(source, OntNDP.class, PoleDistanceMapTest.LATITUDE_NAME);
        OntNDP lon = TestUtils.findOntEntity(source, OntNDP.class, PoleDistanceMapTest.LONGITUDE_NAME);
        OntNDP dis = TestUtils.findOntEntity(target, OntNDP.class, getDistanceDataPropertyLocalName());

        return assembleMapping(manager, city, lat, lon, pole, dis, north);
    }

    void validate(OntGraphModel m) {
        OntNDP dis = TestUtils.findOntEntity(m, OntNDP.class, getDistanceDataPropertyLocalName());
        expectedValues().forEach((k, v) -> m.listResourcesWithProperty(RDFS.label, k)
                .mapWith(x -> m.getRequiredProperty(x, dis).getLiteral().getInt())
                .forEachRemaining(i -> Assert.assertEquals(v, i)));
    }
}

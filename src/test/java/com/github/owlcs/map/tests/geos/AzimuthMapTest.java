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
import com.github.owlcs.map.MapManager;
import com.github.owlcs.map.MapModel;
import com.github.owlcs.map.spin.geos.vocabulary.SPATIAL;
import com.github.owlcs.map.spin.vocabulary.SPINMAPL;
import com.github.owlcs.map.utils.TestUtils;
import com.github.owlcs.ontapi.jena.OntModelFactory;
import com.github.owlcs.ontapi.jena.model.*;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.XSD;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.topbraid.spin.vocabulary.SP;

import java.util.function.Supplier;

/**
 * Created by @ssz on 07.07.2019.
 */
@RunWith(Parameterized.class)
public class AzimuthMapTest {

    private final AzimuthData data;

    public AzimuthMapTest(AzimuthData data) {
        this.data = data;
    }

    @Parameterized.Parameters(name = "{0}")
    public static AzimuthData[] getTestData() {
        return AzimuthData.values();
    }

    @Test
    public void testInference() {
        OntModel src = createSource(OntModelFactory::createModel);


        OntModel dst = PoleDistanceMapTest.createTarget(OntModelFactory::createModel, "PointX", "AzimuthX");
        MapManager manager = Managers.createMapManager();

        MapFunction.Call self = manager.getFunction(SPINMAPL.self).create().build();
        MapFunction azimuth = manager.getFunction(data.getFunction());

        OntClass pointSrc = TestUtils.findOntEntity(src, OntClass.Named.class, PoleDistanceMapTest.POINT_NAME);
        OntClass pointDst = dst.classes().findFirst().orElseThrow(AssertionError::new);
        OntDataProperty lat = TestUtils.findOntEntity(src, OntDataProperty.class, PoleDistanceMapTest.LATITUDE_NAME);
        OntDataProperty lon = TestUtils.findOntEntity(src, OntDataProperty.class, PoleDistanceMapTest.LONGITUDE_NAME);
        OntDataProperty angle = dst.dataProperties().findFirst().orElseThrow(AssertionError::new);

        MapModel m = manager.createMapModel().createContext(pointSrc, pointDst, self)
                .addPropertyBridge(azimuth.create()
                        .addProperty(SP.arg1, lat)
                        .addProperty(SP.arg2, lon)
                        .addLiteral(SP.arg3, 0.)
                        .addLiteral(SP.arg4, 0.).build(), angle)
                .getModel();

        TestUtils.debug(m);
        m.runInference(src.getGraph(), dst.getBaseGraph());
        TestUtils.debug(dst);

        // validate
        double x1 = TestUtils.findOntEntity(dst, OntIndividual.Named.class, "P1")
                .getRequiredProperty(angle).getLiteral().getDouble();
        Assert.assertEquals(data.value(Azimuth.SOUTH), x1, 0.0001);
        double x2 = TestUtils.findOntEntity(dst, OntIndividual.Named.class, "P2")
                .getRequiredProperty(angle).getLiteral().getDouble();
        Assert.assertEquals(data.value(Azimuth.WEST), x2, 0.0001);
    }

    private static OntModel createSource(Supplier<OntModel> factory) {
        String uri = "http://geo.source.test";
        String ns = uri + "#";
        OntModel base = PoleDistanceMapTest.createBase(factory);
        OntModel res = factory.get().setNsPrefixes(OntModelFactory.STANDARD).setNsPrefix("src", ns);
        res.setID(uri);
        res.addImport(base);

        OntClass point = TestUtils.findOntEntity(res, OntClass.Named.class, PoleDistanceMapTest.POINT_NAME);
        OntDataProperty lat = TestUtils.findOntEntity(res, OntDataProperty.class, PoleDistanceMapTest.LATITUDE_NAME);
        OntDataProperty lon = TestUtils.findOntEntity(res, OntDataProperty.class, PoleDistanceMapTest.LONGITUDE_NAME);
        OntDataRange.Named xdouble = res.getDatatype(XSD.xdouble);

        // coords P1:  60 n.lat. 0 e.lon.
        point.createIndividual(ns + "P1")
                .addAssertion(lat, xdouble.createLiteral(60))
                .addAssertion(lon, xdouble.createLiteral(0));

        // coords P2: 50 n.lat. 45 e.lon.
        point.createIndividual(ns + "P2")
                .addAssertion(lat, xdouble.createLiteral(0))
                .addAssertion(lon, xdouble.createLiteral(65));
        return res;
    }

    enum Azimuth {
        WEST {
            @Override
            int degree() {
                return 270;
            }
        },
        SOUTH {
            @Override
            int degree() {
                return 180;
            }
        },
        ;

        abstract int degree();

        double radian() {
            return Math.toRadians(degree());
        }
    }

    enum AzimuthData {
        IN_RADIAN {
            @Override
            Resource getFunction() {
                return SPATIAL.azimuth;
            }

            @Override
            double value(Azimuth a) {
                return a.radian();
            }
        },
        IN_DEGREE {
            @Override
            Resource getFunction() {
                return SPATIAL.azimuthDeg;
            }

            @Override
            double value(Azimuth a) {
                return a.degree();
            }
        };

        abstract Resource getFunction();

        abstract double value(Azimuth a);
    }
}

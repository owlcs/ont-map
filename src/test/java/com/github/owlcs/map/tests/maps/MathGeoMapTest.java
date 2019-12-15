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

import com.github.owlcs.map.*;
import com.github.owlcs.map.utils.TestUtils;
import com.github.owlcs.ontapi.jena.OntModelFactory;
import com.github.owlcs.ontapi.jena.model.*;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.XSD;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * TODO: develop and implement spif:cast checking (instead of xsd:double)
 * Created by @szz on 22.11.2018.
 */
@SuppressWarnings("WeakerAccess")
public class MathGeoMapTest extends AbstractMapTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(MathGeoMapTest.class);

    @Test
    public void testRunInference() {
        double[][] data = new double[][]{{2.2, -30.233535}, {3, 4}};

        OntModel src = createSourceModel(data);
        TestUtils.debug(src);
        OntModel dst = createTargetModel();
        MapModel map = createMapping(Managers.createMapManager(), src, dst);
        TestUtils.debug(map);
        map.rules().map(MapResource::getMapping).forEach(x -> LOGGER.debug("Function: {}", x));

        map.runInference(src.getGraph(), dst.getGraph());

        validate(dst, data);
    }

    public static void validate(OntModel dst, double[][] data) {
        TestUtils.debug(dst);
        // validate:
        OntDataProperty r = TestUtils.findOntEntity(dst, OntDataProperty.class, "r");

        List<Double> expected = Arrays.stream(data)
                .mapToDouble(s -> Math.pow(s[0], 2) + Math.pow(s[1], 2)).map(Math::sqrt).boxed()
                .sorted().collect(Collectors.toList());
        List<Double> actual = dst.statements(null, r, null)
                .map(Statement::getLiteral).map(Literal::getDouble)
                .sorted().collect(Collectors.toList());
        LOGGER.debug("Expected: {}", expected);
        LOGGER.debug("Actual: {}", actual);
        Assert.assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            Assert.assertEquals(expected.get(i), actual.get(i), 0.000001);
        }
    }

    public static OntModel createSourceModel(double[][] data) {
        LOGGER.debug("Create source.");
        String uri = "http://geo-src.avc.ru";
        String ns = uri + "#";
        OntModel res = OntModelFactory.createModel()
                .setNsPrefixes(OntModelFactory.STANDARD)
                .setNsPrefix("coords", ns);
        res.setID(uri);
        OntClass c = res.createOntClass(ns + "Coordinates");
        OntDataRange.Named dt = res.getDatatype(XSD.xdouble);
        OntDataProperty x = res.createOntEntity(OntDataProperty.class, ns + "x");
        OntDataProperty y = res.createOntEntity(OntDataProperty.class, ns + "y");
        x.addDomain(c);
        y.addDomain(c);
        x.addRange(dt);
        y.addRange(dt);

        for (int i = 0; i < data.length; i++) {
            OntIndividual p = c.createIndividual(ns + "Point-" + i);
            p.addProperty(x, dt.createLiteral(String.valueOf(data[i][0])));
            p.addProperty(y, dt.createLiteral(String.valueOf(data[i][1])));
        }
        return res;
    }

    public static OntModel createTargetModel() {
        LOGGER.debug("Create target");
        String uri = "http://geo-dst.avc.ru";
        String ns = uri + "#";
        OntModel res = OntModelFactory.createModel()
                .setNsPrefixes(OntModelFactory.STANDARD)
                .setNsPrefix("coords", ns);
        res.setID(uri);
        OntClass c = res.createOntClass(ns + "Coordinates");
        OntDataRange dt = res.getDatatype(XSD.xdouble);
        OntDataProperty r = res.createOntEntity(OntDataProperty.class, ns + "r");
        r.addDomain(c);
        r.addRange(dt);
        return res;
    }

    public static MapModel createMapping(MapManager m, OntModel src, OntModel dst) {
        OntClass s = TestUtils.findOntEntity(src, OntClass.Named.class, "Coordinates");
        OntClass t = TestUtils.findOntEntity(dst, OntClass.Named.class, "Coordinates");
        OntDataProperty x = TestUtils.findOntEntity(src, OntDataProperty.class, "x");
        OntDataProperty y = TestUtils.findOntEntity(src, OntDataProperty.class, "y");
        OntDataProperty r = TestUtils.findOntEntity(dst, OntDataProperty.class, "r");

        MapFunction self = m.getFunction(m.prefixes().expandPrefix("spinmapl:self"));
        MapFunction pow = m.getFunction(m.prefixes().expandPrefix("math:pow"));
        MapFunction sum = m.getFunction(m.prefixes().expandPrefix("sp:add"));
        MapFunction sqrt = m.getFunction(m.prefixes().expandPrefix("math:sqrt"));
        MapFunction xd = m.getFunction(XSD.xdouble);

        MapModel res = m.createMapModel();
        res.asGraphModel().setID("http://geo-map.avc.ru");
        MapContext c = res.createContext(s, t, self.create().build());
        MapFunction.Call f = sqrt.create().addFunction(SP.arg1,
                xd.create().addFunction(SP.arg1,
                        sum.create()
                                .addFunction(SP.arg1, pow.create()
                                        .addProperty(SP.arg1, x)
                                        .addLiteral(SP.arg2, 2))
                                .addFunction(SP.arg2, pow.create()
                                        .addProperty(SP.arg1, y)
                                        .addLiteral(SP.arg2, 2))))
                .build();
        c.addPropertyBridge(f, r);
        return res;
    }

    @Override
    public MapModel assembleMapping(MapManager manager, OntModel src, OntModel dst) {
        return createMapping(manager, src, dst);
    }

    @Override
    public OntModel assembleSource() {
        return createSourceModel(new double[][]{{1.2, 34}, {23, 54545}, {121312.23, -34}});
    }

    @Override
    public OntModel assembleTarget() {
        return createTargetModel();
    }
}

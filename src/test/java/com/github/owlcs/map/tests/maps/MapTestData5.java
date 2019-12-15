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

import com.github.owlcs.ontapi.jena.model.*;
import org.apache.jena.vocabulary.XSD;

import java.util.stream.Stream;

/**
 * Created by @szuev on 23.05.2018.
 */
abstract class MapTestData5 extends AbstractMapTest {

    @Override
    public OntModel assembleSource() {
        OntModel m = createDataModel("source");
        String ns = m.getID().getURI() + "#";
        OntClass class1 = m.createOntClass(ns + "SrcClass1");
        OntDataProperty prop1 = m.createOntEntity(OntDataProperty.class, ns + "srcDataProperty1");
        OntDataProperty prop2 = m.createOntEntity(OntDataProperty.class, ns + "srcDataProperty2");
        OntDataRange.Named xdouble = m.getDatatype(XSD.xdouble);
        prop1.addDomain(class1);
        prop1.addRange(xdouble);
        prop2.addDomain(class1);
        prop2.addRange(xdouble);

        // data
        OntIndividual.Named individual1 = class1.createIndividual(ns + "A");
        OntIndividual.Named individual2 = class1.createIndividual(ns + "B");
        // warning: bug (in jena?) xdouble.createLiteral(1) produces wrong result. fix in ont-api?
        individual1.addProperty(prop1, xdouble.createLiteral("1.0"));
        individual2.addProperty(prop2, xdouble.createLiteral(String.valueOf(Math.E)));
        return m;
    }

    @Override
    public OntModel assembleTarget() {
        OntModel m = createDataModel("target");
        String ns = m.getID().getURI() + "#";
        OntClass clazz = m.createOntClass(ns + "DstClass1");
        OntDataRange xdouble = m.getDatatype(XSD.xdouble);
        OntDataRange xstring = m.getDatatype(XSD.xstring);
        Stream.of("dstDataProperty1", "dstDataProperty2").forEach(s -> {
            OntDataProperty p = m.createOntEntity(OntDataProperty.class, ns + s);
            p.addDomain(clazz);
            p.addRange(xdouble);
        });
        m.createOntEntity(OntDataProperty.class, ns + "dstDataProperty3")
                .addDomain(clazz)
                .addRange(xstring);
        return m;
    }

    @Override
    public String getDataNameSpace() {
        return getNameSpace(MapTestData5.class);
    }
}

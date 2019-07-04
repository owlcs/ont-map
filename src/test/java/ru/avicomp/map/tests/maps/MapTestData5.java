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

import org.apache.jena.vocabulary.XSD;
import ru.avicomp.ontapi.jena.model.*;

import java.util.stream.Stream;

/**
 * Created by @szuev on 23.05.2018.
 */
abstract class MapTestData5 extends AbstractMapTest {

    @Override
    public OntGraphModel assembleSource() {
        OntGraphModel m = createDataModel("source");
        String ns = m.getID().getURI() + "#";
        OntClass class1 = m.createOntEntity(OntClass.class, ns + "SrcClass1");
        OntNDP prop1 = m.createOntEntity(OntNDP.class, ns + "srcDataProperty1");
        OntNDP prop2 = m.createOntEntity(OntNDP.class, ns + "srcDataProperty2");
        OntDT xdouble = m.getOntEntity(OntDT.class, XSD.xdouble);
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
    public OntGraphModel assembleTarget() {
        OntGraphModel m = createDataModel("target");
        String ns = m.getID().getURI() + "#";
        OntClass clazz = m.createOntEntity(OntClass.class, ns + "DstClass1");
        OntDT xdouble = m.getOntEntity(OntDT.class, XSD.xdouble);
        OntDT xstring = m.getOntEntity(OntDT.class, XSD.xstring);
        Stream.of("dstDataProperty1", "dstDataProperty2").forEach(s -> {
            OntNDP p = m.createOntEntity(OntNDP.class, ns + s);
            p.addDomain(clazz);
            p.addRange(xdouble);
        });
        m.createOntEntity(OntNDP.class, ns + "dstDataProperty3")
                .addDomain(clazz)
                .addRange(xstring);
        return m;
    }

    @Override
    public String getDataNameSpace() {
        return getNameSpace(MapTestData5.class);
    }
}

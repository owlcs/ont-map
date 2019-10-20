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

package ru.avicomp.map.tests.maps;

import org.apache.jena.vocabulary.XSD;
import ru.avicomp.ontapi.jena.model.*;

/**
 * Created by @szuev on 10.05.2018.
 */
abstract class MapTestData3 extends AbstractMapTest {

    @Override
    public OntGraphModel assembleSource() {
        OntGraphModel m = createDataModel("people");
        String ns = m.getID().getURI() + "#";
        OntClass person = m.createOntEntity(OntClass.class, ns + "Person");
        OntDT xsdString = m.getOntEntity(OntDT.class, XSD.xstring);
        OntDT xsdBoolean = m.getOntEntity(OntDT.class, XSD.xboolean);

        OntNDP address = createDataProperty(m, "address", person, xsdString);
        OntNDP firstName = createDataProperty(m, "first-name", person, xsdString);
        OntNDP secondName = createDataProperty(m, "second-name", person, xsdString);
        OntNDP middleName = createDataProperty(m, "middle-name", person, xsdString);
        OntNDP gender = createDataProperty(m, "gender", person, xsdBoolean);

        // data:
        person.createIndividual(ns + "Person-1")
                .addAssertion(firstName, xsdString.createLiteral("Bartholomew"))
                .addAssertion(secondName, xsdString.createLiteral("Stotch"))
                .addAssertion(middleName, xsdString.createLiteral("Reuel"))
                .addAssertion(gender, xsdBoolean.createLiteral(Boolean.FALSE.toString()))
                .addAssertion(address, xsdString.createLiteral("EverGreen, 112, Springfield, Avalon, OZ"));
        person.createIndividual(ns + "Person-2")
                .addAssertion(firstName, xsdString.createLiteral("Matthew"))
                .addAssertion(secondName, xsdString.createLiteral("Scotch"))
                .addAssertion(middleName, xsdString.createLiteral("Pavlovich"))
                .addAssertion(gender, xsdBoolean.createLiteral(Boolean.TRUE.toString()))
                .addAssertion(address, xsdString.createLiteral("Oxford Rd, Manchester M13 9PL, GB"));
        //todo:
        return m;
    }

    private static OntNDP createDataProperty(OntGraphModel m, String name, OntClass domain, OntDT range) {
        OntNDP res = m.createOntEntity(OntNDP.class, m.getID().getURI() + "#" + name);
        res.addDomain(domain);
        if (range != null)
            res.addRange(range);
        return res;
    }

    @Override
    public OntGraphModel assembleTarget() {
        OntGraphModel m = createDataModel("contacts");
        String ns = m.getID().getURI() + "#";
        OntClass contact = m.createOntEntity(OntClass.class, ns + "Contact");
        OntClass address = m.createOntEntity(OntClass.class, ns + "Address");
        OntNDP fullName = createDataProperty(m, "full-name", contact, null);

        OntNOP hasAddress = m.createOntEntity(OntNOP.class, ns + "contact-address");
        hasAddress.addRange(address);
        hasAddress.addDomain(contact);

        // todo:
        return m;
    }

    @Override
    public String getDataNameSpace() {
        return getNameSpace(MapTestData3.class);
    }
}

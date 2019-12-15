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

/**
 * Created by @szuev on 10.05.2018.
 */
abstract class MapTestData3 extends AbstractMapTest {

    @Override
    public OntModel assembleSource() {
        OntModel m = createDataModel("people");
        String ns = m.getID().getURI() + "#";
        OntClass person = m.createOntClass(ns + "Person");
        OntDataRange.Named xsdString = m.getDatatype(XSD.xstring);
        OntDataRange.Named xsdBoolean = m.getDatatype(XSD.xboolean);

        OntDataProperty address = createDataProperty(m, "address", person, xsdString);
        OntDataProperty firstName = createDataProperty(m, "first-name", person, xsdString);
        OntDataProperty secondName = createDataProperty(m, "second-name", person, xsdString);
        OntDataProperty middleName = createDataProperty(m, "middle-name", person, xsdString);
        OntDataProperty gender = createDataProperty(m, "gender", person, xsdBoolean);

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

    private static OntDataProperty createDataProperty(OntModel m, String name, OntClass domain, OntDataRange range) {
        OntDataProperty res = m.createOntEntity(OntDataProperty.class, m.getID().getURI() + "#" + name);
        res.addDomain(domain);
        if (range != null)
            res.addRange(range);
        return res;
    }

    @Override
    public OntModel assembleTarget() {
        OntModel m = createDataModel("contacts");
        String ns = m.getID().getURI() + "#";
        OntClass contact = m.createOntClass(ns + "Contact");
        OntClass address = m.createOntClass(ns + "Address");
        OntDataProperty fullName = createDataProperty(m, "full-name", contact, null);

        OntObjectProperty hasAddress = m.createObjectProperty(ns + "contact-address");
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

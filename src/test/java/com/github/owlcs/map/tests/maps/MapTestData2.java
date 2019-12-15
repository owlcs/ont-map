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

import com.github.owlcs.map.MapManager;
import com.github.owlcs.map.MapModel;
import com.github.owlcs.map.utils.TestUtils;
import com.github.owlcs.ontapi.jena.model.*;
import org.apache.jena.vocabulary.XSD;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Stream;

/**
 * Created by @szuev on 01.05.2018.
 */
@SuppressWarnings("WeakerAccess")
abstract class MapTestData2 extends AbstractMapTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(MapTestData2.class);
    // test data:
    static final String DATA_EMAIL_JANE = "Jeanette@SuperMail.ts";
    static final String DATA_FIRST_NAME_JANE = "Jaanette";
    static final String DATA_EMAIL_JHON = "jhon-doe-xxxx@x-x-x.mail.org";
    static final String DATA_SKYPE_JHON = "jhon-skype";
    static final String DATA_FIRST_NAME_JHON = "Jhon";
    static final String DATA_SECOND_NAME_JHON = "Doe";
    static final String DATA_PHONE_BOB_LONG = Long.valueOf(96_322_09_43_034L).toString();
    static final String DATA_PHONE_BOB = DATA_PHONE_BOB_LONG;
    static final String DATA_EMAIL_BOB = "bob@x-email.com";
    static final String DATA_ADDRESS_BOB = "1313 Disneyland Dr, Anaheim, CA 92802, USA";
    static final String DATA_ADDRESS_KARL = "Highgate Cemetery, London, UK";
    static final String DATA_FIRST_NAME_BOB = "Mr. Bob";

    @Test
    public void testInference() {
        LOGGER.info("Assembly models.");
        OntModel s = assembleSource();
        TestUtils.debug(s);
        OntModel t = assembleTarget();
        TestUtils.debug(t);
        MapManager manager = manager();
        MapModel m = assembleMapping(manager, s, t);
        TestUtils.debug(m);

        LOGGER.info("Run inference.");
        manager.getInferenceEngine(m).run(s, t);
        TestUtils.debug(t);

        LOGGER.info("Validate.");
        validate(t);
        commonValidate(t);
    }

    public abstract void validate(OntModel result);

    @Override
    public OntModel assembleSource() {
        OntModel m = createDataModel("contacts");
        String schemaNS = m.getID().getURI() + "#";
        String dataNS = m.getID().getURI() + "/data#";
        m.setNsPrefix("data", dataNS);

        OntDataProperty firstName = m.createDataProperty(schemaNS + "firstName");
        OntDataProperty secondName = m.createDataProperty(schemaNS + "secondName");
        OntDataProperty age = m.createDataProperty(schemaNS + "age");
        OntDataProperty address = m.createDataProperty(schemaNS + "address");
        OntDataRange.Named email = m.createDatatype(schemaNS + "email");
        OntDataRange.Named phone = m.createDatatype(schemaNS + "phone");
        OntDataRange.Named skype = m.createDatatype(schemaNS + "skype");
        OntDataProperty contactInfo = m.createOntEntity(OntDataProperty.class, schemaNS + "info");
        OntClass contact = m.createOntClass(schemaNS + "Contact");
        OntClass person = m.createOntClass(schemaNS + "Person");
        OntObjectProperty.Named hasContact = m.createObjectProperty(schemaNS + "contact");

        firstName.addDomain(person);
        secondName.addDomain(person);
        age.addDomain(person);
        age.addRange(m.getDatatype(XSD.xint));

        // link between classes
        hasContact.addDomain(person);
        hasContact.addRange(contact);

        address.addDomain(contact);
        contactInfo.addDomain(contact);

        contactInfo.addRange(email);
        contactInfo.addRange(phone);
        contactInfo.addRange(skype);

        // Bob's data:
        OntIndividual.Named bobContacts = contact.createIndividual(dataNS + "bobs");
        bobContacts.addAssertion(contactInfo, email.createLiteral(DATA_EMAIL_BOB));
        bobContacts.addAssertion(contactInfo, phone.createLiteral(DATA_PHONE_BOB_LONG));
        bobContacts.addAssertion(address, m.createLiteral(DATA_ADDRESS_BOB));
        person.createIndividual(dataNS + "Bob")
                .addAssertion(age, m.createTypedLiteral(42))
                .addAssertion(hasContact, bobContacts)
                .addAssertion(firstName, m.createLiteral(DATA_FIRST_NAME_BOB));

        // Jhon's data
        person.createIndividual(dataNS + "Jhon")
                .addAssertion(age, m.createTypedLiteral(33))
                .addAssertion(hasContact, contact.createIndividual(dataNS + "jhons")
                        .addAssertion(contactInfo, skype.createLiteral(DATA_SKYPE_JHON))
                        .addAssertion(contactInfo, email.createLiteral(DATA_EMAIL_JHON)))
                .addAssertion(firstName, m.createLiteral(DATA_FIRST_NAME_JHON))
                .addAssertion(secondName, m.createLiteral(DATA_SECOND_NAME_JHON));

        // Jane's data
        person.createIndividual(dataNS + "Jane")
                .addAssertion(age, m.createTypedLiteral(22))
                .addAssertion(hasContact, contact.createIndividual(dataNS + "jane-contacts")
                        .addAssertion(contactInfo, email.createLiteral(DATA_EMAIL_JANE)))
                .addAssertion(firstName, m.createLiteral(DATA_FIRST_NAME_JANE, "en"));

        // Karl's data
        person.createIndividual(dataNS + "Karl")
                .addAssertion(age, m.createTypedLiteral(120))
                .addAssertion(hasContact, contact.createIndividual(dataNS + "karls")
                        .addAssertion(address, m.createLiteral(DATA_ADDRESS_KARL)));
        return m;
    }

    @Override
    public OntModel assembleTarget() {
        OntModel m = createDataModel("users");
        String ns = m.getID().getURI() + "#";
        OntClass user = m.createOntClass(ns + "User");
        OntDataRange string = m.getDatatype(XSD.xstring);
        Stream.of("user-name", "user-age", "user-address", "email", "phone", "skype").forEach(s -> {
            OntDataProperty p = m.createOntEntity(OntDataProperty.class, ns + s);
            p.addRange(string);
            p.addDomain(user);
        });
        return m;
    }

    @Override
    public String getDataNameSpace() {
        return getNameSpace(MapTestData2.class);
    }
}

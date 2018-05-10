package ru.avicomp.map.tests;

import org.apache.jena.vocabulary.XSD;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.map.Managers;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.MapModel;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.model.*;

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
    static final Long DATA_PHONE_BOB_LONG = 96_322_09_43_034L;
    static final String DATA_PHONE_BOB = DATA_PHONE_BOB_LONG.toString();
    static final String DATA_EMAIL_BOB = "bob@x-email.com";
    static final String DATA_ADDRESS_BOB = "1313 Disneyland Dr, Anaheim, CA 92802, USA";
    static final String DATA_ADDRESS_KARL = "Highgate Cemetery, London, UK";
    static final String DATA_FIRST_NAME_BOB = "Mr. Bob";

    @Test
    public void testInference() {
        LOGGER.info("Assembly models.");
        OntGraphModel s = assembleSource();
        TestUtils.debug(s);
        OntGraphModel t = assembleTarget();
        TestUtils.debug(t);
        MapManager manager = Managers.getMapManager();
        MapModel m = assembleMapping(manager, s, t);
        TestUtils.debug(m);

        LOGGER.info("Run inference.");
        manager.getInferenceEngine().run(m, s, t);
        TestUtils.debug(t);

        LOGGER.info("Validate.");
        validate(t);
    }

    public abstract void validate(OntGraphModel result);

    @Override
    public OntGraphModel assembleSource() {
        OntGraphModel m = createDataModel("contacts");
        String schemaNS = m.getID().getURI() + "#";
        String dataNS = m.getID().getURI() + "/data#";
        m.setNsPrefix("data", dataNS);

        OntNDP firstName = m.createOntEntity(OntNDP.class, schemaNS + "firstName");
        OntNDP secondName = m.createOntEntity(OntNDP.class, schemaNS + "secondName");
        OntNDP age = m.createOntEntity(OntNDP.class, schemaNS + "age");
        OntNDP address = m.createOntEntity(OntNDP.class, schemaNS + "address");
        OntDT email = m.createOntEntity(OntDT.class, schemaNS + "email");
        OntDT phone = m.createOntEntity(OntDT.class, schemaNS + "phone");
        OntDT skype = m.createOntEntity(OntDT.class, schemaNS + "skype");
        OntNDP contactInfo = m.createOntEntity(OntNDP.class, schemaNS + "info");
        OntClass contact = m.createOntEntity(OntClass.class, schemaNS + "Contact");
        OntClass person = m.createOntEntity(OntClass.class, schemaNS + "Person");
        OntNOP hasContact = m.createOntEntity(OntNOP.class, schemaNS + "contact");

        firstName.addDomain(person);
        secondName.addDomain(person);
        age.addDomain(person);
        age.addRange(m.getOntEntity(OntDT.class, XSD.xint));

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
    public OntGraphModel assembleTarget() {
        OntGraphModel m = createDataModel("users");
        String ns = m.getID().getURI() + "#";
        OntClass user = m.createOntEntity(OntClass.class, ns + "User");
        OntDT string = m.getOntEntity(OntDT.class, XSD.xstring);
        Stream.of("user-name", "user-age", "user-address", "email", "phone", "skype").forEach(s -> {
            OntNDP p = m.createOntEntity(OntNDP.class, ns + s);
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

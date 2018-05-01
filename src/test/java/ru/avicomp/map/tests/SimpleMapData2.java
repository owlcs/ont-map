package ru.avicomp.map.tests;

import org.apache.jena.vocabulary.XSD;
import org.junit.Test;
import ru.avicomp.ontapi.jena.model.*;

import java.util.stream.Stream;

/**
 * Created by @szuev on 01.05.2018.
 */
@SuppressWarnings("WeakerAccess")
abstract class SimpleMapData2 extends AbstractMapTest {
    // test data:
    static final String DATA_EMAIL_JANE = "Jeanette@SuperMail.ts";
    static final String DATA_EMAIL_JHON = "jhon-doe-xxxx@x-x-x.mail.org";
    static final String DATA_SKYPE_JHON = "jhon-skype";
    static final Long DATA_PHONE_BOB_LONG = 96_322_09_43_034L;
    static final String DATA_PHONE_BOB = DATA_PHONE_BOB_LONG.toString();
    static final String DATA_EMAIL_BOB = "bob@x-email.com";

    @Test
    public abstract void testInference();

    @Override
    public OntGraphModel assembleSource() {
        OntGraphModel m = createModel("contacts");
        String schemaNS = m.getID().getURI() + "#";
        String dataNS = m.getID().getURI() + "/data#";
        m.setNsPrefix("data", dataNS);

        OntNDP firstName = m.createOntEntity(OntNDP.class, schemaNS + "firstName");
        OntNDP secondName = m.createOntEntity(OntNDP.class, schemaNS + "secondName");
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

        // link between classes
        hasContact.addDomain(person);
        hasContact.addRange(contact);

        address.addDomain(contact);
        contactInfo.addDomain(contact);

        contactInfo.addRange(email);
        contactInfo.addRange(phone);
        contactInfo.addRange(skype);

        // data:
        OntIndividual.Named bobContacts = contact.createIndividual(dataNS + "bobs");
        bobContacts.addAssertion(contactInfo, email.createLiteral(DATA_EMAIL_BOB));
        bobContacts.addAssertion(contactInfo, phone.createLiteral(DATA_PHONE_BOB_LONG));
        person.createIndividual(dataNS + "Bob")
                .addAssertion(hasContact, bobContacts)
                .addAssertion(firstName, m.createLiteral("Mr. Bob"))
                .addAssertion(address, m.createLiteral("1313 Disneyland Dr, Anaheim, CA 92802, USA"));

        person.createIndividual(dataNS + "Jhon")
                .addAssertion(hasContact, contact.createIndividual(dataNS + "jhons")
                        .addAssertion(contactInfo, skype.createLiteral(DATA_SKYPE_JHON))
                        .addAssertion(contactInfo, email.createLiteral(DATA_EMAIL_JHON)))
                .addAssertion(firstName, m.createLiteral("Jhon"))
                .addAssertion(secondName, m.createLiteral("Doe"));

        person.createIndividual(dataNS + "Jane")
                .addAssertion(hasContact, contact.createIndividual(dataNS + "jane-contacts")
                        .addAssertion(contactInfo, email.createLiteral(DATA_EMAIL_JANE)))
                .addAssertion(firstName, m.createLiteral("Jeanette", "en"));
        return m;
    }

    @Override
    public OntGraphModel assembleTarget() {
        OntGraphModel m = createModel("users");
        String ns = m.getID().getURI() + "#";
        OntClass user = m.createOntEntity(OntClass.class, ns + "User");
        OntDT string = m.getOntEntity(OntDT.class, XSD.xstring);
        Stream.of("email", "phone", "skype", "user-name").forEach(s -> {
            OntNDP p = m.createOntEntity(OntNDP.class, ns + s);
            p.addRange(string);
            p.addDomain(user);
        });
        return m;
    }
}

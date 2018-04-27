package ru.avicomp.map.tests;

import org.apache.jena.vocabulary.XSD;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.MapModel;
import ru.avicomp.ontapi.jena.model.*;

import java.util.stream.Stream;

/**
 * TODO: not ready
 * Created by @szuev on 26.04.2018.
 */
public class ConditionalMapTest extends AbstractMapTest {
    @Override
    public MapModel assembleMapping(MapManager manager, OntGraphModel src, OntGraphModel dst) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public OntGraphModel assembleSource() {
        OntGraphModel m = createModel("contacts");
        String schemaNS = m.getID().getURI() + "#";
        String dataNS = m.getID().getURI() + "/data#";
        m.setNsPrefix("data", dataNS);

        OntDT email = m.createOntEntity(OntDT.class, schemaNS + "email");
        OntDT phone = m.createOntEntity(OntDT.class, schemaNS + "phone");
        OntDT skype = m.createOntEntity(OntDT.class, schemaNS + "skype");
        OntNDP contactInfo = m.createOntEntity(OntNDP.class, schemaNS + "info");
        OntClass contact = m.createOntEntity(OntClass.class, schemaNS + "Contact");
        OntClass person = m.createOntEntity(OntClass.class, schemaNS + "Person");
        OntNOP hasContact = m.createOntEntity(OntNOP.class, schemaNS + "contact");

        hasContact.addDomain(person);
        hasContact.addRange(contact);

        contactInfo.addDomain(contact);
        contactInfo.addRange(email);
        contactInfo.addRange(phone);
        contactInfo.addRange(skype);

        // data:
        OntIndividual.Named bobContacts = contact.createIndividual(dataNS + "bobs");
        bobContacts.addAssertion(contactInfo, email.createLiteral("bob@x-email.com"));
        bobContacts.addAssertion(contactInfo, phone.createLiteral("+96 32 0943 03454"));
        person.createIndividual(dataNS + "Bob").addAssertion(hasContact, bobContacts);

        person.createIndividual(dataNS + "Jhon").addAssertion(hasContact,
                contact.createIndividual(dataNS + "jhons").addAssertion(contactInfo,
                        skype.createLiteral("jhon-skype")));
        System.out.println(m.statements().count());
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

    public static void main(String... args) {
        ConditionalMapTest t = new ConditionalMapTest();
        t.assembleSource().write(System.out, "ttl");
        System.out.println("-----------");
        t.assembleTarget().write(System.out, "ttl");
    }
}

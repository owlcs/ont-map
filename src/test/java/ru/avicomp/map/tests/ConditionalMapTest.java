package ru.avicomp.map.tests;

import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.XSD;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPINMAP;
import org.topbraid.spin.vocabulary.SPINMAPL;
import ru.avicomp.map.*;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.model.*;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * TODO: not ready
 * Created by @szuev on 26.04.2018.
 */
public class ConditionalMapTest extends AbstractMapTest {
    private final Logger LOGGER = LoggerFactory.getLogger(ConditionalMapTest.class);

    @Test
    public void testInference() {
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

        Assert.assertEquals(3, t.listNamedIndividuals().count());
    }

    @Override
    public MapModel assembleMapping(MapManager manager, OntGraphModel src, OntGraphModel dst) {
        OntClass srcClass = TestUtils.findOntEntity(src, OntClass.class, "Contact");
        OntClass dstClass = TestUtils.findOntEntity(dst, OntClass.class, "User");
        Map<OntDT, OntNDP> propsMap = src.listDatatypes()
                .map(Resource::getLocalName)
                .collect(Collectors.toMap(
                        s -> TestUtils.findOntEntity(src, OntDT.class, s),
                        s -> TestUtils.findOntEntity(dst, OntNDP.class, s)));
        OntNDP sourceProperty = TestUtils.findOntEntity(src, OntNDP.class, "info");

        MapFunction.Call targetFunctionCall = manager.getFunction(SPINMAPL.composeURI.getURI())
                .createFunctionCall()
                .add(SPINMAPL.template.getURI(), dst.getID().getURI() + "#res-{?1}")
                .build();
        MapFunction eq = manager.getFunction(SP.eq.getURI());
        MapFunction datatype = manager.getFunction(SP.resource("datatype").getURI());
        MapFunction equals = manager.getFunction(SPINMAP.equals.getURI());

        MapModel res = manager.createMapModel();
        res.setID(getNameSpace() + "/map");
        Context context = res.createContext(srcClass, dstClass, targetFunctionCall);
        propsMap.forEach((sourceDatatype, targetProperty) -> {
            MapFunction.Call filter = eq.createFunctionCall().add(SP.arg1.getURI(), sourceDatatype.getURI())
                    .add(SP.arg2.getURI(), datatype.createFunctionCall().add(SP.arg1.getURI(), sourceProperty.getURI())).build();
            MapFunction.Call mapping = equals.createFunctionCall().add(SP.arg1.getURI(), sourceProperty.getURI()).build();
            context.addPropertyBridge(filter, mapping, targetProperty);
        });
        return res;
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
        bobContacts.addAssertion(contactInfo, phone.createLiteral(96_322_09_43_034L));
        person.createIndividual(dataNS + "Bob").addAssertion(hasContact, bobContacts);

        person.createIndividual(dataNS + "Jhon").addAssertion(hasContact,
                contact.createIndividual(dataNS + "jhons")
                        .addAssertion(contactInfo, skype.createLiteral("jhon-skype"))
                        .addAssertion(contactInfo, email.createLiteral("jhon-doe-xxxx@x-x-x.mail.org")));

        person.createIndividual(dataNS + "Jane").addAssertion(hasContact,
                contact.createIndividual(dataNS + "jane-contacts").addAssertion(contactInfo, email.createLiteral("Jeanette@SuperMail.ts")));
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

}

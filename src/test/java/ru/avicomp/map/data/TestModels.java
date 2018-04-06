package ru.avicomp.map.data;

import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDFS;
import ru.avicomp.map.OntObjects;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.model.*;
import ru.avicomp.ontapi.jena.vocabulary.RDF;
import ru.avicomp.ontapi.jena.vocabulary.XSD;
import ru.avicomp.ontapi.utils.ReadWriteUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by @szuev on 02.04.2018.
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class TestModels {
    private static final Instant TIMESTAMP = Instant.now();
    private static final String BASE_URI = "http://test.avc.ru/";

    private static final PrefixMapping STANDARD = PrefixMapping.Factory.create()
            .setNsPrefix("rdf", RDF.getURI())
            .setNsPrefix("rdfs", RDFS.getURI())
            .setNsPrefix("xsd", XSD.getURI())
            .setNsPrefix("owl", OWL.getURI()).lock();

    public static final OntGraphModel BEING_ONTOLOGY = createBeingOntology(BASE_URI + "beings");
    public static final OntGraphModel PERSON_ONTOLOGY = createPersonOntology(BASE_URI + "people");
    public static final OntGraphModel CITIZEN_ONTOLOGY = createCitizenOntology(BASE_URI + "citizens");
    public static final OntGraphModel USER_ONTOLOGY = createUserOntology(BASE_URI + "users");

    private static OntGraphModel createBeingOntology(String base) {
        OntGraphModel model = createModel(base, "The test ontology of beings");
        String ns = base + "#";

        OntClass ontClass = model.createOntEntity(OntClass.class, ns + Being.CLASS);
        OntObjects.setComment(ontClass, "This is the being's class.");
        OntObjects.setLabel(ontClass, "aBeing").addSubClassOf(OntObjects.owlThing(model));

        OntNDP name = model.createOntEntity(OntNDP.class, ns + Being.NAME);
        OntObjects.setComment(name, "name of the being");
        name.addRange(OntObjects.xsdString(model));
        name.addDomain(ontClass);
        return model;
    }

    private static OntGraphModel createPersonOntology(String base) {
        OntGraphModel model = createModel(base, "The test ontology of people");
        String ns = base + "#";

        OntClass owlThing = OntObjects.owlThing(model);
        OntDT xsdString = OntObjects.xsdString(model);

        OntClass personClass = model.createOntEntity(OntClass.class, ns + Contact.Person.CLASS);
        OntObjects.setComment(personClass, "This is the person's class.");
        OntObjects.setLabel(personClass, "aPerson").addSubClassOf(owlThing);

        OntNDP firstName = model.createOntEntity(OntNDP.class, ns + Contact.Person.FIRST_NAME);
        OntObjects.setComment(firstName, "the first name of person");
        firstName.addRange(xsdString);
        firstName.addDomain(personClass);

        OntNDP secondName = model.createOntEntity(OntNDP.class, ns + Contact.Person.SECOND_NAME);
        OntObjects.setComment(secondName, "the second name of person");
        secondName.addRange(xsdString);
        secondName.addDomain(personClass);

        OntNDP gender = model.createOntEntity(OntNDP.class, ns + Contact.Person.GENDER);
        OntObjects.setComment(gender, "gender, xsd:string");
        gender.addRange(xsdString);
        gender.addDomain(personClass);

        OntNDP address = model.createOntEntity(OntNDP.class, ns + Contact.Person.ADDRESS);
        OntObjects.setComment(address, "full address, xsd:string");
        address.addRange(xsdString);
        address.addDomain(personClass);

        OntNDP birthDate = model.createOntEntity(OntNDP.class, ns + Contact.Person.BIRTH_DATE);
        OntObjects.setComment(birthDate, "the birthday date in format yyyy-MM-dd, xsd:string");
        birthDate.addRange(xsdString);
        birthDate.addDomain(personClass);

        OntNDP info = model.createOntEntity(OntNDP.class, ns + Contact.Person.LOCATION);
        OntObjects.setComment(info, "location info in the form of rdf:List (which is not supported by OWL2)");
        // todo: OWL2 does not support custom lists:
        info.addProperty(RDFS.range, RDF.List);
        info.addDomain(personClass);

        OntClass contactClass = model.createOntEntity(OntClass.class, ns + Contact.CLASS);
        OntObjects.setComment(contactClass, "This is the contact's class.");
        OntObjects.setLabel(contactClass, "aContact");
        contactClass.addSubClassOf(owlThing);

        OntNDP contactInfo = model.createOntEntity(OntNDP.class, ns + Contact.INFO);
        OntObjects.setComment(contactInfo, "the contact info (email, home phone, mobile phone, skype id), xsd:string");
        contactInfo.addRange(xsdString);
        contactInfo.addDomain(contactClass);

        OntNDP contactType = model.createOntEntity(OntNDP.class, ns + Contact.TYPE);
        OntObjects.setComment(contactType, "the contact type, constant, could be only Email, Home Phone, Mobile phone or Skype id, xsd:string");
        contactType.addRange(xsdString);
        contactType.addDomain(contactClass);

        OntNOP contactPerson = OntObjects.createOWLObjectProperty(model, ns + Contact.PERSON);
        OntObjects.setComment(contactPerson, "the contact person (the reference to :Person class)");
        contactPerson.addRange(personClass);
        contactPerson.addDomain(contactClass);

        return model;
    }

    private static OntGraphModel createCitizenOntology(String base) {
        OntGraphModel model = createModel(base, "The test ontology for citizens");
        String ns = base + "#";

        OntClass owlThing = OntObjects.owlThing(model);
        OntDT xsdString = OntObjects.xsdString(model);
        OntDT xsdDate = OntObjects.xsdDate(model);
        OntDT xsdBoolean = OntObjects.xsdBoolean(model);
        OntDT xsdInt = OntObjects.xsdInt(model);

        OntClass mainClass = OntObjects.createOWLClass(model, ns + Citizen.CLASS);
        OntObjects.setComment(mainClass, "This is the citizen's class.");
        OntObjects.setLabel(mainClass, "aCitizen");
        mainClass.addSubClassOf(owlThing);

        OntNDP fullName = OntObjects.createOWLDataProperty(model, ns + Citizen.FULL_NAME);
        OntObjects.setComment(fullName, "full name of citizen");
        fullName.addRange(xsdString);
        fullName.addDomain(mainClass);

        OntNDP birthDate = OntObjects.createOWLDataProperty(model, ns + Citizen.BIRTH_DATE);
        OntObjects.setComment(birthDate, "the birthday, xsd:date");
        birthDate.addRange(xsdDate);
        birthDate.addDomain(mainClass);

        OntNDP gender = OntObjects.createOWLDataProperty(model, ns + Citizen.GENDER);
        OntObjects.setComment(gender, "gender, xsd:boolean");
        gender.addRange(xsdBoolean);
        gender.addDomain(mainClass);

        OntNDP location = OntObjects.createOWLDataProperty(model, ns + Citizen.LOCATION);
        OntObjects.setComment(location, "location info, xsd:string");
        location.addRange(xsdString);
        location.addDomain(mainClass);

        OntClass addressClass = OntObjects.createOWLClass(model, ns + Citizen.Address.CLASS);
        OntObjects.setComment(addressClass, "This is the address' class.");
        OntObjects.setLabel(addressClass, "aAddress");
        addressClass.addSubClassOf(owlThing);

        OntNOP address = OntObjects.createOWLObjectProperty(model, ns + Citizen.ADDRESS);
        OntObjects.setComment(address, "address property (the reference to :Address class)");
        address.addRange(addressClass);
        address.addDomain(mainClass);

        OntNDP country = OntObjects.createOWLDataProperty(model, ns + Citizen.Address.COUNTRY);
        OntObjects.setComment(country, "the country, xsd:string");
        country.addRange(xsdString);
        country.addDomain(addressClass);

        OntNDP region = OntObjects.createOWLDataProperty(model, ns + Citizen.Address.REGION);
        OntObjects.setComment(region, "region/state/distinct, xsd:string").addRange(xsdString);
        region.addDomain(addressClass);

        OntNDP city = OntObjects.createOWLDataProperty(model, ns + Citizen.Address.CITY);
        OntObjects.setComment(city, "city/suburb, xsd:string");
        city.addRange(xsdString);
        city.addDomain(addressClass);

        OntNDP postalCode = OntObjects.createOWLDataProperty(model, ns + Citizen.Address.POSTAL_CODE);
        OntObjects.setComment(postalCode, "the postal code, xsd:integer");
        postalCode.addRange(xsdInt);
        postalCode.addDomain(addressClass);

        OntNDP street = OntObjects.createOWLDataProperty(model, ns + Citizen.Address.STREET);
        OntObjects.setComment(street, "the street address, xsd:string");
        street.addRange(xsdString);
        street.addDomain(addressClass);

        return model;
    }

    private static OntGraphModel createUserOntology(String base) {
        OntGraphModel model = createModel(base, "The test ontology for users");
        String ns = base + "#";

        OntClass owlThing = OntObjects.owlThing(model);
        OntDT xsdString = OntObjects.xsdString(model);

        OntClass userClass = OntObjects.createOWLClass(model, ns + User.CLASS);
        OntObjects.setComment(userClass, "This is the user's class.");
        OntObjects.setLabel(userClass, "aUser");
        userClass.addSubClassOf(owlThing);

        OntNDP name = OntObjects.createOWLDataProperty(model, ns + User.NAME);
        OntObjects.setComment(name, "the name of user");
        name.addRange(xsdString);
        name.addDomain(userClass);

        OntNDP email = OntObjects.createOWLDataProperty(model, ns + User.EMAIL);
        OntObjects.setComment(email, "the email of user");
        email.addRange(xsdString);
        email.addDomain(userClass);

        OntNDP homePhone = OntObjects.createOWLDataProperty(model, ns + User.HOME_PHONE);
        OntObjects.setComment(homePhone, "the home phone of user");
        homePhone.addRange(xsdString);
        homePhone.addDomain(userClass);

        OntNDP mobilePhone = OntObjects.createOWLDataProperty(model, ns + User.MOBILE_PHONE);
        OntObjects.setComment(mobilePhone, "the mobile phone of user");
        mobilePhone.addRange(xsdString);
        mobilePhone.addDomain(userClass);

        OntNDP skype = OntObjects.createOWLDataProperty(model, ns + User.SKYPE_ID);
        OntObjects.setComment(skype, "the skype id of user");
        skype.addRange(xsdString);
        skype.addDomain(userClass);

        return model;
    }

    public static OntGraphModel createModel(String iri, String name, String description) {
        OntGraphModel res = OntModelFactory.createModel();
        res.setNsPrefixes(STANDARD);
        res.setNsPrefix(name, iri + "#");
        OntID id = res.setID(iri);
        id.setVersionIRI(iri + "#" + TIMESTAMP.getEpochSecond());
        id.addAnnotation(OntObjects.versionInfo(res), "version " + TIMESTAMP, null);
        if (description != null)
            id.addComment(description, null);
        return res;
    }

    private static OntGraphModel createModel(String iri, String description) {
        String name = iri.replaceFirst(".+/([^/]+)$", "$1");
        return createModel(iri, name, description);
    }

    public static class Citizen extends TestIndividual {
        public static final String CLASS = "Citizen";
        public static final String ADDRESS = "address";
        public static final String BIRTH_DATE = "birthDate";
        public static final String FULL_NAME = "fullName";
        public static final String GENDER = "gender";
        public static final String LOCATION = "currentLocation";

        public Citizen(String localName) {
            super(CLASS, localName);
        }

        public boolean hasAddress() {
            return hasProperty(ADDRESS);
        }

        public Address getAddress() {
            return (Address) properties.computeIfAbsent(ADDRESS, s -> new Address());
        }

        public void setLocation(String loc) {
            setProperty(LOCATION, loc);
        }

        public void setAddress(Address address) {
            setProperty(ADDRESS, address);
        }

        public void setBirthDate(LocalDate birthDate) {
            setProperty(BIRTH_DATE, birthDate);
        }

        public LocalDate getBirthDate() {
            return (LocalDate) getProperty(BIRTH_DATE);
        }

        public String getFullName() {
            return (String) getProperty(FULL_NAME);
        }

        public void setFullName(String fullName) {
            setProperty(FULL_NAME, fullName);
        }

        public void setGender(Boolean gender) {
            setProperty(GENDER, gender);
        }

        public static class Address extends TestIndividual {
            public static final String CLASS = "Address";
            public static final String CITY = "city";
            public static final String COUNTRY = "country";
            public static final String REGION = "region";
            public static final String STREET = "street";
            public static final String POSTAL_CODE = "postalCode";

            public Address(String localName) {
                super(CLASS, localName);
            }

            private Address() {
                this(null);
            }

            public void setCity(String city) {
                setProperty(CITY, city);
            }

            public String getCity() {
                return (String) getProperty(CITY);
            }

            public void setCountry(String country) {
                setProperty(COUNTRY, country);
            }

            public String getCountry() {
                return (String) getProperty(COUNTRY);
            }

            public void setRegion(String region) {
                setProperty(REGION, region);
            }

            public String getRegion() {
                return (String) getProperty(REGION);
            }

            public void setStreet(String street) {
                setProperty(STREET, street);
            }

            public String getStreet() {
                return (String) getProperty(STREET);
            }

            public void setPostalCode(Integer postalCode) {
                setProperty(POSTAL_CODE, postalCode);
            }

            public Integer getPostalCode() {
                return (Integer) getProperty(POSTAL_CODE);
            }
        }
    }

    public static class Contact extends TestIndividual {
        public static final String CLASS = "Contact";
        public static final String PERSON = "contactPerson";
        public static final String INFO = "contactInfo";
        public static final String TYPE = "contactType";

        public Contact(String localName) {
            super(CLASS, localName);
        }

        public void setContactInfo(String contactInfo) {
            setProperty(INFO, contactInfo);
        }

        public void setContactType(Type contactType) {
            setProperty(TYPE, contactType.getKey());
        }

        public String getContactInfo() {
            return (String) getProperty(INFO);
        }

        public Type getContactType() {
            String key = (String) getProperty(TYPE);
            for (Type t : Type.values()) {
                if (t.getKey().equalsIgnoreCase(key)) return t;
            }
            return null;
        }

        public void setPerson(Person person) {
            setProperty(PERSON, person);
            person.addDependency(this);
        }

        public static class Person extends TestIndividual {
            public static final String CLASS = "Person";
            public static final String ADDRESS = "address";
            public static final String BIRTH_DATE = "birthDate";
            public static final String FIRST_NAME = "firstName";
            public static final String SECOND_NAME = "secondName";
            public static final String GENDER = "gender";
            public static final String LOCATION = "location";

            public Set<Contact> getContacts() {
                return dependencies.stream().map(Contact.class::cast).collect(Collectors.toSet());
            }

            public void setLocation(Double x, Double y, Double z) {
                setList(LOCATION, Arrays.asList(x, y, z));
            }

            public List<Double> getLocation() {
                return hasLocation() ? getList(LOCATION).stream().map(Double.class::cast).collect(Collectors.toList()) : null;
            }

            public boolean hasLocation() {
                return hasProperty(LOCATION);
            }

            public Person(String localName) {
                super(CLASS, localName);
            }

            public void setAddress(String address) {
                setProperty(ADDRESS, address);
            }

            public void setBirthDate(String birthDate) {
                setProperty(BIRTH_DATE, birthDate);
            }

            public String getBirthDate() {
                return (String) getProperty(BIRTH_DATE);
            }

            public String getAddress() {
                return (String) getProperty(ADDRESS);
            }

            public void setFirstName(String firstName) {
                setProperty(FIRST_NAME, firstName);
            }

            public String getFirstName() {
                return (String) getProperty(FIRST_NAME);
            }

            public void setSecondName(String secondName) {
                setProperty(SECOND_NAME, secondName);
            }

            public String getSecondName() {
                return (String) getProperty(SECOND_NAME);
            }

            public void setGender(String gender) {
                setProperty(GENDER, gender);
            }
        }

        public enum Type {
            HOME_PHONE("Home Phone"),
            MOBILE_PHONE("Mobile Phone"),
            SKYPE_ID("Skype"),
            EMAIL("Email"),;

            public String getKey() {
                return key;
            }

            String key;

            Type(String s) {
                this.key = s;
            }
        }
    }

    public static class User extends TestIndividual {
        public static final String CLASS = "User";
        public static final String NAME = "userName";
        public static final String EMAIL = "email";
        public static final String MOBILE_PHONE = "mobilePhone";
        public static final String HOME_PHONE = "homePhone";
        public static final String SKYPE_ID = "skype";

        public User(String localName) {
            super(CLASS, localName);
        }

        public void setName(String name) {
            setProperty(NAME, name);
        }

        public void setEmail(String email) {
            setProperty(EMAIL, email);
        }

        public void setMobilePhone(String phone) {
            setProperty(MOBILE_PHONE, phone);
        }

        public void setHomePhone(String phone) {
            setProperty(HOME_PHONE, phone);
        }

        public void setSkypeId(String id) {
            setProperty(SKYPE_ID, id);
        }
    }

    public static class Being extends TestIndividual {
        public static final String CLASS = "Being";
        public static final String NAME = "name";

        public Being(String localName) {
            super(CLASS, localName);
        }

        public void setName(String name) {
            setProperty(NAME, name);
        }
    }

    public static void main(String... args) {
        ReadWriteUtils.print(BEING_ONTOLOGY);
        System.out.println("================");
        ReadWriteUtils.print(PERSON_ONTOLOGY);
        System.out.println("================");
        ReadWriteUtils.print(CITIZEN_ONTOLOGY);
        System.out.println("================");
        ReadWriteUtils.print(USER_ONTOLOGY);
    }

}

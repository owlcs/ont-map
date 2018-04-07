package ru.avicomp.map.data;

import org.apache.commons.lang3.builder.CompareToBuilder;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.datatypes.xsd.XSDDateTime;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDFS;
import org.junit.Assert;
import ru.avicomp.map.utils.OntObjects;
import ru.avicomp.ontapi.jena.model.*;
import ru.avicomp.ontapi.jena.utils.Iter;
import ru.avicomp.ontapi.jena.utils.Models;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Java representation of OWL Named ({@code owl:NamedIndividual}) and Anonymous individual.
 * This class allows to read/write from/to {@link ru.avicomp.ontapi.jena.model.OntGraphModel Jena OWL2 model}.
 * For tests purposes only.
 * TODO: updated copy-paste from old ont-map version.
 * <p>
 * Created by @szuev on 15.07.2016.
 */
public class TestIndividual implements Comparable<TestIndividual> {
    private final String localName;
    private final String type;
    protected final Map<String, Object> properties;
    protected Set<TestIndividual> dependencies = new HashSet<>();

    private String label;
    private String comment;

    public TestIndividual(String type, String localName) {
        Assert.assertNotNull("Type is mandatory", type);
        this.type = type;
        this.localName = localName;
        this.properties = new TreeMap<>();
    }

    public String getLocalName() {
        return localName;
    }

    public boolean isAnon() {
        return localName == null;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public void addDependency(TestIndividual t) {
        dependencies.add(t);
    }

    public void setList(String key, List<?> list) {
        setProperty(key, list);
    }

    public List<?> getList(String key) {
        Object value = getProperty(key);
        if (value == null) return null;
        Assert.assertTrue("Not list (key=" + key + ", value=" + value + ")", value instanceof List);
        return (List) value;
    }

    public void setProperty(String key, Object o) {
        properties.put(key, o);
    }

    public boolean hasProperty(String key) {
        return properties.containsKey(key);
    }

    public Object getProperty(String key) {
        return properties.get(key);
    }

    public String getType() {
        return type;
    }

    public OntClass getOWLClass(OntGraphModel model) {
        String t = getType();
        List<OntClass> res = model.ontEntities(OntClass.class).filter(s -> t.equals(s.getLocalName())).collect(Collectors.toList());
        Assert.assertEquals(1, res.size());
        return res.get(0);
    }

    public static String makeIRI(OntGraphModel model, String localName) {
        String iri = model.getID().getURI();
        return iri.replaceFirst("/$", "") + "#" + localName;
    }

    public boolean containsNulls() {
        if (properties.isEmpty()) return true;
        for (String key : properties.keySet()) {
            if (properties.get(key) == null) return true;
        }
        return false;
    }

    public OntIndividual writeIndividual(OntGraphModel model) {
        OntClass clazz = getOWLClass(model);
        OntIndividual res = isAnon() ? clazz.createIndividual() : clazz.createIndividual(makeIRI(model, localName));
        if (label != null) {
            res.addLabel(label, null);
        }
        if (comment != null) {
            res.addComment(comment, null);
        }
        for (String key : properties.keySet()) {
            String propertyURI = makeIRI(model, key);
            Object value = properties.get(key);
            if (value == null) continue;
            if (List.class.isInstance(value)) { // only literals list here. todo: such kind of property assertion is not supported by OWL2
                List<?> list = (List) value;
                Iterator<Literal> literals = list.stream().map(TestIndividual::createLiteral).iterator();
                OntNDP p = OntObjects.createOWLDataProperty(model, propertyURI);
                res.addProperty(p, model.createList(literals));
            } else if (TestIndividual.class.isInstance(value)) { // treat as object property
                TestIndividual another = (TestIndividual) value;
                OntNOP p = OntObjects.createOWLObjectProperty(model, propertyURI);
                OntIndividual toAdd = another.writeIndividual(model);
                res.addProperty(p, toAdd);
            } else {
                OntNDP p = OntObjects.createOWLDataProperty(model, propertyURI);
                res.addLiteral(p, createLiteral(value));
            }
        }
        dependencies.forEach(d -> d.writeIndividual(model));
        return res;
    }

    public static List<TestIndividual> readIndividuals(OntClass type) {
        return type.individuals().map(TestIndividual::readIndividual).collect(Collectors.toList());
    }

    private static final Set<Property> INFO_PROPERTIES = Stream.of(RDF.type, RDFS.label, RDFS.comment).collect(Iter.toUnmodifiableSet());

    private static TestIndividual readIndividual(OntIndividual individual) {
        String localName = individual.isAnon() ? null : individual.getLocalName();
        List<OntClass> types = individual.classes().filter(RDFNode::isURIResource).map(s -> s.as(OntClass.class)).collect(Collectors.toList());
        Assert.assertEquals("Incorrect number of OWL-Classes", 1, types.size());
        TestIndividual res = new TestIndividual(types.get(0).getLocalName(), localName);
        res.setLabel(OntObjects.getLabel(individual));
        res.setComment(OntObjects.getComment(individual));
        individual.statements().filter(s -> !INFO_PROPERTIES.contains(s.getPredicate())).forEach(new Consumer<OntStatement>() {
            @Override
            public void accept(OntStatement statement) {
                Property p = statement.getPredicate();
                RDFNode node = statement.getObject();
                Object value;
                if (node.isLiteral()) {
                    value = getLiteralValue(node.asLiteral());
                } else if (node.canAs(RDFList.class)) {
                    RDFList list = node.as(RDFList.class);
                    value = list.asJavaList().stream().map(n -> n.isLiteral() ? getLiteralValue(n.asLiteral()) : null).collect(Collectors.toList());
                } else {
                    OntIndividual sub;
                    if (node.isAnon()) {
                        sub = Models.asAnonymousIndividual(node);
                    } else {
                        List<OntIndividual> individuals = individual.getModel().ontObjects(OntIndividual.class).filter(node::equals).collect(Collectors.toList());
                        Assert.assertEquals("Incorrect number of sub individuals by resource " + node, 1, individuals.size());
                        sub = individuals.get(0);
                    }
                    value = readIndividual(sub);
                }
                res.setProperty(p.getLocalName(), value);
            }
        });
        return res;
    }

    public static Object getLiteralValue(Literal literal) {
        // todo: create and register custom Datatypes
        Object res = literal.getValue();
        if (XSDDateTime.class.isInstance(res)) {
            RDFDatatype type = literal.getDatatype();
            XSDDateTime dateTime = (XSDDateTime) res;
            Calendar c = dateTime.asCalendar();
            ZonedDateTime zonedDateTime = c.toInstant().atZone(c.getTimeZone().toZoneId()); // always GMT? whatever
            if (XSDDatatype.XSDdateTime.equals(type)) {
                return zonedDateTime.toLocalDateTime();
            }
            if (XSDDatatype.XSDdate.equals(type)) {
                return zonedDateTime.toLocalDate();
            }
            if (XSDDatatype.XSDtime.equals(type)) {
                return zonedDateTime.toLocalTime();
            }
        }
        return res;
    }

    public static Literal createLiteral(Object object) {
        if (LocalDate.class.isInstance(object)) {
            return ResourceFactory.createTypedLiteral(DateTimeFormatter.ISO_DATE.format((TemporalAccessor) object), XSDDatatype.XSDdate);
        }
        if (LocalTime.class.isInstance(object)) {
            return ResourceFactory.createTypedLiteral(DateTimeFormatter.ISO_TIME.format((TemporalAccessor) object), XSDDatatype.XSDtime);
        }
        if (LocalDateTime.class.isInstance(object)) {
            return ResourceFactory.createTypedLiteral(DateTimeFormatter.ISO_DATE_TIME.format((TemporalAccessor) object), XSDDatatype.XSDdateTime);
        }
        return ResourceFactory.createTypedLiteral(object);
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{").append(type).append("::").append(localName).append("}");
        if (label != null) {
            sb.append(", label='").append(label).append("'");
        }
        if (comment != null) {
            sb.append(", comment='").append(comment).append("'");
        }
        sb.append(properties);
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        // ignore the class type info, requiring only that it is TestIndividual:
        if (!TestIndividual.class.isInstance(o)) return false;
        TestIndividual that = (TestIndividual) o;
        return Objects.equals(type, that.type) &&
                Objects.equals(localName, that.localName) &&
                Objects.equals(properties, that.properties) &&
                Objects.equals(label, that.label) &&
                Objects.equals(comment, that.comment);

    }

    @Override
    public int hashCode() {
        int result = localName != null ? localName.hashCode() : 0;
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (properties != null ? properties.hashCode() : 0);
        result = 31 * result + (label != null ? label.hashCode() : 0);
        result = 31 * result + (comment != null ? comment.hashCode() : 0);
        return result;
    }

    public TestIndividual convert(Converter c) {
        return c.map(this);
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public int compareTo(TestIndividual other) {
        return new CompareToBuilder().append(type, other.type).append(localName, other.localName).toComparison();
    }

    public interface Converter {
        TestIndividual map(TestIndividual source);
    }
}

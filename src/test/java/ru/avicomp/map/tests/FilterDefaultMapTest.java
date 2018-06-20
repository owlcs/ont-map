package ru.avicomp.map.tests;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;
import ru.avicomp.map.*;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntNDP;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by @szuev on 07.05.2018.
 */
public class FilterDefaultMapTest extends MapTestData2 {
    private static final Logger LOGGER = LoggerFactory.getLogger(FilterDefaultMapTest.class);

    private static final String DATA_FIRST_NAME_DEFAULT = "Unknown first name";
    private static final String DATA_SECOND_NAME_DEFAULT = "Unknown second name";
    private static final String CONCAT_SEPARATOR = ", ";

    @Override
    public void validate(OntGraphModel result) {
        // expected 4 individuals, one of them are naked
        Assert.assertEquals(4, result.listNamedIndividuals().count());
        Map<Long, Long> map = result.listNamedIndividuals()
                .map(i -> TestUtils.plainAssertions(i).count())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        Assert.assertEquals(2, map.size());
        Assert.assertTrue("Expected one naked individual", map.containsKey(0L));
        Assert.assertTrue("Expected three individuals with one assertion", map.containsKey(1L));
        OntNDP name = TestUtils.findOntEntity(result, OntNDP.class, "user-name");
        List<String> names = result.statements(null, name, null)
                .map(Statement::getObject)
                .map(RDFNode::asLiteral)
                .map(Literal::getString)
                .sorted()
                .collect(Collectors.toList());
        LOGGER.debug("Names: {}", names.stream().map(s -> "'" + s + "'").collect(Collectors.joining(", ")));
        Assert.assertEquals(3, names.size());

        List<String> expected = Arrays.asList(
                DATA_FIRST_NAME_JHON + CONCAT_SEPARATOR + DATA_SECOND_NAME_JHON, // Jhon
                DATA_FIRST_NAME_BOB + CONCAT_SEPARATOR + DATA_SECOND_NAME_DEFAULT, // Bob
                DATA_FIRST_NAME_DEFAULT + CONCAT_SEPARATOR + DATA_SECOND_NAME_DEFAULT // Karl
        );
        Assert.assertEquals(expected, names);
    }

    @Override
    public MapModel assembleMapping(MapManager manager, OntGraphModel src, OntGraphModel dst) {
        OntClass person = TestUtils.findOntEntity(src, OntClass.class, "Person");
        OntClass user = TestUtils.findOntEntity(dst, OntClass.class, "User");
        OntNDP firstName = TestUtils.findOntEntity(src, OntNDP.class, "firstName");
        OntNDP secondName = TestUtils.findOntEntity(src, OntNDP.class, "secondName");
        OntNDP age = TestUtils.findOntEntity(src, OntNDP.class, "age");
        OntNDP resultName = TestUtils.findOntEntity(dst, OntNDP.class, "user-name");

        MapFunction uuid = manager.getFunction(AVC.UUID.getURI());
        MapFunction concatWithSeparator = manager.getFunction(SPINMAPL.concatWithSeparator.getURI());
        MapFunction withDefault = manager.getFunction(AVC.withDefault.getURI());
        MapFunction gt = manager.getFunction(manager.prefixes().expandPrefix("sp:gt"));

        MapModel res = createMappingModel(manager, "Used functions: avc:UUID, avc:withDefault, spinmapl:concatWithSeparator, sp:gt");
        MapContext context = res.createContext(person, user, uuid.create().build());
        MapFunction.Call propertyMapFunc = concatWithSeparator.create()
                .addFunction(SP.arg1, withDefault.create()
                        .addProperty(SP.arg1, firstName)
                        .addLiteral(SP.arg2, DATA_FIRST_NAME_DEFAULT))
                .addFunction(SP.arg2, withDefault.create()
                        .addProperty(SP.arg1, secondName)
                        .addLiteral(SP.arg2, DATA_SECOND_NAME_DEFAULT))
                .addLiteral(SPINMAPL.separator, CONCAT_SEPARATOR)
                .build();
        MapFunction.Call filterFunction = gt.create().addProperty(SP.arg1, age).addLiteral(SP.arg2, 30).build();
        context.addPropertyBridge(
                filterFunction,
                propertyMapFunc, resultName);
        return res;
    }

    @Test
    public void testValidateMapping() {
        MapModel m = assembleMapping();
        Assert.assertEquals(2, m.rules().count());
        MapContext c = m.contexts().findFirst().orElseThrow(AssertionError::new);
        Assert.assertEquals("avc:UUID()", c.getMapping().toString());
        Assert.assertNull(c.getFilter());
        PropertyBridge p = c.properties().findFirst().orElseThrow(AssertionError::new);

        OntNDP firstName = TestUtils.findOntEntity(m.asOntModel(), OntNDP.class, "firstName");
        OntNDP secondName = TestUtils.findOntEntity(m.asOntModel(), OntNDP.class, "secondName");
        OntNDP age = TestUtils.findOntEntity(m.asOntModel(), OntNDP.class, "age");
        OntNDP resultName = TestUtils.findOntEntity(m.asOntModel(), OntNDP.class, "user-name");

        Assert.assertEquals(resultName, p.getTarget());

        String propertyMappingFunc = String.format("spinmapl:concatWithSeparator(" +
                        "?arg1=avc:withDefault(?arg1=%s, ?arg2=\"%s\"), " +
                        "?arg2=avc:withDefault(?arg1=%s, ?arg2=\"%s\"), " +
                        "?separator=\"%s\")",
                m.asOntModel().shortForm(firstName.getURI()),
                DATA_FIRST_NAME_DEFAULT,
                m.asOntModel().shortForm(secondName.getURI()),
                DATA_SECOND_NAME_DEFAULT,
                CONCAT_SEPARATOR);
        String filterMappingFunc = String.format("sp:gt(?arg1=%s, ?arg2=\"%s\"^^xsd:int)",
                m.asOntModel().shortForm(age.getURI()), 30);

        Assert.assertEquals(propertyMappingFunc, p.getMapping().toString());
        Assert.assertEquals(filterMappingFunc, p.getFilter().toString());
    }
}

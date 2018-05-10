package ru.avicomp.map.tests;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;
import ru.avicomp.map.Context;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.MapModel;
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

        MapModel res = manager.createMapModel();
        res.setID(getNameSpace() + "/map")
                .addComment("Used functions: avc:UUID, avc:withDefault, spinmapl:concatWithSeparator, sp:gt", null);
        Context context = res.createContext(person, user, uuid.create().build());
        MapFunction.Call propertyMapFunc = concatWithSeparator.create()
                .add(SP.arg1.getURI(), withDefault.create()
                        .add(SP.arg1, firstName)
                        .add(SP.arg2, DATA_FIRST_NAME_DEFAULT))
                .add(SP.arg2.getURI(), withDefault.create()
                        .add(SP.arg1, secondName)
                        .add(SP.arg2, DATA_SECOND_NAME_DEFAULT))
                .add(SPINMAPL.separator, CONCAT_SEPARATOR)
                .build();
        MapFunction.Call filterFunction = gt.create().add(SP.arg1, age).add(SP.arg2, 30).build();
        context.addPropertyBridge(
                filterFunction,
                propertyMapFunc, resultName);
        return res;
    }
}

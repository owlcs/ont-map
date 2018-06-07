package ru.avicomp.map.tests;

import org.apache.jena.rdf.model.Statement;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.*;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntNDP;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by @szuev on 10.05.2018.
 */
public class FilterIndividualsMapTest extends MapTestData2 {
    private static final Logger LOGGER = LoggerFactory.getLogger(FilterIndividualsMapTest.class);

    @Override
    public void validate(OntGraphModel result) {
        OntNDP age = TestUtils.findOntEntity(result, OntNDP.class, "user-age");
        Assert.assertEquals(2, result.listNamedIndividuals().count());
        result.listNamedIndividuals().forEach(i -> {
            List<Statement> assertions = TestUtils.plainAssertions(i).collect(Collectors.toList());
            Assert.assertEquals("Incorrect assertions for individual " + i, 1, assertions.size());
            Assert.assertEquals(age, assertions.get(0).getPredicate());
            int a = assertions.get(0).getObject().asLiteral().getInt();
            LOGGER.debug("Individual: {}, age: {}", i, assertions.get(0).getObject());
            Assert.assertTrue("Wrong age for individual " + i, a > 25 && a < 100);
        });
    }

    @Override
    public MapModel assembleMapping(MapManager manager, OntGraphModel src, OntGraphModel dst) {
        OntClass person = TestUtils.findOntEntity(src, OntClass.class, "Person");
        OntClass user = TestUtils.findOntEntity(dst, OntClass.class, "User");
        OntNDP srcAge = TestUtils.findOntEntity(src, OntNDP.class, "age");
        OntNDP dstAge = TestUtils.findOntEntity(dst, OntNDP.class, "user-age");

        MapModel res = createMappingModel(manager, "Used functions: avc:UUID, spinmap:equals, sp:gt, sp:lt, sp:and");

        MapFunction gt = manager.getFunction(manager.prefixes().expandPrefix("sp:gt"));
        MapFunction lt = manager.getFunction(manager.prefixes().expandPrefix("sp:lt"));
        MapFunction and = manager.getFunction(manager.prefixes().expandPrefix("sp:and"));
        MapFunction uuid = manager.getFunction(AVC.UUID.getURI());
        MapFunction equals = manager.getFunction(SPINMAP.equals.getURI());

        Context context = res.createContext(person, user);
        context.addClassBridge(
                and.create()
                        .addFunction(SP.arg1, gt.create()
                                .addProperty(SP.arg1, srcAge)
                                .addLiteral(SP.arg2, 25).build())
                        .addFunction(SP.arg2, lt.create()
                                .addProperty(SP.arg1, srcAge)
                                .addLiteral(SP.arg2, 100).build())
                        .build(),
                uuid.create().build());
        context.addPropertyBridge(equals.create().addProperty(SP.arg1, srcAge).build(), dstAge);

        return res;
    }

    @Test
    public void testValidateMapping() {
        MapModel m = assembleMapping();


        Assert.assertEquals(2, m.rules().count());
        Context c = m.contexts().findFirst().orElseThrow(AssertionError::new);
        Assert.assertEquals("avc:UUID()", c.getMapping().toString());
        String srcAge = m.asOntModel().shortForm(TestUtils.findOntEntity(m.asOntModel(), OntNDP.class, "age").getURI());
        String contextFilterFunction = String.format("sp:and(?arg1=sp:gt(?arg1=%s, ?arg2=\"%s\"^^xsd:int), " +
                        "?arg2=sp:lt(?arg1=%s, ?arg2=\"%s\"^^xsd:int))",
                srcAge, 25, srcAge, 100);
        Assert.assertEquals(contextFilterFunction, c.getFilter().toString());

        PropertyBridge p = c.properties().findFirst().orElseThrow(AssertionError::new);

        Assert.assertEquals(TestUtils.findOntEntity(m.asOntModel(), OntNDP.class, "user-age"), p.getTarget());

        Assert.assertEquals(String.format("spinmap:equals(%s)", srcAge), p.getMapping().toString());
        Assert.assertNull(p.getFilter());
    }

}

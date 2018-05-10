package ru.avicomp.map.tests;

import org.apache.jena.rdf.model.Statement;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.Context;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.MapModel;
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
        // TODO: there are also assertions for age detached from any individual.
        // TODO: So we should inherit filter from class-bridge to property-bridge
        // TODO: and add corresponding additional checking.
    }

    @Override
    public MapModel assembleMapping(MapManager manager, OntGraphModel src, OntGraphModel dst) {
        OntClass person = TestUtils.findOntEntity(src, OntClass.class, "Person");
        OntClass user = TestUtils.findOntEntity(dst, OntClass.class, "User");
        OntNDP srcAge = TestUtils.findOntEntity(src, OntNDP.class, "age");
        OntNDP dstAge = TestUtils.findOntEntity(dst, OntNDP.class, "user-age");

        MapModel res = manager.createMapModel();
        res.setID(getNameSpace() + "/map")
                .addComment("Used functions: avc:UUID, spinmap:equals, sp:gt, sp:lt, sp:and", null);

        MapFunction gt = manager.getFunction(manager.prefixes().expandPrefix("sp:gt"));
        MapFunction lt = manager.getFunction(manager.prefixes().expandPrefix("sp:lt"));
        MapFunction and = manager.getFunction(manager.prefixes().expandPrefix("sp:and"));
        MapFunction uuid = manager.getFunction(AVC.UUID.getURI());
        MapFunction equals = manager.getFunction(SPINMAP.equals.getURI());

        Context context = res.createContext(person, user);
        context.addClassBridge(
                and.create()
                        .add(SP.arg1, gt.create()
                                .add(SP.arg1, srcAge)
                                .add(SP.arg2, 25).build())
                        .add(SP.arg2, lt.create()
                                .add(SP.arg1, srcAge)
                                .add(SP.arg2, 100).build())
                        .build(),
                uuid.create().build());
        context.addPropertyBridge(equals.create().add(SP.arg1, srcAge).build(), dstAge);

        return res;
    }
}

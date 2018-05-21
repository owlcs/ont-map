package ru.avicomp.map.tests;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.shared.PrefixMapping;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.map.*;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntIndividual;
import ru.avicomp.ontapi.jena.model.OntNDP;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by @szuev on 01.05.2018.
 */
public class RelatedContextMapTest extends MapTestData2 {
    private static final Logger LOGGER = LoggerFactory.getLogger(RelatedContextMapTest.class);

    @Override
    public void validate(OntGraphModel result) {
        Assert.assertEquals(4, result.listNamedIndividuals().count());

        OntIndividual.Named iBob = TestUtils.findOntEntity(result, OntIndividual.Named.class, "Bob");
        OntIndividual.Named iJane = TestUtils.findOntEntity(result, OntIndividual.Named.class, "Jane");
        OntIndividual.Named iJhon = TestUtils.findOntEntity(result, OntIndividual.Named.class, "Jhon");
        OntIndividual.Named iKarl = TestUtils.findOntEntity(result, OntIndividual.Named.class, "Karl");

        // no address for Jane and Jhon
        Assert.assertEquals(0, TestUtils.plainAssertions(iJane).count());
        Assert.assertEquals(0, TestUtils.plainAssertions(iJhon).count());

        // Bob and Karl:
        Assert.assertEquals(1, TestUtils.plainAssertions(iBob).count());
        Assert.assertEquals(1, TestUtils.plainAssertions(iKarl).count());
        Assert.assertEquals(DATA_ADDRESS_BOB, getString(iBob));
        Assert.assertEquals(DATA_ADDRESS_KARL, getString(iKarl));
    }

    private static String getString(OntIndividual i) {
        return TestUtils.plainAssertions(i)
                .map(Statement::getObject)
                .filter(RDFNode::isLiteral)
                .map(RDFNode::asLiteral)
                .map(Literal::getString).findFirst()
                .orElseThrow(AssertionError::new);
    }

    @Override
    public MapModel assembleMapping(MapManager manager, OntGraphModel src, OntGraphModel dst) {
        PrefixMapping pm = manager.prefixes();
        String targetNS = dst.getID().getURI() + "#";
        OntClass person = TestUtils.findOntEntity(src, OntClass.class, "Person");
        OntClass contact = TestUtils.findOntEntity(src, OntClass.class, "Contact");
        OntClass user = TestUtils.findOntEntity(dst, OntClass.class, "User");
        OntNDP contactAddress = TestUtils.findOntEntity(src, OntNDP.class, "address");
        OntNDP userAddress = TestUtils.findOntEntity(dst, OntNDP.class, "user-address");

        MapModel res = createMappingModel(manager,
                "Used functions: spinmapl:relatedSubjectContext, spinmapl:changeNamespace, spinmap:equals");

        Context person2user = res.createContext(person, user);
        Context contact2user = person2user.createRelatedContext(contact);

        MapFunction changeNamespace = manager.getFunction(pm.expandPrefix("spinmapl:changeNamespace"));
        person2user.addClassBridge(changeNamespace
                .create().add(pm.expandPrefix("spinmapl:targetNamespace"), targetNS).build());
        MapFunction equals = manager.getFunction(pm.expandPrefix("spinmap:equals"));
        String arg1 = pm.expandPrefix("sp:arg1");
        contact2user.addPropertyBridge(equals.create().add(arg1, contactAddress.getURI()).build(), userAddress);
        return res;
    }

    @Test
    public void testDeleteContext() {
        MapModel m = assembleMapping();
        OntClass person = m.asOntModel().listClasses()
                .filter(s -> Objects.equals(s.getLocalName(), "Person")).findFirst().orElseThrow(AssertionError::new);
        OntClass contact = m.asOntModel().listClasses()
                .filter(s -> Objects.equals(s.getLocalName(), "Contact")).findFirst().orElseThrow(AssertionError::new);
        Function<List<Context>, Context> firstContext = contexts -> contexts.stream()
                .filter(c -> Objects.equals(c.getSource(), contact)).findFirst().orElseThrow(AssertionError::new);
        Function<List<Context>, Context> secondContext = contexts -> contexts.stream()
                .filter(c -> Objects.equals(c.getSource(), person)).findFirst().orElseThrow(AssertionError::new);
        deleteDependentContextTest(m, firstContext, secondContext);
    }

    static void deleteDependentContextTest(MapModel m, Function<List<Context>, Context> firstContext, Function<List<Context>, Context> secondContext) {
        TestUtils.debug(m);
        List<Context> contexts = m.contexts().collect(Collectors.toList());
        LOGGER.debug("Contexts: {}", contexts);
        Assert.assertEquals(2, contexts.size());

        Context context2 = secondContext.apply(contexts);
        Context context1 = firstContext.apply(contexts);
        List<Context> related2 = context2.dependentContexts().collect(Collectors.toList());
        List<Context> related1 = context1.dependentContexts().collect(Collectors.toList());
        LOGGER.debug("Contexts, which dependent on {}: {}", context2, related2);
        LOGGER.debug("Contexts, which dependent on {}: {}", context1, related1);
        Assert.assertEquals(1, related2.size());
        Assert.assertEquals(context1, related2.get(0));
        Assert.assertEquals(0, related1.size());

        LOGGER.debug("Try to delete {}", context2);
        try {
            m.deleteContext(context2);
            Assert.fail("Context " + context2 + " has been deleted");
        } catch (MapJenaException j) {
            LOGGER.debug("Expected error: {}", j.getMessage());
        }
        Assert.assertEquals(2, m.contexts().count());

        LOGGER.debug("Delete {}", context1);
        m.deleteContext(context1);
        TestUtils.debug(m);
        Assert.assertEquals(1, m.contexts().count());

        LOGGER.debug("Delete {}", context2);
        m.deleteContext(context2);
        TestUtils.debug(m);
        Assert.assertEquals(0, m.contexts().count());
        Assert.assertEquals(6, m.asOntModel().getBaseGraph().size());
    }
}
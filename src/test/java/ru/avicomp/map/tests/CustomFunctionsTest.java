package ru.avicomp.map.tests;

import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.function.FunctionRegistry;
import org.apache.jena.sparql.pfunction.PropertyFunctionRegistry;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import org.topbraid.spin.vocabulary.SPL;
import ru.avicomp.map.Managers;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.MapModel;
import ru.avicomp.map.spin.QueryHelper;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

/**
 * Created by @szuev on 15.06.2018.
 */
public class CustomFunctionsTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomFunctionsTest.class);

    @Test
    public void testAddFunction() {
        MapManager manager = Managers.createMapManager();
        long c1 = manager.functions().count();
        LOGGER.debug("Count before adding model: {}", c1);
        long g1 = Iter.count(FunctionRegistry.get().keys());
        long g2 = Iter.count(PropertyFunctionRegistry.get().keys());

        OntGraphModel m1 = makeFuncModel1();
        TestUtils.debug(m1);

        MapModel m2 = manager.asMapModel(m1);
        Assert.assertEquals(0, m2.rules().count());
        long c2 = manager.functions().count();
        LOGGER.info("Count after adding model: {}", c2);
        Assert.assertEquals(c1 + 1, c2);
        long c3 = Managers.createMapManager().functions().count();
        LOGGER.debug("Count in new manager: {}", c3);
        Assert.assertEquals(c1, c3);

        LOGGER.debug("Global counts: {}/{}", g1, g2);
        Assert.assertEquals("Changes in PropertyFunctionRegistry", g2, Iter.count(PropertyFunctionRegistry.get().keys()));
        Assert.assertEquals("Changes in FunctionRegistry", g1, Iter.count(FunctionRegistry.get().keys()));
    }

    private static OntGraphModel makeFuncModel1() {
        String q = "SELECT (xsd:string(?untyped) AS ?result)\n" +
                "WHERE {\n" +
                "    BIND (CONCAT(xsd:string(?arg1), ?separator, xsd:string(?arg2), ?separator, xsd:string(?arg3)) AS ?untyped) .\n" +
                "}";
        String uri = "http://test.func.com";
        OntGraphModel m = TestUtils.createMapModel(uri);

        m.createResource(uri + "#concatWithSeparator_3")
                .addProperty(RDF.type, SPIN.Function)
                .addProperty(RDFS.subClassOf, SPL.StringFunctions)
                .addProperty(SPIN.returnType, XSD.xstring)
                .addProperty(RDFS.comment, "Test func")
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg1)
                        .addProperty(SPL.valueType, XSD.xstring))
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg2)
                        .addProperty(SPL.valueType, XSD.xstring))
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg2)
                        .addProperty(SPL.valueType, XSD.xstring))
                .addProperty(SPIN.body, QueryHelper.parseQuery(q, m));
        return m;
    }

    private static OntGraphModel makeFuncModel2(String uri, String val) {
        String q = "SELECT (CONCAT(?arg1, \"" + val + "\"))\n" +
                "WHERE {\n" +
                "}";
        OntGraphModel m = TestUtils.createMapModel(uri);
        Resource function = m.createResource(uri)
                .addProperty(RDF.type, SPIN.Function)
                .addProperty(SPIN.returnType, XSD.xstring)
                .addProperty(RDFS.subClassOf, SPIN.Functions)
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg1)
                        .addProperty(SPL.valueType, XSD.xstring))
                .addProperty(SPIN.body, QueryHelper.parseQuery(q, m));
        OntClass clazz = m.createOntEntity(OntClass.class, uri + "#Class1");
        Resource context = m.createResource(uri + "#Context1", SPINMAP.Context)
                .addProperty(SPINMAP.sourceClass, clazz)
                .addProperty(SPINMAP.targetClass, clazz)
                .addProperty(SPINMAP.target, m.createResource()
                        .addProperty(RDF.type, SPINMAPL.self)
                        .addProperty(SPINMAP.source, SPINMAP.sourceVariable));
        Resource rule = m.createResource().addProperty(RDF.type, SPINMAP.Mapping_1_1)
                .addProperty(SPINMAP.context, context)
                .addProperty(SPINMAP.sourcePredicate1, RDFS.label)
                .addProperty(SPINMAP.targetPredicate1, RDFS.label)
                .addProperty(SPINMAP.expression, m.createResource()
                        .addProperty(RDF.type, function)
                        .addProperty(SP.arg1, SPIN._arg1));

        clazz.addProperty(SPINMAP.rule, rule);
        clazz.createIndividual(uri + "#Individual1").addLabel("The label", null);
        return m;
    }

}

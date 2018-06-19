package ru.avicomp.map.tests;

import org.apache.jena.atlas.iterator.Iter;
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
import org.topbraid.spin.vocabulary.SPL;
import ru.avicomp.map.Managers;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.MapModel;
import ru.avicomp.map.spin.QueryHelper;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

/**
 * Created by @szuev on 15.06.2018.
 */
public class LoadFunctionsTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadFunctionsTest.class);

    @Test
    public void testLoadSingleFunction() {
        MapManager manager = Managers.createMapManager();
        long c1 = manager.functions().count();
        LOGGER.debug("Count before adding model: {}", c1);
        long g1 = Iter.count(FunctionRegistry.get().keys());
        long g2 = Iter.count(PropertyFunctionRegistry.get().keys());

        OntGraphModel m1 = makeSingleFunctionModel();
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

    @Test
    public void testLoadAndInference() {
        LoadMapTestData data = new LoadMapTestData("http://test.com/some-function2", "--suff");
        MapManager manager = Managers.createMapManager();
        long c1 = manager.functions().count();
        LOGGER.debug("Count before adding model: {}", c1);

        String uri = "http://test.com/some-function2";
        MapModel m = data.assembleMapping(manager, null, null);
        TestUtils.debug(m);

        Assert.assertEquals(1, manager.getFunction(uri).args().count());
        long c2 = manager.functions().count();
        Assert.assertEquals(c1 + 1, c2);

        data.validateMapping(m);

        OntGraphModel res = data.assembleTarget();
        manager.getInferenceEngine().run(m, m.asOntModel().getGraph(), res.getGraph());
        data.validateResult(res);
    }

    private static OntGraphModel makeSingleFunctionModel() {
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


}

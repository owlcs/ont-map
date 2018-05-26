package ru.avicomp.map.tests;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.shared.PrefixMapping;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;
import ru.avicomp.map.*;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.spin.vocabulary.MATH;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntIndividual;
import ru.avicomp.ontapi.jena.model.OntNDP;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by @szuev on 23.05.2018.
 */
public class MathOpsMapTest extends MapTestData5 {
    private static final Logger LOGGER = LoggerFactory.getLogger(MathOpsMapTest.class);
    private static final String RES_URI = "http://x";

    @Test
    public void testInference() {
        OntGraphModel s = assembleSource();
        TestUtils.debug(s);
        OntGraphModel t = assembleTarget();
        TestUtils.debug(t);

        MapManager man = Managers.getMapManager();
        MapModel m = assembleMapping(man, s, t);
        TestUtils.debug(m);

        LOGGER.info("Run inference");
        man.getInferenceEngine().run(m, s, t);

        TestUtils.debug(t);

        // validate:
        OntNDP p1 = TestUtils.findOntEntity(t, OntNDP.class, "dstDataProperty1");
        OntNDP p2 = TestUtils.findOntEntity(t, OntNDP.class, "dstDataProperty2");
        Assert.assertEquals(1, t.statements(null, p1, null).count());
        Assert.assertEquals(1, t.statements(null, p2, null).count());

        List<OntIndividual> res = t.listNamedIndividuals().collect(Collectors.toList());
        Assert.assertEquals(1, res.size());
        OntIndividual i = res.get(0);
        Assert.assertEquals(RES_URI, i.getURI());
        double d1 = i.statement(p1).map(Statement::getObject).map(RDFNode::asLiteral).map(Literal::getDouble).orElseThrow(AssertionError::new);
        double d2 = i.statement(p2).map(Statement::getObject).map(RDFNode::asLiteral).map(Literal::getDouble).orElseThrow(AssertionError::new);
        Assert.assertEquals(Math.PI - 1, d2, 0.01);
        Assert.assertEquals(Math.E * Math.E, d1, 0.01);
        AbstractMapTest.commonValidate(t);
    }

    @Override
    public MapModel assembleMapping(MapManager manager, OntGraphModel src, OntGraphModel dst) {
        PrefixMapping pm = manager.prefixes();

        MapFunction iri = manager.getFunction(AVC.IRI);
        MapFunction mul = manager.getFunction(SP.resource("mul"));
        MapFunction ln = manager.getFunction(MATH.log);
        MapFunction exp = manager.getFunction(MATH.exp);
        MapFunction sub = manager.getFunction(SP.sub);
        MapFunction p = manager.getFunction(MATH.pi);
        MapFunction e = manager.getFunction(pm.expandPrefix("afn:e"));
        MapFunction abs = manager.getFunction(pm.expandPrefix("fn:abs"));

        MapModel res = createMappingModel(manager, "Used functions: " + Stream.of(iri, mul, ln, exp, sub, p, e, abs).
                map(MapFunction::name).map(pm::shortForm).collect(Collectors.joining(", ")));

        OntClass srcClass = TestUtils.findOntEntity(src, OntClass.class, "SrcClass1");
        OntClass dstClass = TestUtils.findOntEntity(dst, OntClass.class, "DstClass1");
        OntNDP srcProp1 = TestUtils.findOntEntity(src, OntNDP.class, "srcDataProperty1");
        OntNDP srcProp2 = TestUtils.findOntEntity(src, OntNDP.class, "srcDataProperty2");
        OntNDP dstProp1 = TestUtils.findOntEntity(dst, OntNDP.class, "dstDataProperty1");
        OntNDP dstProp2 = TestUtils.findOntEntity(dst, OntNDP.class, "dstDataProperty2");

        Context c = res.createContext(srcClass, dstClass, iri.create().addLiteral(SP.arg1, RES_URI).build());
        // e * exp($val) ::: src-prop1(=1) => dst-prop1 ::: 2.72 * exp(1) = e^2 = 7.39
        c.addPropertyBridge(mul.create()
                .addFunction(SP.arg1, e.create())
                .addFunction(SP.arg2, exp.create().addProperty(SP.arg1, srcProp1)), dstProp1);

        //  [ln($val) - pi] ::: src-prop2(=2.72) => dst-prop2 ::: - pi + ln(2.72) = 2.1415
        c.addPropertyBridge(abs.create().addFunction(SP.arg1, sub.create()
                .addFunction(SP.arg1, ln.create().addProperty(SP.arg1, srcProp2))
                .addFunction(SP.arg2, p.create())), dstProp2);
        return res;
    }
}
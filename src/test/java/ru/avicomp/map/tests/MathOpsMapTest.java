package ru.avicomp.map.tests;

import org.junit.Assert;
import org.junit.Test;
import org.topbraid.spin.vocabulary.SP;
import ru.avicomp.map.*;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.spin.vocabulary.MATH;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntNDP;

/**
 * Created by @szuev on 23.05.2018.
 */
public class MathOpsMapTest extends MapTestData5 {

    @Test
    public void testInference() {
        OntGraphModel s = assembleSource();
        TestUtils.debug(s);
        OntGraphModel t = assembleTarget();
        TestUtils.debug(t);

        MapManager man = Managers.getMapManager();
        MapModel m = assembleMapping(man, s, t);
        TestUtils.debug(m);
        // todo
        Assert.fail("Not ready");
    }

    @Override
    public MapModel assembleMapping(MapManager manager, OntGraphModel src, OntGraphModel dst) {
        MapModel res = createMappingModel(manager, "Used functions: avc:IRI, math:log, sp:mul, afn:e");
        OntClass srcClass = TestUtils.findOntEntity(src, OntClass.class, "SrcClass1");
        OntClass dstClass = TestUtils.findOntEntity(dst, OntClass.class, "DstClass1");
        OntNDP srcProp1 = TestUtils.findOntEntity(src, OntNDP.class, "srcDataProperty1");
        OntNDP srcProp2 = TestUtils.findOntEntity(src, OntNDP.class, "srcDataProperty2");
        OntNDP dstProp1 = TestUtils.findOntEntity(dst, OntNDP.class, "dstDataProperty1");
        OntNDP dstProp2 = TestUtils.findOntEntity(dst, OntNDP.class, "dstDataProperty2");

        MapFunction iri = manager.getFunction(AVC.IRI);
        MapFunction mul = manager.getFunction(SP.resource("mul"));
        MapFunction ln = manager.getFunction(MATH.log);
        MapFunction exp = manager.getFunction(MATH.exp);
        MapFunction sub = manager.getFunction(SP.sub);
        MapFunction p = manager.getFunction(MATH.pi);
        MapFunction e = manager.getFunction(manager.prefixes().expandPrefix("afn:e"));
        MapFunction abs = manager.getFunction(manager.prefixes().expandPrefix("fn:abs"));

        Context c = res.createContext(srcClass, dstClass, iri.create().addLiteral(SP.arg1, "http://x").build());
        // e * exp($val) ::: src-prop1(=1) => dst-prop1 ::: 2.72 * exp(1) = 2.72
        c.addPropertyBridge(mul.create()
                .addFunction(SP.arg1, e.create())
                .addFunction(SP.arg2, exp.create().addProperty(SP.arg1, srcProp1)), dstProp1);

        //  [ln($val) - pi] ::: src-prop2(=2.72) => dst-prop2 ::: - pi + ln(2.72) = 1.1415
        c.addPropertyBridge(abs.create().addFunction(SP.arg1, sub.create()
                .addFunction(SP.arg1, ln.create().addProperty(SP.arg1, srcProp2))
                .addFunction(SP.arg2, p.create())), dstProp2);
        return res;
    }
}

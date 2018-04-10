package ru.avicomp.map.tests;

import ru.avicomp.map.Managers;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.MapModel;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntNDP;

/**
 * TODO: Not ready. For developing.
 * Created by @szuev on 09.04.2018.
 */
public class DevelopExample1 {

    public static void main(String... args) {
        OntGraphModel src = assembleSource();
        src.write(System.out, "ttl");
        System.out.println("==========");
        OntGraphModel dst = assembleTarget();
        dst.write(System.out, "ttl");
        System.out.println("==========");

        OntClass srcClass = src.listClasses().findFirst().orElseThrow(AssertionError::new);
        OntClass dstClass = dst.listClasses().findFirst().orElseThrow(AssertionError::new);


        MapManager manager = Managers.getMapManager();
        MapFunction func = manager.getFunction(manager.prefixes().expandPrefix("sp:UUID"));
        MapFunction.Call targetFunction = func.createFunctionCall().build();
        MapModel res = manager.getModelBuilder().addClassBridge(srcClass, dstClass, targetFunction).back()
                // topbraid has difficulties with anonymous ontologies:
                .addName("http://example.com.map")
                .build();
        res.write(System.out, "ttl");

        System.out.println("==========");
        res.runInferences(src, dst);
        System.out.println("==========");
        dst.write(System.out, "ttl"); // 2

        System.out.println("==========");
        res.runInferences(src, dst);
        System.out.println("==========");
        dst.write(System.out, "ttl"); // 4
    }

    public static OntGraphModel assembleSource() {
        OntGraphModel m = OntModelFactory.createModel();
        m.setNsPrefixes(OntModelFactory.STANDARD);
        m.setID("http://example.com/source1");
        String ns = m.getID().getURI() + "#";
        m.setNsPrefix("source", ns);
        OntClass cl = m.createOntEntity(OntClass.class, ns + "CL1");
        OntNDP dp1 = m.createOntEntity(OntNDP.class, ns + "DP1");
        OntNDP dp2 = m.createOntEntity(OntNDP.class, ns + "DP1");
        dp1.addDomain(cl);
        dp2.addDomain(cl);
        cl.createIndividual(ns + "i1");
        cl.createIndividual(ns + "i2");
        return m;
    }

    public static OntGraphModel assembleTarget() {
        OntGraphModel m = OntModelFactory.createModel();
        m.setNsPrefixes(OntModelFactory.STANDARD);
        m.setID("http://example.com/target1");
        String ns = m.getID().getURI() + "#";
        m.setNsPrefix("target", ns);
        OntClass cl = m.createOntEntity(OntClass.class, ns + "T_CL1");
        OntNDP dp1 = m.createOntEntity(OntNDP.class, ns + "T_DP1");
        dp1.addDomain(cl);
        return m;
    }
}

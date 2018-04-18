package ru.avicomp.map.tests;

import org.apache.jena.vocabulary.RDF;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.map.*;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntNDP;

import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * TODO: Not ready. For developing.
 * Created by @szuev on 09.04.2018.
 */
public class DevelopExample1 extends AbstractMapTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(DevelopExample1.class);

    @Test
    public void testProcess() {
        OntGraphModel src = assembleSource();
        TestUtils.debug(LOGGER, src);
        OntGraphModel dst = assembleTarget();
        TestUtils.debug(LOGGER, dst);

        OntClass dstClass = dst.listClasses().findFirst().orElseThrow(AssertionError::new);

        MapManager manager = Managers.getMapManager();
        MapModel mapping = assembleMapping(manager, src, dst);
        TestUtils.debug(LOGGER, mapping);

        manager.getInferenceEngine().run(mapping, src, dst);
        TestUtils.debug(LOGGER, dst);
        Assert.assertEquals(2, dst.statements(null, RDF.type, dstClass).count());

        manager.getInferenceEngine().run(mapping, src, dst);
        TestUtils.debug(LOGGER, dst);
        Assert.assertEquals(4, dst.statements(null, RDF.type, dstClass).count());
    }

    @Override
    public MapModel assembleMapping(MapManager manager, OntGraphModel src, OntGraphModel dst) {
        OntClass srcClass = src.listClasses().findFirst().orElseThrow(AssertionError::new);
        OntClass dstClass = dst.listClasses().findFirst().orElseThrow(AssertionError::new);

        MapFunction func = manager.getFunction(manager.prefixes().expandPrefix("sp:UUID"));
        MapFunction.Call targetFunction = func.createFunctionCall().build();
        MapModel res = manager.createModel();
        // topbraid (gui, not spinmap) has difficulties with anonymous ontologies:
        res.setID(getNameSpace() + "/map");
        res.createContext(srcClass, dstClass).addExpression(targetFunction);
        Assert.assertEquals(1, res.contexts().count());
        Assert.assertEquals(srcClass, res.contexts().map(Context::getSource).findFirst().orElseThrow(AssertionError::new));
        Assert.assertEquals(dstClass, res.contexts().map(Context::getTarget).findFirst().orElseThrow(AssertionError::new));
        return res;
    }

    @Override
    public OntGraphModel assembleSource() {
        return assemblePrimitiveOntology(this, "source", "CL1",
                Stream.of("i1", "i2").collect(Collectors.toList()),
                Stream.of("DP1", "DP2").collect(Collectors.toList()));
    }

    @Override
    public OntGraphModel assembleTarget() {
        return assemblePrimitiveOntology(this, "target", "T_CL1",
                Collections.emptyList(),
                Collections.singletonList("T_DP2"));
    }

    protected static OntGraphModel assemblePrimitiveOntology(AbstractMapTest base,
                                                             String name,
                                                             String className,
                                                             Collection<String> individuals,
                                                             Collection<String> dataProperties) {
        OntGraphModel m = base.createModel(name);
        String ns = m.getID().getURI() + "#";
        OntClass clazz = m.createOntEntity(OntClass.class, ns + className);
        dataProperties.forEach(s -> m.createOntEntity(OntNDP.class, ns + s).addDomain(clazz));
        individuals.forEach(s -> clazz.createIndividual(ns + s));
        return m;
    }

}

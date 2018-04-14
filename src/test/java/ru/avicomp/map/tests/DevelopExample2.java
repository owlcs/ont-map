package ru.avicomp.map.tests;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPINMAPL;
import ru.avicomp.map.*;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntNDP;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * TODO: For developing. Will be moved/renamed
 * Created by @szuev on 14.04.2018.
 */
public class DevelopExample2 extends AbstractMapTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(DevelopExample2.class);

    @Test
    public void testProcess() {
        OntGraphModel src = assembleSource();
        TestUtils.debug(LOGGER, src);
        OntGraphModel dst = assembleTarget();
        TestUtils.debug(LOGGER, dst);
        MapManager manager = Managers.getMapManager();
        MapModel mapping = assembleMapping(manager, src, dst);
        TestUtils.debug(LOGGER, mapping);

        manager.getInferenceEngine().run(mapping, src, dst);
        TestUtils.debug(LOGGER, dst);
        //Assert.assertEquals(2, dst.statements(null, RDF.type, dstClass).count());
    }

    @Override
    public MapModel assembleMapping(MapManager manager, OntGraphModel src, OntGraphModel dst) {
        OntClass srcClass = src.listClasses().findFirst().orElseThrow(AssertionError::new);
        OntClass dstClass = dst.listClasses().findFirst().orElseThrow(AssertionError::new);
        List<OntNDP> props = src.listDataProperties().collect(Collectors.toList());

        MapFunction func = manager.getFunction(SPINMAPL.buildURI2.getURI());
        MapFunction.Call targetFunction = func.createFunctionCall()
                .add(SP.arg1.getURI(), props.get(0).getURI())
                .add(SP.arg2.getURI(), props.get(1).getURI())
                .add(SPINMAPL.template.getURI(), "target:Individual-{?1}-{?2}")
                .build();

        MapModel res = manager.createModel();
        res.setID(getNameSpace() + "/map");
        res.createContext(srcClass, dstClass).addExpression(targetFunction);
        Assert.assertEquals(1, res.contexts().count());
        Assert.assertEquals(srcClass, res.contexts().map(Context::getSource).findFirst().orElseThrow(AssertionError::new));
        Assert.assertEquals(dstClass, res.contexts().map(Context::getTarget).findFirst().orElseThrow(AssertionError::new));
        return res;
    }

    @Override
    public OntGraphModel assembleSource() {
        OntGraphModel res = assemblePrimitiveOntology(this, "source", "CL1",
                Stream.of("i1", "i2").collect(Collectors.toList()),
                Stream.of("DP1", "DP2").collect(Collectors.toList()));
        // data-property assertions:
        res.listNamedIndividuals().forEach(i -> res.listDataProperties().forEach(p -> i.addProperty(p, i.getLocalName() + "-" + p.getLocalName())));
        return res;
    }

    @Override
    public OntGraphModel assembleTarget() {
        return assemblePrimitiveOntology(this, "target", "T_CL1",
                Collections.emptyList(),
                Collections.singletonList("T_DP2"));
    }
}

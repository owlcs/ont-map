package ru.avicomp.map.tests;

import org.apache.jena.vocabulary.RDF;
import org.junit.Assert;
import org.junit.Test;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.*;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntStatement;
import ru.avicomp.ontapi.jena.utils.Models;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by @szuev on 09.04.2018.
 */
public class UUIDMapTest extends SimpleMapTest {

    @Test
    @Override
    public void testInference() {
        OntGraphModel src = assembleSource();
        TestUtils.debug(src);
        OntGraphModel dst = assembleTarget();
        TestUtils.debug(dst);

        OntClass dstClass = dst.listClasses().findFirst().orElseThrow(AssertionError::new);

        MapManager manager = Managers.getMapManager();
        MapModel mapping = assembleMapping(manager, src, dst);
        TestUtils.debug(mapping);

        manager.getInferenceEngine().run(mapping, src, dst);
        TestUtils.debug(dst);
        Assert.assertEquals(3, dst.listNamedIndividuals().count());

        manager.getInferenceEngine().run(mapping, src, dst);
        Assert.assertEquals(3, dst.listNamedIndividuals().count());
        Assert.assertEquals(3, dstClass.individuals().count());
    }

    @Override
    public MapModel assembleMapping(MapManager manager, OntGraphModel src, OntGraphModel dst) {
        OntClass srcClass = src.listClasses().findFirst().orElseThrow(AssertionError::new);
        OntClass dstClass = dst.listClasses().findFirst().orElseThrow(AssertionError::new);

        MapFunction func = manager.getFunction(AVC.UUID.getURI());
        MapFunction.Call targetFunction = func.createFunctionCall().build();
        MapModel res = createFreshMapping(manager, "To test custom no-arg avc:UUID.\n" +
                "Please note: custom functions which is generated by API does not require additional imports.");
        res.createContext(srcClass, dstClass).addExpression(targetFunction);
        Assert.assertEquals(1, res.contexts().count());
        Assert.assertEquals(2, res.imports().count());
        Assert.assertEquals(srcClass, res.contexts().map(Context::getSource).findFirst().orElseThrow(AssertionError::new));
        Assert.assertEquals(dstClass, res.contexts().map(Context::getTarget).findFirst().orElseThrow(AssertionError::new));
        // validate the graph has function body (svc:UUID) inside:
        List<OntStatement> statements = ((OntGraphModel) res).statements(AVC.UUID, RDF.type, SPINMAP.TargetFunction)
                .filter(OntStatement::isLocal)
                .collect(Collectors.toList());
        Assert.assertEquals(1, statements.size());
        Assert.assertEquals(40, Models.getAssociatedStatements(statements.get(0).getSubject()).size());
        return res;
    }

}
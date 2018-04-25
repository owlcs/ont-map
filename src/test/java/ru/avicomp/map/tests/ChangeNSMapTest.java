package ru.avicomp.map.tests;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.PrefixMapping;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPINMAPL;
import ru.avicomp.map.*;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntIndividual;
import ru.avicomp.ontapi.jena.model.OntNDP;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by @szuev on 25.04.2018.
 */
public class ChangeNSMapTest extends SimpleMapTest {
    private final Logger LOGGER = LoggerFactory.getLogger(ChangeNSMapTest.class);

    @Override
    public MapModel assembleMapping(MapManager manager, OntGraphModel src, OntGraphModel dst) {
        OntClass srcClass = src.listClasses().findFirst().orElseThrow(AssertionError::new);
        OntClass dstClass = dst.listClasses().findFirst().orElseThrow(AssertionError::new);
        List<OntNDP> props = src.listDataProperties().sorted(Comparator.comparing(Resource::getURI)).collect(Collectors.toList());
        Assert.assertEquals(3, props.size());
        OntNDP dstProp = dst.listDataProperties().findFirst().orElseThrow(AssertionError::new);

        MapFunction.Call targetFunction = manager.getFunction(SPINMAPL.changeNamespace.getURI())
                .createFunctionCall()
                .add(SPINMAPL.targetNamespace.getURI(),
                        manager.getFunction(manager.prefixes().expandPrefix("afn:namespace"))
                                .createFunctionCall().add(SP.arg1.getURI(), dstClass.getURI()))
                .build();
        MapFunction.Call propertyFunction1 = manager.getFunction(SPINMAPL.concatWithSeparator.getURI())
                .createFunctionCall()
                .add(SP.arg1.getURI(), props.get(1).getURI())
                .add(SP.arg2.getURI(), props.get(0).getURI())
                .add(SPINMAPL.separator.getURI(), ", ")
                .build();

        PrefixMapping pm = PrefixMapping.Factory.create()
                .setNsPrefixes(manager.prefixes())
                .setNsPrefixes(srcClass.getModel())
                .setNsPrefixes(dstClass.getModel()).lock();

        TestUtils.debug(targetFunction, pm);
        TestUtils.debug(propertyFunction1, pm);

        MapModel res = manager.createMapModel();
        res.setID(getNameSpace() + "/map");
        res.createContext(srcClass, dstClass, targetFunction)
                .addPropertyBridge(propertyFunction1, dstProp);
        Assert.assertEquals(1, res.contexts().count());
        Assert.assertEquals(2, res.imports().count());
        Assert.assertEquals(srcClass, res.contexts().map(Context::getSource).findFirst().orElseThrow(AssertionError::new));
        Assert.assertEquals(dstClass, res.contexts().map(Context::getTarget).findFirst().orElseThrow(AssertionError::new));
        return res;
    }

    @Override
    public void testInference() {
        OntGraphModel src = assembleSource();
        TestUtils.debug(src);
        List<OntNDP> props = src.listDataProperties().sorted(Comparator.comparing(Resource::getURI)).collect(Collectors.toList());
        List<OntIndividual.Named> srcIndividuals = src.listNamedIndividuals().sorted(Comparator.comparing(Resource::getURI)).collect(Collectors.toList());
        Assert.assertEquals(3, props.size());
        Assert.assertEquals(3, srcIndividuals.size());

        OntGraphModel dst = assembleTarget();
        TestUtils.debug(dst);
        OntNDP dstProp = dst.listDataProperties().findFirst().orElseThrow(AssertionError::new);

        MapManager manager = Managers.getMapManager();
        MapModel mapping = assembleMapping(manager, src, dst);
        TestUtils.debug(mapping);

        LOGGER.info("Run inference.");
        manager.getInferenceEngine().run(mapping, src, dst);
        TestUtils.debug(dst);

        LOGGER.info("Validate.");
        Assert.assertEquals(srcIndividuals.size(), dst.listNamedIndividuals().count());
        String ns = dst.getNsPrefixURI("target");
        List<OntIndividual.Named> dstIndividuals = srcIndividuals.stream()
                .map(i -> ns + i.getLocalName())
                .map(dst::getResource)
                .map(s -> s.as(OntIndividual.Named.class))
                .collect(Collectors.toList());
        // two of them has data property assertions
        List<String> values = dstIndividuals.stream()
                .flatMap(i -> i.objects(dstProp, Literal.class))
                .map(Literal::getString)
                .sorted()
                .map(s -> "'" + s + "'")
                .collect(Collectors.toList());
        LOGGER.debug("Values: {}", values);
        Assert.assertEquals(2, values.size());
    }
}

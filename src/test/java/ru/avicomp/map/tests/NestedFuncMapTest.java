package ru.avicomp.map.tests;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.PrefixMapping;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;
import ru.avicomp.map.*;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntIndividual;
import ru.avicomp.ontapi.jena.model.OntNDP;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by @szuev on 25.04.2018.
 */
public class NestedFuncMapTest extends MapTestData1 {
    private final Logger LOGGER = LoggerFactory.getLogger(NestedFuncMapTest.class);

    @Override
    public MapModel assembleMapping(MapManager manager, OntGraphModel src, OntGraphModel dst) {
        OntClass srcClass = TestUtils.findOntEntity(src, OntClass.class, "SourceClass1");
        OntClass dstClass = TestUtils.findOntEntity(dst, OntClass.class, "TargetClass1");
        List<OntNDP> props = src.listDataProperties().sorted(Comparator.comparing(Resource::getURI)).collect(Collectors.toList());
        Assert.assertEquals(3, props.size());
        OntNDP dstProp = dst.listDataProperties().findFirst().orElseThrow(AssertionError::new);

        MapFunction.Call targetFunction = manager.getFunction(SPINMAPL.changeNamespace.getURI())
                .create()
                .add(SPINMAPL.targetNamespace.getURI(),
                        manager.getFunction(manager.prefixes().expandPrefix("afn:namespace"))
                                .create().add(SP.arg1.getURI(), dstClass.getURI()))
                .build();
        MapFunction concatWithSeparator = manager.getFunction(SPINMAPL.concatWithSeparator.getURI());
        // concat template: %2, %1--SOME VALUE???%3
        MapFunction.Call propertyFunction1 = concatWithSeparator
                .create()
                .add(SP.arg1.getURI(), props.get(1).getURI())
                .add(SP.arg2.getURI(), concatWithSeparator.create()
                        .add(SP.arg1.getURI(), props.get(0).getURI())
                        .add(SP.arg2.getURI(), concatWithSeparator.create()
                                .add(SP.arg1.getURI(), "SOME VALUE")
                                .add(SP.arg2.getURI(), props.get(2).getURI())
                                .add(SPINMAPL.separator.getURI(), "???"))
                        .add(SPINMAPL.separator.getURI(), "--"))
                .add(SPINMAPL.separator.getURI(), ", ")
                .build();
        PrefixMapping pm = PrefixMapping.Factory.create()
                .setNsPrefixes(manager.prefixes())
                .setNsPrefixes(srcClass.getModel())
                .setNsPrefixes(dstClass.getModel()).lock();

        TestUtils.debug(targetFunction, pm);
        TestUtils.debug(propertyFunction1, pm);

        MapModel res = createMappingModel(manager,
                "Please note: TopBraid Composer (5.5.1) has a problem with displaying diagram.\n" +
                        "Don't worry: that's just its problem:\n" +
                        "to make sure run inferences and check result individuals.");
        res.createContext(srcClass, dstClass, targetFunction)
                .addPropertyBridge(propertyFunction1, dstProp);
        Assert.assertEquals(1, res.contexts().count());
        Assert.assertEquals(2, res.ontologies().count());
        Assert.assertEquals(srcClass, res.contexts().map(Context::getSource).findFirst().orElseThrow(AssertionError::new));
        Assert.assertEquals(dstClass, res.contexts().map(Context::getTarget).findFirst().orElseThrow(AssertionError::new));
        return res;
    }

    @Test
    @Override
    public void testInference() {
        OntGraphModel src = assembleSource();
        TestUtils.debug(src);
        List<OntNDP> props = src.listDataProperties()
                .sorted(Comparator.comparing(Resource::getURI)).collect(Collectors.toList());
        List<OntIndividual.Named> srcIndividuals = src.listNamedIndividuals()
                .sorted(Comparator.comparing(Resource::getURI)).collect(Collectors.toList());
        Assert.assertEquals(3, props.size());
        Assert.assertEquals(3, srcIndividuals.size());

        String v11 = TestUtils.getStringValue(srcIndividuals.get(0), props.get(0));
        String v12 = TestUtils.getStringValue(srcIndividuals.get(0), props.get(1));
        String v13 = TestUtils.getStringValue(srcIndividuals.get(0), props.get(2));
        String v21 = TestUtils.getStringValue(srcIndividuals.get(1), props.get(0));
        String v22 = TestUtils.getStringValue(srcIndividuals.get(1), props.get(1));
        String v23 = TestUtils.getStringValue(srcIndividuals.get(1), props.get(2));

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
        List<String> actualValues = dstIndividuals.stream()
                .flatMap(i -> i.objects(dstProp, Literal.class))
                .map(Literal::getString)
                .sorted()
                .collect(Collectors.toList());
        LOGGER.debug("Values: {}", actualValues);

        String valueTemplate = "%2, %1--SOME VALUE???%3";
        List<String> expectedValues = Arrays.asList(
                valueTemplate.replace("%1", v11).replace("%2", v12).replace("%3", v13),
                valueTemplate.replace("%1", v21).replace("%2", v22).replace("%3", v23)
        );
        Collections.sort(expectedValues);
        Assert.assertEquals("Wrong values!", expectedValues, actualValues);
    }
}

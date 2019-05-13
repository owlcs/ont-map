/*
 * This file is part of the ONT MAP.
 * The contents of this file are subject to the Apache License, Version 2.0.
 * Copyright (c) 2018, Avicomp Services, AO
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import ru.avicomp.ontapi.jena.model.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Created by @szuev on 25.04.2018.
 */
public class NestedFuncMapTest extends MapTestData1 {
    private static final Logger LOGGER = LoggerFactory.getLogger(NestedFuncMapTest.class);

    @Override
    public MapModel assembleMapping(MapManager manager, OntGraphModel src, OntGraphModel dst) {
        OntClass dstClass = TestUtils.findOntEntity(dst, OntClass.class, "TargetClass1");
        return createMapping(manager, src, dst, () -> manager.getFunction(SPINMAPL.changeNamespace.getURI())
                .create()
                .add(SPINMAPL.targetNamespace.getURI(),
                        manager.getFunction(manager.prefixes().expandPrefix("afn:namespace"))
                                .create().add(SP.arg1.getURI(), dstClass.getURI()))
                .build());
    }

    @Test
    public void testValidateMapping() {
        MapModel m = assembleMapping();
        OntClass sc = TestUtils.findOntEntity(m.asGraphModel(), OntClass.class, "SourceClass1");
        OntClass tc = TestUtils.findOntEntity(m.asGraphModel(), OntClass.class, "TargetClass1");
        OntNDP sp1 = TestUtils.findOntEntity(m.asGraphModel(), OntNDP.class, "sourceDataProperty1");
        OntNDP sp2 = TestUtils.findOntEntity(m.asGraphModel(), OntNDP.class, "sourceDataProperty2");
        OntNDP sp3 = TestUtils.findOntEntity(m.asGraphModel(), OntNDP.class, "sourceDataProperty3");
        OntNDP tp = TestUtils.findOntEntity(m.asGraphModel(), OntNDP.class, "targetDataProperty2");

        Assert.assertEquals(2, m.rules().count());
        MapContext context = m.contexts().findFirst().orElseThrow(AssertionError::new);
        Assert.assertEquals(sc, context.getSource());
        Assert.assertEquals(tc, context.getTarget());

        Assert.assertEquals(String.format("spinmapl:changeNamespace(afn:namespace(%s))",
                m.asGraphModel().shortForm(tc.getURI())), context.getMapping().toString());
        Assert.assertNull(context.getFilter());

        PropertyBridge p = context.properties().findFirst().orElseThrow(AssertionError::new);

        Assert.assertEquals(tp, p.getTarget());

        String propertyMappingFunc = String.format("spinmapl:concatWithSeparator(" +
                        "?arg1=%s, " +
                        "?arg2=spinmapl:concatWithSeparator(?arg1=%s, " +
                        "?arg2=spinmapl:concatWithSeparator(?arg1=\"%s\", ?arg2=%s, ?separator=\"%s\"), " +
                        "?separator=\"%s\"), " +
                        "?separator=\"%s\")",
                m.asGraphModel().shortForm(sp2.getURI()),
                m.asGraphModel().shortForm(sp1.getURI()),
                "SOME VALUE",
                m.asGraphModel().shortForm(sp3.getURI()),
                "???", "--", ", ");
        LOGGER.debug(propertyMappingFunc);
        Assert.assertEquals(propertyMappingFunc, p.getMapping().toString());
        Assert.assertNull(p.getFilter());
    }

    @Test
    public void testChangeTargetFunction() {
        MapModel m = assembleMapping();
        MapManager manager = m.getManager();
        OntCE sc = m.contexts().map(MapContext::getSource).findFirst().orElseThrow(AssertionError::new);
        OntCE tc = m.contexts().map(MapContext::getTarget).findFirst().orElseThrow(AssertionError::new);
        MapFunction composeURI = manager.getFunction(SPINMAPL.composeURI);
        MapFunction.Call targetFunction = composeURI.create().addLiteral(SPINMAPL.template, tc.getNameSpace() + "{?1}").build();
        m.createContext(sc, tc, targetFunction);
        TestUtils.debug(m);

        Assert.assertEquals(2, m.rules().count());
        List<MapFunction> funcs = m.contexts().map(MapContext::getMapping).map(MapFunction.Call::getFunction).collect(Collectors.toList());
        Assert.assertEquals(1, funcs.size());
        Assert.assertEquals(composeURI, funcs.get(0));
        Assert.assertEquals(2, m.rules().flatMap(MapResource::functions).count());

        OntGraphModel src = m.ontologies().filter(o -> o.classes().anyMatch(sc::equals)).findFirst().orElseThrow(AssertionError::new);
        OntGraphModel dst = m.ontologies().filter(o -> o.classes().anyMatch(tc::equals)).findFirst().orElseThrow(AssertionError::new);

        LOGGER.info("Run inference.");
        manager.getInferenceEngine(m).run(src, dst);
        TestUtils.debug(dst);

        LOGGER.info("Validate.");
        validateAfterInference(src, dst);

    }

    @Test
    @Override
    public void testInference() {
        OntGraphModel src = assembleSource();
        TestUtils.debug(src);

        OntGraphModel dst = assembleTarget();
        TestUtils.debug(dst);

        MapManager manager = manager();
        MapModel mapping = assembleMapping(manager, src, dst);
        TestUtils.debug(mapping);

        LOGGER.info("Run inference.");
        manager.getInferenceEngine(mapping).run(src, dst);
        TestUtils.debug(dst);

        LOGGER.info("Validate.");
        validateAfterInference(src, dst);
    }

    MapModel createMapping(MapManager manager,
                           OntGraphModel src,
                           OntGraphModel dst,
                           Supplier<MapFunction.Call> targetFunctionMaker) {
        OntClass srcClass = TestUtils.findOntEntity(src, OntClass.class, "SourceClass1");
        OntClass dstClass = TestUtils.findOntEntity(dst, OntClass.class, "TargetClass1");
        List<OntNDP> props = src.dataProperties().sorted(Comparator.comparing(Resource::getURI))
                .collect(Collectors.toList());
        Assert.assertEquals(3, props.size());
        OntNDP dstProp = dst.dataProperties().findFirst().orElseThrow(AssertionError::new);

        MapFunction.Call targetFunction = targetFunctionMaker.get();

        MapFunction concatWithSeparator = manager.getFunction(SPINMAPL.concatWithSeparator.getURI());
        // concat template: %2, %1--SOME VALUE???%3
        MapFunction.Call propertyFunction = concatWithSeparator
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
        TestUtils.debug(propertyFunction, pm);

        MapModel res = createMappingModel(manager,
                "Please note: TopBraid Composer (5.5.1) has a problem with displaying diagram.\n" +
                        "Don't worry: that's just its problem:\n" +
                        "to make sure run inferences and check result individuals.");
        res.createContext(srcClass, dstClass, targetFunction)
                .addPropertyBridge(propertyFunction, dstProp);
        Assert.assertEquals(1, res.contexts().count());
        Assert.assertEquals(2, res.ontologies().count());
        Assert.assertEquals(srcClass, res.contexts().map(MapContext::getSource).findFirst().orElseThrow(AssertionError::new));
        Assert.assertEquals(dstClass, res.contexts().map(MapContext::getTarget).findFirst().orElseThrow(AssertionError::new));
        return res;
    }

    void validateAfterInference(OntGraphModel src, OntGraphModel dst) {
        OntNDP dstProp = dst.dataProperties().findFirst().orElseThrow(AssertionError::new);
        List<OntNDP> props = src.dataProperties()
                .sorted(Comparator.comparing(Resource::getURI)).collect(Collectors.toList());
        List<OntIndividual.Named> srcIndividuals = src.namedIndividuals()
                .sorted(Comparator.comparing(Resource::getURI)).collect(Collectors.toList());
        Assert.assertEquals(3, props.size());
        Assert.assertEquals(3, srcIndividuals.size());

        String v11 = TestUtils.getStringValue(srcIndividuals.get(0), props.get(0));
        String v12 = TestUtils.getStringValue(srcIndividuals.get(0), props.get(1));
        String v13 = TestUtils.getStringValue(srcIndividuals.get(0), props.get(2));
        String v21 = TestUtils.getStringValue(srcIndividuals.get(1), props.get(0));
        String v22 = TestUtils.getStringValue(srcIndividuals.get(1), props.get(1));
        String v23 = TestUtils.getStringValue(srcIndividuals.get(1), props.get(2));

        Assert.assertEquals(srcIndividuals.size(), dst.namedIndividuals().count());
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
        commonValidate(dst);
    }


}

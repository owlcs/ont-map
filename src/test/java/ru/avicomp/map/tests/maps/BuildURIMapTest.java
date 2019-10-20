/*
 * This file is part of the ONT MAP.
 * The contents of this file are subject to the Apache License, Version 2.0.
 * Copyright (c) 2019, The University of Manchester, owl.cs group.
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

package ru.avicomp.map.tests.maps;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.RDFS;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.MapContext;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.MapModel;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.model.*;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * TODO: For developing. Will be moved/renamed
 * Created by @szuev on 14.04.2018.
 */
public class BuildURIMapTest extends MapTestData1 {
    private final Logger LOGGER = LoggerFactory.getLogger(BuildURIMapTest.class);

    private static final String SEPARATOR = "=&=";
    private static final String TEMPLATE = "Individual-%s-%s";
    private static final String DST_INDIVIDUAL_LABEL = "Created by spin";

    @Test
    @Override
    public void testInference() {
        OntGraphModel src = assembleSource();
        TestUtils.debug(src);
        List<OntNDP> props = src.dataProperties()
                .sorted(Comparator.comparing(Resource::getURI)).collect(Collectors.toList());
        List<OntIndividual.Named> named = src.namedIndividuals()
                .sorted(Comparator.comparing(Resource::getURI)).collect(Collectors.toList());
        List<OntIndividual.Anonymous> anons = src.ontObjects(OntIndividual.Anonymous.class)
                .collect(Collectors.toList());
        Assert.assertEquals(3, props.size());
        Assert.assertEquals(3, named.size());
        Assert.assertEquals(1, anons.size());

        OntGraphModel dst = assembleTarget();
        TestUtils.debug(dst);
        OntNDP dstProp = dst.dataProperties().findFirst().orElseThrow(AssertionError::new);

        MapManager manager = manager();
        MapModel mapping = assembleMapping(manager, src, dst);
        TestUtils.debug(mapping);

        LOGGER.info("Run inference.");
        manager.getInferenceEngine(mapping).run(src, dst);
        TestUtils.debug(dst);

        LOGGER.info("Validate.");
        OntClass dstClass = dst.classes().findFirst().orElseThrow(AssertionError::new);
        // one individual is skipped from inferencing:
        Assert.assertEquals(3, dst.statements(null, RDF.type, dstClass).count());

        String v11 = getDataPropertyAssertionStringValue(named.get(0), props.get(0));
        String v12 = getDataPropertyAssertionStringValue(named.get(0), props.get(1));
        String v21 = getDataPropertyAssertionStringValue(named.get(1), props.get(0));
        String v22 = getDataPropertyAssertionStringValue(named.get(1), props.get(1));
        String v31 = getDataPropertyAssertionStringValue(anons.get(0), props.get(0));
        String v32 = getDataPropertyAssertionStringValue(anons.get(0), props.get(1));

        String ns = dst.getID().getURI() + "#";
        Resource i1 = ResourceFactory.createResource(ns + String.format(TEMPLATE, v11, v12).replace(" ", "_"));
        Resource i2 = ResourceFactory.createResource(ns + String.format(TEMPLATE, v21, v22).replace(" ", "_"));
        Resource i3 = ResourceFactory.createResource(ns + String.format(TEMPLATE, v31, v32).replace(" ", "_"));
        Literal v1 = ResourceFactory.createStringLiteral(v12 + SEPARATOR + v11);
        Literal v2 = ResourceFactory.createStringLiteral(v22 + SEPARATOR + v21);
        Literal v3 = ResourceFactory.createStringLiteral(v32 + SEPARATOR + v31);

        assertDataPropertyAssertion(dst, i1, dstProp, v1);
        assertDataPropertyAssertion(dst, i2, dstProp, v2);
        assertDataPropertyAssertion(dst, i3, dstProp, v3);
        assertIndividualLabel(dst, i1);
        assertIndividualLabel(dst, i2);
        assertIndividualLabel(dst, i3);

        OntIndividual in3 = i3.inModel(dst).as(OntIndividual.Named.class);
        OntIndividual in2 = i2.inModel(dst).as(OntIndividual.Named.class);
        OntIndividual in1 = i1.inModel(dst).as(OntIndividual.Named.class);
        Assert.assertEquals(dstClass, in3.classes().findFirst().orElseThrow(AssertionError::new));
        Assert.assertEquals(dstClass, in2.classes().findFirst().orElseThrow(AssertionError::new));
        Assert.assertEquals(dstClass, in1.classes().findFirst().orElseThrow(AssertionError::new));
    }

    private static String getDataPropertyAssertionStringValue(OntIndividual individual, OntNDP property) {
        return individual.objects(property, Literal.class)
                .map(Literal::getString).findFirst().orElseThrow(AssertionError::new);
    }

    private static void assertDataPropertyAssertion(OntGraphModel m, Resource i, OntNDP p, Literal v) {
        Assert.assertTrue(String.format("Can't find [%s %s '%s']",
                m.shortForm(i.getURI()), m.shortForm(p.getURI()), m.shortForm(v.toString())), m.contains(i, p, v));
    }

    private static void assertIndividualLabel(OntGraphModel m, Resource i) {
        List<OntStatement> annotations = i.inModel(m).as(OntObject.class).annotations().collect(Collectors.toList());
        Assert.assertEquals(1, annotations.size());
        Assert.assertEquals(RDFS.label, annotations.get(0).getPredicate());
        Assert.assertEquals(DST_INDIVIDUAL_LABEL, annotations.get(0).getObject().asLiteral().getString());
    }

    @Override
    public MapModel assembleMapping(MapManager manager, OntGraphModel src, OntGraphModel dst) {
        OntClass srcClass = src.classes().findFirst().orElseThrow(AssertionError::new);
        OntClass dstClass = dst.classes().findFirst().orElseThrow(AssertionError::new);
        List<OntNDP> props = src.dataProperties()
                .sorted(Comparator.comparing(Resource::getURI)).collect(Collectors.toList());
        Assert.assertEquals(3, props.size());
        OntNDP dstProp = dst.dataProperties().findFirst().orElseThrow(AssertionError::new);

        MapFunction.Call targetFunction = manager.getFunction(SPINMAPL.buildURI2.getURI())
                .create()
                .add(SP.arg1.getURI(), props.get(0).getURI())
                .add(SP.arg2.getURI(), props.get(1).getURI())
                .add(SPINMAPL.template.getURI(), "target:" + String.format(TEMPLATE, "{?1}", "{?2}"))
                .build();
        MapFunction.Call propertyFunction1 = manager.getFunction(SPINMAPL.concatWithSeparator.getURI())
                .create()
                .add(SP.arg1.getURI(), props.get(1).getURI())
                .add(SP.arg2.getURI(), props.get(0).getURI())
                .add(SPINMAPL.separator.getURI(), SEPARATOR)
                .build();
        MapFunction.Call propertyFunction2 = manager.getFunction(SPINMAP.equals.getURI())
                .create()
                .add(SP.arg1.getURI(), DST_INDIVIDUAL_LABEL)
                .build();

        PrefixMapping pm = PrefixMapping.Factory.create()
                .setNsPrefixes(manager.prefixes())
                .setNsPrefixes(srcClass.getModel())
                .setNsPrefixes(dstClass.getModel()).lock();

        TestUtils.debug(targetFunction, pm);
        TestUtils.debug(propertyFunction1, pm);
        TestUtils.debug(propertyFunction2, pm);

        MapModel res = createMappingModel(manager,
                "Used functions: spinmapl:buildURI2, spinmapl:concatWithSeparator, spinmap:equals");

        res.createContext(srcClass, dstClass, targetFunction)
                .addPropertyBridge(propertyFunction1, dstProp).getContext()
                .addPropertyBridge(propertyFunction2, dst.getRDFSLabel());
        Assert.assertEquals(1, res.contexts().count());
        Assert.assertEquals(2, res.ontologies().count());
        Assert.assertEquals(srcClass, res.contexts()
                .map(MapContext::getSource).findFirst().orElseThrow(AssertionError::new));
        Assert.assertEquals(dstClass, res.contexts()
                .map(MapContext::getTarget).findFirst().orElseThrow(AssertionError::new));
        return res;
    }


}

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

import org.junit.Assert;
import org.junit.Test;
import org.topbraid.spin.vocabulary.SP;
import ru.avicomp.map.*;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.model.*;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Created by @szuev on 17.05.2018.
 */
public class GroupConcatTest extends AbstractMapTest {

    @Override
    public MapModel assembleMapping(MapManager manager, OntGraphModel src, OntGraphModel dst) {
        MapModel res = createMappingModel(manager, "Used functions: spinmapl:composeURI, avc:groupConcat, avc:asIRI, avc:currentIndividual");
        OntClass srcClass = TestUtils.findOntEntity(src, OntClass.class, "SourceClass1");
        OntClass dstClass = TestUtils.findOntEntity(dst, OntClass.class, "TargetClass1");
        OntNDP srcProp = TestUtils.findOntEntity(src, OntNDP.class, "sourceDataProperty1");
        OntNDP dstProp = TestUtils.findOntEntity(dst, OntNDP.class, "targetDataProperty1");
        MapContext context = res.createContext(srcClass, dstClass,
                manager.getFunction(SPINMAPL.composeURI.getURI()).create()
                        .addLiteral(SPINMAPL.template, "http://{?1}").build());

        MapFunction groupConcat = manager.getFunction(AVC.groupConcat);
        MapFunction getIRI = manager.getFunction(AVC.asIRI);
        MapFunction get = manager.getFunction(AVC.currentIndividual);

        context.addPropertyBridge(groupConcat.create()
                .addLiteral(SPINMAPL.separator, ",")
                .addFunction(SP.arg2, get.create())
                .add(SP.arg1.getURI(), getIRI.create()
                        .addProperty(SP.arg1, srcProp))
                .build(), dstProp);
        return res;
    }

    @Test
    public void testInference() {
        OntGraphModel s = assembleSource();
        TestUtils.debug(s);
        OntGraphModel t = assembleTarget();
        TestUtils.debug(t);
        MapManager manager = manager();
        MapModel map = assembleMapping(manager, s, t);
        TestUtils.debug(map);

        map.runInference(s.getGraph(), t.getGraph());
        TestUtils.debug(t);
        List<OntIndividual> individuals = t.individuals().collect(Collectors.toList());
        Assert.assertEquals(3, individuals.size());
        validateIndividual(t, "http://individual-1", "A,B,C");
        validateIndividual(t, "http://individual-3", "23,D");
        validateIndividual(t, "http://individual-2", "2,4.34");
        AbstractMapTest.commonValidate(t);
    }

    @Test
    public void testDeleteContext() {
        MapModel m = assembleMapping();
        TestUtils.debug(m);
        MapContext context = m.contexts().findFirst().orElseThrow(AssertionError::new);
        m.deleteContext(context);
        TestUtils.debug(m);
        Assert.assertEquals(0, m.contexts().count());
        Assert.assertEquals(4, m.asGraphModel().getBaseGraph().size());
        Assert.assertEquals(1, m.asGraphModel().imports().count());
    }

    @Test
    public void testDeletePropertyBridge() {
        MapManager manager = manager();
        MapModel m = assembleMapping(manager);
        MapContext c = m.contexts().findFirst().orElseThrow(AssertionError::new);
        PropertyBridge p = c.properties().findFirst().orElseThrow(AssertionError::new);
        MapContext c2 = c.deletePropertyBridge(p);
        Assert.assertSame(c, c2);
        TestUtils.debug(m);
        Assert.assertEquals(1, m.contexts().count());
        Assert.assertEquals(2, m.ontologies().count());
        Assert.assertEquals(1, m.rules().count());
        long expected = TestUtils.getMappingConfiguration(manager).generateNamedIndividuals() ? 29 : 18;
        Assert.assertEquals(expected, m.asGraphModel().getBaseGraph().size());
    }

    private void validateIndividual(OntGraphModel m, String name, String value) {
        OntIndividual i = m.individuals()
                .filter(s -> Objects.equals(s.getURI(), name))
                .findFirst().orElseThrow(AssertionError::new);
        List<OntStatement> assertions = i.positiveAssertions().collect(Collectors.toList());
        Assert.assertEquals(1, assertions.size());
        String actual = assertions.get(0).getObject().asLiteral().getString();
        Assert.assertEquals(value, actual);
    }

    @Override
    public OntGraphModel assembleSource() {
        OntGraphModel m = createDataModel("source");
        String ns = m.getID().getURI() + "#";
        OntClass class1 = m.createOntEntity(OntClass.class, ns + "SourceClass1");
        OntNDP prop1 = m.createOntEntity(OntNDP.class, ns + "sourceDataProperty1");
        OntNDP prop2 = m.createOntEntity(OntNDP.class, ns + "sourceDataProperty2");
        OntNDP prop3 = m.createOntEntity(OntNDP.class, ns + "sourceDataProperty3");
        prop1.addDomain(class1);
        prop2.addDomain(class1);
        prop3.addDomain(class1);
        OntIndividual.Named individual1 = class1.createIndividual(ns + "individual-1");
        OntIndividual.Named individual2 = class1.createIndividual(ns + "individual-2");
        OntIndividual.Named individual3 = class1.createIndividual(ns + "individual-3");

        // data property assertions:
        individual1.addProperty(prop1, "C");
        individual1.addProperty(prop1, "A");
        individual1.addProperty(prop1, "B");

        individual2.addProperty(prop1, m.createTypedLiteral(2));
        individual2.addProperty(prop1, m.createTypedLiteral(4.34));

        individual3.addProperty(prop1, "D");
        individual3.addProperty(prop1, m.createTypedLiteral(23));
        return m;
    }

    @Override
    public OntGraphModel assembleTarget() {
        OntGraphModel m = createDataModel("target");
        String ns = m.getID().getURI() + "#";
        OntClass clazz = m.createOntEntity(OntClass.class, ns + "TargetClass1");
        m.createOntEntity(OntNDP.class, ns + "targetDataProperty1").addDomain(clazz);
        m.createOntEntity(OntNDP.class, ns + "targetDataProperty2").addDomain(clazz);
        return m;
    }
}

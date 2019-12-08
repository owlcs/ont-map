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

package com.github.owlcs.map.tests.maps;

import com.github.owlcs.map.MapContext;
import com.github.owlcs.map.MapManager;
import com.github.owlcs.map.MapModel;
import com.github.owlcs.map.utils.TestUtils;
import com.github.owlcs.ontapi.jena.model.OntClass;
import com.github.owlcs.ontapi.jena.model.OntGraphModel;
import com.github.owlcs.ontapi.jena.model.OntIndividual;
import com.github.owlcs.ontapi.jena.model.OntNDP;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by @szuev on 25.04.2018.
 */
abstract class MapTestData1 extends AbstractMapTest {

    @Test
    public abstract void testInference();

    @Test
    public void testDeleteContext() {
        OntGraphModel src = assembleSource();
        OntGraphModel dst = assembleTarget();
        MapManager manager = manager();
        MapModel mapping = assembleMapping(manager, src, dst);
        Assert.assertEquals(2, mapping.ontologies().count());
        List<MapContext> contexts = mapping.contexts().collect(Collectors.toList());
        Assert.assertEquals(1, contexts.size());
        mapping = mapping.deleteContext(contexts.get(0));
        TestUtils.debug(mapping);
        Assert.assertEquals(0, mapping.contexts().count());
        Assert.assertEquals(4, mapping.asGraphModel().getBaseGraph().size());
        Assert.assertEquals(0, mapping.ontologies().count());
        Assert.assertEquals(1, mapping.asGraphModel().imports().count());
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
        OntClass class2 = m.createOntEntity(OntClass.class, ns + "SubClass1").addSuperClass(class1);

        // individuals:
        OntIndividual individual1 = class2.createIndividual(ns + "a");
        OntIndividual individual2 = class2.createIndividual(ns + "b");
        OntIndividual individual3 = class2.createIndividual();
        class1.createIndividual(ns + "c");

        // data property assertions:
        individual1.addProperty(prop1, "x y z", "e");
        individual1.addProperty(prop2, ResourceFactory.createTypedLiteral(2));
        individual1.addProperty(prop3, "individual#1 - property#3 value");
        individual2.addProperty(prop1, "A");
        individual2.addProperty(prop2, "B");
        individual2.addProperty(prop3, "individual#2 - property#3 value");
        individual3.addProperty(prop1, "dp1");
        individual3.addProperty(prop2, "dp2");
        individual3.addProperty(prop3, "dp3");
        return m;
    }

    @Override
    public OntGraphModel assembleTarget() {
        OntGraphModel m = createDataModel("target");
        String ns = m.getID().getURI() + "#";
        OntClass clazz = m.createOntEntity(OntClass.class, ns + "TargetClass1");
        m.createOntEntity(OntNDP.class, ns + "targetDataProperty2").addDomain(clazz);
        return m;
    }

    @Override
    public String getDataNameSpace() {
        return getNameSpace(MapTestData1.class);
    }
}

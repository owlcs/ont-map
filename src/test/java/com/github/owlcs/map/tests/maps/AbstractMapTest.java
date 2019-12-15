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

import com.github.owlcs.map.Managers;
import com.github.owlcs.map.MapFunction;
import com.github.owlcs.map.MapManager;
import com.github.owlcs.map.MapModel;
import com.github.owlcs.map.utils.TestUtils;
import com.github.owlcs.ontapi.jena.OntModelFactory;
import com.github.owlcs.ontapi.jena.model.OntModel;
import com.github.owlcs.ontapi.jena.model.OntObject;
import com.github.owlcs.ontapi.jena.model.OntStatement;
import org.apache.jena.shared.PrefixMapping;
import org.junit.Assert;
import org.junit.BeforeClass;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by @szuev on 14.04.2018.
 */
@SuppressWarnings("WeakerAccess")
public abstract class AbstractMapTest {

    private static MapManager manager;

    @BeforeClass
    public static void before() {
        manager = createManager();
    }

    private static MapManager createManager() {
        return Managers.createMapManager();
    }

    public abstract MapModel assembleMapping(MapManager manager, OntModel src, OntModel dst);

    public abstract OntModel assembleSource();

    public abstract OntModel assembleTarget();

    public MapManager manager() {
        return manager == null ? manager = createManager() : manager;
    }

    public MapModel assembleMapping() {
        return assembleMapping(manager());
    }

    public MapModel assembleMapping(MapManager manager) {
        return assembleMapping(manager, assembleSource(), assembleTarget());
    }

    public static String getNameSpace(Class clazz) {
        return String.format("http://example.com/%s", clazz.getSimpleName());
    }

    public String getDataNameSpace() {
        return getNameSpace(getClass());
    }

    public String getMapNameSpace() {
        return getNameSpace(getClass());
    }

    public OntModel createDataModel(String name) {
        OntModel res = OntModelFactory.createModel();
        res.setNsPrefixes(OntModelFactory.STANDARD);
        res.setID(getDataNameSpace() + "/" + name);
        String ns = res.getID().getURI() + "#";
        res.setNsPrefix(name, ns);
        return res;
    }

    public MapModel createMappingModel(MapManager manager, String description) {
        MapModel res = manager.createMapModel();
        // TopBraid Composer (gui, not spin) has difficulties with anonymous ontologies:
        res.asGraphModel().setID(getMapNameSpace() + "/map").addComment(description, null);
        return res;
    }

    public static void commonValidate(OntModel m) {
        // check there is no any garbage in the model:
        List<OntObject> undeclaredIndividuals = TestUtils.plainAssertions(m)
                .map(OntStatement::getSubject)
                .filter(o -> !o.types().findFirst().isPresent())
                .collect(Collectors.toList());
        Assert.assertEquals("Model has unattached assertions: " + undeclaredIndividuals, 0, undeclaredIndividuals.size());
    }

    public static String toMessage(PrefixMapping pm, MapFunction... functions) {
        return Arrays.stream(functions).map(MapFunction::name).map(pm::shortForm).collect(Collectors.joining(", "));
    }
}

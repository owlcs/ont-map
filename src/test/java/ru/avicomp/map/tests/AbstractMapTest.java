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

import org.apache.jena.shared.PrefixMapping;
import org.junit.Assert;
import org.junit.BeforeClass;
import ru.avicomp.map.Managers;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.MapModel;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntObject;
import ru.avicomp.ontapi.jena.model.OntStatement;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by @szuev on 14.04.2018.
 */
public abstract class AbstractMapTest {

    private static MapManager manager;

    @BeforeClass
    public static void before() {
        manager = createManager();
    }

    private static MapManager createManager() {
        return Managers.createMapManager();
    }

    public abstract MapModel assembleMapping(MapManager manager, OntGraphModel src, OntGraphModel dst);

    public abstract OntGraphModel assembleSource();

    public abstract OntGraphModel assembleTarget();

    MapManager manager() {
        return manager == null ? manager = createManager() : manager;
    }

    MapModel assembleMapping() {
        return assembleMapping(manager(), assembleSource(), assembleTarget());
    }

    static String getNameSpace(Class clazz) {
        return String.format("http://example.com/%s", clazz.getSimpleName());
    }

    public String getDataNameSpace() {
        return getNameSpace(getClass());
    }

    public String getMapNameSpace() {
        return getNameSpace(getClass());
    }

    OntGraphModel createDataModel(String name) {
        OntGraphModel res = OntModelFactory.createModel();
        res.setNsPrefixes(OntModelFactory.STANDARD);
        res.setID(getDataNameSpace() + "/" + name);
        String ns = res.getID().getURI() + "#";
        res.setNsPrefix(name, ns);
        return res;
    }

    MapModel createMappingModel(MapManager manager, String description) {
        MapModel res = manager.createMapModel();
        // TopBraid Composer (gui, not spin) has difficulties with anonymous ontologies:
        res.setID(getMapNameSpace() + "/map")
                .addComment(description, null);
        return res;
    }

    static void commonValidate(OntGraphModel m) {
        // check there is no any garbage in the model:
        List<OntObject> undeclaredIndividuals = TestUtils.plainAssertions(m)
                .map(OntStatement::getSubject)
                .filter(o -> !o.types().findFirst().isPresent())
                .collect(Collectors.toList());
        Assert.assertEquals("Model has unattached assertions: " + undeclaredIndividuals, 0, undeclaredIndividuals.size());
    }

    static String toMessage(PrefixMapping pm, MapFunction... functions) {
        return Arrays.stream(functions).map(MapFunction::name).map(pm::shortForm).collect(Collectors.joining(", "));
    }
}

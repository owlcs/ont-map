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

package ru.avicomp.map.tools;

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.topbraid.spin.vocabulary.SPL;
import ru.avicomp.map.spin.SPINLibrary;
import ru.avicomp.map.spin.SpinModelConfig;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.utils.AutoPrefixListener;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.impl.conf.OntModelConfig;
import ru.avicomp.ontapi.jena.impl.conf.OntPersonality;
import ru.avicomp.ontapi.jena.impl.conf.PersonalityBuilder;
import ru.avicomp.ontapi.jena.model.OntGraphModel;

/**
 * Created by @szuev on 09.06.2018.
 */
abstract class LibraryMaker {


    static Resource createConstraint(Model m, Property predicate, Resource returnType) {
        return createConstraint(m, predicate).addProperty(SPL.valueType, returnType);
    }

    static Resource createConstraint(Model m, Property predicate) {
        return m.createResource().addProperty(SPL.predicate, predicate);
    }

    static OntGraphModel createModel(Graph graph) {
        OntPersonality p = PersonalityBuilder.from(OntModelConfig.ONT_PERSONALITY_LAX)
                .addPersonality(SpinModelConfig.LIB_PERSONALITY).build();
        OntGraphModel res = OntModelFactory.createModel(graph, p).setNsPrefix("avc", AVC.NS);
        AutoPrefixListener.addAutoPrefixListener((UnionGraph) res.getGraph(), SPINLibrary.prefixes());
        return res;
    }


}

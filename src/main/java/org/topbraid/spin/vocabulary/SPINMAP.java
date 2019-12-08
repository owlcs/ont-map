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

package org.topbraid.spin.vocabulary;

import com.github.owlcs.map.spin.system.Resources;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;

/**
 * Vocabulary for {@code http://spinrdf.org/spinmap}
 * Created by @szuev on 07.04.2018.
 * @see SP description about reasons of copy-pasting
 * @see <a href='http://spinrdf.org/spinmap#'>spinmap</a>
 */
@SuppressWarnings({"unused"})
public class SPINMAP {

    public static final String BASE_URI = Resources.SPINMAP.getURI();
    public static final String NS = BASE_URI + "#";
    public static final String PREFIX = "spinmap";

    public static final String SOURCE_PREDICATE = "sourcePredicate";
    public static final String TARGET_PREDICATE = "targetPredicate";

    public static final Resource Conditional_Mapping_1 = resource("Conditional-Mapping-1");
    public static final Resource Conditional_Mapping_1_1 = resource("Conditional-Mapping-1-1");

    public static final Resource Context = resource("Context");
    public static final Resource Mapping = resource("Mapping");
    public static final Resource Mapping_0_1 = Mapping(0, 1);
    public static final Resource Mapping_1 = resource("Mapping-1");
    public static final Resource Mapping_1_1 = Mapping(1, 1);
    public static final Resource Mapping_1_1_Inverse = resource("Mapping-1-1-Inverse");
    public static final Resource Mapping_1_Path_1 = resource("Mapping-1-Path-1");
    public static final Resource Mapping_2_1 = Mapping(2, 1);
    public static final Resource SplitMapping_1_1 = resource("SplitMapping-1-1");
    public static final Resource TargetFunction = resource("TargetFunction");
    public static final Resource TargetFunctions = resource("TargetFunctions");
    public static final Resource TransformationFunction = resource("TransformationFunction");
    public static final Resource TransformationFunctions = resource("TransformationFunctions");

    public static final Property context = property("context");
    public static final Resource equals = resource("equals");
    public static final Property expression = property("expression");
    public static final Property function = property("function");
    public static final Property inverseExpression = property("inverseExpression");
    public static final Property postRule = property("postRule");
    public static final Property predicate = property("predicate");
    public static final Property prepRule = property("prepRule");
    public static final Property rule = property("rule");
    public static final Property separator = property("separator");
    public static final Property shortLabel = property("shortLabel");
    public static final Property source = property("source");
    public static final Property sourceClass = property("sourceClass");
    public static final Property sourcePath = property("sourcePath");
    public static final Property sourcePredicate1 = sourcePredicate(1);
    public static final Property sourcePredicate2 = sourcePredicate(2);
    public static final Property sourcePredicate3 = sourcePredicate(3);
    public static final Resource sourceVariable = resource("_source");
    public static final Property suggestion_0_1 = property("suggestion-0-1");
    public static final Property suggestion_1_1 = property("suggestion-1-1");
    public static final Property suggestionScore = property("suggestionScore");
    public static final Property target = property("target");
    public static final Property targetClass = property("targetClass");
    public static final Property targetPredicate1 = targetPredicate(1);
    public static final Property targetPredicate2 = targetPredicate(2);

    public static final Resource targetResource = resource("targetResource");

    public static final Property template = property("template");
    public static final Property type = property("type");
    public static final Property value = property("value");
    public static final Property value1 = property("value1");
    public static final Property value2 = property("value2");
    public static final Property condition = property("condition");

    public static Resource resource(String local) {
        return ResourceFactory.createResource(NS + local);
    }

    public static Property property(String local) {
        return ResourceFactory.createProperty(NS + local);
    }

    public static Resource Mapping(int i, int j) {
        if (i < 0 || j <= 0) throw new IllegalArgumentException();
        return resource(String.format("Mapping-%d-%d", i, j));
    }

    public static Property targetPredicate(int i) {
        if (i <= 0) throw new IllegalArgumentException();
        return property(TARGET_PREDICATE + i);
    }

    public static Property sourcePredicate(int i) {
        if (i <= 0) throw new IllegalArgumentException();
        return property(SOURCE_PREDICATE + i);
    }

    public static boolean exists(Model model) {
        return model.contains(model.getResource(BASE_URI), RDF.type, OWL.Ontology);
    }

    public static String getURI() {
        return NS;
    }

}

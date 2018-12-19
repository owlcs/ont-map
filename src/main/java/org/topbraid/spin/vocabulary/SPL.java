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

package org.topbraid.spin.vocabulary;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import ru.avicomp.map.spin.SpinModelConfig;
import ru.avicomp.map.spin.system.Resources;

/**
 * A copy-paste from spin.
 * Created by @szuev on 13.04.2018.
 *
 * @see SP description about reasons of copy-pasting
 */
@SuppressWarnings({"unused"})
public class SPL {
    public static final String BASE_URI = Resources.SPL.getURI();
    public static final String NS = BASE_URI + "#";
    public static final String PREFIX = "spl";

    public static final Resource Argument = resource("Argument");
    public static final Resource Attribute = resource("Attribute");
    public static final Resource InferDefaultValue = resource("InferDefaultValue");
    public static final Resource ObjectCountPropertyConstraint = resource("ObjectCountPropertyConstraint");
    public static final Resource primaryKeyProperty = resource("primaryKeyProperty");
    public static final Resource primaryKeyURIStart = resource("primaryKeyURIStart");
    public static final Resource PrimaryKeyPropertyConstraint = resource("PrimaryKeyPropertyConstraint");
    public static final Resource PropertyConstraintTemplates = resource("PropertyConstraintTemplates");
    public static final Resource RunTestCases = resource("RunTestCases");
    public static final Resource SPINOverview = resource("SPINOverview");
    public static final Resource TestCase = resource("TestCase");
    public static final Resource UnionTemplate = resource("UnionTemplate");
    public static final Resource object = resource("object");
    public static final Resource objectCount = resource("objectCount");
    public static final Resource subjectCount = resource("subjectCount");
    public static final Resource StringFunctions = resource("StringFunctions");
    public static final Resource ConstraintTemplate = resource("ConstraintTemplate");
    public static final Resource OntologyFunctions = resource("OntologyFunctions");
    public static final Resource MathematicalFunctions = resource("MathematicalFunctions");
    public static final Resource BooleanFunctions = resource("BooleanFunctions");
    public static final Resource DateFunctions = resource("DateFunctions");
    public static final Resource MiscFunctions = resource("MiscFunctions");
    public static final Resource URIFunctions = resource("URIFunctions");
    public static final Resource max = resource("max");
    public static final Resource min = resource("min");

    public static final Property defaultValue = property("defaultValue");
    public static final Property dynamicEnumRange = property("dynamicEnumRange");
    public static final Property hasValue = property("hasValue");
    public static final Property maxCount = property("maxCount");
    public static final Property minCount = property("minCount");
    public static final Property optional = property("optional");
    public static final Property predicate = property("predicate");
    public static final Property valueType = property("valueType");

    public static Resource resource(String local) {
        return ResourceFactory.createResource(NS + local);
    }

    public static Property property(String local) {
        return ResourceFactory.createProperty(NS + local);
    }

    public static Model getModel() {
        return SpinModelConfig.createSpinModel(Resources.SPL.getGraph());
    }
}

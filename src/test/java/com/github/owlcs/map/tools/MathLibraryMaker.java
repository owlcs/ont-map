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

package com.github.owlcs.map.tools;

import com.github.owlcs.map.spin.system.Resources;
import com.github.owlcs.map.spin.vocabulary.AVC;
import com.github.owlcs.map.spin.vocabulary.MATH;
import com.github.owlcs.ontapi.jena.model.OntGraphModel;
import com.github.owlcs.ontapi.jena.model.OntID;
import com.github.owlcs.ontapi.jena.vocabulary.OWL;
import com.github.owlcs.ontapi.jena.vocabulary.RDF;
import com.github.owlcs.ontapi.jena.vocabulary.XSD;
import org.apache.jena.graph.Factory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDFS;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import org.topbraid.spin.vocabulary.SPL;

/**
 * An utility class to produce <p>avc.math.ttl</p> (see {@code resources/etc} directory).
 * For developing and demonstration.
 * NOTE: Not a part of API or APIs Tests: will be removed.
 * Created by @szuev on 22.05.2018.
 *
 * @see MATH
 */
public class MathLibraryMaker {

    public static void main(String... args) {
        OntGraphModel m = LibraryMaker.createModel(Factory.createGraphMem());
        OntID id = m.setID(Resources.AVC_MATH.getURI());
        id.setVersionIRI(id.getURI() + "#1.0");
        id.addComment("A library that contains mathematical functions for some reason missing in the standard spin delivery.", null);
        id.addProperty(RDFS.seeAlso, m.getResource(MATH.URI));
        id.addAnnotation(m.getAnnotationProperty(OWL.versionInfo), "version 1.0", null);
        id.addImport(AVC.BASE_URI);

        createDoubleFuncWithDoubleArg(MATH.acos.inModel(m), "acos", "arccosine", "Returns the arc cosine of the argument.", null);
        createDoubleFuncWithDoubleArg(MATH.asin.inModel(m), "asin", "arcsine", "Returns the arc sine of the argument.", null);
        createDoubleFuncWithDoubleArg(MATH.atan.inModel(m), "atan", "arctangent", "Returns the arc tangent of the argument.", null);

        createDoubleFuncWithDoubleArg(MATH.cos.inModel(m), "cos", "cosinus", "Returns the cosine of the argument. The argument is an angle in radians.", "Radians");
        createDoubleFuncWithDoubleArg(MATH.exp.inModel(m), "exp", "exponent", "Returns the value of e^x.", null);
        createDoubleFuncWithDoubleArg(MATH.exp10.inModel(m), "exp10", "base-ten exponent", "Returns the value of 10^x.", null);
        createDoubleFuncWithDoubleArg(MATH.log.inModel(m), "log", "natural logarithm", "Returns the natural logarithm of the argument.", null);
        createDoubleFuncWithDoubleArg(MATH.log10.inModel(m), "log10", "base-ten logarithm", "Returns the base-ten logarithm of the argument.", null);
        createDoubleFunction(MATH.pi.inModel(m), "pi", "pi", "Returns an approximation to the mathematical constant π.");

        createDoubleFunction(MATH.atan2.inModel(m), "atan2", "arctangent 2",
                "Returns the angle in radians subtended at the origin by the point on a plane with coordinates (x, y) and the positive x-axis.")
                .addProperty(SPIN.constraint, createDoubleArg(m, SP.arg1, "the ordinate coordinate"))
                .addProperty(SPIN.constraint, createDoubleArg(m, SP.arg2, "the abscissa coordinate"));

        createDoubleFuncWithDoubleArg(MATH.pow.inModel(m), "pow", "power", "Returns the result of raising the first argument to the power of the second.", null)
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg2)
                        .addProperty(SPL.valueType, AVC.numeric));

        createDoubleFuncWithDoubleArg(MATH.sin.inModel(m), "sin", "sine", "Returns the sine of the argument. The argument is an angle in radians.", "Radians");
        createDoubleFuncWithDoubleArg(MATH.sqrt.inModel(m), "sqrt", "square root", "Returns the non-negative square root of the argument.", null);
        createDoubleFuncWithDoubleArg(MATH.tan.inModel(m), "tan", "tangent", "Returns the tangent of the argument. The argument is an angle in radians.", null);

        m.write(System.out, "ttl");
    }

    private static Resource createDoubleFunction(Resource name, String shortLabel, String label, String comment) {
        return name.addProperty(RDF.type, SPIN.Function)
                .addProperty(RDFS.subClassOf, SPL.MathematicalFunctions)
                .addProperty(SPINMAP.shortLabel, shortLabel)
                .addProperty(RDFS.label, label)
                .addProperty(RDFS.comment, comment)
                .addProperty(SPIN.returnType, XSD.xdouble);
    }

    private static Resource createDoubleFuncWithDoubleArg(Resource name,
                                                          String shortLabel,
                                                          String label,
                                                          String comment,
                                                          String argComment) {
        Resource arg = createDoubleArg(name.getModel(), SP.arg1, argComment);
        return createDoubleFunction(name, shortLabel, label, comment).addProperty(SPIN.constraint, arg);
    }

    private static Resource createDoubleArg(Model m, Property predicate, String argComment) {
        Resource arg = m.createResource()
                .addProperty(RDF.type, SPL.Argument)
                .addProperty(SPL.predicate, predicate)
                .addProperty(SPL.valueType, XSD.xdouble);
        if (argComment != null) arg.addProperty(RDFS.comment, argComment);
        return arg;
    }

}

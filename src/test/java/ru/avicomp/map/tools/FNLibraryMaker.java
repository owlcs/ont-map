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

import org.apache.jena.graph.Factory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import org.topbraid.spin.vocabulary.SPL;
import ru.avicomp.map.spin.system.Resources;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.spin.vocabulary.FN;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntID;
import ru.avicomp.ontapi.jena.utils.Models;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.XSD;

/**
 * An utility class to produce <p>avc.fn.ttl</p> (see {@code resources/etc} directory).
 * For developing and demonstration.
 * NOTE: Not a part of API or APIs Tests: will be removed.
 * Created by @szuev on 09.06.2018.
 *
 * @see FN
 */
public class FNLibraryMaker {

    public static void main(String... args) {
        OntGraphModel m = LibraryMaker.createModel(Factory.createGraphMem());
        OntID id = m.setID(Resources.AVC_FN.getURI());
        id.setVersionIRI(id.getURI() + "#1.0");
        id.addComment("XQuery, XPath, and XSLT Functions and Operators.\n" +
                "An addition to the <http://topbraid.org/functions-fn> library.", null);
        id.addProperty(RDFS.seeAlso, m.getResource(FN.URI));
        id.addProperty(RDFS.seeAlso, m.getResource("http://topbraid.org/functions-fn"));
        id.addAnnotation(m.getAnnotationProperty(OWL.versionInfo), "version 1.0", null);
        id.addImport(AVC.BASE_URI);

        // FN:format-number
        FN.format_number.inModel(m)
                .addProperty(RDF.type, SPIN.Function)
                .addProperty(RDFS.subClassOf, SPL.StringFunctions)
                .addProperty(SPIN.returnType, XSD.xstring)
                .addProperty(RDFS.seeAlso, m.getResource("https://www.w3.org/TR/xpath-functions-31/#func-format-number"))
                .addProperty(SPINMAP.shortLabel, "format-number")
                .addProperty(RDFS.label, "formats a numeric literal")
                .addProperty(RDFS.comment, "Returns a string containing a number formatted according to a given picture string, " +
                        "taking account of decimal formats specified in the static context ")
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg1)
                        .addProperty(SPL.valueType, AVC.numeric)
                        .addProperty(RDFS.comment, "Value to format"))
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg2)
                        .addProperty(SPL.optional, Models.TRUE)
                        .addProperty(SPL.valueType, XSD.xstring)
                        .addProperty(RDFS.comment, "Picture"))
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg3)
                        .addProperty(SPL.optional, Models.TRUE)
                        .addProperty(SPL.valueType, XSD.xstring)
                        .addProperty(RDFS.comment, "Decimal format name")
                        .addProperty(RDFS.seeAlso, m.getResource("https://tools.ietf.org/html/bcp47")));

        m.write(System.out, "ttl");
    }
}

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
import com.github.owlcs.ontapi.jena.model.OntGraphModel;
import com.github.owlcs.ontapi.jena.model.OntID;
import com.github.owlcs.ontapi.jena.vocabulary.OWL;
import com.github.owlcs.ontapi.jena.vocabulary.XSD;
import org.apache.jena.graph.Factory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import org.topbraid.spin.vocabulary.SPL;

/**
 * An utility class to produce <p>avc.xsd.ttl</p> (see {@code resources/etc} directory).
 * For developing and demonstration.
 * NOTE: Not a part of API or APIs Tests: will be removed.
 * <p>
 * The library <p>avc.xsd.ttl</p> is a collection of spin-definitions for xsd-types functions,
 * which must be are used to cast datatypes.
 * The library <p>spinmapl.ttl</p> contains only 7 of them ({@code xsd:time}, {@code xsd:float}, {@code xsd:dateTime},
 * {@code xsd:integer}, {@code xsd:boolean}, {@code xsd:date}, {@code xsd:string}),
 * while really there are 29 types (see {@link org.apache.jena.sparql.function.StandardFunctions}
 * and {@link org.apache.jena.sparql.function.CastXSD}).
 * <p>
 * Found the additional following types, that need to be handled somehow:
 * {@code xsd:anyURI}, {@code xsd:byte}, {@code xsd:dayTimeDuration}, {@code xsd:decimal}, {@code xsd:double},
 * {@code xsd:duration}, {@code xsd:gDay}, {@code xsd:gMonth}, {@code xsd:gMonthDay}, {@code xsd:gYear},
 * {@code xsd:gYearMonth}, {@code xsd:int}, {@code xsd:long}, {@code xsd:negativeInteger},
 * {@code xsd:nonNegativeInteger}, {@code xsd:nonPositiveInteger}, {@code xsd:positiveInteger}, {@code xsd:short},
 * {@code xsd:unsignedInt}, {@code xsd:unsignedLong}, {@code xsd:unsignedShort}, {@code xsd:yearMonthDuration}.
 * <p>
 * Created by @szz on 22.11.2018.
 */
public class XSDLibraryMaker {

    public static void main(String... args) {
        OntGraphModel m = LibraryMaker.createModel(Factory.createGraphMem());
        OntID id = m.setID(Resources.AVC_XSD.getURI());
        id.setVersionIRI(id.getURI() + "#1.0");
        id.addComment("A collection of XSD-cast functions.\n" +
                "An addition to the <http://topbraid.org/spinmapl> library.", null);
        id.addProperty(RDFS.seeAlso, m.getResource(XSD.NS));
        id.addAnnotation(m.getAnnotationProperty(OWL.versionInfo), "version 1.0", null);
        id.addImport(AVC.BASE_URI);

        addType(m, XSD.xdouble);
        addType(m, XSD.xlong);
        addType(m, XSD.xbyte);
        addType(m, XSD.xint);
        addType(m, XSD.xshort);

        m.write(System.out, "ttl");
    }

    private static void addType(Model m, Resource r) {
        String sf = m.shortForm(r.getURI());
        r.inModel(m)
                .addProperty(RDF.type, SPIN.Function)
                .addProperty(RDFS.subClassOf, SPIN.Functions)
                .addProperty(SPIN.returnType, r)
                .addProperty(SPINMAP.shortLabel, sf)
                .addProperty(RDFS.label, "casts a given node to " + sf)
                .addProperty(RDFS.comment, "As a SPARQL function, this converts a given node (?arg1) to an " + sf +
                        " literal.")
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg1)
                        .addProperty(SPL.valueType, AVC.undefined)
                        .addProperty(RDFS.comment, "The input value to cast"));
    }
}

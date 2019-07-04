/*
 * This file is part of the ONT MAP.
 * The contents of this file are subject to the Apache License, Version 2.0.
 * Copyright (c) 2019, Avicomp Services, AO
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
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPL;
import ru.avicomp.map.spin.geos.vocabulary.GEO;
import ru.avicomp.map.spin.geos.vocabulary.GEOSPARQL;
import ru.avicomp.map.spin.geos.vocabulary.SPATIAL;
import ru.avicomp.map.spin.geos.vocabulary.UOM;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.ontapi.jena.model.*;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

/**
 * Library maker for GeoSPARQL support.
 * TODO: not ready
 *
 * @see <a href='https://github.com/avicomp/ont-map/issues/27'>issue #27</a>
 * Created by @szz on 02.07.2019.
 */
public class GeoLibraryMaker {

    public static void main(String... args) {
        OntGraphModel m = LibraryMaker.createModel(Factory.createGraphMem());
        m.setNsPrefix(GEOSPARQL.PREFIX, GEOSPARQL.NS)
                .setNsPrefix(GEO.PREFIX, GEO.NS)
                .setNsPrefix(SPATIAL.PREFIX, SPATIAL.NS)
                .setNsPrefix(UOM.PREFIX, UOM.NS);

        OntID id = m.setID(GEO.BASE_URI);

        id.addImport(AVC.BASE_URI);
        id.addImport(GEOSPARQL.BASE_URI);

        m.createDatatype(GEOSPARQL.wktLiteral.getURI());
        OntDT xdouble = m.getDatatype(XSD.xdouble);
        OntDR positiveDouble = m.createRestrictionDataRange(xdouble,
                m.createFacetRestriction(OntFR.MinLength.class, xdouble.createLiteral(0)));
        OntDT km1 = m.createDatatype(UOM.kilometer.getURI()).addEquivalentClass(positiveDouble);
        OntDT km2 = m.createDatatype(UOM.kilometre.getURI()).addEquivalentClass(km1);
        OntDT km3 = m.createDatatype(UOM.URN.kilometer.getURI()).addEquivalentClass(km1);
        OntDT m1 = m.createDatatype(UOM.meter.getURI()).addEquivalentClass(positiveDouble);
        OntDT m2 = m.createDatatype(UOM.metre.getURI()).addEquivalentClass(m1);
        OntDT m3 = m.createDatatype(UOM.URN.metre.getURI()).addEquivalentClass(m1);

        SPATIAL.convertLatLon.inModel(m)
                .addProperty(RDF.type, SPIN.Function)
                .addProperty(RDFS.subClassOf, GEO.GeoSPARQLFunctions)
                .addProperty(SPIN.returnType, GEOSPARQL.wktLiteral)
                .addProperty(RDFS.comment, "Converts Lat and Lon double values into WKT string of a Point with WGS84 SRS.")
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg1)
                        .addProperty(SPL.valueType, XSD.xdouble))
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg2)
                        .addProperty(SPL.valueType, XSD.xdouble));

        SPATIAL.distance.inModel(m)
                .addProperty(RDF.type, SPIN.Function)
                .addProperty(RDFS.subClassOf, GEO.GeoSPARQLFunctions)
                .addProperty(SPIN.returnType, XSD.xdouble)
                .addProperty(RDFS.comment, "Distance between two Geometry Literals in distance units. " +
                        "Chooses distance measure based on SRS type. " +
                        "Great Circle distance for Geographic SRS and Euclidean otherwise.")
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg1)
                        .addProperty(SPL.valueType, GEOSPARQL.wktLiteral))
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg2)
                        .addProperty(SPL.valueType, GEOSPARQL.wktLiteral))
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg3)
                        .addProperty(SPL.valueType, RDFS.Datatype)
                        .addProperty(AVC.oneOf, m.createList(km1, km2, km3, m1, m2, m3)));

        m.write(System.out, "ttl");

    }

}

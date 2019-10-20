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
import org.apache.jena.rdf.model.Resource;
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
import ru.avicomp.ontapi.jena.model.OntDR;
import ru.avicomp.ontapi.jena.model.OntDT;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntID;
import ru.avicomp.ontapi.jena.utils.Models;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.HashMap;
import java.util.Map;

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
                .setNsPrefix(SPATIAL.PREFIX, SPATIAL.NS)
                .setNsPrefix(UOM.PREFIX, UOM.NS);

        OntID id = m.setID(GEO.LIB_URI);

        id.addImport(AVC.BASE_URI);
        id.addImport(GEOSPARQL.BASE_URI);
        id.addComment("A library that described GeoSPARQL SPIN-functions.");

        m.createDatatype(GEOSPARQL.wktLiteral.getURI());

        OntDT units = m.createDatatype(GEO.Units.getURI());
        units.addComment("Represents all OGC Unit of Measure datatypes");
        units.addAnnotation(m.getAnnotationProperty(RDFS.seeAlso), m.createResource(UOM.getURI() + "#"));
        units.addEquivalentClass(m.createResource(RDFS.Datatype)
                .addProperty(OWL.unionOf, m.createList(UOM.getAllUOMs().iterator())).as(OntDR.class));

        GEO.GeoSPARQLFunctions.inModel(m)
                .addProperty(RDF.type, SPIN.Function)
                .addProperty(RDFS.subClassOf, SPL.MathematicalFunctions)
                .addProperty(SPIN.abstract_, Models.TRUE)
                .addProperty(RDFS.label, "GeoSPARQL Functions")
                .addProperty(RDFS.comment, "A collection of GeoSPARQL functions.");

        SPATIAL.convertLatLon.inModel(m)
                .addProperty(RDF.type, SPIN.Function)
                .addProperty(RDFS.subClassOf, GEO.GeoSPARQLFunctions)
                .addProperty(SPIN.returnType, GEOSPARQL.wktLiteral)
                .addProperty(RDFS.comment, "Converts Lat and Lon double values into WKT string of a Point with WGS84 SRS.")
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDFS.comment, "The latitude.")
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg1)
                        .addProperty(SPL.valueType, XSD.xdouble))
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDFS.comment, "The longitude.")
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
                        .addProperty(SPL.defaultValue, UOM.meter)
                        .addProperty(RDFS.comment, "Unit of measures, by default it is meter.")
                        .addProperty(AVC.oneOf, units));

        Map<Resource, String> azimuthFunctions = new HashMap<>();
        azimuthFunctions.put(SPATIAL.azimuth,
                "Forward azimuth clockwise from North between two Lat/Lon Points in 0 to 2π radians.");
        azimuthFunctions.put(SPATIAL.azimuthDeg,
                "Forward azimuth clockwise from North between two Lat/Lon Points in 0 to 360 degrees.");
        azimuthFunctions.forEach((f, c) -> f.inModel(m)
                .addProperty(RDF.type, SPIN.Function)
                .addProperty(RDFS.subClassOf, GEO.GeoSPARQLFunctions)
                .addProperty(SPIN.returnType, XSD.xdouble)
                .addProperty(RDFS.comment, c)
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDFS.comment, "The first point latitude.")
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg1)
                        .addProperty(SPL.valueType, XSD.xdouble))
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDFS.comment, "The first point longitude.")
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg2)
                        .addProperty(SPL.valueType, XSD.xdouble))
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDFS.comment, "The second point latitude.")
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg3)
                        .addProperty(SPL.valueType, XSD.xdouble))
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDFS.comment, "The second point longitude.")
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg4)
                        .addProperty(SPL.valueType, XSD.xdouble)));

        m.write(System.out, "ttl");
    }

}

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

package ru.avicomp.map.tools;

import org.apache.jena.graph.Factory;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDFS;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import org.topbraid.spin.vocabulary.SPL;
import ru.avicomp.map.spin.AdjustGroupConcatImpl;
import ru.avicomp.map.spin.QueryHelper;
import ru.avicomp.map.spin.SPINLibrary;
import ru.avicomp.map.spin.vocabulary.ARQ;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntID;
import ru.avicomp.ontapi.jena.utils.Models;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;
import ru.avicomp.ontapi.jena.vocabulary.XSD;

/**
 * An utility class to produce <p>avc.lib.ttl</p> (see {@code resources/etc} directory).
 * For developing and demonstration.
 * NOTE: Not a part of API or APIs Tests: will be removed.
 * <p>
 * The library <p>avc.lib.ttl</p> is a collection of new ONT-MAP functions, that are absent in the standard spin-library.
 * Each of the functions is SPARQL based.
 * Also note: you do not need to have this file separately to force a mapping to work.
 * Mappings, that are produced by the ONT-MAP, must contain everything needed, with except of spin-library,
 * i.e. all needed functions are printed directly to mappings.
 *
 * Created by @szuev on 14.06.2018.
 *
 * @see AVC
 */
public class LIBLibraryMaker {

    public static void main(String... args) {
        OntGraphModel m = LibraryMaker.createModel(Factory.createGraphMem());
        OntID id = m.setID(AVC.LIB_URI);
        id.setVersionIRI(id.getURI() + "#1.0");
        id.addComment("An additional library of functions that can be expressed through SPARQL " +
                "or the other functions from a standard spin-library.\n" +
                "It is assumed that this ontology will not be included to the \"owl:import\" statement of mappings, produces by the API.\n" +
                "Instead, any of the functions below will be printed directly to the graph of mapping, which use that function, " +
                "and therefore the library can be considered just as templates collection.", null);
        id.addAnnotation(m.getAnnotationProperty(OWL.versionInfo), "version 1.0", null);
        id.addImport(AVC.BASE_URI);
        ((UnionGraph) m.getGraph()).addGraph(SPINLibrary.SPL.getGraph());
        m.setNsPrefix("afn", ARQ.NS);

        // AVC:withDefault
        AVC.withDefault.inModel(m)
                .addProperty(RDF.type, SPIN.Function)
                .addProperty(RDFS.subClassOf, AVC.PropertyFunctions)
                .addProperty(SPIN.returnType, RDF.Property)
                .addProperty(RDFS.seeAlso, SPINMAP.equals)
                .addProperty(RDFS.seeAlso, AVC.asIRI)
                .addProperty(SPINMAP.shortLabel, "withDefault")
                .addProperty(RDFS.label, "With Default")
                .addProperty(RDFS.comment, "An ontology function for passing default values into a property mapping.\n" +
                        "It is used for mapping data/annotation property assertion that absences in a particular source individual.\n" +
                        "Like spinmap:equals it returns the primary input property (?arg1) unchanged.")
                .addProperty(SPIN.body, QueryHelper.parseQuery("SELECT ?arg1\nWHERE {\n}", m))
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg1)
                        .addProperty(SPL.valueType, RDF.Property)
                        .addProperty(RDFS.comment, "The property to get assertion value"))
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg2)
                        .addProperty(SPL.valueType, RDFS.Literal)
                        .addProperty(RDFS.comment, "The default value to form a fake assertion on the source individual"));

        // AVC:asIRI
        AVC.asIRI.inModel(m)
                .addProperty(RDF.type, SPIN.Function)
                .addProperty(RDFS.subClassOf, AVC.PropertyFunctions)
                .addProperty(SPIN.returnType, RDF.Property)
                .addProperty(RDFS.seeAlso, SPINMAP.equals)
                .addProperty(RDFS.seeAlso, AVC.withDefault)
                .addProperty(SPINMAP.shortLabel, "asIRI")
                .addProperty(RDFS.label, "As IRI")
                .addProperty(RDFS.comment, "An ontology function for passing property IRI as it is.\n" +
                        "Any other map-functions will actually accept a property assertion value found by mapping template call,\n" +
                        "while this function forces not to get a value but use a predicate IRI instead.")
                .addProperty(SPIN.body, QueryHelper.parseQuery("SELECT ?arg1\nWHERE {\n}", m))
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg1)
                        .addProperty(SPL.valueType, RDF.Property)
                        .addProperty(RDFS.comment, "The property to return as it is"));

        // AVC:currentIndividual
        AVC.currentIndividual.inModel(m)
                .addProperty(RDF.type, SPIN.Function)
                .addProperty(RDFS.subClassOf, AVC.MagicFunctions)
                .addProperty(SPIN.returnType, RDFS.Resource)
                .addProperty(SPINMAP.shortLabel, "currentIndividual")
                .addProperty(RDFS.label, "Get current individual")
                .addProperty(RDFS.comment, "A magic function to get current individual while inference.\n" +
                        "Equivalent to ?this\n" +
                        "Please note: this function may not work as expected when using Composer.")
                .addProperty(SPIN.body, QueryHelper.parseQuery("SELECT ?r\nWHERE {\n\tBIND(?this AS ?r)\n}", m));

        // AVC:groupConcat
        AVC.groupConcat.inModel(m)
                .addProperty(RDF.type, SPIN.Function)
                .addProperty(RDFS.subClassOf, AVC.AggregateFunctions)
                .addProperty(SPIN.returnType, XSD.xstring)
                .addProperty(AVC.runtime, AdjustGroupConcatImpl.class.getName())
                .addProperty(SPINMAP.shortLabel, "groupConcat")
                .addProperty(RDFS.label, "Group concat")
                .addProperty(RDFS.comment, "An aggregate function to concatenate values from assertions with " +
                        "the same individual and property using specified separator.\n" +
                        "Notice: string natural sort order is used")
                .addProperty(SPIN.body,
                        QueryHelper.parseQuery("SELECT GROUP_CONCAT(DISTINCT ?r; SEPARATOR=' + ')\n" +
                                "WHERE {\n" +
                                "    {\n" +
                                "        SELECT *\n" +
                                "        WHERE {\n" +
                                "            ?arg2 ?arg1 ?r .\n" +
                                "        }\n" +
                                "        ORDER BY (?r)\n" +
                                "    } .\n" +
                                "}", m))
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg1)
                        .addProperty(SPL.valueType, RDF.Property)
                        .addProperty(RDFS.comment, "The predicate (property)"))
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg2)
                        .addProperty(SPL.valueType, RDFS.Resource)
                        .addProperty(SPL.optional, Models.TRUE)
                        .addProperty(RDFS.comment, "The subject (instance)"))
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SPINMAPL.separator)
                        .addProperty(SPL.valueType, XSD.xstring)
                        .addProperty(SPL.optional, Models.TRUE)
                        .addProperty(SPL.defaultValue, " + ")
                        .addProperty(RDFS.comment, "The separator to put between the two values."));

        // AVC:UUID
        AVC.UUID.inModel(m)
                .addProperty(RDF.type, SPINMAP.TargetFunction)
                .addProperty(RDFS.subClassOf, SPINMAP.TargetFunctions)
                .addProperty(SPIN.returnType, RDFS.Resource)
                .addProperty(RDFS.seeAlso, SP.resource("UUID"))
                .addProperty(RDFS.seeAlso, ResourceFactory.createResource("https://www.w3.org/TR/sparql11-query/#func-uuid"))
                .addProperty(SPINMAP.shortLabel, "UUID")
                .addProperty(RDFS.label, "MD5 UUID")
                .addProperty(RDFS.comment, "A target function.\n" +
                        "Generates an IRI from the UUID URN scheme based on source individual MD5 sum.\n" +
                        "Each call of AVC:UUID returns the same UUID IRI.\n" +
                        "Example: <urn:uuid:f3bf688d44e249fade9ca8ca23e29884>.\n" +
                        "Can work both with named and anonymous individuals.")
                .addProperty(AVC.optimize, ru.avicomp.map.spin.functions.avc.UUID.class.getName())
                .addProperty(SPIN.body,
                        QueryHelper.parseQuery("SELECT IRI(?uri)\n" +
                                "WHERE {\n" +
                                "    BIND (IF(isBlank(?source), afn:bnode(?source), str(?source)) AS ?s) .\n" +
                                "    BIND (MD5(?s) AS ?value) .\n" +
                                "    BIND (CONCAT(\"urn:uuid:\", ?value) AS ?uri) .\n" +
                                "}", m));

        // AVC:IRI
        AVC.IRI.inModel(m)
                .addProperty(RDF.type, SPINMAP.TargetFunction)
                .addProperty(RDFS.subClassOf, SPINMAP.TargetFunctions)
                .addProperty(SPIN.returnType, RDFS.Resource)
                .addProperty(RDFS.seeAlso, SP.resource("iri"))
                .addProperty(RDFS.seeAlso, ResourceFactory.createResource("https://www.w3.org/TR/sparql11-query/#func-iri"))
                .addProperty(SPINMAP.shortLabel, "IRI")
                .addProperty(RDFS.label, "creates IRI resource")
                .addProperty(RDFS.comment, "A target function.\b" +
                        "Returns an IRI as a single target resource (individual).\n" +
                        "Please note: inference will create only one target individual per rule with merging all sources to it.")
                .addProperty(SPIN.body, QueryHelper.parseQuery("SELECT (IRI(?arg1))\nWHERE {\n}", m))
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg1)
                        .addProperty(SPL.valueType, XSD.xstring)
                        .addProperty(RDFS.comment, "An IRI (xsd:string)"));

        // AVC:self
        AVC.self.inModel(m)
                .addProperty(RDF.type, SPINMAP.TargetFunction)
                .addProperty(RDFS.subClassOf, SPINMAP.TargetFunctions)
                .addProperty(SPIN.returnType, RDFS.Resource)
                .addProperty(RDFS.seeAlso, SPINMAPL.self)
                .addProperty(SPIN.private_, Models.TRUE)
                .addProperty(SPIN.body, QueryHelper.parseQuery("SELECT ?source\nWHERE {\n\tFILTER isIRI(?source) .\n}", m));

        // AVC:objectWithFilter
        AVC.objectWithFilter.inModel(m)
                .addProperty(RDF.type, SPIN.Function)
                .addProperty(RDFS.subClassOf, SPL.OntologyFunctions)
                .addProperty(SPIN.returnType, AVC.undefined)
                .addProperty(AVC.optimize, ru.avicomp.map.spin.functions.avc.objectWithFilter.class.getName())
                .addProperty(RDFS.seeAlso, SPL.object)
                .addProperty(SPINMAP.shortLabel, "object")
                .addProperty(RDFS.label, "object with filter")
                .addProperty(RDFS.comment, "Gets the object of a given subject (?arg1) / predicate (?arg2) combination " +
                        "which match predicate (?arg3) / object (?arg4), returns a RDFNode.")
                .addProperty(SPIN.body, QueryHelper.parseQuery("SELECT ?object\n" +
                        "WHERE {\n" +
                        "    OPTIONAL {\n" +
                        "        BIND (?arg4 AS ?value) .\n" +
                        "    } .\n" +
                        "    OPTIONAL {\n" +
                        "        BIND (?arg3 AS ?property) .\n" +
                        "    } .\n" +
                        "    ?arg1 ?arg2 ?object .\n" +
                        "    FILTER EXISTS {\n" +
                        "        ?object ?property ?value .\n" +
                        "    } .\n" +
                        "}", m))
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg1)
                        .addProperty(SPL.valueType, RDFS.Resource)
                        .addProperty(RDFS.comment, "The subject to get the object from."))
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg2)
                        .addProperty(SPL.valueType, RDF.Property)
                        .addProperty(RDFS.comment, "The predicate to get the object of."))
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg3)
                        .addProperty(SPL.valueType, RDF.Property)
                        .addProperty(SPL.optional, Models.TRUE)
                        .addProperty(RDFS.comment, "Second predicate to filter results of select. Optional"))
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg4)
                        .addProperty(SPL.valueType, AVC.undefined)
                        .addProperty(SPL.optional, Models.TRUE)
                        .addProperty(RDFS.comment, "Object (RDFNode) to filter results of select. Optional"));

        m.write(System.out, "ttl");
    }
}

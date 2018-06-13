package ru.avicomp.map.tools;

import org.apache.jena.graph.Factory;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDFS;
import org.topbraid.spin.arq.ARQ2SPIN;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import org.topbraid.spin.vocabulary.SPL;
import ru.avicomp.map.Context;
import ru.avicomp.map.spin.AdjustGroupConcatImpl;
import ru.avicomp.map.spin.vocabulary.ARQ;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.spin.vocabulary.SPIF;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;
import ru.avicomp.ontapi.jena.model.*;
import ru.avicomp.ontapi.jena.utils.Models;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;
import ru.avicomp.ontapi.jena.vocabulary.XSD;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An utility class to produce avc.spin.ttl (see resources/etc directory).
 * For developing and demonstration.
 * NOTE: Not a part of API or APIs Tests: will be removed.
 * <p>
 * Created by @szuev on 07.04.2018.
 *
 * @see AVC
 * @see ARQ
 */
public class AVCLibraryMaker {

    public static void main(String... args) {
        OntGraphModel m = LibraryMaker.createModel(Factory.createGraphMem());
        OntID id = m.setID(AVC.BASE_URI);
        id.setVersionIRI(AVC.NS + "1.0");
        id.addComment("This is an addition to the spin-family in order to customize spin-functions behaviour in GUI.\n" +
                "Also it contains several custom functions, which can be expressed through the other spin-library and SPARQL.\n" +
                "Currently it is assumed that this library is not going to be included as \"owl:import\" to the mappings produces by the API,\n" +
                "and all listed custom functions can be considered as templates.", null);
        id.addAnnotation(m.getAnnotationProperty(OWL.versionInfo), "version 1.0", null);
        // depends on spinmap to reuse variables (spinmap:_source, spin:_this, spin:_arg*) while building functions bodies
        m.addImport(LibraryMaker.createModel(LibraryMaker.getSPINMAPGraph()));

        OntDT xsdString = m.getOntEntity(OntDT.class, XSD.xstring);

        OntNDP hidden = m.createOntEntity(OntNDP.class, AVC.hidden.getURI());
        hidden.addRange(xsdString);
        hidden.addComment("A property for marking unused functions from standard spin-map library supply.", null);

        OntNDP runtime = m.createOntEntity(OntNDP.class, AVC.runtime.getURI());
        runtime.addRange(xsdString);
        runtime.addComment("A property for using to describe runtime functionality provided by ONT-MAP API", null);

        // numeric datatype (xs:numeric)
        OntDT numeric = m.createOntEntity(OntDT.class, AVC.numeric.getURI());
        numeric.addComment("Represents all numeric datatypes", null);
        numeric.addProperty(RDFS.seeAlso, m.getResource("https://www.w3.org/TR/sparql11-query/#operandDataTypes"));
        List<OntDR> numberDRs = Stream.of(XSD.integer, XSD.decimal, XSD.xfloat, XSD.xdouble,
                XSD.nonPositiveInteger, XSD.negativeInteger,
                XSD.nonNegativeInteger, XSD.positiveInteger,
                XSD.xlong, XSD.xint, XSD.xshort, XSD.xbyte,
                XSD.unsignedLong, XSD.unsignedInt, XSD.unsignedShort, XSD.unsignedByte).map(r -> m.getOntEntity(OntDT.class, r)).collect(Collectors.toList());
        numeric.addEquivalentClass(m.createUnionOfDataRange(numberDRs));

        // AVC:MagicFunctions
        AVC.MagicFunctions.inModel(m)
                .addProperty(RDF.type, SPIN.Function)
                .addProperty(RDFS.subClassOf, SPIN.Functions)
                .addProperty(SPIN.abstract_, Models.TRUE)
                .addProperty(RDFS.label, "Magic functions")
                .addProperty(RDFS.comment, "A special collection of functions provided by AVC that require special treatment while inference\n" +
                        "and therefore may not work as expected in Topbraid Composer.");

        // AVC:AggregateFunctions
        AVC.AggregateFunctions.inModel(m)
                .addProperty(RDF.type, SPIN.Function)
                .addProperty(RDFS.subClassOf, SPIN.Functions)
                .addProperty(SPIN.abstract_, Models.TRUE)
                .addProperty(RDFS.label, "Aggregate functions")
                .addProperty(RDFS.comment,
                        "A collection of functions that uses SPARQL aggregate functionality (i.e. COUNT, SUM, MIN, MAX, GROUP_CONCAT).");

        // Customize mathematical functions:
        // SP:abs
        SP.resource("abs").inModel(m).addProperty(hidden,
                "Duplicates the function fn:abs, which is preferable, since it has information about return types.");
        // SP:max & SP:min can handle also xsd:string since it is SPARQL which is used overloaded operators ">" and "<"
        Stream.of(SPL.max, SPL.min).map(r -> r.inModel(m))
                .forEach(r -> r.addProperty(RDFS.comment, "Can work both with numeric datatypes and xsd:string.")
                        .addProperty(RDFS.subClassOf, SPL.StringFunctions));
        // SP:sub can accept any numbers
        SP.sub.inModel(m)
                .addProperty(RDFS.seeAlso, m.getResource("https://www.w3.org/TR/xpath-functions/#func-numeric-subtract"))
                .addProperty(AVC.returnType, AVC.numeric)
                .addProperty(AVC.constraint, LibraryMaker.createConstraint(m, SP.arg1, AVC.numeric))
                .addProperty(AVC.constraint, LibraryMaker.createConstraint(m, SP.arg2, AVC.numeric));
        // SP:add can handle string also since it is SPIN representation of SPARQL "+", which is overloaded
        SP.add.inModel(m).addProperty(RDFS.comment, "Can work both with numeric datatypes and xsd:string.")
                .addProperty(RDFS.subClassOf, SPL.StringFunctions)
                .addProperty(AVC.returnType, RDFS.Literal)
                .addProperty(AVC.constraint, LibraryMaker.createConstraint(m, SP.arg1, RDFS.Literal))
                .addProperty(AVC.constraint, LibraryMaker.createConstraint(m, SP.arg2, RDFS.Literal));
        // SP:divide
        SP.divide.inModel(m)
                .addProperty(RDFS.seeAlso, m.getResource("https://www.w3.org/TR/xpath-functions/#func-numeric-divide"))
                .addProperty(AVC.returnType, AVC.numeric)
                .addProperty(AVC.constraint, LibraryMaker.createConstraint(m, SP.arg1, AVC.numeric))
                .addProperty(AVC.constraint, LibraryMaker.createConstraint(m, SP.arg2, AVC.numeric));
        // SP:mul can accept only numeric
        SP.mul.inModel(m)
                .addProperty(RDFS.seeAlso, m.getResource("https://www.w3.org/TR/xpath-functions/#func-numeric-multiply"))
                .addProperty(AVC.returnType, AVC.numeric)
                .addProperty(AVC.constraint, LibraryMaker.createConstraint(m, SP.arg1, AVC.numeric))
                .addProperty(AVC.constraint, LibraryMaker.createConstraint(m, SP.arg2, AVC.numeric));
        // SP:unaryMinus & SP:unaryPlus
        SP.unaryPlus.inModel(m)
                .addProperty(RDFS.seeAlso, m.getResource("https://www.w3.org/TR/xpath-functions/#func-numeric-unary-plus"))
                .addProperty(AVC.returnType, AVC.numeric)
                .addProperty(AVC.constraint, LibraryMaker.createConstraint(m, SP.arg1, AVC.numeric));
        SP.unaryMinus.inModel(m)
                .addProperty(RDFS.seeAlso, m.getResource("https://www.w3.org/TR/xpath-functions/#func-numeric-unary-minus"))
                .addProperty(AVC.returnType, AVC.numeric)
                .addProperty(AVC.constraint, LibraryMaker.createConstraint(m, SP.arg1, AVC.numeric));
        // SP:ceil & SP:floor & SP:round
        Stream.of(SP.ceil, SP.floor, SP.round).map(r -> r.inModel(m))
                .forEach(r -> r.addProperty(AVC.returnType, AVC.numeric)
                        .addProperty(AVC.constraint, LibraryMaker.createConstraint(m, SP.arg1, AVC.numeric)));

        // Boolean functions:
        SP.isNumeric.inModel(m).addProperty(AVC.returnType, XSD.xboolean);

        // todo: hide SPIF:random in favour of SP:rand?

        // SP:datatype
        SP.resource("datatype").inModel(m).addProperty(AVC.returnType, RDFS.Datatype);

        // SP:eq can accept any resource, not only boolean literals
        SP.eq.inModel(m)
                .addProperty(AVC.constraint, LibraryMaker.createConstraint(m, SP.arg1, AVC.undefined))
                .addProperty(AVC.constraint, LibraryMaker.createConstraint(m, SP.arg2, AVC.undefined));

        // ARQ(prefix=afn) functions: AFN:max and AFN:min can handle only numeric
        Stream.of(ARQ.max, ARQ.min).map(r -> r.inModel(m))
                .forEach(r -> r.addProperty(AVC.returnType, AVC.numeric)
                        .addProperty(AVC.constraint, LibraryMaker.createConstraint(m, SP.arg1, AVC.numeric))
                        .addProperty(AVC.constraint, LibraryMaker.createConstraint(m, SP.arg2, AVC.numeric)));
        // ARQ:pi replaced with MATH:pi
        ARQ.resource("pi").inModel(m).addProperty(AVC.hidden, "Use math:pi instead");

        // SPINMAP:targetResource
        SPINMAP.targetResource.inModel(m)
                .addProperty(SPIN.private_, Models.TRUE)
                .addProperty(hidden, "This function should not be allowed to be used explicitly by API.\n" +
                        "All cases when spinmap:targetResource might be used, should be described through other functions.");

        // SPINMAPL:relatedSubjectContext
        SPINMAPL.relatedSubjectContext.inModel(m)
                .addProperty(hidden, "Instead of explicit calling this function, please use " + Context.class.getName() +
                        "#createRelatedContext(...) methods.");
        // SPINMAPL:relatedObjectContext
        SPINMAPL.relatedObjectContext.inModel(m)
                .addProperty(hidden, "Instead of explicit calling this function, please use " + Context.class.getName() +
                        "#createRelatedContext(...) methods.");

        // Exclude SPIN primary key functionality
        String primaryKeyExclusionReason = "Primary-key functionality is excluded since it is not compatible with ONT-MAP logic";
        Stream.of(SPINMAPL.resource("resourceWithPrimaryKey"),
                SPINMAPL.resource("usePrimaryKey"),
                SPL.resource("primaryKeyProperty"),
                SPL.resource("primaryKeyURIStart"),
                SPL.resource("hasPrimaryKey"),
                SPL.resource("isPrimaryKeyPropertyOfInstance"))
                .map(r -> r.inModel(m))
                .forEach(r -> r.addProperty(hidden, primaryKeyExclusionReason));
        // Exclude functions affecting spin:Module, spin:Function, sp:Query as not compatible with this API
        String spinExclusionReason = "Functions accepting spin:Module or sp:Query are not compatible with ONT-MAP logic";
        Stream.of(SPL.resource("hasArgument"),
                SPIF.resource("convertSPINRDFToString"),
                SPIF.resource("invoke"),
                SPIF.resource("canInvoke"),
                SPIF.resource("walkObjects")).map(r -> r.inModel(m))
                .forEach(r -> r.addProperty(hidden, spinExclusionReason));

        // varargs:
        SP.resource("concat").inModel(m).addProperty(SPIN.constraint, m.createResource()
                .addProperty(RDF.type, SPL.Argument)
                .addProperty(SPL.predicate, AVC.vararg)
                .addProperty(SPL.valueType, XSD.xstring));
        Stream.of("in", "notIn")
                .map(SP::resource)
                .map(r -> r.inModel(m))
                .forEach(r -> r.addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, AVC.vararg)
                        .addProperty(SPL.valueType, RDFS.Literal)));

        // AVC:withDefault
        AVC.withDefault.inModel(m)
                .addProperty(RDF.type, SPIN.Function)
                .addProperty(RDFS.subClassOf, SPL.OntologyFunctions)
                .addProperty(SPIN.returnType, RDF.Property)
                .addProperty(RDFS.seeAlso, SPINMAP.equals)
                .addProperty(RDFS.seeAlso, AVC.asIRI)
                .addProperty(SPINMAP.shortLabel, "withDefault")
                .addProperty(RDFS.label, "With Default")
                .addProperty(RDFS.comment, "An ontology function for passing default values into a property mapping.\n" +
                        "It is used for mapping data/annotation property assertion that absences in a particular source individual.\n" +
                        "Like spinmap:equals it returns the primary input property (?arg1) unchanged.")
                .addProperty(SPIN.body, ARQ2SPIN.parseQuery("SELECT ?arg1\nWHERE {\n}", m))
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
                .addProperty(RDFS.subClassOf, SPL.OntologyFunctions)
                .addProperty(SPIN.returnType, RDF.Property)
                .addProperty(RDFS.seeAlso, SPINMAP.equals)
                .addProperty(RDFS.seeAlso, AVC.withDefault)
                .addProperty(SPINMAP.shortLabel, "asIRI")
                .addProperty(RDFS.label, "As IRI")
                .addProperty(RDFS.comment, "An ontology function for passing property IRI as it is.\n" +
                        "Any other map-functions will actually accept a property assertion value found by mapping template call,\n" +
                        "while this function forces not to get a value but use a predicate IRI instead.")
                .addProperty(SPIN.body, ARQ2SPIN.parseQuery("SELECT ?arg1\nWHERE {\n}", m))
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
                .addProperty(SPIN.body, ARQ2SPIN.parseQuery("SELECT ?r\nWHERE {\n\tBIND(?this AS ?r)\n}", m));

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
                        ARQ2SPIN.parseQuery("SELECT GROUP_CONCAT(DISTINCT ?r; SEPARATOR=' + ')\n" +
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
                        "Example: <urn:uuid:f3bf688d44e249fade9ca8ca23e29884>.")
                .addProperty(SPIN.body,
                        ARQ2SPIN.parseQuery("SELECT IRI(?uri)\n" +
                                "WHERE {\n" +
                                "    BIND (MD5(str(?source)) AS ?value) .\n" +
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
                .addProperty(RDFS.comment, "A target function.\n" +
                        "Returns an IRI as target resource (individual).\n" +
                        "Please note: mapping inference will create only single target individual, merging all sources to one")
                .addProperty(SPIN.body, ARQ2SPIN.parseQuery("SELECT (IRI(?arg1))\nWHERE {\n}", m))
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg1)
                        .addProperty(SPL.valueType, XSD.xstring)
                        .addProperty(RDFS.comment, "An IRI (xsd:string)"));

        // AVC:objectWithFilter
        AVC.objectWithFilter.inModel(m)
                .addProperty(RDF.type, SPIN.Function)
                .addProperty(RDFS.subClassOf, SPL.OntologyFunctions)
                .addProperty(SPIN.returnType, AVC.undefined)
                .addProperty(RDFS.seeAlso, SPL.object)
                .addProperty(SPINMAP.shortLabel, "object")
                .addProperty(RDFS.label, "object with filter")
                .addProperty(RDFS.comment, "Gets the object of a given subject (?arg1) / predicate (?arg2) combination " +
                        "which match predicate (?arg3) / object (?arg4), returns a RDFNode.")
                .addProperty(SPIN.body, ARQ2SPIN.parseQuery("SELECT ?object\n" +
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

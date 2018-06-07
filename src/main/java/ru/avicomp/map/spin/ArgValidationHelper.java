package ru.avicomp.map.spin;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapJenaException;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.ontapi.jena.model.*;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.stream.Stream;

/**
 * Auxiliary class, a helper to validate a {@link MapFunction.Arg function-call argument} values,
 * which could be either nested function or string representation of literal or resource
 * Just to relieve the main (context) class.
 */
class ArgValidationHelper {
    private final MapModelImpl model;
    private final MapFunction.Arg argument;

    ArgValidationHelper(MapModelImpl model, MapFunction.Arg argument) {
        this.model = model;
        this.argument = argument;
    }

    /**
     * Validates function argument input against specified mapping model.
     *
     * @param value {@link MapFunction}
     */
    public void testFunctionValue(MapFunction value) throws MapJenaException {
        Resource arg = model.createResource(argument.type());
        Resource type = model.createResource(value.type());
        Exceptions.Builder error = Exceptions.FUNCTION_CALL_INCOMPATIBLE_NESTED_FUNCTION.create()
                .add(Exceptions.Key.ARG, argument.name())
                .addFunction(value)
                .add(Exceptions.Key.ARG_TYPE, arg.toString())
                .add(Exceptions.Key.ARG_VALUE, type.toString());
        if (arg.equals(type)) {
            return;
        }
        if (AVC.undefined.equals(arg) || AVC.undefined.equals(type)) {
            // seems it is okay
            return;
        }
        if (RDFS.Literal.equals(arg)) {
            if (type.canAs(OntDT.class)) return;
            throw error.build();
        }
        if (AVC.numeric.equals(arg)) {
            if (type.canAs(OntDT.class) && model.isNumeric(type.as(OntDT.class).toRDFDatatype())) return;
            throw error.build();
        }
        if (RDF.PlainLiteral.equals(arg)) {
            if (XSD.xstring.equals(type)) return;
            throw error.build();
        }
        if (RDF.Property.equals(arg)) {
            if (Stream.of(RDF.Property, OWL.ObjectProperty, OWL.DatatypeProperty, OWL.AnnotationProperty).anyMatch(type::equals))
                return;
            throw error.build();
        }
        if (RDFS.Class.equals(arg)) {
            if (OWL.Class.equals(arg)) return;
            throw error.build();
        }
        if (RDFS.Resource.equals(arg)) {
            return;
        }
        throw error.build();
    }

    /**
     * Validates string argument input against specified mapping model.
     *
     * @param value String argument value
     * @throws MapJenaException if parameter string value does not match argument type
     */
    public void testStringValue(String value) throws MapJenaException {
        Resource arg = model.createResource(argument.type());
        RDFNode node = model.toNode(value);
        Exceptions.Builder error = Exceptions.FUNCTION_CALL_WRONG_ARGUMENT_VALUE.create()
                .add(Exceptions.Key.ARG, argument.name())
                .add(Exceptions.Key.ARG_TYPE, arg.toString())
                .add(Exceptions.Key.ARG_VALUE, node.toString());
        // anything:
        if (AVC.undefined.equals(arg)) {
            return;
        }
        // value is literal
        if (node.isLiteral()) {
            Literal literal = node.asLiteral();
            if (arg.getURI().equals(literal.getDatatypeURI())) return;
            if (RDFS.Literal.equals(arg)) return;
            if (AVC.numeric.equals(arg)) {
                if (model.isNumeric(literal.getDatatype())) return;
                throw error.build();
            }
            if (RDF.PlainLiteral.equals(arg)) {
                if (XSD.xstring.getURI().equals(literal.getDatatypeURI())) return;
                throw error.build();
            }
            throw error.build();
        }
        // then resource
        if (RDFS.Resource.equals(arg)) {
            return;
        }
        if (RDFS.Datatype.equals(arg)) {
            if (node.canAs(OntDR.class)) return;
            throw error.build();
        }
        if (RDFS.Class.equals(arg)) {
            if (node.canAs(OntCE.class)) return;
            throw error.build();
        }
        if (SPINMAP.Context.equals(arg)) {
            if (node.asResource().hasProperty(RDF.type, SPINMAP.Context)) return;
            throw error.build();
        }
        if (RDF.Property.equals(arg)) {
            //if (node.isURIResource() && node.canAs(OntPE.class)) return;
            // can be passed built-in property, e.g. rdf:type
            if (node.isURIResource()) return;
            throw error.build();
        }
        if (arg.canAs(OntDT.class) && (node.canAs(OntNDP.class) || node.canAs(OntNAP.class))) {
            // todo: validate also range for datatype properties while building mapping
            return;
        }
        // todo: also possible types: sp:Query, spin:Function, spin:Module, rdf:List
        throw error.build();
    }
}

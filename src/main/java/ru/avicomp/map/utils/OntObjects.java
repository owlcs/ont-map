package ru.avicomp.map.utils;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDFS;
import ru.avicomp.ontapi.jena.model.*;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.XSD;

import java.util.Objects;

/**
 * A sugar helper to work with {@link OntObject} (mostly with {@link OntEntity}).
 *
 * Created by @szuev on 04.04.2018.
 */
public class OntObjects {

    public static OntClass createOWLClass(OntGraphModel model, String uri) {
        return model.createOntEntity(OntClass.class, uri);
    }

    public static OntNDP createOWLDataProperty(OntGraphModel model, String uri) {
        return model.createOntEntity(OntNDP.class, uri);
    }

    public static OntNOP createOWLObjectProperty(OntGraphModel model, String uri) {
        return model.createOntEntity(OntNOP.class, uri);
    }

    public static String getLabel(OntObject obj) {
        return getFirstStringLiteralValue(obj, RDFS.label);
    }

    public static String getComment(OntObject obj) {
        return getFirstStringLiteralValue(obj, RDFS.comment);
    }

    public static <O extends OntObject> O setLabel(O obj, String label) {
        return setFirstStringLiteralValue(obj, RDFS.label, label);
    }

    public static <O extends OntObject> O setComment(O obj, String comment) {
        return setFirstStringLiteralValue(obj, RDFS.comment, comment);
    }

    private static String getFirstStringLiteralValue(OntObject obj, Property p) {
        return obj.annotations()
                .filter(s -> p.equals(s.getPredicate()))
                .map(Statement::getObject)
                .filter(RDFNode::isLiteral)
                .map(RDFNode::asLiteral)
                .map(Literal::getString)
                .findFirst().orElse(null);
    }

    private static <O extends OntObject> O setFirstStringLiteralValue(O obj, Property p, String value) {
        Objects.requireNonNull(value);
        obj.removeAll(p);
        obj.addAnnotation(p.inModel(obj.getModel()).as(OntNAP.class), value, null);
        return obj;
    }

    public static OntNAP versionInfo(OntGraphModel model) {
        return model.getAnnotationProperty(OWL.versionInfo);
    }

    public static OntDT xsdString(OntGraphModel model) {
        return model.getOntEntity(OntDT.class, XSD.xstring);
    }

    public static OntDT xsdDate(OntGraphModel model) {
        return model.getOntEntity(OntDT.class, XSD.date);
    }

    public static OntDT xsdBoolean(OntGraphModel model) {
        return model.getOntEntity(OntDT.class, XSD.xboolean);
    }

    public static OntDT xsdInt(OntGraphModel model) {
        return model.getOntEntity(OntDT.class, XSD.xint);
    }

    public static OntClass owlThing(OntGraphModel model) {
        return model.getOWLThing();
    }
}

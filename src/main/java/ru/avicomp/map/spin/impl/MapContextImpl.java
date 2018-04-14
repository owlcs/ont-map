package ru.avicomp.map.spin.impl;

import org.apache.jena.enhanced.EnhGraph;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.impl.ResourceImpl;
import org.apache.jena.shared.JenaException;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.Context;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapJenaException;
import ru.avicomp.map.spin.MapModelImpl;
import ru.avicomp.map.spin.model.MapContext;
import ru.avicomp.map.utils.Models;
import ru.avicomp.ontapi.jena.model.OntCE;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by @szuev on 14.04.2018.
 */
public class MapContextImpl extends ResourceImpl implements MapContext {

    public MapContextImpl(Node n, EnhGraph m) {
        super(n, m);
    }

    @Override
    public MapModelImpl getModel() {
        return (MapModelImpl) super.getModel();
    }

    @Override
    public OntCE getSource() throws JenaException {
        return getRequiredProperty(SPINMAP.sourceClass).getObject().as(OntCE.class);
    }

    @Override
    public OntCE getTarget() throws JenaException {
        return getRequiredProperty(SPINMAP.targetClass).getObject().as(OntCE.class);
    }

    @Override
    public Context addExpression(MapFunction.Call func) throws MapJenaException {
        if (!func.getFunction().isTarget()) {
            // TODO:
            throw new MapJenaException();
        }
        Resource expr = buildExpression(func);
        // collects statements for existing expression to be deleted :
        List<Statement> prev = getModel().statements(this, SPINMAP.target, null)
                .map(Statement::getObject)
                .filter(RDFNode::isResource)
                .map(RDFNode::asResource)
                .map(Models::getAssociatedStatements)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        addProperty(SPINMAP.target, expr);

        // todo: build rule. now it is only for testing
        Resource mapping01 = getModel().createResource().addProperty(RDF.type, SPINMAP.Mapping_0_1);
        getSource().addProperty(SPINMAP.rule, mapping01);
        mapping01.addProperty(SPINMAP.context, this);
        mapping01.addProperty(SPINMAP.expression, getTarget());
        mapping01.addProperty(SPINMAP.targetPredicate1, RDF.type);

        getModel().remove(prev);
        return null;
    }

    /**
     * todo: not ready.
     * <pre>{@code
     * [ a  spinmapl:buildURI2 ;
     *      sp:arg1            people:secondName ;
     *      sp:arg2            people:firstName ;
     *      spinmap:source     spinmap:_source ;
     *      spinmapl:template  "beings:Being-{?1}-{?2}"
     *  ] ;
     *  }</pre>
     *
     * @param func
     * @return
     */
    protected Resource buildExpression(MapFunction.Call func) {
        Model model = getModel();
        Resource res = model.createResource();
        Resource function = model.createResource(func.getFunction().name());
        res.addProperty(RDF.type, function);
        return res;
    }
}

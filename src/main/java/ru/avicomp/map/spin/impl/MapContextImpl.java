package ru.avicomp.map.spin.impl;

import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.enhanced.EnhGraph;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.*;
import org.apache.jena.rdf.model.impl.ResourceImpl;
import org.apache.jena.shared.JenaException;
import org.apache.jena.vocabulary.RDFS;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.Context;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapJenaException;
import ru.avicomp.map.spin.MapFunctionImpl;
import ru.avicomp.map.spin.MapModelImpl;
import ru.avicomp.map.spin.model.MapContext;
import ru.avicomp.map.utils.Models;
import ru.avicomp.ontapi.jena.model.OntCE;
import ru.avicomp.ontapi.jena.model.OntDT;
import ru.avicomp.ontapi.jena.model.OntPE;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by @szuev on 14.04.2018.
 */
public class MapContextImpl extends ResourceImpl implements MapContext {
    private static final Map<String, Class<? extends RDFNode>> RESOURCE_TYPE_MAPPING = Collections.unmodifiableMap(new HashMap<String, Class<? extends RDFNode>>() {
        {
            put(RDF.Property.getURI(), OntPE.class);
            put(RDFS.Class.getURI(), OntCE.class);
            put(RDFS.Datatype.getURI(), OntDT.class);
        }
    });
    private final TypeMapper rdfTypes = TypeMapper.getInstance();

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
            // TODO: exception mechanism
            throw new MapJenaException();
        }
        validate(func);
        Resource expr = createExpression(func);
        // collects statements for existing expression to be deleted :
        List<Statement> prev = getModel().statements(this, SPINMAP.target, null)
                .map(Statement::getObject)
                .filter(RDFNode::isResource)
                .map(RDFNode::asResource)
                .map(Models::getAssociatedStatements)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        addProperty(SPINMAP.target, expr);

        Resource mapping = createMapping(0, 1);
        getSource().addProperty(SPINMAP.rule, mapping);
        mapping.addProperty(SPINMAP.context, this);
        mapping.addProperty(SPINMAP.expression, getTarget());
        mapping.addProperty(SPINMAP.targetPredicate1, RDF.type);

        getModel().remove(prev);
        return this;
    }

    /**
     * todo: not fully ready.
     * Example of expression:
     * <pre>{@code
     * [ a  spinmapl:buildURI2 ;
     *      sp:arg1            people:secondName ;
     *      sp:arg2            people:firstName ;
     *      spinmap:source     spinmap:_source ;
     *      spinmapl:template  "beings:Being-{?1}-{?2}"
     *  ] ;
     *  }</pre>
     *
     * @param func {@link MapFunction.Call} function call to write
     * @return {@link Resource}
     */
    public Resource createExpression(MapFunction.Call func) {
        Model model = getModel();
        Resource res = model.createResource();
        Resource function = model.createResource(func.getFunction().name());
        res.addProperty(RDF.type, function);
        func.asMap().forEach((arg, value) -> {
            if (!(value instanceof String)) // todo:
                throw new UnsupportedOperationException("TODO");
            Property predicate = model.createResource(arg.name()).as(Property.class);
            res.addProperty(predicate, createRDFNode(arg.type(), (String) value));
        });
        return res;
    }

    public Resource createMapping(int i, int j) {
        return getModel().createResource().addProperty(RDF.type, SPINMAP.mapping(i, j));
    }

    /**
     * Validates a function-call against the context.
     *
     * @param func {@link MapFunction.Call} an expression.
     * @throws MapJenaException if something is wrong with function, e.g. wrong argument types.
     */
    @Override
    public void validate(MapFunction.Call func) throws MapJenaException {
        func.asMap().forEach(this::validateArg);
    }

    private void validateArg(MapFunction.Arg arg, Object value) {
        String argType = arg.type();
        if (MapFunctionImpl.UNDEFINED.equals(argType))
            throw new MapJenaException("TODO");
        if (value instanceof String) {
            createRDFNode(argType, (String) value);
            return;
        }
        if (value instanceof MapFunction.Call) {
            String funcType = ((MapFunction.Call) value).getFunction().returnType();
            validateFuncReturnType(argType, funcType);
            validate((MapFunction.Call) value);
        }
        throw new IllegalStateException("??");
    }

    private void validateFuncReturnType(String argType, String funcType) {
        if (argType.equals(funcType)) return;
        RDFDatatype literalType = rdfTypes.getTypeByName(funcType);
        if (literalType != null)
            throw new MapJenaException("TODO");
        if (RDFS.Resource.getURI().equals(argType))
            return;
        throw new MapJenaException("TODO");
    }

    public RDFNode createRDFNode(String type, String value) throws MapJenaException {
        RDFDatatype literalType = rdfTypes.getTypeByName(type);
        if (literalType != null) {
            return getModel().createTypedLiteral(value, literalType);
        }
        Resource uri = getModel().createResource(value);
        Class<? extends RDFNode> view = RESOURCE_TYPE_MAPPING.get(type);
        if (view != null) {
            try {
                return uri.as(view);
            } catch (JenaException j) { // todo: exception mechanism
                throw new MapJenaException("TODO:", j);
            }
        }
        // any resource:
        if (getModel().containsResource(uri)) {
            return uri;
        }
        // todo: exception mechanism
        throw new MapJenaException("TODO");
    }
}

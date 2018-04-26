package ru.avicomp.map.spin.impl;

import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.enhanced.EnhGraph;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.*;
import org.apache.jena.rdf.model.impl.ResourceImpl;
import org.apache.jena.shared.JenaException;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.Context;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapJenaException;
import ru.avicomp.map.PropertyBridge;
import ru.avicomp.map.spin.Exceptions;
import ru.avicomp.map.spin.MapFunctionImpl;
import ru.avicomp.map.spin.MapModelImpl;
import ru.avicomp.ontapi.jena.model.*;
import ru.avicomp.ontapi.jena.utils.Models;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.*;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ru.avicomp.map.spin.Exceptions.*;

/**
 * Implementation of class context (for resource with type {@code spinmap:Context}).
 * <p>
 * Created by @szuev on 14.04.2018.
 */
@SuppressWarnings("WeakerAccess")
public class MapContextImpl extends ResourceImpl implements Context {
    private static final Map<String, Predicate<Resource>> ARG_RESOURCE_MAPPING = Collections.unmodifiableMap(new HashMap<String, Predicate<Resource>>() {
        {
            put(RDFS.Resource.getURI(), r -> true);
            put(RDF.Property.getURI(), r -> r.canAs(OntNAP.class) || r.canAs(OntNDP.class));
            put(RDFS.Class.getURI(), r -> r.canAs(OntCE.class));
            put(RDFS.Datatype.getURI(), r -> r.canAs(OntDT.class));
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
        return target().as(OntCE.class);
    }

    /**
     * Returns a target class resource, which may not be {@link OntCE} in special case of {@code owl:NamedIndividual} mapping.
     *
     * @return {@link Resource}
     * @throws JenaException illegal state - no resource found
     */
    public Resource target() throws JenaException {
        return getRequiredProperty(SPINMAP.targetClass).getObject().asResource();
    }

    @Override
    public MapContextImpl addExpression(MapFunction.Call func) throws MapJenaException {
        if (!MapJenaException.notNull(func, "Null function call").getFunction().isTarget()) {
            throw exception(CONTEXT_REQUIRE_TARGET_FUNCTION).add(Key.FUNCTION, func.getFunction().name()).build();
        }
        validate(func);
        RDFNode expr = createExpression(func);
        // collects statements for existing expression to be deleted :
        List<Statement> prev = getModel().statements(this, SPINMAP.target, null)
                .map(Statement::getObject)
                .filter(RDFNode::isResource)
                .map(RDFNode::asResource)
                .map(Models::getAssociatedStatements)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        addProperty(SPINMAP.target, expr);
        addMapping(target(), Collections.emptyList(), Collections.singletonList(RDF.type));
        getModel().remove(prev);
        printFunction(func);
        return this;
    }

    public Stream<Statement> listRules() {
        MapModelImpl m = getModel();
        return m.statements(null, SPINMAP.context, this)
                .map(OntStatement::getSubject)
                .filter(RDFNode::isAnon)
                .flatMap(r -> m.statements(getSource(), SPINMAP.rule, r));
    }

    @Override
    public MapFunction.Call getExpression() {
        // todo:
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public MapPropertiesImpl addPropertyBridge(MapFunction.Call func, Property target) throws MapJenaException {
        Predicate<Resource> isProperty = ARG_RESOURCE_MAPPING.get(RDF.Property.getURI());
        if (!isProperty.test(target)) {
            throw exception(CONTEXT_WRONG_TARGET_PROPERTY).add(Key.TARGET_PROPERTY, target.getURI()).build();
        }
        validate(func);
        RDFNode expr = createExpression(func);
        List<Property> props;
        if (expr.isAnon()) {
            props = //Iter.asStream(expr.asResource().listProperties())
                    properties(expr.asResource())
                            .map(Statement::getObject)
                            .filter(RDFNode::isURIResource)
                            .filter(p -> isProperty.test(p.asResource()))
                            .map(p -> p.as(Property.class)).collect(Collectors.toList());
        } else {
            props = Collections.emptyList();
        }
        // as a fix:
        Resource mapping = addMapping(expr, props, Collections.singletonList(target));
        MapPropertiesImpl res = asProperties(mapping);
        printFunction(func);
        return res;
    }

    private void printFunction(MapFunction.Call call) {
        MapFunctionImpl function = (MapFunctionImpl) call.getFunction();
        if (function.isCustom()) addFunctionBody(function);
    }

    /**
     * Adds function as is to the graph.
     *
     * @param function {@link MapFunctionImpl}
     */
    protected void addFunctionBody(MapFunctionImpl function) {
        Model m = getModel();
        Models.getAssociatedStatements(function.asResource()).forEach(m::add);
    }

    /**
     * Adds a mapping to the graph
     *
     * @param expression resource
     * @param sources    List of source properties
     * @param targets    List of target properties
     * @return a mapping resource inside graph
     */
    protected Resource addMapping(RDFNode expression, List<Property> sources, List<Property> targets) {
        Resource mapping = createMapping(expression, sources, targets);
        getSource().addProperty(SPINMAP.rule, mapping);
        return mapping;
    }

    public Resource createMapping(RDFNode expression, List<Property> sources, List<Property> targets) {
        Resource template = getModel().getCommonMappingTemplate(sources.size(), targets.size());
        Resource res = getModel().createResource().addProperty(RDF.type, template);
        res.addProperty(SPINMAP.context, this);
        res.addProperty(SPINMAP.expression, expression);
        MapModelImpl m = getModel();
        processProperties(res, expression, sources, m::getSourcePredicate);
        processProperties(res, expression, targets, m::getTargetPredicate);
        return res;
    }

    protected void processProperties(Resource mapping, RDFNode expression, List<Property> properties, IntFunction<Property> predicate) {
        for (int i = 0; i < properties.size(); i++) {
            Property src = properties.get(i);
            Property mapPredicate = predicate.apply(i + 1);
            if (expression.isAnon()) {
                // replace with variable (spin:_arg*):
                Resource var = getModel().getArgVariable(i + 1);
                List<Statement> res = //Iter.asStream(expression.asResource().listProperties())
                        properties(expression.asResource())
                                .filter(s -> Objects.equals(s.getObject(), src))
                                .collect(Collectors.toList());
                res.forEach(s -> {
                    getModel().add(s.getSubject(), s.getPredicate(), var);
                    getModel().remove(s);
                });
            }
            mapping.addProperty(mapPredicate, src);
        }
    }

    public static Stream<Statement> properties(Resource subject) { // todo: possible recursion
        return Iter.asStream(subject.listProperties())
                .flatMap(s -> s.getObject().isAnon() ? properties(s.getObject().asResource()) : Stream.of(s));
    }

    @Override
    public Stream<PropertyBridge> properties() {
        // todo:
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public Context removeProperties(PropertyBridge properties) {
        // todo:
        throw new UnsupportedOperationException("TODO");
    }

    private MapPropertiesImpl asProperties(Resource resource) {
        return new MapPropertiesImpl(resource.asNode(), getModel());
    }

    /**
     * Creates an expression resource.
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
     * @param call {@link MapFunction.Call} function call to write
     * @return {@link Resource}
     */
    public RDFNode createExpression(MapFunction.Call call) {
        Model model = getModel();
        MapFunction func = call.getFunction();
        // special case of spinmap:equial (alternative way to record)
        if (SPINMAP.equals.getURI().equals(func.name())) {
            MapFunction.Arg arg = func.getArg(SP.arg1.getURI());
            Object val = call.asMap().get(arg);
            if (val instanceof String) {
                return createArgRDFNode(arg.type(), (String) val);
            }
        }
        Resource res = model.createResource();
        Resource function = model.createResource(func.name());
        res.addProperty(RDF.type, function);
        call.asMap().forEach((arg, value) -> {
            RDFNode param = null;
            if (value instanceof MapFunction.Call) {
                if (Objects.equals(value, call)) throw new IllegalArgumentException("Self call");
                param = createExpression((MapFunction.Call) value);
            }
            if (value instanceof String) {
                param = createArgRDFNode(arg.type(), (String) value);
            }
            if (param == null)
                throw new IllegalArgumentException("Wrong value for " + arg.name() + ": " + value);
            Property predicate = getModel().createArgProperty(arg.name());
            res.addProperty(predicate, param);

        });
        return res;
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

    private void validateArg(MapFunction.Arg arg, Object value) throws MapJenaException {
        String argType = arg.type();
        if (value instanceof String) {
            createArgRDFNode(argType, (String) value);
            return;
        }
        if (value instanceof MapFunction.Call) {
            String funcType = ((MapFunction.Call) value).getFunction().returnType();
            validateFuncReturnType(argType, funcType);
            validate((MapFunction.Call) value);
            return;
        }
        throw new IllegalStateException("??");
    }

    private void validateFuncReturnType(String argType, String funcType) {
        if (argType.equals(funcType)) {
            return;
        }
        if (MapFunctionImpl.UNDEFINED.equals(argType) || MapFunctionImpl.UNDEFINED.equals(funcType)) {
            // seems it is okay
            return;
        }
        RDFDatatype literalType = rdfTypes.getTypeByName(funcType);
        if (literalType != null) // todo:
            throw new MapJenaException("TODO:" + literalType);
        if (RDFS.Resource.getURI().equals(argType))
            return;
        // todo:
        throw new MapJenaException("TODO");
    }

    public RDFNode createArgRDFNode(String type, String value) throws MapJenaException {
        Resource uri = getModel().createResource(value);
        if (MapFunctionImpl.UNDEFINED.equals(type)) {
            if (getModel().containsResource(uri)) { // todo: what kind of property?
                type = RDF.Property.getURI();
            } else {
                type = XSD.xstring.getURI();
            }
        }
        RDFDatatype literalType = rdfTypes.getTypeByName(type);
        if (literalType != null) {
            return getModel().createTypedLiteral(value, literalType);
        }
        if (ARG_RESOURCE_MAPPING.getOrDefault(type, r -> false).test(uri)) {
            return uri;
        }
        throw exception(CONTEXT_WRONG_EXPRESSION_ARGUMENT)
                .add(Key.ARG_TYPE, type)
                .add(Key.ARG_VALUE, value).build();
    }

    private Exceptions.Builder exception(Exceptions code) {
        return code.create()
                .add(Key.CONTEXT_SOURCE, getSource().asNode().toString())
                .add(Key.CONTEXT_TARGET, getTarget().asNode().toString());
    }

    @Override
    public String toString() {
        return toString(getModel());
    }

    public String toString(PrefixMapping pm) {
        return String.format("Context{%s => %s}", pm.shortForm(getSource().getURI()), pm.shortForm(target().getURI()));
    }
}

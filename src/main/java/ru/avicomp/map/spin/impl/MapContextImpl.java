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
import ru.avicomp.map.spin.vocabulary.AVC;
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
    private final TypeMapper rdfTypes = TypeMapper.getInstance();
    private final Map<String, Predicate<Resource>> argResourceMapping = Collections.unmodifiableMap(new HashMap<String, Predicate<Resource>>() {
        {
            put(RDFS.Resource.getURI(), r -> true);
            put(RDF.Property.getURI(), r -> r.canAs(OntNAP.class) || r.canAs(OntNDP.class));
            put(RDFS.Class.getURI(), r -> r.canAs(OntCE.class));
            put(RDFS.Datatype.getURI(), r -> r.canAs(OntDT.class));
        }
    });

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
        addMappingRule(target(), null, Collections.emptyList(), Collections.singletonList(RDF.type));
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
    public MapPropertiesImpl addPropertyBridge(MapFunction.Call filterFunction,
                                               MapFunction.Call mappingFunction,
                                               Property target) throws MapJenaException {
        Predicate<Resource> isProperty = argResourceMapping.get(RDF.Property.getURI());
        if (!isProperty.test(target)) {
            throw exception(CONTEXT_WRONG_TARGET_PROPERTY).add(Key.TARGET_PROPERTY, target.getURI()).build();
        }
        validate(mappingFunction);
        RDFNode filterExpression = null;
        if (filterFunction != null) {
            MapFunction f = filterFunction.getFunction();
            if (!f.isBoolean()) {
                throw exception(CONTEXT_NOT_BOOLEAN_FILTER_FUNCTION).add(Key.FUNCTION, f.name()).build();
            }
            validate(filterFunction);
            filterExpression = createExpression(filterFunction);
        }
        RDFNode mappingExpression = createExpression(mappingFunction);

        // collect properties from expressions:
        List<Property> props = Stream.of(filterExpression, mappingExpression)
                .filter(Objects::nonNull)
                .flatMap(MapContextImpl::properties)
                .map(Statement::getObject)
                .filter(RDFNode::isURIResource)
                .filter(p -> isProperty.test(p.asResource()))
                .map(p -> p.as(Property.class))
                .distinct()
                .collect(Collectors.toList());

        Resource mapping = addMappingRule(mappingExpression, filterExpression, props, Collections.singletonList(target));
        MapPropertiesImpl res = asProperties(mapping);
        printFunction(mappingFunction);
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
     * Adds a mapping to the graph as {@code spinmap:rule}.
     *
     * @param mappingExpression resource describing mapping expression
     * @param filterExpression resource describing filter expression
     * @param sources    List of source properties
     * @param targets    List of target properties
     * @return a mapping resource inside graph
     */
    protected Resource addMappingRule(RDFNode mappingExpression, RDFNode filterExpression, List<Property> sources, List<Property> targets) {
        Resource mapping = createMapping(mappingExpression, filterExpression, sources, targets);
        getSource().addProperty(SPINMAP.rule, mapping);
        return mapping;
    }

    public Resource createMapping(RDFNode mappingExpression, RDFNode filterExpression, List<Property> sources, List<Property> targets) {
        MapModelImpl m = getModel();
        Resource template = filterExpression != null ?
                m.getConditionalMappingTemplate(sources.size(), targets.size()) :
                m.getCommonMappingTemplate(sources.size(), targets.size());
        Resource res = m.createResource().addProperty(RDF.type, template);
        res.addProperty(SPINMAP.context, this);
        res.addProperty(SPINMAP.expression, processExpression(res, mappingExpression, sources, targets));
        if (filterExpression != null) {
            res.addProperty(AVC.filter, processExpression(res, filterExpression, sources, targets));
        }
        return res;
    }

    protected RDFNode processExpression(Resource mapping, RDFNode expression, List<Property> sources, List<Property> targets) {
        MapModelImpl m = getModel();
        // replace properties with variables:
        processProperties(mapping, expression, sources, m::getSourcePredicate);
        processProperties(mapping, expression, targets, m::getTargetPredicate);
        // simplify special case of spinmap:equial (this is alternative way to record without anon root)
        if (expression.isAnon() && SPINMAP.equals.equals(expression.asResource().getPropertyResourceValue(RDF.type))) {
            RDFNode arg = expression.asResource().getProperty(SP.arg1).getObject();
            Models.deleteAll(expression.asResource());
            expression = arg;
        }
        return expression;
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

    public static Stream<Statement> properties(RDFNode subject) { // todo: possible recursion
        if (subject == null || !subject.isAnon()) return Stream.empty();
        return Iter.asStream(subject.asResource().listProperties())
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

    @Override
    public MapContextImpl createRelatedContext(OntCE source) throws MapJenaException {
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
                param = makeArgRDFNode(func.name(), arg.type(), (String) value);
            }
            if (param == null)
                throw new IllegalArgumentException("Wrong value for " + arg.name() + ": " + value);
            Property predicate = getModel().createArgProperty(arg.name());
            res.addProperty(predicate, param);

        });
        return res;
    }

    /**
     * Validates a function-call against this context.
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
            makeArgRDFNode(arg.getFunction().name(), argType, (String) value);
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
        if (AVC.undefined.getURI().equals(argType) || AVC.undefined.getURI().equals(funcType)) {
            // seems it is okay
            return;
        }
        RDFDatatype literalType = rdfTypes.getTypeByName(funcType);
        if (literalType != null) // todo:
            throw new MapJenaException("TODO:" + literalType);
        if (RDFS.Resource.getURI().equals(argType))
            return;
        // todo:
        throw new MapJenaException("TODO: " + argType + "|" + funcType);
    }

    public RDFNode makeArgRDFNode(final String function, final String type, final String value) throws MapJenaException {
        Resource uri = null;
        if (getModel().containsResource(ResourceFactory.createResource(value))) {
            uri = getModel().createResource(value);
        }
        // clarify type:
        String foundType;
        if (AVC.undefined.getURI().equals(type)) {
            if (uri != null) {
                foundType = RDFS.Resource.getURI();
            } else {
                foundType = XSD.xstring.getURI();
            }
        } else {
            foundType = type;
        }
        RDFDatatype literalType = rdfTypes.getTypeByName(foundType);
        if (literalType != null) {
            if (uri == null) {
                return getModel().createTypedLiteral(value, literalType);
            } else { // data or annotation property with literal assertion
                foundType = RDF.Property.getURI();
            }
        }
        if (argResourceMapping.getOrDefault(foundType, r -> false).test(uri)) {
            return uri;
        }
        throw exception(CONTEXT_WRONG_EXPRESSION_ARGUMENT)
                .add(Key.FUNCTION, function)
                .add(Key.ARG_TYPE, type)
                .add(Key.ARG_VALUE, value).build();
    }

    protected Exceptions.Builder exception(Exceptions code) {
        return code.create()
                .add(Key.CONTEXT_SOURCE, getSource().asNode().toString())
                .add(Key.CONTEXT_TARGET, target().asNode().toString());
    }

    @Override
    public String toString() {
        return toString(getModel());
    }

    public String toString(PrefixMapping pm) {
        return String.format("Context{%s => %s}", pm.shortForm(getSource().getURI()), pm.shortForm(target().getURI()));
    }
}

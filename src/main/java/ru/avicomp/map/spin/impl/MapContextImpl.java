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
import ru.avicomp.map.*;
import ru.avicomp.map.spin.Exceptions;
import ru.avicomp.map.spin.MapFunctionImpl;
import ru.avicomp.map.spin.MapModelImpl;
import ru.avicomp.map.spin.MappingBuilder;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;
import ru.avicomp.ontapi.jena.model.*;
import ru.avicomp.ontapi.jena.utils.Models;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.*;
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
    protected final TypeMapper rdfTypes = TypeMapper.getInstance();
    protected final Map<String, Predicate<Resource>> argResourceMapping = Collections.unmodifiableMap(new HashMap<String, Predicate<Resource>>() {
        {
            put(RDFS.Resource.getURI(), r -> true);
            put(RDF.Property.getURI(), r -> r.canAs(OntPE.class));
            put(RDFS.Class.getURI(), r -> r.canAs(OntCE.class));
            put(RDFS.Datatype.getURI(), r -> r.canAs(OntDT.class));
            put(SPINMAP.Context.getURI(), r -> r.hasProperty(RDF.type, SPINMAP.Context));
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
        // collects target expression statements to be deleted :
        List<Statement> prev = getModel().statements(this, SPINMAP.target, null)
                .collect(Collectors.toList());
        addProperty(SPINMAP.target, expr);
        // add Mapping-0-1 to create individual with type
        addMappingRule(x -> false, target(), null, RDF.type);
        // delete old target expressions:
        prev.forEach(s -> {
            if (s.getObject().isAnon()) {
                Models.deleteAll(s.getObject().asResource());
            }
            getModel().remove(s);
        });
        writeFunctionBody(func);
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
        // only annotation and data property is allowed for property bridge
        Predicate<RDFNode> isAssertionProperty = r -> r.canAs(OntNDP.class) || r.canAs(OntNAP.class);
        if (!isAssertionProperty.test(target)) {
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
        Resource mapping = addMappingRule(isAssertionProperty, mappingExpression, filterExpression, target);
        MapPropertiesImpl res = asProperties(mapping);
        writeFunctionBody(mappingFunction);
        return res;
    }

    /**
     * Writes a custom function "as is" to the mapping graph.
     *
     * @param call {@link MapFunction.Call}
     */
    protected void writeFunctionBody(MapFunction.Call call) {
        MapFunctionImpl function = (MapFunctionImpl) call.getFunction();
        if (function.isCustom()) {
            addFunctionBody(function);
        }
    }

    /**
     * Adds function "as is" to the graph.
     *
     * @param function {@link MapFunctionImpl}
     */
    protected void addFunctionBody(MapFunctionImpl function) {
        Model m = getModel();
        Models.getAssociatedStatements(function.asResource()).forEach(m::add);
    }

    /**
     * Adds a mapping template call to the graph as {@code spinmap:rule}.
     *
     * @param isProperty        to test that a property can have literal assertions and therefore can be passed to mapping construct.
     * @param mappingExpression resource describing mapping expression
     * @param filterExpression  resource describing filter expression
     * @param target            {@link Property}
     * @return {@link Resource}
     */
    public Resource addMappingRule(Predicate<RDFNode> isProperty, RDFNode mappingExpression, RDFNode filterExpression, Property target) {
        MapModelImpl m = getModel();
        Resource mapping = m.createResource()
                .addProperty(SPINMAP.context, this)
                .addProperty(SPINMAP.expression, mappingExpression)
                .addProperty(m.getTargetPredicate(1), target);
        List<Property> mappingPredicates = setMappingPredicates(mapping, SPINMAP.expression, true, isProperty);
        List<Property> filterPredicates = new ArrayList<>();
        if (filterExpression != null) {
            mapping.addProperty(AVC.filter, filterExpression);
            filterPredicates.addAll(setMappingPredicates(mapping, AVC.filter, false, isProperty));
        }

        boolean hasDefaults = Iter.asStream(mapping.listProperties())
                .map(Statement::getPredicate).map(Property::getLocalName).anyMatch(s -> s.endsWith(AVC.DEFAULT_PREDICATE_SUFFIX));
        Resource template;
        if (filterExpression == null && !hasDefaults && mappingPredicates.size() < 3) {
            // use standard (spinmap) mapping, which does not support filter and default values
            template = SPINMAP.Mapping(mappingPredicates.size(), 1).inModel(m);
        } else {
            // use custom (avc) mapping
            template = MappingBuilder.createMappingTemplate(m, filterPredicates, mappingPredicates);
        }
        mapping.addProperty(RDF.type, template);
        getSource().addProperty(SPINMAP.rule, mapping);
        simplify(mapping);
        return mapping;
    }

    /**
     * Processes mapping template call arguments (predicates).
     *
     * @param mapping             {@link Resource}
     * @param expressionPredicate predicate to find expression
     * @param isProperty          tester to check that a property can have literal assertions and therefore can be passed to mapping construct
     * @param withDefault         if true and there is {@code avc:withDefault} in call adds also default values from expression to mapping
     * @return List of mapping source predicates, e.g. {@code spinmap:sourcePredicate1}, with possible repetitions
     */
    protected List<Property> setMappingPredicates(Resource mapping, Property expressionPredicate, boolean withDefault, Predicate<RDFNode> isProperty) {
        MapModelImpl m = getModel();
        Statement expression = mapping.getRequiredProperty(expressionPredicate);
        // properties from expression, not distinct flat list, i.e. with possible repetitions
        List<Statement> properties = Stream.concat(Stream.of(expression), listProperties(expression.getObject()))
                .filter(s -> isProperty.test(s.getObject()))
                .collect(Collectors.toList());

        // prev property-predicate from mapping
        Map<Property, Property> prev =
                Iter.asStream(mapping.listProperties())
                        .filter(s -> s.getPredicate().getLocalName().startsWith(SPINMAP.SOURCE_PREDICATE_PREFIX))
                        .filter(s -> isProperty.test(s.getObject()))
                        .collect(Collectors.toMap(s -> s.getObject().as(Property.class), Statement::getPredicate));
        List<Property> res = new ArrayList<>();
        int variableIndex = 1;
        Map<Property, Resource> replacement = new HashMap<>();
        for (Statement s : properties) {
            // replace argument property with variable, e.g. spin:_arg1
            Property property = s.getObject().as(Property.class);
            Resource variable;
            if (replacement.containsKey(property)) {
                variable = replacement.get(property);
            } else {
                replacement.put(property, variable = m.getArgVariable(variableIndex++));
            }
            m.add(s.getSubject(), s.getPredicate(), variable);
            m.remove(s);
            // add mapping predicate
            Property predicate;
            if (!prev.containsKey(property)) {
                predicate = m.getSourcePredicate(prev.size() + 1);
                mapping.addProperty(predicate, property);
                prev.put(property, predicate);
            } else {
                predicate = prev.get(property);
            }
            res.add(predicate);
            Resource expr;
            if (withDefault && (expr = s.getSubject()).hasProperty(RDF.type, AVC.withDefault) && expr.hasProperty(SP.arg2)) {
                Literal defaultValue = expr.getProperty(SP.arg2).getObject().asLiteral();
                mapping.addProperty(m.createArgProperty(AVC.sourceDefaultValue(predicate.getLocalName()).getURI()), defaultValue);
            }
        }
        // mapping predicates
        return res;
    }

    /**
     * Simplifies mapping by replacing {@code spinmap:equals} and {@code avc:withDefault} function calls with its short form.
     *
     * @param mapping {@link Resource}
     */
    protected static void simplify(Resource mapping) {
        Model m = mapping.getModel();
        Set<Resource> expressions = listProperties(mapping)
                .filter(s -> Objects.equals(s.getObject(), SPINMAP.equals) || Objects.equals(s.getObject(), AVC.withDefault))
                .filter(s -> Objects.equals(s.getPredicate(), RDF.type))
                .map(Statement::getSubject)
                .collect(Collectors.toSet());
        expressions.forEach(expr -> {
            RDFNode arg = expr.getRequiredProperty(SP.arg1).getObject();
            Set<Statement> statements = m.listStatements(null, null, expr).toSet();
            statements.forEach(s -> {
                m.add(s.getSubject(), s.getPredicate(), arg);
                m.remove(s);
                Models.deleteAll(expr);
            });
        });
    }

    /**
     * Recursively lists all statements for specified subject.
     * Note: a possibility of StackOverflowError in case graph contains a recursion.
     *
     * @param subject {@link RDFNode}, nullable
     * @return Stream of {@link Statement}s
     */
    public static Stream<Statement> listProperties(RDFNode subject) {
        if (subject == null || !subject.isAnon()) return Stream.empty();
        return Iter.asStream(subject.asResource().listProperties())
                .flatMap(s -> s.getObject().isAnon() ? listProperties(s.getObject().asResource()) : Stream.of(s));
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
    public MapContextImpl createRelatedContext(OntCE src2) throws MapJenaException {
        OntCE src1 = getSource();
        List<OntOPE> res = getModel().ontObjects(OntOPE.class)
                .filter(p -> isLinkProperty(p, src2, src1) || isLinkProperty(p, src1, src2))
                .distinct()
                .collect(Collectors.toList());
        if (res.isEmpty()) {
            throw exception(RELATED_CONTEXT_SOURCES_CLASS_NOT_LINKED)
                    .add(Key.CONTEXT_SOURCE, toString(src2)).build();
        }
        if (res.size() != 1) {
            Exceptions.Builder err = exception(RELATED_CONTEXT_AMBIGUOUS_CLASS_LINK)
                    .add(Key.CONTEXT_SOURCE, toString(src2));
            res.forEach(p -> err.add(Key.LINK_PROPERTY, ClassPropertyMap.toNamed(p).getURI()));
            throw err.build();
        }
        return createRelatedContext(res.get(0), src2);
    }

    @Override
    public MapContextImpl createRelatedContext(OntOPE property, OntCE source) throws MapJenaException {
        MapFunction.Builder builder;
        if (isLinkProperty(property, getSource(), source)) {
            builder = createRelatedContextTargetFunction(SPINMAPL.relatedSubjectContext, property);
        } else if (isLinkProperty(property, source, getSource())) {
            builder = createRelatedContextTargetFunction(SPINMAPL.relatedObjectContext, property);
        } else {
            throw exception(RELATED_CONTEXT_SOURCES_CLASS_NOT_LINKED)
                    .add(Key.LINK_PROPERTY, toString(property))
                    .add(Key.CONTEXT_SOURCE, toString(source)).build();
        }
        return getModel().createContext(source, getTarget())
                .addExpression(builder.add(SPINMAPL.context.getURI(), getURI()).build());
    }

    /**
     * Creates a function call builder for {@code spinmapl:relatedSubjectContext} or {@code spinmapl:relatedObjectContext}
     *
     * @param func {@link Resource}
     * @param p    {@link OntOPE}
     * @return {@link MapFunction.Builder}
     */
    private MapFunction.Builder createRelatedContextTargetFunction(Resource func, OntOPE p) {
        return getModel().getManager()
                .getFunction(func.getURI())
                .create()
                .add(SPINMAPL.predicate.getURI(), ClassPropertyMap.toNamed(p).getURI());
    }

    /**
     * Answers if specified property links classes together through domain and range axioms.
     *
     * @param property {@link OntOPE} property to testg
     * @param domain   {@link OntCE} domain candidate
     * @param range    {@link OntCE} range candidate
     * @return true if it is link property.
     */
    public static boolean isLinkProperty(OntOPE property, OntCE domain, OntCE range) {
        return property.domain().anyMatch(d -> Objects.equals(d, domain)) && property.range().anyMatch(r -> Objects.equals(r, range));
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
                param = makeArgRDFNode(func, arg.type(), (String) value);
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
            makeArgRDFNode(arg.getFunction(), argType, (String) value);
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

    public RDFNode makeArgRDFNode(MapFunction function, final String type, final String value) throws MapJenaException {
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
                .add(Key.FUNCTION, function.name())
                .add(Key.ARG_TYPE, type)
                .add(Key.ARG_VALUE, value).build();
    }

    protected Exceptions.Builder exception(Exceptions code) {
        return code.create()
                .add(Key.CONTEXT_SOURCE, toString(getSource()))
                .add(Key.CONTEXT_TARGET, toString(target()));
    }

    protected static String toString(Resource r) {
        return r.asNode().toString();
    }

    @Override
    public String toString() {
        return toString(getModel());
    }

    public String toString(PrefixMapping pm) {
        return String.format("Context{%s => %s}", pm.shortForm(getSource().getURI()), pm.shortForm(target().getURI()));
    }
}

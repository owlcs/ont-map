package ru.avicomp.map.spin;

import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.enhanced.EnhGraph;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.*;
import org.apache.jena.rdf.model.impl.ResourceImpl;
import org.apache.jena.shared.JenaException;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.*;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;
import ru.avicomp.ontapi.jena.model.*;
import ru.avicomp.ontapi.jena.utils.Iter;
import ru.avicomp.ontapi.jena.utils.Models;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.*;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
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

    public static final String DATA_PROPERTY_KEY = "DataProperty";
    protected final Map<String, Predicate<Resource>> argResourceMapping = Collections.unmodifiableMap(new HashMap<String, Predicate<Resource>>() {
        {
            put(RDFS.Resource.getURI(), r -> true);
            put(RDF.Property.getURI(), r -> r.isURIResource() && r.canAs(OntPE.class));
            put(DATA_PROPERTY_KEY, r -> r.canAs(OntNDP.class) || r.canAs(OntNAP.class));
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

    public Stream<OntCE> classes() {
        return target().canAs(OntCE.class) ? Stream.of(getSource(), getTarget()) : Stream.of(getSource());
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
    public MapContextImpl addClassBridge(MapFunction.Call filterFunction, MapFunction.Call mappingFunction) throws MapJenaException {
        if (!testFunction(mappingFunction).getFunction().isTarget()) {
            throw exception(CONTEXT_REQUIRE_TARGET_FUNCTION).addFunction(mappingFunction.getFunction()).build();
        }
        Supplier<RDFNode> filterExpression = filterExpression(filterFunction);
        // collects target expression statements to be deleted :
        List<Statement> prev = getModel().statements(this, SPINMAP.target, null)
                .collect(Collectors.toList());
        // add Mapping-0-1 to create individual with type
        addMappingRule(this::target, filterExpression, RDF.type);
        // add target expression
        RDFNode mappingExpression = createExpression(mappingFunction);
        addProperty(SPINMAP.target, mappingExpression);
        // delete old target expressions:
        prev.forEach(s -> {
            if (s.getObject().isAnon()) {
                Models.deleteAll(s.getObject().asResource());
            }
            getModel().remove(s);
        });
        writeFunctionBody(mappingFunction);
        return this;
    }

    public Stream<Statement> listRules() {
        MapModelImpl m = getModel();
        return m.statements(null, SPINMAP.context, this)
                .map(OntStatement::getSubject)
                .filter(RDFNode::isAnon)
                .flatMap(r -> m.statements(getSource(), SPINMAP.rule, r));
    }

    /**
     * Gets a primary (class to class) mapping rule as ordinal resource.
     * For a valid (see {@link Context#isValid()}) context the result should be present, otherwise it may be empty.
     *
     * @return Optional around {@link Resource}
     */
    public Optional<Resource> primaryRule() {
        return listRules()
                .map(Statement::getObject)
                .map(RDFNode::asResource)
                .filter(r -> r.hasProperty(SPINMAP.targetPredicate1, RDF.type))
                .findFirst();
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
        testFunction(mappingFunction);
        Resource mapping = addMappingRule(() -> createExpression(mappingFunction), filterExpression(filterFunction), target);
        MapPropertiesImpl res = asPropertyBridge(mapping);
        writeFunctionBody(mappingFunction);
        return res;
    }

    protected Supplier<RDFNode> filterExpression(MapFunction.Call func) {
        return testFilterFunction(func) != null ? () -> createExpression(func) : () -> null;
    }

    /**
     * Writes a custom function "as is" to the mapping graph.
     *
     * @param call {@link MapFunction.Call}
     */
    protected void writeFunctionBody(MapFunction.Call call) {
        MapFunctionImpl function = (MapFunctionImpl) call.getFunction();
        if (function.isCustom()) {
            MapModelImpl m = getModel();
            Resource res = addFunctionBody(function);
            Iter.asStream(res.listProperties(AVC.runtime))
                    .map(Statement::getObject)
                    .filter(RDFNode::isLiteral)
                    .map(RDFNode::asLiteral)
                    .map(Literal::getString)
                    .forEach(s -> getRuntimeBody(function, s).apply(m, call));
            // subClassOf
            Iter.asStream(res.listProperties(RDFS.subClassOf))
                    .map(Statement::getResource)
                    .map(Resource::getURI)
                    .map(u -> m.getManager().getFunction(u))
                    .forEach(this::addFunctionBody);
        }

        call.asMap().values().stream()
                .filter(MapFunction.Call.class::isInstance)
                .map(MapFunction.Call.class::cast)
                .forEach(this::writeFunctionBody);
    }

    protected AdjustFunctionBody getRuntimeBody(MapFunction func, String path) {
        Exceptions.Builder err = exception(Exceptions.CONTEXT_WRONG_RUNTIME_FUNCTION_BODY_CLASS).addFunction(func);
        try {
            Class<?> res = Class.forName(path);
            if (!AdjustFunctionBody.class.isAssignableFrom(res)) {
                throw err.build();
            }
            return (AdjustFunctionBody) res.newInstance();
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
            throw err.build(e);
        }
    }

    /**
     * Adds function "as is" to the graph.
     *
     * @param function {@link MapFunctionImpl}
     * @return Resource function in model
     */
    protected Resource addFunctionBody(MapFunctionImpl function) {
        MapModelImpl m = getModel();
        Models.getAssociatedStatements(function.asResource()).forEach(m::add);
        return function.asResource().inModel(m);
    }

    /**
     * Adds a mapping template call to the graph as {@code spinmap:rule}.
     *
     * @param mappingExpression resource describing mapping expression
     * @param filterExpression  resource describing filter expression
     * @param target            {@link Property}
     * @return {@link Resource}
     */
    public Resource addMappingRule(Supplier<RDFNode> mappingExpression,
                                   Supplier<RDFNode> filterExpression,
                                   Property target) {
        MapModelImpl m = getModel();
        ContextMappingHelper helper = new ContextMappingHelper(this, m.createResource());
        // if it is property mapping, the target property must "belong" to the target class:
        Optional<Resource> classMapRule = primaryRule();
        if (classMapRule.isPresent() && !helper.isTargetProperty(target)) {
            throw exception(CONTEXT_WRONG_TARGET_PROPERTY).add(Key.TARGET_PROPERTY, target.getURI()).build();
        }
        helper.getMapping()
                .addProperty(SPINMAP.context, this)
                .addProperty(SPINMAP.targetPredicate1, target);
        RDFNode filterExpr = filterExpression.get();
        RDFNode mapExpr = mappingExpression.get();
        List<Property> mappingPredicates = helper.addExpression(SPINMAP.expression, mapExpr).getSources();
        List<Property> filterPredicates;
        if (filterExpr != null) {
            filterPredicates = helper.addExpression(AVC.filter, filterExpr).getSources();
        } else {
            filterPredicates = Collections.emptyList();
        }

        boolean hasDefaults = helper.hasDefaults();
        boolean hasClassMapFilter = classMapRule.map(r -> r.hasProperty(AVC.filter)).orElse(false);

        Resource template;
        if (filterExpr == null && !hasClassMapFilter && !hasDefaults && mappingPredicates.size() < 3) {
            // use standard (spinmap) mapping, which does not support filter and default values
            template = SPINMAP.Mapping(mappingPredicates.size(), 1).inModel(m);
        } else {
            // use custom (avc) mapping
            template = MappingBuilder.createMappingTemplate(m, classMapRule.isPresent(), filterPredicates, mappingPredicates);
        }
        getSource().addProperty(SPINMAP.rule, helper.getMapping().addProperty(RDF.type, template));
        helper.simplify();
        return helper.getMapping();
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
        List<OntOPE> res = getModel().linkProperties(src1, src2).collect(Collectors.toList());
        if (res.isEmpty()) {
            throw exception(RELATED_CONTEXT_SOURCES_CLASS_NOT_LINKED).add(Key.CONTEXT_SOURCE, src2).build();
        }
        if (res.size() != 1) {
            Exceptions.Builder err = exception(RELATED_CONTEXT_AMBIGUOUS_CLASS_LINK).add(Key.CONTEXT_SOURCE, src2);
            res.forEach(p -> err.add(Key.LINK_PROPERTY, ClassPropertyMap.toNamed(p).getURI()));
            throw err.build();
        }
        return createRelatedContext(src2, res.get(0));
    }

    @Override
    public MapContextImpl createRelatedContext(OntCE source, OntOPE link) throws MapJenaException {
        MapFunction.Builder builder;
        Property property = ClassPropertyMap.toNamed(link);
        if (MapModelImpl.isLinkProperty(link, getSource(), source)) {
            builder = createRelatedContextTargetFunction(SPINMAPL.relatedSubjectContext, property);
        } else if (MapModelImpl.isLinkProperty(link, source, getSource())) {
            builder = createRelatedContextTargetFunction(SPINMAPL.relatedObjectContext, property);
        } else {
            throw exception(RELATED_CONTEXT_SOURCES_CLASS_NOT_LINKED)
                    .add(Key.LINK_PROPERTY, property)
                    .add(Key.CONTEXT_SOURCE, source).build();
        }
        return getModel().createContext(source, getTarget())
                .addClassBridge(null, builder.add(SPINMAPL.context.getURI(), getURI()).build());
    }

    @Override
    public MapContextImpl attachContext(Context other, OntOPE link) throws MapJenaException {
        if (this.equals(other)) {
            throw exception(ATTACHED_CONTEXT_SELF_CALL).build();
        }
        OntCE target = other.getTarget();
        OntCE source = other.getSource();
        if (!getSource().equals(source)) {
            throw exception(ATTACHED_CONTEXT_DIFFERENT_SOURCES).addContext(other).build();
        }
        Property property = ClassPropertyMap.toNamed(link);
        if (!MapModelImpl.isLinkProperty(link, getTarget(), target)) {
            throw exception(ATTACHED_CONTEXT_TARGET_CLASS_NOT_LINKED)
                    .addContext(other)
                    .add(Key.LINK_PROPERTY, property).build();
        }
        // todo: following is a temporary solution, will be replaced with common method #addPropertyBridge
        Model m = getModel();
        getSource().addProperty(SPINMAP.rule,
                m.createResource()
                        .addProperty(RDF.type, SPINMAP.Mapping_0_1)
                        .addProperty(SPINMAP.context, this)
                        .addProperty(SPINMAP.targetPredicate1, link)
                        .addProperty(SPINMAP.expression, m.createResource()
                                .addProperty(RDF.type, SPINMAP.targetResource)
                                .addProperty(SP.arg1, SPIN._arg1)
                                .addProperty(SPINMAP.context, (MapContextImpl) other)));
        return this;
    }

    /**
     * Creates a function call builder for {@code spinmapl:relatedSubjectContext} or {@code spinmapl:relatedObjectContext}
     *
     * @param func {@link Resource}
     * @param p    {@link Property}
     * @return {@link MapFunction.Builder}
     */
    private MapFunction.Builder createRelatedContextTargetFunction(Resource func, Property p) {
        return getModel().getManager()
                .getFunction(func.getURI())
                .create()
                .add(SPINMAPL.predicate.getURI(), p.getURI());
    }

    @Override
    public Stream<Context> dependentContexts() {
        return getModel().listRelatedContexts(this).map(Context.class::cast);
    }

    protected MapPropertiesImpl asPropertyBridge(Resource resource) {
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
        Model m = getModel();
        MapFunction func = call.getFunction();
        Resource res = m.createResource();
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
        return res.addProperty(RDF.type, m.createResource(func.name()));
    }

    /**
     * Validates a function-call against this context.
     *
     * @param func {@link MapFunction.Call} an expression.
     * @throws MapJenaException if something is wrong with function, e.g. wrong argument types.
     */
    @Override
    public void validate(MapFunction.Call func) throws MapJenaException {
        testFunction(func);
    }

    public MapFunction.Call testFunction(MapFunction.Call func) {
        MapJenaException.notNull(func, "Null function call").asMap().forEach(this::validateArg);
        return func;
    }

    public MapFunction.Call testFilterFunction(MapFunction.Call func) throws MapJenaException {
        if (func == null) {
            return null;
        }
        MapFunction f = func.getFunction();
        if (!f.isBoolean()) {
            throw exception(CONTEXT_NOT_BOOLEAN_FILTER_FUNCTION).addFunction(f).build();
        }
        return testFunction(func);
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
            testFunction((MapFunction.Call) value);
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
        RDFDatatype literalType = getModel().getDatatype(funcType);
        if (literalType != null) // todo:
            throw new MapJenaException("TODO:" + literalType);
        if (RDFS.Resource.getURI().equals(argType))
            return;
        // todo:
        throw new MapJenaException("TODO: " + argType + "|" + funcType);
    }

    /**
     * Maps {@link MapFunction.Arg} typed value to {@link RDFNode} if possible, otherwise throws an exception.
     * Notice that the result node is not attached to the graph physically.
     *
     * @param function {@link MapFunction}
     * @param type     String argument type
     * @param value    String argument value
     * @return fresh rdf-node, either literal or resource.
     * @throws MapJenaException if parameters cannot be mapped to RDFNode
     */
    public RDFNode makeArgRDFNode(MapFunction function, final String type, final String value) throws MapJenaException {
        MapModelImpl m = getModel();
        Resource uri = null;
        if (m.containsResource(ResourceFactory.createResource(value))) {
            uri = m.createResource(value);
        }
        String foundType = type;
        String foundValue = value;
        if (AVC.undefined.getURI().equals(type)) {
            if (uri != null) {
                foundType = RDFS.Resource.getURI();
            } else {
                foundType = RDFS.Literal.getURI();
            }
        }
        if (RDFS.Literal.getURI().equals(foundType)) {
            if (value.contains("^^")) {
                foundValue = value.replaceFirst("(.+)\\^\\^.+", "$1");
                foundType = m.expandPrefix(value.replaceFirst(".+\\^\\^(.+)", "$1"));
            } else {
                foundType = XSD.xstring.getURI();
            }
        }
        RDFDatatype literalType = m.getDatatype(foundType);
        if (literalType != null) {
            if (uri == null) {
                return m.createTypedLiteral(foundValue, literalType);
            } else { // data or annotation property with literal assertion
                foundType = DATA_PROPERTY_KEY;
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
        return code.create().addContext(this);
    }

    @Override
    public String toString() {
        return toString(getModel());
    }

    public String toString(PrefixMapping pm) {
        return String.format("Context{%s => %s}", pm.shortForm(getSource().getURI()), pm.shortForm(target().getURI()));
    }

    /**
     * Auxiliary class, a helper to process mapping template call arguments (predicates).
     * Also a holder for class-properties maps related to the specified context.
     */
    static class ContextMappingHelper {
        private final Resource mapping;
        private final MapContextImpl context;
        private Set<? extends RDFNode> sourceClassProperties;
        private Set<? extends RDFNode> targetClassProperties;

        ContextMappingHelper(MapContextImpl context, Resource mapping) {
            this.mapping = Objects.requireNonNull(mapping);
            this.context = Objects.requireNonNull(context);
        }

        /**
         * Simplifies mapping by replacing {@code spinmap:equals} and {@code avc:withDefault} function calls with its short form.
         */
        void simplify() {
            Model m = mapping.getModel();
            Set<Resource> expressions = MapModelImpl.listProperties(mapping)
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

        Resource getMapping() {
            return mapping;
        }

        Map<Property, Property> getSourcePredicatesMap() {
            return getMapPredicates(SPINMAP.SOURCE_PREDICATE_PREFIX);
        }

        Map<Property, Property> getTargetPredicatesMap() {
            return getMapPredicates(SPINMAP.TARGET_PREDICATE_PREFIX);
        }

        boolean hasDefaults() {
            return properties()
                    .map(Statement::getPredicate)
                    .map(Property::getLocalName)
                    .anyMatch(s -> s.endsWith(AVC.DEFAULT_PREDICATE_SUFFIX));
        }

        /**
         * Gets and caches properties belonging to the source context class.
         *
         * @return Set of properties
         */
        public Set<? extends RDFNode> getSourceClassProperties() {
            return sourceClassProperties == null ? sourceClassProperties = getClassProperties(context.getSource()) : sourceClassProperties;
        }

        /**
         * Gets and caches properties belonging to the target context class.
         *
         * @return Set of properties
         */
        public Set<? extends RDFNode> getTargetClassProperties() {
            return targetClassProperties == null ? targetClassProperties = getClassProperties(context.target()) : targetClassProperties;
        }

        boolean isContextProperty(RDFNode node) {
            return node.isURIResource() && (isSourceProperty(node) || isTargetProperty(node));
        }

        boolean isSourceProperty(RDFNode property) {
            return getSourceClassProperties().contains(property);
        }

        boolean isTargetProperty(RDFNode property) {
            return getTargetClassProperties().contains(property);
        }

        private Set<Property> getClassProperties(Resource clazz) {
            return clazz.canAs(OntCE.class) ?
                    context.getModel().properties(clazz.as(OntCE.class)).collect(Collectors.toSet()) :
                    Collections.emptySet();
        }

        private Map<Property, Property> getMapPredicates(String prefix) {
            return properties()
                    .filter(s -> s.getPredicate().getLocalName().startsWith(prefix))
                    .collect(Collectors.toMap(s -> s.getObject().as(Property.class), Statement::getPredicate));
        }

        private Stream<Statement> properties() {
            return Iter.asStream(mapping.listProperties())
                    .filter(s -> s.getObject().isURIResource());
        }

        /**
         * Adds an expression to mapping call and process its arguments (predicates).
         *
         * @param expressionPredicate {@link Property} predicate. e.g. {@code spinmap:expression}
         * @param expressionObject    {@link RDFNode} the expression body
         * @return {@link ExprRes} container with property collections that will be needed when building a mapping template.
         */
        ExprRes addExpression(Property expressionPredicate, RDFNode expressionObject) {
            mapping.addProperty(expressionPredicate, expressionObject);
            ExprRes res = addExpression(expressionPredicate);
            if (!res.target.isEmpty()) {
                throw new UnsupportedOperationException("TODO: expression with arguments from right side are not supported right now.");
            }
            return res;
        }

        private ExprRes addExpression(Property expressionPredicate) {
            ExprRes res = new ExprRes();
            MapModelImpl m = context.getModel();
            Map<Property, Property> sourcePredicatesMap = getSourcePredicatesMap();
            Map<Property, Property> targetPredicatesMap = getTargetPredicatesMap();
            Statement expression = mapping.getRequiredProperty(expressionPredicate);
            // properties from expression, not distinct flat list, i.e. with possible repetitions
            List<Statement> properties = Stream.concat(Stream.of(expression), MapModelImpl.listProperties(expression.getObject()))
                    .filter(s -> isContextProperty(s.getObject()))
                    .collect(Collectors.toList());
            int variableIndex = 1;
            for (Statement s : properties) {
                Resource expr = s.getSubject();
                Property property = s.getObject().as(Property.class);
                if (!expr.hasProperty(RDF.type, AVC.asIRI)) {
                    // replace argument property with variable, e.g. spin:_arg1
                    Resource variable;
                    if (res.replacement.containsKey(property)) {
                        variable = res.replacement.get(property);
                    } else {
                        res.replacement.put(property, variable = m.getArgVariable(variableIndex++));
                    }
                    m.add(expr, s.getPredicate(), variable);
                    m.remove(s);
                }
                // add mapping predicate
                Property predicate;
                if (isSourceProperty(property)) {
                    predicate = processPredicate(property, sourcePredicatesMap, m::getSourcePredicate);
                    res.sources.add(predicate);
                } else {
                    predicate = processPredicate(property, targetPredicatesMap, m::getTargetPredicate);
                    res.target.add(predicate);
                }
                // process default value
                if (expr.hasProperty(RDF.type, AVC.withDefault) && expr.hasProperty(SP.arg2)) {
                    Literal defaultValue = expr.getProperty(SP.arg2).getObject().asLiteral();
                    mapping.addProperty(m.createArgProperty(AVC.predicateDefaultValue(predicate.getLocalName()).getURI()), defaultValue);
                }
            }
            return res;
        }

        private Property processPredicate(Property property, Map<Property, Property> prev, IntFunction<Property> generator) {
            Property predicate;
            if (!prev.containsKey(property)) {
                predicate = generator.apply(prev.size() + 1);
                mapping.addProperty(predicate, property);
                prev.put(property, predicate);
            } else {
                predicate = prev.get(property);
            }
            return predicate;
        }

        /**
         * Expression settings holder.
         * {@code sources} - List of mapping source predicates, e.g. {@code spinmap:sourcePredicate1}, with possible repetitions
         * {@code targets} - List of mapping target predicates, e.g. {@code spinmap:targetPredicate1}, with possible repetitions
         * {@code replacement} - Property-Variable map, where keys are existing properties either from source or target classes
         * and values - spin variables, e.g. {@code spin:_arg1}
         */
        class ExprRes {
            private final List<Property> sources = new ArrayList<>();
            private final List<Property> target = new ArrayList<>();
            private final Map<Property, Resource> replacement = new HashMap<>();

            public List<Property> getSources() {
                return Collections.unmodifiableList(sources);
            }
        }
    }
}

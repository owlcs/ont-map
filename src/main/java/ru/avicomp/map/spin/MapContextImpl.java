package ru.avicomp.map.spin;

import org.apache.jena.enhanced.EnhGraph;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.*;
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
import ru.avicomp.ontapi.jena.impl.OntObjectImpl;
import ru.avicomp.ontapi.jena.model.*;
import ru.avicomp.ontapi.jena.utils.Iter;
import ru.avicomp.ontapi.jena.utils.Models;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.*;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ru.avicomp.map.spin.Exceptions.*;

/**
 * Implementation of class context (for resource with type {@code spinmap:Context}).
 * <p>
 * Created by @szuev on 14.04.2018.
 */
@SuppressWarnings("WeakerAccess")
public class MapContextImpl extends OntObjectImpl implements Context {

    public MapContextImpl(Node n, EnhGraph m) {
        super(n, m);
    }

    @Override
    public MapModelImpl getModel() {
        return (MapModelImpl) super.getModel();
    }

    @Override
    public OntObject asResource() {
        return this;
    }

    @Override
    public OntCE getSource() throws JenaException {
        return source().as(OntCE.class);
    }

    @Override
    public OntCE getTarget() throws JenaException {
        return target().as(OntCE.class);
    }

    public Stream<OntCE> classes() {
        return target().canAs(OntCE.class) ? Stream.of(getSource(), getTarget()) : Stream.of(getSource());
    }

    /**
     * Returns source class expression as a regular resource.
     *
     * @return {@link Resource} for predicate {@code spinmap:sourceClass}
     * @throws JenaException nothing found
     */
    public Resource source() throws JenaException {
        return getRequiredProperty(SPINMAP.sourceClass).getObject().asResource();
    }

    /**
     * Returns a target class resource, which may not be {@link OntCE} in special case of {@code owl:NamedIndividual} mapping.
     *
     * @return {@link Resource} for predicate {@code spinmap:targetClass}
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
        testFilterFunction(filterFunction);
        MapModelImpl m = getModel();

        // collects target expression statements to be deleted :
        List<Statement> prev = m.statements(this, SPINMAP.target, null).collect(Collectors.toList());

        // don't add new non-filtering mapping in case there is already other non-filtering mapping deriving the class
        if (filterFunction != null || m.listContexts()
                .filter(c -> Objects.equals(c.target(), target()))
                .map(MapContextImpl::primaryRule)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .allMatch(r -> r.hasProperty(AVC.filter))) {
            RDFNode filterExpression = filterFunction == null ? null : createExpression(filterFunction);
            // add Mapping-0-1 to create individual with target type
            ContextMappingHelper.addPrimaryRule(this, filterExpression);
        }

        // add target expression
        RDFNode mappingExpression = createExpression(mappingFunction);
        addProperty(SPINMAP.target, mappingExpression);
        // delete old target expressions:
        prev.forEach(s -> {
            if (s.getObject().isAnon()) {
                Models.deleteAll(s.getObject().asResource());
            }
            m.remove(s);
        });
        writeFunctionBody(mappingFunction);
        return this;
    }

    @Override
    public MapPropertiesImpl addPropertyBridge(MapFunction.Call filterFunction,
                                               MapFunction.Call mappingFunction,
                                               Property target) throws MapJenaException {
        // the target property must "belong" to the target class:
        ContextMappingHelper helper = ContextMappingHelper.create(this);
        if (!helper.isTargetProperty(target)) {
            throw exception(CONTEXT_WRONG_TARGET_PROPERTY).add(Key.TARGET_PROPERTY, target.getURI()).build();
        }
        testFunction(mappingFunction);
        RDFNode filterExpression = testFilterFunction(filterFunction) != null ? createExpression(filterFunction) : null;
        RDFNode mappingExpression = createExpression(mappingFunction);
        Resource mapping = ContextMappingHelper.addMappingRule(helper, mappingExpression, filterExpression, target);
        writeFunctionBody(mappingFunction);
        return asPropertyBridge(mapping);
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
            Resource res = m.addFunctionBody(function);
            Iter.asStream(res.listProperties(AVC.runtime))
                    .map(Statement::getObject)
                    .filter(RDFNode::isLiteral)
                    .map(RDFNode::asLiteral)
                    .map(Literal::getString)
                    .forEach(s -> findRuntimeBody(function, s).apply(m, call));
            // subClassOf
            Iter.asStream(res.listProperties(RDFS.subClassOf))
                    .map(Statement::getResource)
                    .map(Resource::getURI)
                    .map(u -> m.getManager().getFunction(u))
                    .forEach(m::addFunctionBody);
        }
        call.asMap().values().stream()
                .filter(MapFunction.Call.class::isInstance)
                .map(MapFunction.Call.class::cast)
                .forEach(this::writeFunctionBody);
    }

    /**
     * @param func      {@link MapFunctionImpl}
     * @param classPath String
     * @return {@link AdjustFunctionBody}
     * @throws MapJenaException can't fetch runtime body
     */
    protected static AdjustFunctionBody findRuntimeBody(MapFunctionImpl func, String classPath) throws MapJenaException {
        try {
            Class<?> res = Class.forName(classPath);
            if (!AdjustFunctionBody.class.isAssignableFrom(res)) {
                throw new MapJenaException(func.name() + ": incompatible class type: " + classPath + " <> " + res.getName());
            }
            return (AdjustFunctionBody) res.newInstance();
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
            throw new MapJenaException(func.name() + ": can't init " + classPath, e);
        }
    }

    /**
     * Gets a primary (class to class) mapping rule as ordinal resource.
     * For a valid (see {@link Context#isValid()}) standalone context
     * the result should be present, otherwise it may be empty.
     *
     * @return Optional around mapping {@link Resource}
     */
    public Optional<Resource> primaryRule() {
        return listTypedRules()
                .filter(r -> r.hasProperty(SPINMAP.expression, target()))
                .findFirst();
    }

    /**
     * Lists {@code spinmap:rule} which are deriving {@code rdf:type}.
     *
     * @return Stream of {@link Resource}s
     */
    public Stream<Resource> listTypedRules() {
        return listRules()
                .filter(r -> r.hasProperty(SPINMAP.targetPredicate1, RDF.type));
    }

    @Override
    public MapFunction.Call getMapping() {
        MapModelImpl m = getModel();
        Optional<RDFNode> target = m.statements(this, SPINMAP.target, null)
                .map(Statement::getObject).findFirst();
        if (!target.isPresent()) return null;
        // todo: implement
        throw new UnsupportedOperationException("TODO");
    }


    @Override
    public Stream<PropertyBridge> properties() {
        return listMapProperties().map(PropertyBridge.class::cast);
    }

    public Stream<MapPropertiesImpl> listMapProperties() {
        return listRules() // skip primary rule:
                .filter(r -> !(r.hasProperty(SPINMAP.expression, target()) && r.hasProperty(SPINMAP.targetPredicate1, RDF.type)))
                .map(this::asPropertyBridge);
    }

    protected Stream<Resource> listRules() {
        return listRuleStatements()
                .map(Statement::getObject)
                .map(RDFNode::asResource);
    }

    /**
     * Lists all {@code _:s spinmap:rule _:o} statements belonging to this context.
     *
     * @return Stream of {@link Statement}
     */
    public Stream<Statement> listRuleStatements() {
        MapModelImpl m = getModel();
        return m.statements(null, SPINMAP.context, this)
                .map(OntStatement::getSubject)
                .filter(RDFNode::isAnon)
                .flatMap(r -> m.statements(getSource(), SPINMAP.rule, r));
    }

    @Override
    public Context removeProperties(PropertyBridge properties) {
        // todo: implement
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
        MapModelImpl m = getModel();
        MapFunction.Builder builder;
        Property property = ClassPropertyMap.toNamed(link);
        if (m.isLinkProperty(link, getSource(), source)) {
            builder = createRelatedContextTargetFunction(SPINMAPL.relatedSubjectContext, property);
        } else if (m.isLinkProperty(link, source, getSource())) {
            builder = createRelatedContextTargetFunction(SPINMAPL.relatedObjectContext, property);
        } else {
            throw exception(RELATED_CONTEXT_SOURCES_CLASS_NOT_LINKED)
                    .add(Key.LINK_PROPERTY, property)
                    .add(Key.CONTEXT_SOURCE, source).build();
        }
        return m.createContext(source, getTarget())
                .addClassBridge(null, builder.add(SPINMAPL.context.getURI(), getURI()).build());
    }

    @Override
    public MapPropertiesImpl attachContext(Context other, OntOPE link) throws MapJenaException {
        if (this.equals(other)) {
            throw exception(ATTACHED_CONTEXT_SELF_CALL).build();
        }
        MapModelImpl m = getModel();
        OntCE target = other.getTarget();
        OntCE source = other.getSource();
        if (!getSource().equals(source)) {
            throw exception(ATTACHED_CONTEXT_DIFFERENT_SOURCES).addContext(other).build();
        }
        Property property = ClassPropertyMap.toNamed(link);
        if (!m.isLinkProperty(link, getTarget(), target)) {
            throw exception(ATTACHED_CONTEXT_TARGET_CLASS_NOT_LINKED)
                    .addContext(other)
                    .add(Key.LINK_PROPERTY, property).build();
        }
        // todo: following is a temporary solution, will be replaced with common method #addPropertyBridge
        Resource mapping = m.createResource()
                .addProperty(RDF.type, SPINMAP.Mapping_0_1)
                .addProperty(SPINMAP.context, this)
                .addProperty(SPINMAP.targetPredicate1, link)
                .addProperty(SPINMAP.expression, m.createResource()
                        .addProperty(RDF.type, SPINMAP.targetResource)
                        .addProperty(SP.arg1, SPIN._arg1)
                        .addProperty(SPINMAP.context, (MapContextImpl) other));
        getSource().addProperty(SPINMAP.rule, mapping);
        return asPropertyBridge(mapping);
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
        MapModelImpl m = getModel();
        return Stream.concat(m.listChainedContexts(this), m.listRelatedContexts(this)).distinct().map(Context.class::cast);
    }

    /**
     * Makes a new {@link PropertyBridge} instance.
     *
     * @param resource {@link Resource} to wrap
     * @return {@link MapPropertiesImpl}
     */
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
        MapModelImpl m = getModel();
        MapFunction func = call.getFunction();
        Resource res = m.createResource();
        call.asMap().forEach((arg, value) -> {
            RDFNode param = null;
            if (value instanceof MapFunction.Call) {
                if (Objects.equals(value, call)) throw new IllegalArgumentException("Self call");
                param = createExpression((MapFunction.Call) value);
            }
            if (value instanceof String) {
                param = m.toNode((String) value);
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

    public MapFunction.Call testFunction(MapFunction.Call func) throws MapJenaException {
        MapJenaException holder = exception(CONTEXT_WRONG_EXPRESSION_ARGUMENT).addFunction(func.getFunction()).build();
        MapJenaException.notNull(func, "Null function call").asMap().forEach((arg, value) -> {
            try {
                MapModelImpl m = getModel();
                ArgValidator v = new ArgValidator(m, arg);
                if (value instanceof String) {
                    v.testStringValue((String) value);
                    return;
                }
                if (value instanceof MapFunction.Call) {
                    MapFunction.Call call = (MapFunction.Call) value;
                    v.testFunctionValue(call.getFunction());
                    testFunction(call);
                    return;
                }
                throw new IllegalStateException("Should never happen");
            } catch (MapJenaException e) {
                holder.addSuppressed(e);
            }
        });
        if (holder.getSuppressed().length == 0)
            return func;
        throw holder;
    }

    protected Exceptions.Builder exception(Exceptions code) {
        return code.create().addContext(this);
    }

    @Override
    public String toString() {
        return toString(getModel());
    }

    public String toString(PrefixMapping pm) {
        return toString(r -> r.isAnon() ? r.toString() : pm.shortForm(r.getURI()));
    }

    private String toString(Function<Resource, String> toString) {
        return String.format("Context{%s => %s}", toString.apply(source()), toString.apply(target()));
    }

    /**
     * Auxiliary class, a helper to validate a {@link MapFunction.Arg function-call argument} values,
     * which could be either nested function or string representation of literal or resource
     */
    static class ArgValidator {
        private final MapModelImpl model;
        private final MapFunction.Arg argument;

        ArgValidator(MapModelImpl model, MapFunction.Arg argument) {
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
                    .add(Key.ARG, argument.name())
                    .addFunction(value)
                    .add(Key.ARG_TYPE, arg.toString())
                    .add(Key.ARG_VALUE, type.toString());
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
                    .add(Key.ARG, argument.name())
                    .add(Key.ARG_TYPE, arg.toString())
                    .add(Key.ARG_VALUE, node.toString());
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

    /**
     * Auxiliary class, a helper to process mapping template call arguments (predicates).
     * Also a holder for class-properties maps related to the specified context.
     * To relieve the main class.
     */
    static class ContextMappingHelper {
        private final Resource mapping;
        private final MapContextImpl context;

        private Set<? extends RDFNode> sourceClassProperties;
        private Set<? extends RDFNode> targetClassProperties;

        private ContextMappingHelper(MapContextImpl context, Resource mapping) {
            this.mapping = Objects.requireNonNull(mapping);
            this.context = Objects.requireNonNull(context);
        }

        static ContextMappingHelper create(MapContextImpl context) {
            return new ContextMappingHelper(context, context.getModel().createResource());
        }

        static void addPrimaryRule(MapContextImpl context, RDFNode filterExpression) {
            addMappingRule(create(context), context.target(), filterExpression, RDF.type);
        }

        /**
         * Adds a mapping template call to the graph as {@code spinmap:rule}.
         *
         * @param helper            {@link ContextMappingHelper}
         * @param mappingExpression resource describing mapping expression
         * @param filterExpression  resource describing filter expression
         * @param target            {@link Property}
         * @return {@link Resource}
         */
        static Resource addMappingRule(ContextMappingHelper helper,
                                       RDFNode mappingExpression,
                                       RDFNode filterExpression,
                                       Property target) {
            // todo: validate property ranges if it is possible
            MapContextImpl context = helper.context;
            Resource mapping = helper.mapping;
            MapModelImpl m = context.getModel();
            Optional<Resource> classMapRule = context.primaryRule();
            mapping.addProperty(SPINMAP.context, context)
                    .addProperty(SPINMAP.targetPredicate1, target);
            List<Property> mappingPredicates = helper.addExpression(SPINMAP.expression, mappingExpression).getSources();
            List<Property> filterPredicates;
            if (filterExpression != null) {
                filterPredicates = helper.addExpression(AVC.filter, filterExpression).getSources();
            } else {
                filterPredicates = Collections.emptyList();
            }

            boolean hasDefaults = helper.hasDefaults();
            boolean hasClassMapFilter = classMapRule.map(r -> r.hasProperty(AVC.filter)).orElse(false);

            Resource template;
            int mappingSources = (int) mappingPredicates.stream().distinct().count();
            if (filterExpression == null && !hasClassMapFilter && !hasDefaults && mappingSources < 3) {
                // use standard (spinmap) mapping, which does not support filter and default values
                template = SPINMAP.Mapping(mappingSources, 1).inModel(m);
            } else {
                // use custom (avc) mapping
                template = MappingBuilder.createMappingTemplate(m, classMapRule.isPresent(), filterPredicates, mappingPredicates);
            }
            context.getSource().addProperty(SPINMAP.rule, mapping.addProperty(RDF.type, template));
            simplify(mapping);
            return mapping;
        }

        /**
         * Simplifies mapping by replacing {@code spinmap:equals} function calls with its short form.
         */
        private static void simplify(Resource mapping) {
            Model m = mapping.getModel();
            Set<Resource> expressions = MapModelImpl.listProperties(mapping)
                    .filter(s -> Objects.equals(s.getObject(), SPINMAP.equals))
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

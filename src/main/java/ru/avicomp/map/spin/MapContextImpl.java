package ru.avicomp.map.spin;

import org.apache.jena.enhanced.EnhGraph;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.*;
import org.apache.jena.shared.JenaException;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.RDFS;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.*;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;
import ru.avicomp.ontapi.jena.impl.OntObjectImpl;
import ru.avicomp.ontapi.jena.model.OntCE;
import ru.avicomp.ontapi.jena.model.OntOPE;
import ru.avicomp.ontapi.jena.model.OntObject;
import ru.avicomp.ontapi.jena.model.OntStatement;
import ru.avicomp.ontapi.jena.utils.Iter;
import ru.avicomp.ontapi.jena.utils.Models;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
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
            RDFNode filterExpression = filterFunction == null ? null : m.createExpression(filterFunction);
            // add Mapping-0-1 to create individual with target type
            ContextMappingHelper.addPrimaryRule(this, filterExpression);
        }

        // add target expression
        RDFNode mappingExpression = m.createExpression(mappingFunction);
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
        MapModelImpl m = getModel();
        RDFNode filterExpression = testFilterFunction(filterFunction) != null ? m.createExpression(filterFunction) : null;
        RDFNode mappingExpression = m.createExpression(mappingFunction);
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

    @Override
    public MapFunction.Call getMapping() {
        MapModelImpl m = getModel();
        Optional<RDFNode> target = m.statements(this, SPINMAP.target, null)
                .map(Statement::getObject).findFirst();
        return target.map(n -> m.parseExpression(m.createResource(), n.asResource())).orElse(null);
    }

    @Override
    public MapFunction.Call getFilter() {
        Optional<Resource> mapping = primaryRule().filter(r -> r.hasProperty(AVC.filter));
        return mapping.map(r -> getModel().parseExpression(r, r.getPropertyResourceValue(AVC.filter), true)).orElse(null);
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

    /**
     * Gets a primary (class to class) mapping rule as ordinal resource.
     * For a valid (see {@link Context#isValid()}) standalone context
     * the result should be present, otherwise it may be empty.
     * Example of such mapping:
     * <pre>{@code
     * [ a  spinmap:Mapping-0-1 ;
     *      spinmap:context           map:Context-SourceClass-TargetClass ;
     *      spinmap:expression        :TargetClass ;
     *      spinmap:targetPredicate1  rdf:type
     *  ] .
     * }</pre>
     *
     * @return Optional around mapping {@link Resource}, which is an anonymous resource:
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
    public Context deletePropertyBridge(PropertyBridge properties) {
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
                ArgValidationHelper v = new ArgValidationHelper(m, arg);
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

}

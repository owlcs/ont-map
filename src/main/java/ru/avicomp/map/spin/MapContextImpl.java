/*
 * This file is part of the ONT MAP.
 * The contents of this file are subject to the Apache License, Version 2.0.
 * Copyright (c) 2018, Avicomp Services, AO
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.avicomp.map.spin;

import org.apache.jena.enhanced.EnhGraph;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.*;
import org.apache.jena.shared.JenaException;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.RDFS;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import org.topbraid.spin.vocabulary.SPL;
import ru.avicomp.map.MapContext;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapJenaException;
import ru.avicomp.map.PropertyBridge;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;
import ru.avicomp.map.utils.ModelUtils;
import ru.avicomp.ontapi.jena.impl.OntObjectImpl;
import ru.avicomp.ontapi.jena.model.OntCE;
import ru.avicomp.ontapi.jena.model.OntOPE;
import ru.avicomp.ontapi.jena.utils.Iter;
import ru.avicomp.ontapi.jena.utils.Models;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static ru.avicomp.map.spin.Exceptions.*;

/**
 * Implementation of class context (for resource with type {@code spinmap:Context}).
 * <p>
 * Created by @szuev on 14.04.2018.
 */
@SuppressWarnings("WeakerAccess")
public class MapContextImpl extends OntObjectImpl implements MapContext, ToString {

    public MapContextImpl(Node n, EnhGraph m) {
        super(n, m);
    }

    @Override
    public String name() {
        return ModelUtils.getResourceID(this);
    }

    @Override
    public MapModelImpl getModel() {
        return (MapModelImpl) super.getModel();
    }

    @Override
    public MapContextImpl asResource() {
        return this;
    }

    /**
     * Returns the source (RDFS) class expression as a regular resource.
     *
     * @return {@link Resource} for predicate {@code spinmap:sourceClass}
     * @throws JenaException nothing found
     */
    public Resource getSourceClass() throws JenaException {
        return getRequiredProperty(SPINMAP.sourceClass).getResource();
    }

    /**
     * Returns the target (RDFS) class resource, which may not be {@link OntCE}
     * in the special case of {@code owl:NamedIndividual} mapping.
     *
     * @return {@link Resource} for predicate {@code spinmap:targetClass}
     * @throws JenaException illegal state - no resource found
     */
    public Resource getTargetClass() throws JenaException {
        return getRequiredProperty(SPINMAP.targetClass).getResource();
    }

    @Override
    public OntCE getSource() throws JenaException {
        return getModel().getRequiredObject(this, SPINMAP.sourceClass, OntCE.class);
    }

    @Override
    public OntCE getTarget() throws JenaException {
        return getModel().getRequiredObject(this, SPINMAP.targetClass, OntCE.class);
    }

    /**
     * Lists all classes for which this context is built.
     *
     * @return {@link ExtendedIterator} of {@link OntCE}s
     */
    public ExtendedIterator<OntCE> listClasses() {
        return Iter.of(SPINMAP.sourceClass, SPINMAP.targetClass)
                .mapWith(x -> getModel().getObject(this, x, OntCE.class))
                .filterDrop(Objects::isNull);
    }

    /**
     * Answers {@code true} if the context is public.
     * A public context is allowed to be shown in GUI.
     *
     * @return boolean
     */
    public boolean isPublic() {
        return getTargetClass().canAs(OntCE.class) && getSourceClass().canAs(OntCE.class);
    }

    @Override
    public boolean isValid() {
        return isPublic() && findMapping().isPresent();
    }

    @Override
    public ModelCallImpl getMapping() {
        return findMapping().orElse(null);
    }

    @Override
    public ModelCallImpl getFilter() {
        return findFilter().orElse(null);
    }

    @Override
    public MapContextImpl addClassBridge(MapFunction.Call filterFunction,
                                         MapFunction.Call mappingFunction) throws MapJenaException {
        Optional<MapFunction.Call> filter = Optional.ofNullable(filterFunction);

        Optional<ModelCallImpl> filterPrev = findFilter();
        Optional<ModelCallImpl> mappingPrev = findMapping();
        Optional<Statement> rule = findPrimaryRuleStatement();

        ContextHelper context = ContextHelper.create(this);
        validateContextMapping(mappingFunction, context);
        filter.ifPresent(x -> validateContextFilter(x, context));

        // collect and delete all target expression statements:
        MapModelImpl m = getModel();
        m.listStatements(this, SPINMAP.target, (RDFNode) null).toList().forEach(s -> {
            if (s.getObject().isAnon()) {
                Models.deleteAll(s.getObject().asResource());
            }
            m.remove(s);
        });
        // delete the previously associated filter function-call
        filterPrev.ifPresent(this::eraseFunction);
        // delete the previously associated mapping function-call
        mappingPrev.ifPresent(this::eraseFunction);
        // delete the previous primary rule
        rule.ifPresent(this::deleteRule);

        // add mapping template call, primary rule and filter expression.
        context.addMappingRule(getTargetClass(), filter.map(m::createExpression).orElse(null), RDF.type);

        // add target expression
        addProperty(SPINMAP.target, m.createExpression(mappingFunction));

        // populate the mapping with functions bodies,
        // this is need to make sure it will work correctly in any other SPIN-based system
        writeFunction(mappingFunction);
        filter.ifPresent(this::writeFunction);

        // update named individual context if it is present and allowed ->
        // it should not produce the owl:NamedIndividual declaration for anonymous individuals
        handleNamedIndividualContext();

        return this;
    }

    /**
     * Creates, updates or deletes a technical (i.e. not {@link #isPublic() public}) context,
     * that derives a {@link OWL#NamedIndividual owl:NamedIndividual} declaration.
     *
     * @see MapConfigImpl#generateNamedIndividuals()
     * @see AVC#self
     * @see SPINMAPL#self
     */
    protected void handleNamedIndividualContext() {
        Resource target = getTargetClass();
        if (OWL.NamedIndividual.equals(target)) return;
        MapModelImpl model = getModel();
        MapManagerImpl manager = model.getManager();
        MapContextImpl context = findNamedIndividualContext().orElse(null);

        // delete old if it is found
        if (!manager.getMappingConfiguration().generateNamedIndividuals()) {
            if (context != null) {
                context.deleteAll();
            }
            return;
        }

        Resource self = findMapping().orElseThrow(MapJenaException.IllegalState::new)
                .getFunction().canProduceBNodes() ? AVC.self : SPINMAPL.self;
        // create new
        if (context == null) {
            model.asContext(model.makeContext(target, OWL.NamedIndividual))
                    .addClassBridge(null, manager.getFunction(self).create().build());
            return;
        }

        // update
        Resource func = context.findMapping().orElseThrow(MapJenaException.IllegalState::new).getFunction().asResource();
        if (self.equals(func)) {
            // nothing to update
            return;
        }
        // change map-function (spinmap:self -> avc:self or vice versa):
        context.addClassBridge(null, manager.getFunction(self).create().build());
        // need to delete unused avc:self body
        deleteFunction(func);
    }

    /**
     * Finds and returns a context deriving a {@link OWL#NamedIndividual owl:NamedIndividual} type.
     * It is a technical, not {@link #isPublic() public} context,
     * which is regulated by the manager's config (see {@link MapConfigImpl#generateNamedIndividuals()}).
     *
     * @return {@link Optional} around {@link MapContextImpl} to generate {@code owl:NamedIndividual} declarations.
     */
    private Optional<MapContextImpl> findNamedIndividualContext() {
        return Iter.findFirst(getModel().listContextsFor(this)
                .filterKeep(x -> OWL.NamedIndividual.equals(x.getTargetClass())))
                .filter(c -> {
                    if (c.findFilter().isPresent()) return false;
                    return c.findMapping()
                            .map(MapFunctionImpl.CallImpl::getFunction)
                            .map(MapFunctionImpl::asResource)
                            .filter(r -> SPINMAPL.self.equals(r) || AVC.self.equals(r)).isPresent();
                });
    }

    @Override
    public PropertyBridgeImpl addPropertyBridge(MapFunction.Call filterFunction,
                                                MapFunction.Call mappingFunction,
                                                Property target) throws MapJenaException {
        Optional<MapFunction.Call> filter = Optional.ofNullable(filterFunction);
        // the target property must "belong" to the target class:
        ContextHelper context = ContextHelper.create(this);
        if (!context.isTargetProperty(target)) {
            throw error(PROPERTY_BRIDGE_WRONG_TARGET_PROPERTY).addProperty(target).build();
        }
        validatePropertyMapping(mappingFunction, context);
        filter.ifPresent(x -> validatePropertyFilter(x, context));
        MapModelImpl m = getModel();
        RDFNode mappingExpression = m.createExpression(mappingFunction);
        Resource mapping = context.addMappingRule(mappingExpression, filter.map(m::createExpression).orElse(null), target);
        writeFunction(mappingFunction);
        filter.ifPresent(this::writeFunction);
        return asPropertyBridge(mapping);
    }

    @Override
    public MapContext deletePropertyBridge(PropertyBridge properties) throws MapJenaException {
        Optional<MapFunction.Call> filter = Optional.ofNullable(properties.getFilter());
        MapFunction.Call mapping = MapJenaException.mustNotBeNull(properties.getMapping());
        Statement rule = Iter.findFirst(listRuleStatements().filterKeep(s -> Objects.equals(s.getObject(), properties)))
                .orElseThrow(() -> new MapJenaException.IllegalState("Can't find " + properties));
        deleteRule(rule);
        filter.ifPresent(this::eraseFunction);
        eraseFunction(mapping);
        return this;
    }

    /**
     * Deletes all RDF related to context.
     * This includes all property bridges, all functions, templates, etc.
     * After deleting an instance is appeared to be broken and cannot be used anymore.
     */
    protected void deleteAll() {
        // delete property bridges:
        listPropertyBridges().toSet().forEach(this::deletePropertyBridge);
        Optional<ModelCallImpl> filter = findFilter();
        Optional<ModelCallImpl> mapping = findMapping();
        Set<MapContextImpl> related = getModel().listContextsFor(this).toSet();
        // delete all rest rules (but expected only single - primary):
        listRuleStatements().toSet().forEach(this::deleteRule);
        // delete all declarations:
        Models.deleteAll(this);
        // delete related (chained) contexts
        related.forEach(MapContextImpl::deleteAll);
        // delete unused functions
        filter.ifPresent(this::eraseFunction);
        mapping.ifPresent(this::eraseFunction);
    }

    /**
     * Deletes a rule with all its relevant statement.
     * The rule statement pattern is {@code C spinmap:rule  _:b0}.
     *
     * @param s {@link Statement} describing the rule, not {@code null}
     * @see SPINMAP#rule
     */
    protected void deleteRule(Statement s) {
        Resource rule = s.getResource();
        Resource template = rule.getPropertyResourceValue(RDF.type);
        // delete local mapping and its properties:
        Model base = getModel().getBaseModel();
        if (base.contains(template, RDF.type)) { // then local mapping
            if (base.listResourcesWithProperty(RDF.type, template).toList().size() == 1) { // only one usage
                Models.deleteAll(template);
            }
            // delete unused properties:
            base.listResourcesWithProperty(RDFS.subPropertyOf, SP.arg)
                    .filterDrop(x -> base.contains(null, SPL.predicate, x))
                    .toSet()
                    .forEach(Models::deleteAll);
        }
        Models.deleteAll(rule);
        base.remove(s);
    }

    /**
     * Finds the context's mapping function call.
     * In a well-formed context (see {@link #isValid()}) it is a required component.
     *
     * @return {@code Optional} of {@link ModelCallImpl}
     */
    protected Optional<ModelCallImpl> findMapping() {
        MapModelImpl m = getModel();
        Optional<Resource> expr = Iter.findFirst(m.listObjectsOfProperty(this, SPINMAP.target))
                .map(RDFNode::asResource);
        return expr.map(e -> m.parseExpression(null, e, false));
    }

    /**
     * Finds the context's filter function call.
     * It is an optional component.
     *
     * @return {@code Optional} of {@link ModelCallImpl}
     */
    protected Optional<ModelCallImpl> findFilter() {
        Optional<Resource> expr = findPrimaryRule().filter(r -> r.hasProperty(AVC.filter));
        return expr.map(r -> getModel().parseExpression(r, r.getPropertyResourceValue(AVC.filter), true));
    }

    /**
     * Writes function body to the model graph.
     * All nested functions are also handled.
     *
     * @param function {@link MapFunctionImpl.CallImpl}, possibly {@code null}
     * @throws ClassCastException with current implementation it should never happen
     */
    protected void writeFunction(MapFunction.Call function) throws ClassCastException {
        getModel().writeFunctionBody(function);
    }

    /**
     * Erases a function-call components from RDF, if they are functions and out of use.
     * The operation is opposite to the {@link #writeFunction(MapFunction.Call)} method.
     *
     * @param function {@link MapFunction.Call}, not {@code null}
     * @see #writeFunction(MapFunction.Call)
     */
    protected void eraseFunction(MapFunction.Call function) {
        deleteFunction(getModel().getResource(function.getFunction().name()));
        function.functions(true).forEach(this::eraseFunction);
    }

    /**
     * Safely deletes a function-resources and all its super classes, if no usages are found.
     *
     * @param function {@link Resource}, not {@code null}
     */
    private void deleteFunction(Resource function) {
        Model base = getModel().getBaseModel();
        // delete all super classes:
        base.listObjectsOfProperty(function, RDFS.subClassOf)
                .filterKeep(RDFNode::isResource)
                .forEachRemaining(x -> deleteFunction(x.asResource()));
        if (base.contains(null, RDF.type, function)) {
            // has some usage -> don't delete
            return;
        }
        if (!base.contains(function, RDF.type)) {
            // has no declaration -> then it is builtin function
            return;
        }
        Models.deleteAll(function.inModel(getModel()));
    }

    @Override
    public Stream<PropertyBridge> properties() {
        return Iter.asStream(listPropertyBridges());
    }

    /**
     * Lists all property bridges.
     *
     * @return an {@code ExtendedIterator} over all {@link PropertyBridgeImpl} related to this context
     */
    public ExtendedIterator<PropertyBridgeImpl> listPropertyBridges() {
        return listRuleStatements().mapWith(Statement::getResource) // skip primary rule:
                .filterKeep(r -> !(r.hasProperty(SPINMAP.expression, getTargetClass())
                        && r.hasProperty(SPINMAP.targetPredicate1, RDF.type)))
                .mapWith(this::asPropertyBridge);
    }

    /**
     * Gets the primary (class to class) mapping rule as ordinal resource.
     * For a valid (see {@link MapContext#isValid()}) standalone context
     * the result should be present, otherwise, if the mapping is incomplete, it is empty.
     * Example of such mapping rule:
     * <pre>{@code
     * [ a  spinmap:Mapping-0-1 ;
     *      spinmap:context           map:Context-SourceClass-TargetClass ;
     *      spinmap:expression        :TargetClass ;
     *      spinmap:targetPredicate1  rdf:type
     *  ] .
     * }</pre>
     *
     * @return {@code Optional} around mapping {@link Resource}, which is an anonymous resource:
     */
    public Optional<Resource> findPrimaryRule() {
        return findPrimaryRuleStatement().map(Statement::getResource);
    }

    /**
     * Finds a primary rule declaration statement ({@code C spinmap:rule _:b0}).
     * @return {@code Optional} of {@link Statement}
     */
    public Optional<Statement> findPrimaryRuleStatement() {
        return Iter.findFirst(listRuleStatements()
                .filterKeep(s -> s.getObject().isResource() &&
                        s.getResource().hasProperty(SPINMAP.targetPredicate1, RDF.type) &&
                        s.getResource().hasProperty(SPINMAP.expression, getTargetClass())));
    }

    /**
     * Lists all {@code _:s spinmap:rule _:o} statements belonging to this context.
     *
     * @return {@link ExtendedIterator} of {@link Statement}
     */
    public ExtendedIterator<Statement> listRuleStatements() {
        MapModelImpl m = getModel();
        return Iter.flatMap(m.listOntStatements(null, SPINMAP.context, this),
                s -> m.listOntStatements(getSource(), SPINMAP.rule, s.getSubject()));
    }

    @Override
    public MapContextImpl createRelatedContext(OntCE src2) throws MapJenaException {
        OntCE src1 = getSource();
        Set<OntOPE> res = getModel().getLinkProperties(src1, src2);
        if (res.isEmpty()) {
            throw error(CONTEXT_RELATED_CONTEXT_SOURCES_CLASS_NOT_LINKED).add(Key.CLASS, src2).build();
        }
        if (res.size() != 1) {
            Exceptions.Builder err = error(CONTEXT_RELATED_CONTEXT_AMBIGUOUS_CLASS_LINK).add(Key.CLASS, src2);
            res.forEach(p -> err.addProperty(p.asProperty()));
            throw err.build();
        }
        return createRelatedContext(src2, res.iterator().next());
    }

    @Override
    public MapContextImpl createRelatedContext(OntCE source, OntOPE link) throws MapJenaException {
        MapModelImpl m = getModel();
        MapFunction.Builder builder;
        Property property = link.asProperty();
        if (m.isLinkProperty(link, getSource(), source)) {
            builder = createRelatedContextTargetFunction(SPINMAPL.relatedSubjectContext, property);
        } else if (m.isLinkProperty(link, source, getSource())) {
            builder = createRelatedContextTargetFunction(SPINMAPL.relatedObjectContext, property);
        } else {
            throw error(CONTEXT_RELATED_CONTEXT_SOURCES_CLASS_NOT_LINKED)
                    .addProperty(property)
                    .add(Key.CLASS, source).build();
        }
        return m.createContext(source, getTarget())
                .addClassBridge(null, builder.add(SPINMAPL.context.getURI(), name()).build());
    }

    @Override
    public PropertyBridgeImpl attachContext(MapContext other, OntOPE link) throws MapJenaException {
        if (this.equals(other)) {
            throw error(CONTEXT_ATTACHED_CONTEXT_SELF_CALL).build();
        }
        MapModelImpl m = getModel();
        OntCE target = other.getTarget();
        OntCE source = other.getSource();
        if (!getSource().equals(source)) {
            throw error(CONTEXT_ATTACHED_CONTEXT_DIFFERENT_SOURCES).addContext(other).build();
        }
        Property property = link.asProperty();
        if (!m.isLinkProperty(link, getTarget(), target)) {
            throw error(CONTEXT_ATTACHED_CONTEXT_TARGET_CLASS_NOT_LINKED)
                    .addContext(other).addProperty(property).build();
        }
        // todo: following is a temporary solution, will be replaced with common method #addPropertyBridge ... or not?
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
     * Creates a function call builder for
     * {@code spinmapl:relatedSubjectContext} or {@code spinmapl:relatedObjectContext}.
     *
     * @param func {@link Resource}
     * @param p    {@link Property}
     * @return {@link MapFunction.Builder}
     */
    private MapFunction.Builder createRelatedContextTargetFunction(Resource func, Property p) {
        return getModel().getManager().getFunction(func.getURI()).create()
                .add(SPINMAPL.predicate.getURI(), p.getURI());
    }

    @Override
    public Stream<MapContext> dependentContexts() {
        return Iter.asStream(listDependentContexts());
    }

    /**
     * Lists all contexts that depend on this somehow.
     *
     * @return {@code ExtendedIterator} of contexts
     */
    public ExtendedIterator<MapContextImpl> listDependentContexts() {
        return Iter.distinct(Iter.concat(listChainedContexts(), listRelatedContexts()));
    }

    /**
     * Lists all contexts that depend on this as parameter function calls.
     * <p>
     * A context can be used as parameter in different function-calls,
     * usually with predicate {@link SPINMAPL#context spinmapl:context}.
     * There is one exclusion: {@link SPINMAP#targetResource spinmap:targetResource} -
     * it uses {@link SPINMAP#context spinmap:context} as predicate
     * for an argument with the type {@link SPINMAP#Context spinmap:Context}.
     *
     * @return <b>distinct</b> {@link ExtendedIterator} over {@link MapContextImpl contexts},
     * that participate somewhere in function calls of this context
     */
    public ExtendedIterator<MapContextImpl> listRelatedContexts() {
        MapModelImpl m = getModel();
        ExtendedIterator<Resource> targetExpressions = m.listResourcesWithProperty(RDF.type, SPINMAP.targetResource)
                .filterKeep(s -> s.hasProperty(SPINMAP.context, this));
        ExtendedIterator<Resource> otherExpressions = m.listResourcesWithProperty(SPINMAPL.context, this);

        ExtendedIterator<Resource> expressions = Iter.concat(targetExpressions, otherExpressions)
                .filterKeep(RDFNode::isAnon);

        ExtendedIterator<RDFNode> trees = Iter.flatMap(expressions,
                x -> Iter.concat(Iter.of(x), Models.listAscendingStatements(x).mapWith(Statement::getSubject)));
        Set<Resource> res = Iter.flatMap(trees, this::listContextsForExpression)
                .filterKeep(RDFNode::isURIResource).toSet();
        return Iter.create(res).mapWith(m::asContext).filterKeep(MapContextImpl::isPublic);
    }

    private ExtendedIterator<Resource> listContextsForExpression(RDFNode expression) {
        return Iter.concat(listContextsForRuleExpression(expression), listContextsForTargetExpression(expression));
    }

    private ExtendedIterator<Resource> listContextsForTargetExpression(RDFNode expression) {
        return getModel().listResourcesWithProperty(SPINMAP.target, expression).filterKeep(SpinModels::isContext);
    }

    private ExtendedIterator<Resource> listContextsForRuleExpression(RDFNode expression) {
        MapModelImpl m = getModel();
        return Iter.flatMap(m.listResourcesWithProperty(SPINMAP.expression, expression),
                s -> m.listResourcesOfProperty(s, SPINMAP.context));
    }

    /**
     * Lists all contexts that depend on this context by derived type.
     *
     * @return {@link ExtendedIterator} over {@link MapContextImpl contexts},
     * that directly depend on this in class chain relationship
     */
    public ExtendedIterator<MapContextImpl> listChainedContexts() {
        return getModel().listContextsFor(this).filterKeep(MapContextImpl::isPublic);
    }

    /**
     * Makes a new {@link PropertyBridge} instance.
     *
     * @param resource {@link Resource} to wrap
     * @return {@link PropertyBridgeImpl}
     */
    protected PropertyBridgeImpl asPropertyBridge(Resource resource) {
        return new PropertyBridgeImpl(resource.asNode(), getModel());
    }

    protected void validateContextMapping(MapFunction.Call func,
                                          ContextHelper context) throws MapJenaException {
        if (!testFunction(func, context, CONTEXT_WRONG_MAPPING_FUNCTION).getFunction().isTarget()) {
            throw error(CONTEXT_REQUIRE_TARGET_FUNCTION).addFunction(func).build();
        }
    }

    protected void validateContextFilter(MapFunction.Call func,
                                         ContextHelper context) throws MapJenaException {
        validateFilterFunction(func, context,
                CONTEXT_NOT_BOOLEAN_FILTER_FUNCTION, CONTEXT_WRONG_FILTER_FUNCTION);
    }

    protected void validatePropertyMapping(MapFunction.Call func,
                                           ContextHelper context) throws MapJenaException {
        if (testFunction(func, context, PROPERTY_BRIDGE_WRONG_MAPPING_FUNCTION).getFunction().isTarget()) {
            throw error(PROPERTY_BRIDGE_REQUIRE_NONTARGET_FUNCTION).addFunction(func).build();
        }
    }

    protected void validatePropertyFilter(MapFunction.Call func,
                                          ContextHelper context) throws MapJenaException {
        validateFilterFunction(func, context,
                PROPERTY_BRIDGE_NOT_BOOLEAN_FILTER_FUNCTION, PROPERTY_BRIDGE_WRONG_FILTER_FUNCTION);
    }

    protected void validateFilterFunction(MapFunction.Call func,
                                          ContextHelper context,
                                          Exceptions requireBoolean,
                                          Exceptions wrongFunction) throws MapJenaException {
        MapFunction f = func.getFunction();
        if (!f.isBoolean()) {
            throw error(requireBoolean).addFunction(f).build();
        }
        testFunction(func, context, wrongFunction);
    }

    protected MapFunction.Call testFunction(MapFunction.Call func,
                                            ContextHelper context,
                                            Exceptions code) throws MapJenaException {
        ValidationHelper.testFunction(func, context, error(code).addFunction(func).build());
        return func;
    }

    @Override
    public void validate(MapFunction.Call func) throws MapJenaException {
        ValidationHelper.testFunction(func, ContextHelper.wrap(this),
                error(CONTEXT_FUNCTION_VALIDATION_FAIL).addFunction(func).build());
    }

    protected Exceptions.Builder error(Exceptions code) {
        return code.create().addContext(this);
    }

    @Override
    public String toString() {
        return toString(getModel());
    }

    @Override
    public String toString(PrefixMapping pm) {
        return toString(r -> r.isAnon() ? r.toString() : ToString.getShortForm(pm, r.getURI()));
    }

    private String toString(Function<Resource, String> toString) {
        return String.format("Context{%s => %s}", toString.apply(getSourceClass()), toString.apply(getTargetClass()));
    }

}

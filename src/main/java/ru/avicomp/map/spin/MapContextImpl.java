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
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.shared.JenaException;
import org.apache.jena.shared.PrefixMapping;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
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
import ru.avicomp.ontapi.jena.model.OntStatement;
import ru.avicomp.ontapi.jena.utils.Iter;
import ru.avicomp.ontapi.jena.utils.Models;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
     * Returns a target class resource, which may not be {@link OntCE}
     * in the special case of {@code owl:NamedIndividual} mapping.
     *
     * @return {@link Resource} for predicate {@code spinmap:targetClass}
     * @throws JenaException illegal state - no resource found
     */
    public Resource target() throws JenaException {
        return getRequiredProperty(SPINMAP.targetClass).getObject().asResource();
    }

    @Override
    public MapContextImpl addClassBridge(MapFunction.Call filterFunction,
                                         MapFunction.Call mappingFunction) throws MapJenaException {
        ContextHelper context = ContextHelper.create(this);
        validateContextMapping(mappingFunction, context);
        validateContextFilter(filterFunction, context);
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
            // add primary rule (Mapping-0-1) to create individual with target rdf:type
            context.populate(target(), filterExpression, RDF.type);
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
        writeFunctions(mappingFunction, filterFunction);
        return this;
    }

    @Override
    public PropertyBridgeImpl addPropertyBridge(MapFunction.Call filterFunction,
                                                MapFunction.Call mappingFunction,
                                                Property target) throws MapJenaException {
        // the target property must "belong" to the target class:
        ContextHelper context = ContextHelper.create(this);
        if (!context.isTargetProperty(target)) {
            throw error(PROPERTY_BRIDGE_WRONG_TARGET_PROPERTY).addProperty(target).build();
        }
        validatePropertyMapping(mappingFunction, context);
        validatePropertyFilter(filterFunction, context);
        MapModelImpl m = getModel();
        RDFNode filterExpression = filterFunction != null ? m.createExpression(filterFunction) : null;
        RDFNode mappingExpression = m.createExpression(mappingFunction);
        Resource mapping = context.populate(mappingExpression, filterExpression, target);
        writeFunctions(mappingFunction, filterFunction);
        return asPropertyBridge(mapping);
    }

    @Override
    public ModelCallImpl getMapping() {
        MapModelImpl m = getModel();
        Optional<Resource> expr = Iter.findFirst(m.listObjectsOfProperty(this, SPINMAP.target))
                .map(RDFNode::asResource);
        return expr.map(e -> m.parseExpression(null, e, false)).orElse(null);
    }

    @Override
    public ModelCallImpl getFilter() {
        Optional<Resource> expr = primaryRule().filter(r -> r.hasProperty(AVC.filter));
        return expr.map(r -> getModel().parseExpression(r, r.getPropertyResourceValue(AVC.filter), true)).orElse(null);
    }

    /**
     * Writes function bodies to the model.
     *
     * @param mappingFunction {@link MapFunctionImpl.CallImpl}, not {@code null}
     * @param filterFunction  {@link MapFunctionImpl.CallImpl}, can be {@code null}
     * @throws ClassCastException with current implementation it should never happen
     */
    protected void writeFunctions(MapFunction.Call mappingFunction,
                                  MapFunction.Call filterFunction) throws ClassCastException {
        MapModelImpl m = getModel();
        m.writeFunctionBody(mappingFunction);
        if (filterFunction != null)
            m.writeFunctionBody(filterFunction);
    }

    @Override
    public Stream<PropertyBridge> properties() {
        return listPropertyBridges().map(PropertyBridge.class::cast);
    }

    public Stream<PropertyBridgeImpl> listPropertyBridges() {
        return listRules() // skip primary rule:
                .filter(r -> !(r.hasProperty(SPINMAP.expression, target())
                        && r.hasProperty(SPINMAP.targetPredicate1, RDF.type)))
                .map(this::asPropertyBridge);
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

    @Override
    public MapContext deletePropertyBridge(PropertyBridge properties) throws MapJenaException {
        Statement res = listRuleStatements()
                .filter(s -> Objects.equals(s.getObject(), properties))
                .findFirst().orElseThrow(() -> new MapJenaException("Can't find " + properties));
        Models.deleteAll(res.getObject().asResource());
        getModel().remove(res);
        getModel().clear();
        return this;
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
        return getModel().getManager()
                .getFunction(func.getURI())
                .create()
                .add(SPINMAPL.predicate.getURI(), p.getURI());
    }

    @Override
    public Stream<MapContext> dependentContexts() {
        MapModelImpl m = getModel();
        return Stream.concat(m.listChainedContexts(this),
                m.listRelatedContexts(this)).distinct().map(MapContext.class::cast);
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
        if (func == null) {
            return;
        }
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
        return String.format("Context{%s => %s}", toString.apply(source()), toString.apply(target()));
    }

}

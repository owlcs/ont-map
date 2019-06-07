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

import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.AnonId;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.JenaException;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.util.iterator.NullIterator;
import org.apache.jena.vocabulary.RDFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.MapContext;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapJenaException;
import ru.avicomp.map.MapModel;
import ru.avicomp.map.utils.ClassPropertyMapListener;
import ru.avicomp.map.utils.ModelUtils;
import ru.avicomp.ontapi.jena.OntJenaException;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.impl.OntGraphModelImpl;
import ru.avicomp.ontapi.jena.impl.conf.OntPersonality;
import ru.avicomp.ontapi.jena.model.*;
import ru.avicomp.ontapi.jena.utils.Graphs;
import ru.avicomp.ontapi.jena.utils.Iter;
import ru.avicomp.ontapi.jena.utils.Models;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ru.avicomp.map.spin.Exceptions.*;

/**
 * Created by @szuev on 10.04.2018.
 */
@SuppressWarnings("WeakerAccess")
public class MapModelImpl extends OntGraphModelImpl implements MapModel {
    private static final Logger LOGGER = LoggerFactory.getLogger(MapManagerImpl.class);

    private static final String CONTEXT_NAME_TEMPLATE = "Context-%s-%s";

    private final MapManagerImpl manager;

    public MapModelImpl(UnionGraph base, OntPersonality personality, MapManagerImpl manager) {
        super(base, personality);
        this.manager = manager;
    }

    /**
     * Recursively lists all parents for the given {@link RDFNode RDF Node}.
     * TODO: move to ONT-API ({@link Models})?
     *
     * @param inModel, not {@code null} must be attached to a model
     * @return {@link ExtendedIterator} of {@link RDFNode}s
     * @see Models#listSubjects(RDFNode)
     */
    public static ExtendedIterator<RDFNode> listParents(RDFNode inModel) {
        ExtendedIterator<? extends RDFNode> direct = inModel.getModel().listResourcesWithProperty(null, inModel);
        ExtendedIterator<RDFNode> res = Iter.flatMap(direct, s -> s.isAnon() ? listParents(s) : NullIterator.instance());
        return Iter.concat(Iter.of(inModel), res);
    }

    @Override
    public String name() {
        return ModelUtils.getResourceID(getID());
    }

    @Override
    public Stream<OntGraphModel> ontologies() {
        Stream<OntGraphModel> res = hasOntEntities() ? Stream.of(this) : Stream.empty();
        Stream<OntGraphModel> imports = super.imports(SpinModelConfig.ONT_PERSONALITY)
                .filter(m -> !SpinModels.isTopSpinURI(m.getID().getURI()));
        return Stream.concat(res, imports);
    }

    /**
     * Answers {@code true} if this mapping model has local defined owl-entities declarations.
     * TODO: move to ONT-API ({@link OntGraphModelImpl}?)
     *
     * @return boolean
     */
    public boolean hasOntEntities() {
        try (Stream<Resource> subjects = Iter.asStream(getBaseModel().listSubjectsWithProperty(RDF.type))) {
            return subjects.filter(RDFNode::isURIResource).anyMatch(r -> r.canAs(OntEntity.class));
        }
    }

    /**
     * Returns a first found typed object from a statement with specified subject and predicate or throws an exception.
     * TODO: it is a generic method. Move to ONT-API ({@link ru.avicomp.ontapi.jena.impl.UnionModel}?)
     *
     * @param s    {@link Resource} to be used as subject in SPO-search pattern, not {@code null}
     * @param p    {@link Property} to be used as predicate in SPO-search pattern, not {@code null}
     * @param type {@link Class}-type of the returned object
     * @param <X>  any subtype of {@link RDFNode}
     * @return {@link X}, not {@code null}
     * @throws JenaException in case the object cannot be found or it has incompatible type
     */
    public <X extends RDFNode> X getRequiredObject(Resource s, Property p, Class<X> type) throws JenaException {
        return Iter.findFirst(getBaseGraph().find(s.asNode(), p.asNode(), Node.ANY)
                .mapWith(x -> getNodeAs(x.getObject(), type)))
                .orElseThrow(() -> new MapJenaException.IllegalState(String.format("Can't find %s from pattern [%s, %s, ANY]", type, s, p)));
    }

    /**
     * Returns a first found typed object from a statement with specified subject and predicate.
     * TODO: it is a generic method. Move to ONT-API ({@link ru.avicomp.ontapi.jena.impl.UnionModel}?)
     *
     * @param s    {@link Resource} to be used as subject in SPO-search pattern, not {@code null}
     * @param p    {@link Property} to be used as predicate in SPO-search pattern, not {@code null}
     * @param type {@link Class}-type of the returned object
     * @param <X>  any subtype of {@link RDFNode}
     * @return {@link X} or {@code null}
     */
    public <X extends RDFNode> X getObject(Resource s, Property p, Class<X> type) {
        return Iter.findFirst(getBaseGraph().find(s.asNode(), p.asNode(), Node.ANY)
                .mapWith(x -> findNodeAs(x.getObject(), type)))
                .orElse(null);
    }

    public ExtendedIterator<Resource> listResourcesOfProperty(Resource s, Property p) {
        return listObjectsOfProperty(s, p).filterKeep(RDFNode::isResource).mapWith(RDFNode::asResource);
    }

    @Override
    public Stream<MapContext> contexts() {
        return Iter.asStream(listContexts());
    }

    public ExtendedIterator<OntCE> listContextClasses() {
        return Iter.distinct(Iter.flatMap(listContexts(), MapContextImpl::listClasses));
    }

    public ExtendedIterator<MapContextImpl> listContexts() {
        return listContextResources().mapWith(this::asContext).filterKeep(MapContextImpl::isPublic);
    }

    /**
     * Lists all chained contexts/
     *
     * @param context {@link MapContextImpl}, not {@code null}
     * @return {@link ExtendedIterator} over all {@link MapContextImpl} that has a source class that matches
     * the target class of the specified context
     */
    protected ExtendedIterator<MapContextImpl> listContextsFor(MapContextImpl context) {
        return listContextResources(context.getTargetClass(), null)
                .filterDrop(context::equals)
                .mapWith(this::asContext);
    }

    public ExtendedIterator<Resource> listContextResources(Resource source, Resource target) {
        return listContextResources().filterKeep(s -> s.hasProperty(SPINMAP.targetClass, target)
                && s.hasProperty(SPINMAP.sourceClass, source));
    }

    public ExtendedIterator<Resource> listContextResources() {
        return listResourcesWithProperty(RDF.type, SPINMAP.Context);
    }

    @Override
    public MapContextImpl createContext(OntCE source, OntCE target) {
        return Iter.findFirst(listContexts()
                .filterKeep(s -> Objects.equals(s.getSource(), source) && Objects.equals(s.getTarget(), target))
                .mapWith(MapContextImpl.class::cast))
                .orElseGet(() -> asContext(makeContext(source, target)));
    }

    /**
     * Wraps the given resource as {@link MapContextImpl}.
     * Auxiliary method.
     *
     * @param context {@link Resource}
     * @return {@link MapContextImpl}
     */
    public MapContextImpl asContext(Resource context) {
        return new MapContextImpl(context.asNode(), this);
    }

    @Override
    public MapModelImpl deleteContext(MapContext context) {
        MapContextImpl c = ((MapContextImpl) MapJenaException.notNull(context, "Null context"));
        List<MapContext> related = c.dependentContexts().collect(Collectors.toList());
        if (!related.isEmpty()) {
            Exceptions.Builder error = error(MAPPING_CONTEXT_CANNOT_BE_DELETED_DUE_TO_DEPENDENCIES).addContext(context);
            related.forEach(error::addContext);
            throw error.build();
        }
        c.deleteAll();
        // remove unused imports (both owl:import declarations and underling graphs)
        Set<OntID> used = listContextClasses().mapWith(this::getOntologyID).toSet();
        Set<OntGraphModel> unused = ontologies()
                .filter(o -> !used.contains(o.getID()))
                .filter(o -> !Objects.equals(o, this))
                .collect(Collectors.toSet());
        unused.stream()
                .peek(m -> {
                    if (!LOGGER.isDebugEnabled()) return;
                    LOGGER.debug("Remove {}", m);
                })
                .forEach(MapModelImpl.this::removeImport);
        return this;
    }

    @Override
    public MapModelImpl removeImport(OntGraphModel m) {
        super.removeImport(m);
        // detach ClassPropertiesMap Listener to let GC clean any cached data, it is just in case
        UnionGraph.OntEventManager events = ((UnionGraph) m.getGraph()).getEventManager();
        events.listeners()
                .filter(l -> ClassPropertyMapListener.class.equals(l.getClass()))
                .collect(Collectors.toSet())
                .forEach(events::unregister);
        return this;
    }

    private OntID getOntologyID(OntCE ce) {
        return findModelByClass(ce).map(OntGraphModel::getID)
                .orElseThrow(() -> new OntJenaException.IllegalState("Can't find ontology for " + ce));
    }

    protected Optional<OntGraphModel> findModelByClass(Resource ce) {
        return ontologies().filter(m -> m.ontObjects(OntCE.class).anyMatch(c -> Objects.equals(c, ce))).findFirst();
    }

    /**
     * Deletes all unused anymore things,
     * that could appeared in the base graph while constructing or removing contexts and property bridges.
     * This includes construct templates, custom functions, {@code sp:Variable}s and {@code sp:arg} properties.
     *
     * @return this model
     * @see #clearUnused()
     */
    @Deprecated
    protected MapModelImpl clear() {
        // clean unused functions, mapping templates, properties, variables, etc
        clearUnused();
        // re-run since RDF is disordered and some data can be omitted in the previous step due to dependencies
        clearUnused();
        return this;
    }

    protected void clearUnused() {
        // delete expressions:
        Iter.flatMap(Iter.of(SPIN.ConstructTemplate, SPIN.Function, SPINMAP.TargetFunction),
                t -> listOntStatements(null, RDF.type, t)).mapWith(OntStatement::getSubject)
                .filterKeep(s -> s.isLocal() && !getBaseModel().contains(null, RDF.type, s))
                .toSet().forEach(Models::deleteAll);
        // delete properties and variables:
        Iter.concat(listOntStatements(null, RDFS.subPropertyOf, SP.arg).mapWith(OntStatement::getSubject)
                        .filterKeep(s -> s.isLocal() && !getBaseModel().contains(null, s.as(Property.class))),
                listOntStatements(null, RDF.type, SP.Variable).mapWith(OntStatement::getSubject)
                        .filterKeep(s -> s.isLocal() && !getBaseModel().contains(null, null, s)))
                .toSet().forEach(Models::deleteAll);
    }

    /**
     * Creates a {@link SPINMAP#Context spinmap:Context} which binds specified class-expressions.
     * It also adds imports for ontologies where arguments are declared in.
     * In case {@link MapConfigImpl#generateNamedIndividuals()}{@code == true}
     * an additional hidden contexts to generate {@code owl:NamedIndividuals} is created.
     *
     * @param source {@link OntCE}
     * @param target {@link OntCE}
     * @return {@link Resource}
     * @throws MapJenaException something goes wrong
     */
    public Resource makeContext(OntCE source, OntCE target) throws MapJenaException {
        // ensue all related models are imported:
        Stream.of(MapJenaException.notNull(source, "Null source CE"),
                MapJenaException.notNull(target, "Null target CE"))
                .map(OntObject::getModel)
                .filter(m -> !Graphs.isSameBase(m.getBaseGraph(), getBaseGraph()))
                .filter(m -> MapModelImpl.this.imports().noneMatch(i -> Objects.equals(i.getID(), m.getID())))
                .peek(m -> {
                    if (!LOGGER.isDebugEnabled()) return;
                    LOGGER.debug("Import {}", m);
                })
                .forEach(MapModelImpl.this::addImport);
        return makeContext(source.asResource(), target.asResource());
    }

    /**
     * Creates an unique {@code spinmap:Context} resource for the specified source and target resources.
     * <pre>{@code
     * _:x rdf:type spinmap:Context ;
     *   spinmap:sourceClass <src> ;
     *   spinmap:targetClass <dst> ;
     * }</pre>
     *
     * @param source {@link Resource}
     * @param target {@link Resource}
     * @return {@link Resource}
     */
    protected Resource makeContext(Resource source, Resource target) {
        String name = String.format(CONTEXT_NAME_TEMPLATE,
                ModelUtils.getResourceName(source), ModelUtils.getResourceName(target));
        Resource res;
        String base = getID().getURI();
        if (base == null || base.contains("#")) { // anonymous mapping or incorrect uri
            base = "urn:mapping:" + ModelUtils.getResourceName(getID());
        }
        do {
            if (base == null) {
                base = "urn:context:" + UUID.randomUUID();
            }
            // right now anonymous contexts are not allowed
            // since they can be used as function call argument parameter
            res = createResource(base + "#" + name);
            base = null;
        } while (containsResource(res));
        return res.addProperty(RDF.type, SPINMAP.Context)
                .addProperty(SPINMAP.sourceClass, source)
                .addProperty(SPINMAP.targetClass, target);
    }

    @Override
    public MapModelImpl bindContexts(MapContext left, MapContext right) {
        OntCE leftClass = left.getTarget();
        OntCE rightClass = right.getTarget();
        Set<OntOPE> res = getLinkProperties(leftClass, rightClass);
        if (res.isEmpty()) {
            throw error(MAPPING_ATTACHED_CONTEXT_TARGET_CLASS_NOT_LINKED)
                    .addContext(left).addContext(right).build();
        }
        if (res.size() != 1) {
            Exceptions.Builder err = error(MAPPING_ATTACHED_CONTEXT_AMBIGUOUS_CLASS_LINK)
                    .addContext(left).addContext(right);
            res.forEach(p -> err.addProperty(p.asProperty()));
            throw err.build();
        }
        OntOPE p = res.iterator().next();
        if (isLinkProperty(p, leftClass, rightClass)) {
            left.attachContext(right, p);
        } else {
            right.attachContext(left, p);
        }
        return this;
    }

    @Override
    public MapModelImpl asGraphModel() {
        return this;
    }

    @Override
    public MapManagerImpl getManager() {
        return manager;
    }

    /**
     * Lists ontological properties for the given OWL class.
     *
     * @param ce {@link OntCE}
     * @return Stream of {@link Property properties}
     */
    public Stream<Property> properties(OntCE ce) {
        return getManager().getClassProperties(this).properties(ce);
    }

    /**
     * Returns {@code spinmap:sourcePredicate$i} mapping template argument property.
     *
     * @param i int
     * @return {@link Property}
     */
    public Property getSourcePredicate(int i) {
        return createArgProperty(SPINMAP.sourcePredicate(i).getURI());
    }

    /**
     * Creates or finds a property which has {@code rdfs:subPropertyOf == sp:arg}.
     *
     * @param uri String
     * @return {@link Property}
     */
    public Property createArgProperty(String uri) {
        return SpinModels.getSpinProperty(this, uri);
    }

    /**
     * Gets rdf-datatype from a model,
     * which can be builtin (e.g {@code xsd:int}) or custom if corresponding declaration is present in the model.
     * TODO: move to ONT-API?
     *
     * @param uri String, not null.
     * @return Optional around {@link RDFDatatype}
     */
    public Optional<RDFDatatype> datatype(String uri) {
        return Optional.ofNullable(getOntEntity(OntDT.class, uri)).map(OntDT::toRDFDatatype);
    }

    /**
     * Converts a string to RDFNode.
     * String form can be obtained using {@link RDFNode#toString()} method.
     * TODO: move to ONT-API?
     *
     * @param value String, not {@code null}
     * @return {@link RDFNode} literal or resource (can be anonymous), not {@code null}
     * @see MapFunctionImpl#getAsString(RDFNode)
     */
    public RDFNode toNode(String value) {
        if (Objects.requireNonNull(value, "Null value").contains("^^")) { // must be typed literal
            String t = expandPrefix(value.replaceFirst(".+\\^\\^(.+)", "$1"));
            Optional<RDFDatatype> type = datatype(t);
            if (type.isPresent()) {
                String lex = value.replaceFirst("(.+)\\^\\^.+", "$1");
                return createTypedLiteral(lex, type.get());
            }
        }
        if (value.contains("@")) { // lang literal
            String lex = value.replaceFirst("@.+", "");
            String lang = value.replaceFirst(".+@", "");
            return createLiteral(lex, lang);
        }
        Resource res = createResource(value);
        if (containsResource(res)) { // uri resource
            return res;
        }
        // ONT-API stupidly overrides toString for OntObject:
        AnonId id = new AnonId(value.replaceFirst("^\\[[^]]+](.+)", "$1"));
        res = createResource(id);
        if (containsResource(res)) { // anonymous resource
            return res;
        }
        // plain literal
        return createLiteral(value);
    }

    /**
     * Answers {@code true} if the given resource is belonging to the mapping.
     * The given resource may be property, class-expression, datatype (and datarange?), another context -
     * (todo) currently, not sure what else.
     * In general it must has content in bounds of the mapping.
     *
     * @param res {@link Resource} to test, not {@code null}
     * @return boolean
     */
    public boolean isEntity(Resource res) {
        if (SpinModels.isVariable(res)) return false;
        // todo: this checking is a temporary solution and not correct
        return Iter.findFirst(res.listProperties()).isPresent();
    }

    /**
     * Returns a Set of all linked properties.
     *
     * @param left  {@link OntCE}
     * @param right {@link OntCE}
     * @return Set of {@link OntOPE}s
     */
    public Set<OntOPE> getLinkProperties(OntCE left, OntCE right) {
        return ontObjects(OntOPE.class)
                .filter(p -> isLinkProperty(p, left, right) || isLinkProperty(p, right, left))
                .collect(Collectors.toSet());
    }

    /**
     * Answers {@code true}
     * if the specified property links classes together through domain/range or restriction relationships.
     *
     * @param property {@link OntOPE} property to test, not {@code null}
     * @param domain   {@link OntCE} "domain" candidate, not {@code null}
     * @param range    {@link OntCE} "range" candidate, not {@code null}
     * @return {@code true} if it is link property
     * @see ModelUtils#listProperties(OntCE)
     * @see ModelUtils#ranges(OntOPE)
     */
    public boolean isLinkProperty(OntOPE property, OntCE domain, OntCE range) {
        Property p = property.asProperty();
        if (properties(domain).noneMatch(p::equals)) return false;
        // range
        if (ModelUtils.ranges(property).anyMatch(r -> Objects.equals(r, range))) return true;
        // object some/all values from or cardinality restriction
        return statements(null, OWL.onProperty, property)
                .map(OntStatement::getSubject)
                .filter(s -> s.canAs(OntCE.ComponentRestrictionCE.class))
                .map(s -> s.as(OntCE.ComponentRestrictionCE.class).getValue())
                .filter(RDFNode::isResource)
                .anyMatch(range::equals);
    }

    /**
     * Creates an expression resource.
     * Example of such expression:
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
     * @return an anonymous {@link Resource}
     * @see #parseExpression(Resource, RDFNode, boolean)
     */
    protected RDFNode createExpression(MapFunction.Call call) {
        MapFunction func = call.getFunction();
        Resource res = createResource();
        call.asMap().forEach((arg, value) -> {
            RDFNode param = null;
            if (value instanceof MapFunction.Call) {
                if (Objects.equals(value, call)) throw new MapJenaException.IllegalArgument("Self call");
                param = createExpression((MapFunction.Call) value);
            }
            if (value instanceof String) {
                param = toNode((String) value);
            }
            if (param == null)
                throw new MapJenaException.IllegalArgument("Wrong value for " + arg.name() + ": " + value);
            Property predicate = createArgProperty(arg.name());
            res.addProperty(predicate, param);
        });
        return res.addProperty(RDF.type, createResource(func.name()));
    }

    /**
     * Creates a {@link MapFunction.Call function call} from the given expression resource.
     *
     * @param rule     {@link Resource} mapping rule
     * @param expr     {@link RDFNode} expression
     * @param isFilter boolean
     * @return {@link ModelCallImpl}
     * @see #createExpression(MapFunction.Call)
     */
    protected ModelCallImpl parseExpression(Resource rule, RDFNode expr, boolean isFilter) {
        MapManagerImpl man = getManager();
        MapFunctionImpl f;
        Map<MapFunctionImpl.ArgImpl, Object> args = new HashMap<>();
        if (expr.isLiteral() || expr.isURIResource()) {
            f = man.getFunction(SPINMAP.equals.getURI());
            String v = (expr.isLiteral() ? expr : ContextHelper.findProperty(rule, expr.asResource(), isFilter))
                    .asNode().toString();
            args.put(f.getArg(SP.arg1.getURI()), v);
            return new ModelCallImpl(this, f, args);
        }
        Resource res = expr.asResource();
        String name = res.getRequiredProperty(RDF.type).getObject().asResource().getURI();
        f = man.getFunction(name);
        res.listProperties()
                .filterDrop(s -> RDF.type.equals(s.getPredicate()))
                .forEachRemaining(s -> {
                    String uri = s.getPredicate().getURI();
                    MapFunctionImpl.ArgImpl a;
                    if (f.isVararg() && !f.hasArg(uri)) {
                        List<MapFunctionImpl.ArgImpl> varargs = f.argImpls()
                                .filter(MapFunctionImpl.ArgImpl::isVararg)
                                .collect(Collectors.toList());
                        if (varargs.size() != 1)
                            throw new MapJenaException.IllegalState("Can't find vararg argument for " + f.name());
                        a = f.newArg(varargs.get(0).arg, uri);
                    } else {
                        a = f.getArg(uri);
                    }
                    Object v = null;
                    RDFNode n = s.getObject();
                    if (n.isResource()) {
                        Resource r = n.asResource();
                        if (r.isAnon()) {
                            v = parseExpression(rule, r, isFilter);
                        } else if (SpinModels.isSpinArgVariable(r)) {
                            v = ContextHelper.findProperty(rule, r, isFilter).asNode().toString();
                        }
                    }
                    if (v == null) {
                        v = n.asNode().toString();
                    }
                    args.put(a, v);
                });
        return new ModelCallImpl(this, f, args);
    }

    /**
     * Validates a function-call against this model.
     *
     * @param func {@link MapFunction.Call} an expression.
     * @throws MapJenaException if something is wrong with function, e.g. wrong argument types.
     */
    @Override
    public void validate(MapFunction.Call func) throws MapJenaException {
        ValidationHelper.testFunction(func, this, error(MAPPING_FUNCTION_VALIDATION_FAIL).addFunction(func).build());
    }

    protected Exceptions.Builder error(Exceptions code) {
        return code.create().add(Key.MAPPING, toString());
    }

    /**
     * Writes a custom function "as it is" into the mapping graph.
     *
     * @param call {@link MapFunction.Call}, not {@code null}
     */
    protected void writeFunctionBody(MapFunction.Call call) {
        MapFunctionImpl function = (MapFunctionImpl) call.getFunction();
        if (function.isCustom()) {
            function.write(MapModelImpl.this);
            function.runtimeBody().ifPresent(x -> x.apply(MapModelImpl.this, call));
            // print sub-class-of and dependencies:
            Stream.concat(function.superClasses(), function.dependencyResources())
                    .map(Resource::getURI)
                    .distinct()
                    .map(manager::getFunction)
                    .filter(MapFunctionImpl::isCustom)
                    .forEach(x -> x.write(MapModelImpl.this));
        }
        // recursively print all nested functions:
        call.functions().forEach(this::writeFunctionBody);
    }

    @Override
    public String toString() {
        return String.format("MapModel{%s}", Graphs.getName(getBaseGraph()));
    }

}

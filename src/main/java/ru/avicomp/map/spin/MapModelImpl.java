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
import org.apache.jena.rdf.model.*;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.MapContext;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapJenaException;
import ru.avicomp.map.MapModel;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;
import ru.avicomp.map.utils.ClassPropertyMapListener;
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

    private static final String CONTEXT_TEMPLATE = "Context-%s-%s";
    private final MapManagerImpl manager;

    public MapModelImpl(UnionGraph base, OntPersonality personality, MapManagerImpl manager) {
        super(base, personality);
        this.manager = manager;
    }

    public static String getLocalName(Resource resource) {
        return resource.isURIResource() ? resource.getLocalName() : resource.getId().getLabelString();
    }

    @Override
    public Stream<OntGraphModel> ontologies() {
        Stream<OntGraphModel> res = hasOntEntities() ? Stream.of(this) : Stream.empty();
        Stream<OntGraphModel> imports = super.imports(SpinModelConfig.ONT_PERSONALITY)
                .filter(m -> !SystemModels.graphs().keySet().contains(m.getID().getURI()));
        return Stream.concat(res, imports);
    }

    /**
     * Answers iff this mapping model has local defined owl-entities declarations.
     * TODO: move to ONT-API?
     *
     * @return boolean
     */
    public boolean hasOntEntities() {
        try (Stream<Resource> subjects = Iter.asStream(getBaseModel().listSubjectsWithProperty(RDF.type))) {
            return subjects.filter(RDFNode::isURIResource).anyMatch(r -> r.canAs(OntEntity.class));
        }
    }

    @Override
    public Stream<MapContext> contexts() {
        return listContexts().map(MapContext.class::cast);
    }

    public Stream<OntCE> classes() {
        return listContexts().flatMap(MapContextImpl::classes).distinct();
    }

    public Stream<MapContextImpl> listContexts() {
        return asContextStream(statements(null, RDF.type, SPINMAP.Context).map(OntStatement::getSubject));
    }

    /**
     * Makes a stream of {@link MapContextImpl} from a stream of {@link Resource}s.
     * Auxiliary method.
     *
     * @param stream Stream
     * @return Stream
     */
    private Stream<MapContextImpl> asContextStream(Stream<Resource> stream) {
        return stream.map(r -> r.as(OntObject.class))
                .filter(s -> s.objects(SPINMAP.targetClass, OntClass.class).findAny().isPresent())
                .filter(s -> s.objects(SPINMAP.sourceClass, OntClass.class).findAny().isPresent())
                .map(this::asContext);
    }

    /**
     * Finds a context by source and target resources.
     *
     * @param source {@link Resource}
     * @param target {@link Resource}
     * @return Optional around context
     */
    public Optional<MapContextImpl> findContext(Resource source, Resource target) {
        return statements(null, RDF.type, SPINMAP.Context)
                .map(OntStatement::getSubject)
                .filter(s -> s.hasProperty(SPINMAP.targetClass, target))
                .filter(s -> s.hasProperty(SPINMAP.sourceClass, source))
                .map(this::asContext)
                .findFirst();
    }

    @Override
    public MapContextImpl createContext(OntCE source, OntCE target) {
        return contexts()
                .filter(s -> Objects.equals(s.getSource(), source))
                .filter(s -> Objects.equals(s.getTarget(), target))
                .map(MapContextImpl.class::cast)
                .findFirst()
                .orElseGet(() -> asContext(makeContext(source, target)));
    }

    /**
     * Wraps a resource as {@link MapContextImpl}.
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
        List<MapContext> related = context.dependentContexts().collect(Collectors.toList());
        if (!related.isEmpty()) {
            Exceptions.Builder error = exception(MAPPING_CONTEXT_CANNOT_BE_DELETED_DUE_TO_DEPENDENCIES)
                    .addContext(context);
            related.forEach(error::addContext);
            throw error.build();
        }
        MapContextImpl c = ((MapContextImpl) MapJenaException.notNull(context, "Null context"));
        if (getManager().getConfig().generateNamedIndividuals()) {
            findContext(c.getTarget(), OWL.NamedIndividual).ifPresent(this::deleteContext);
        }
        deleteContext(c);
        clear();
        // remove unused imports (both owl:import declarations and underling graphs)
        Set<OntID> used = classes().map(this::getOntologyID).collect(Collectors.toSet());
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
        return findModelByClass(ce).map(OntGraphModel::getID).orElseThrow(() -> new IllegalStateException("Can't find ontology for " + ce));
    }

    protected Optional<OntGraphModel> findModelByClass(Resource ce) {
        return ontologies().filter(m -> m.ontObjects(OntCE.class).anyMatch(c -> Objects.equals(c, ce))).findFirst();
    }

    /**
     * @see #clearUnused()
     */
    protected void clear() {
        // clean unused functions, mapping templates, properties, variables, etc
        clearUnused();
        // re-run since RDF is disordered and some data can be omitted in the previous step due to dependencies
        clearUnused();
    }

    /**
     * Deletes unused anymore things, which is appeared in the base graph.
     * I.e. construct templates, custom functions, {@code sp:Variable}s and {@code sp:arg}s.
     */
    protected void clearUnused() {
        // delete expressions:
        Set<Resource> found = Stream.of(SPIN.ConstructTemplate, SPIN.Function, SPINMAP.TargetFunction)
                .flatMap(type -> statements(null, RDF.type, type)
                        .map(OntStatement::getSubject)
                        // defined locally
                        .filter(OntObject::isLocal)
                        // no usage
                        .filter(s -> !getBaseModel().contains(null, RDF.type, s)))
                .collect(Collectors.toSet());
        found.forEach(Models::deleteAll);
        // delete properties and variables:
        found = Stream.concat(statements(null, RDFS.subPropertyOf, SP.arg)
                        .map(OntStatement::getSubject)
                        .filter(OntObject::isLocal)
                        .map(s -> s.as(Property.class))
                        .filter(s -> !getBaseModel().contains(null, s)),
                statements(null, RDF.type, SP.Variable)
                        .map(OntStatement::getSubject)
                        .filter(OntObject::isLocal)
                        .filter(s -> !getBaseModel().contains(null, null, s)))
                .collect(Collectors.toSet());
        found.forEach(Models::deleteAll);
    }

    public void deleteContext(MapContextImpl context) {
        // delete rules:
        Set<Statement> rules = context.listRuleStatements().collect(Collectors.toSet());
        rules.forEach(s -> {
            Models.deleteAll(s.getObject().asResource());
            remove(s);
        });
        // delete declaration:
        Models.deleteAll(context);
    }

    /**
     * Lists all contexts that depend on the specified by function call.
     * A context can be used as parameter in different function-calls, usually with predicate {@code spinmapl:context}.
     * There is one exclusion: {@code spinmap:targetResource},
     * it uses {@code spinmap:context} as predicate for argument with type {@code spinmap:Context}.
     *
     * @param context {@link MapContextImpl} to check
     * @return distinct stream of other contexts
     */
    public Stream<MapContextImpl> listRelatedContexts(MapContextImpl context) {
        Stream<Resource> targetResourceExpressions = statements(null, RDF.type, SPINMAP.targetResource)
                .map(Statement::getSubject)
                .filter(s -> s.hasProperty(SPINMAP.context, context));
        Stream<Resource> otherExpressions = statements(null, SPINMAPL.context, context).map(Statement::getSubject);
        Stream<Resource> res = Stream.concat(targetResourceExpressions, otherExpressions)
                .filter(RDFNode::isAnon)
                .flatMap(e -> Stream.concat(Stream.of(e), Models.listSubjects(e)))
                .flatMap(e -> Stream.concat(contextsByRuleExpression(e), contextsByTargetExpression(e)))
                .filter(RDFNode::isURIResource)
                .distinct();
        return asContextStream(res);
    }

    /**
     * Lists all contexts that depend on the specified by derived type.
     *
     * @param context {@link MapContextImpl} to check
     * @return distinct stream of other contexts
     */
    public Stream<MapContextImpl> listChainedContexts(MapContextImpl context) {
        Resource clazz = context.target();
        return listContexts().filter(c -> !c.equals(context)).filter(c -> Objects.equals(c.source(), clazz));
    }

    public Stream<Resource> contextsByTargetExpression(RDFNode expression) {
        return statements(null, SPINMAP.target, expression).map(Statement::getSubject)
                .filter(s -> s.hasProperty(RDF.type, SPINMAP.Context));
    }

    public Stream<Resource> contextsByRuleExpression(RDFNode expression) {
        return statements(null, SPINMAP.expression, expression).map(OntStatement::getSubject)
                .flatMap(s -> s.objects(SPINMAP.context, Resource.class));
    }

    /**
     * Creates a {@code spinmap:Context} which binds specified class-expressions.
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
        Resource res = makeContext(source.asResource(), target.asResource());
        if (getManager().getConfig().generateNamedIndividuals()
                && !findContext(target.asResource(), OWL.NamedIndividual).isPresent()) {
            MapFunction.Call expr = getManager().getFunction(SPINMAPL.self.getURI()).create().build();
            asContext(makeContext(target.asResource(), OWL.NamedIndividual)).addClassBridge(expr);
        }
        return res;
    }

    /**
     * Creates a {@code spinmap:Context} resource for specified source and target resources.
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
        String ont = getID().getURI();
        Resource res = null;
        if (ont != null && !ont.contains("#")) {
            String name = String.format(CONTEXT_TEMPLATE, getLocalName(source), getLocalName(target));
            res = createResource(ont + "#" + name);
            if (containsResource(res)) { // found different resource with the same local name
                res = null;
            }
        }
        if (res == null) { // anonymous contexts are not allowed since them can be used as function call parameter
            res = createResource("urn:uuid:" + UUID.randomUUID());
        }
        res.addProperty(RDF.type, SPINMAP.Context);
        res.addProperty(SPINMAP.sourceClass, source);
        res.addProperty(SPINMAP.targetClass, target);
        return res;
    }

    @Override
    public MapModelImpl bindContexts(MapContext left, MapContext right) {
        OntCE leftClass = left.getTarget();
        OntCE rightClass = right.getTarget();
        Set<OntOPE> res = getLinkProperties(leftClass, rightClass);
        if (res.isEmpty()) {
            throw exception(MAPPING_ATTACHED_CONTEXT_TARGET_CLASS_NOT_LINKED)
                    .addContext(left)
                    .addContext(right).build();
        }
        if (res.size() != 1) {
            Exceptions.Builder err = exception(MAPPING_ATTACHED_CONTEXT_AMBIGUOUS_CLASS_LINK)
                    .addContext(left)
                    .addContext(right);
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
    public OntGraphModel asGraphModel() {
        return this;
    }

    @Override
    public MapManagerImpl getManager() {
        return manager;
    }

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
     * Returns {@code spinmap:targetPredicate$i} argument property.
     *
     * @param i int
     * @return {@link Property}
     */
    public Property getTargetPredicate(int i) {
        return createArgProperty(SPINMAP.targetPredicate(i).getURI());
    }

    /**
     * Returns {@code spin:_arg$i} argument variable.
     *
     * @param i int
     * @return {@link Resource}
     */
    public Resource getArgVariable(int i) {
        return createVariable(SPIN._arg(i).getURI());
    }

    /**
     * Creates or finds sp:variable.
     *
     * @param url String
     * @return {@link Resource}
     */
    public Resource createVariable(String url) {
        return createResource(url, SP.Variable);
    }

    /**
     * Creates or finds a property which has {@code rdfs:subPropertyOf == sp:arg}.
     *
     * @param uri String
     * @return {@link Property}
     */
    public Property createArgProperty(String uri) {
        Property res = getProperty(uri);
        if (!contains(res, RDF.type, RDF.Property)) {
            createResource(uri, RDF.Property).addProperty(RDFS.subPropertyOf, SP.arg);
        }
        return res;
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
     * @param value String, not null
     * @return {@link RDFNode} literal or resource, not null
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
     * Answers if specified property links classes together through domain/range or restriction relationships.
     *
     * @param property {@link OntOPE} property to test
     * @param domain   {@link OntCE} "domain" candidate
     * @param range    {@link OntCE} "range" candidate
     * @return true if it is link property.
     * @see ru.avicomp.map.utils.ClassPropertyMapImpl#directProperties(OntCE)
     */
    public boolean isLinkProperty(OntOPE property, OntCE domain, OntCE range) {
        Property p = property.asProperty();
        if (properties(domain).noneMatch(p::equals)) return false;
        // range
        if (property.range().anyMatch(r -> Objects.equals(r, range))) return true;
        // object some/all values from or cardinality restriction
        return statements(null, OWL.onProperty, property)
                .map(OntStatement::getSubject)
                .filter(s -> s.canAs(OntCE.ComponentRestrictionCE.class))
                .map(s -> s.as(OntCE.ComponentRestrictionCE.class))
                .map(OntCE.Value::getValue)
                .map(RDFNode.class::cast)
                .filter(RDFNode::isResource)
                .map(RDFNode::asResource)
                .anyMatch(range::equals);
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
     * Adds function "as is" to the graph.
     *
     * @param function {@link MapFunctionImpl}
     * @return Resource function in model
     */
    protected Resource addFunctionBody(MapFunctionImpl function) {
        Models.getAssociatedStatements(function.asResource()).forEach(this::add);
        // also any return types?
        return function.asResource().inModel(this);
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
     * @return an anonymous {@link Resource}
     * @see #parseExpression(Resource, RDFNode, boolean)
     */
    protected RDFNode createExpression(MapFunction.Call call) {
        MapFunction func = call.getFunction();
        Resource res = createResource();
        call.asMap().forEach((arg, value) -> {
            RDFNode param = null;
            if (value instanceof MapFunction.Call) {
                if (Objects.equals(value, call)) throw new IllegalArgumentException("Self call");
                param = createExpression((MapFunction.Call) value);
            }
            if (value instanceof String) {
                param = toNode((String) value);
            }
            if (param == null)
                throw new IllegalArgumentException("Wrong value for " + arg.name() + ": " + value);
            Property predicate = createArgProperty(arg.name());
            res.addProperty(predicate, param);

        });
        return res.addProperty(RDF.type, createResource(func.name()));
    }

    protected MapFunctionImpl.CallImpl parseExpression(Resource mapping, RDFNode expr) {
        return parseExpression(mapping, expr, false);
    }

    /**
     * Creates a {@link MapFunction.Call function call} from a given expression resource.
     *
     * @param mapping  {@link Resource} mapping
     * @param expr     {@link RDFNode} expression
     * @param isFilter boolean
     * @return {@link MapFunction.Call}
     * @see #createExpression(MapFunction.Call)
     */
    protected MapFunctionImpl.CallImpl parseExpression(Resource mapping, RDFNode expr, boolean isFilter) {
        MapManagerImpl man = getManager();
        MapFunctionImpl f;
        Map<MapFunctionImpl.ArgImpl, Object> args = new HashMap<>();

        if (expr.isLiteral() || expr.isURIResource()) {
            f = man.getFunction(SPINMAP.equals.getURI());
            String v = (expr.isLiteral() ? expr :
                    ContextMappingHelper.findProperty(mapping, expr.asResource(), isFilter)).asNode().toString();
            args.put(f.getArg(SP.arg1.getURI()), v);
            return createFunctionCall(f, args);
        }
        if (!expr.isAnon()) throw new IllegalArgumentException("Should never happen: " + expr.toString());
        Resource expression = expr.asResource();
        String name = expression.getRequiredProperty(RDF.type).getObject().asResource().getURI();
        f = man.getFunction(name);
        expression.listProperties()
                .filterDrop(s -> RDF.type.equals(s.getPredicate()))
                .forEachRemaining(s -> {
                    String uri = s.getPredicate().getURI();
                    MapFunctionImpl.ArgImpl a;
                    if (f.isVararg() && !f.hasArg(uri)) {
                        List<MapFunctionImpl.ArgImpl> varargs = f.listArgs()
                                .filter(MapFunctionImpl.ArgImpl::isVararg)
                                .collect(Collectors.toList());
                        if (varargs.size() != 1)
                            throw new IllegalStateException("Can't find vararg argument for " + f.name());
                        a = f.new ArgImpl(varargs.get(0), uri);
                    } else {
                        a = f.getArg(uri);
                    }
                    Object v = null;
                    RDFNode n = s.getObject();
                    if (n.isResource()) {
                        Resource r = n.asResource();
                        if (r.isAnon()) {
                            v = parseExpression(mapping, r, isFilter);
                        } else if (SpinModels.isSpinArgVariable(r)) {
                            v = ContextMappingHelper.findProperty(mapping, r, isFilter).asNode().toString();
                        }
                    }
                    if (v == null) {
                        v = n.asNode().toString();
                    }
                    args.put(a, v);
                });
        return createFunctionCall(f, args);
    }

    /**
     * Makes a {@link MapFunction.Call} implementation with overridden {@code #toString()},
     * to produce a good-looking output, which can be used as label.
     * Actually, it is not a very good idea to override {@code #toString()},
     * there should be a special mechanism to print anything in ONT-MAP api.
     * But as temporary solution it is okay: it is not dangerous in our case.
     *
     * @param f    {@link MapFunctionImpl}
     * @param args Map with {@link MapFunctionImpl.ArgImpl args} as keys
     * @return {@link MapFunction.Call} attached to this model.
     */
    protected MapFunctionImpl.CallImpl createFunctionCall(MapFunctionImpl f, Map<MapFunctionImpl.ArgImpl, Object> args) {
        MapModelImpl m = this;
        return f.new CallImpl(args) {
            @Override
            public String toString(PrefixMapping pm) {
                String name = m.shortForm(getFunction().name());
                List<MapFunctionImpl.ArgImpl> args = listArgs().collect(Collectors.toList());
                if (args.size() == 1) { // print without predicate
                    return name + "(" + getStringValue(m, args.get(0)) + ")";
                }
                return args.stream()
                        .map(a -> toString(pm, a))
                        .collect(Collectors.joining(", ", name + "(", ")"));
            }

            @Override
            protected String getStringValue(PrefixMapping pm, MapFunctionImpl.ArgImpl a) {
                Object v = get(a);
                if (v instanceof String) {
                    RDFNode n = m.toNode((String) v);
                    if (n.isLiteral()) {
                        Literal l = n.asLiteral();
                        String u = l.getDatatypeURI();
                        if (XSD.xstring.getURI().equals(u)) {
                            return l.getLexicalForm();
                        }
                        return String.format("%s^^%s", l.getLexicalForm(), m.shortForm(u));
                    } else {
                        return m.shortForm(n.asNode().toString());
                    }
                }
                return super.getStringValue(pm, a);
            }

            @Override
            protected String getStringKey(PrefixMapping pm, MapFunctionImpl.ArgImpl a) {
                return "?" + toNode(a.name()).asResource().getLocalName();
            }

            @Override
            public String toString() {
                return toString(m);
            }
        };
    }

    /**
     * Validates a function-call against this model.
     *
     * @param func {@link MapFunction.Call} an expression.
     * @throws MapJenaException if something is wrong with function, e.g. wrong argument types.
     */
    @Override
    public void validate(MapFunction.Call func) throws MapJenaException {
        testFunction(func, exception(MAPPING_MAP_FUNCTION_VALIDATION_FAIL).addFunction(func).build());
    }

    /**
     * Tests the given function.
     *
     * @param function {@link MapFunction.Call} to test
     * @param error    {@link MapJenaException} an error holder,
     *                 this exception will be thrown in case validation is fail
     * @return the same map-function as specified in first place
     * @throws MapJenaException the same exception as specified in second place
     */
    public MapFunction.Call testFunction(MapFunction.Call function,
                                         MapJenaException error) throws MapJenaException {
        function.asMap().forEach((arg, value) -> {
            try {
                ArgValidationHelper v = new ArgValidationHelper(this, arg);
                if (value instanceof String) {
                    v.testStringValue((String) value);
                    return;
                }
                if (value instanceof MapFunction.Call) {
                    MapFunction.Call nested = (MapFunction.Call) value;
                    v.testFunctionValue(nested);
                    testFunction(nested, FUNCTION_CALL_WRONG_ARGUMENT_FUNCTION.create().addFunction(nested).build());
                    return;
                }
                throw new IllegalStateException("Should never happen, unexpected value: " + value);
            } catch (MapJenaException e) {
                error.addSuppressed(e);
            }
        });
        if (error.getSuppressed().length == 0)
            return function;
        throw error;
    }

    protected Exceptions.Builder exception(Exceptions code) {
        return code.create().add(Key.MAPPING, toString());
    }

    /**
     * Writes a custom function "as is" to the mapping graph.
     *
     * @param call {@link MapFunction.Call}
     */
    protected void writeFunctionBody(MapFunction.Call call) {
        if (call == null) return;
        MapFunctionImpl function = (MapFunctionImpl) call.getFunction();
        if (function.isCustom()) {
            Resource res = addFunctionBody(function);
            res.listProperties(AVC.runtime)
                    .mapWith(Statement::getObject)
                    .filterKeep(RDFNode::isLiteral)
                    .mapWith(RDFNode::asLiteral)
                    .mapWith(Literal::getString)
                    .forEachRemaining(s -> findRuntimeBody(function, s).apply(this, call));
            // subClassOf
            res.listProperties(RDFS.subClassOf)
                    .mapWith(Statement::getResource)
                    .mapWith(Resource::getURI)
                    .mapWith(manager::getFunction)
                    .forEachRemaining(this::addFunctionBody);
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
    protected static AdjustFunctionBody findRuntimeBody(MapFunctionImpl func,
                                                        String classPath) throws MapJenaException {
        try {
            Class<?> res = Class.forName(classPath);
            if (!AdjustFunctionBody.class.isAssignableFrom(res)) {
                throw new MapJenaException(func.name() +
                        ": incompatible class type: " + classPath + " <> " + res.getName());
            }
            return (AdjustFunctionBody) res.newInstance();
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
            throw new MapJenaException(func.name() + ": can't init " + classPath, e);
        }
    }

    @Override
    public String toString() {
        return String.format("MapModel{%s}", Graphs.getName(getBaseGraph()));
    }
}

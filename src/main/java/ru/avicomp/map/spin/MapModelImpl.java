package ru.avicomp.map.spin;

import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.*;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.impl.OntGraphModelImpl;
import ru.avicomp.ontapi.jena.impl.conf.OntPersonality;
import ru.avicomp.ontapi.jena.model.*;
import ru.avicomp.ontapi.jena.utils.Iter;
import ru.avicomp.ontapi.jena.utils.Models;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ru.avicomp.map.spin.Exceptions.ATTACHED_CONTEXT_AMBIGUOUS_CLASS_LINK;
import static ru.avicomp.map.spin.Exceptions.ATTACHED_CONTEXT_TARGET_CLASS_NOT_LINKED;

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
        Stream<OntGraphModel> imports = super.imports(MapManagerImpl.ONT_PERSONALITY)
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
    public Stream<Context> contexts() {
        return listContexts().map(Context.class::cast);
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
     * @param context {@link Resource}
     * @return {@link MapContextImpl}
     */
    public MapContextImpl asContext(Resource context) {
        return new MapContextImpl(context.asNode(), this);
    }

    @Override
    public MapModelImpl deleteContext(Context context) {
        List<Context> related = context.dependentContexts().collect(Collectors.toList());
        if (!related.isEmpty()) {
            Exceptions.Builder error = Exceptions.CONTEXT_CANNOT_BE_DELETED_DUE_TO_DEPENDENCIES.create().addContext(context);
            related.forEach(error::addContext);
            throw error.build();
        }
        MapContextImpl c = ((MapContextImpl) MapJenaException.notNull(context, "Null context"));
        if (getManager().generateNamedIndividuals()) {
            findContext(c.getTarget(), OWL.NamedIndividual).ifPresent(this::deleteContext);
        }
        deleteContext(c);
        clearUnused();
        // todo: check unused imports
        return this;
    }

    /**
     * Deletes unused anymore things, which is appeared in the base graph.
     * I.e. construct templates, custom functions, {@code sp:Variable}s and {@code sp:arg}s.
     */
    public void clearUnused() {
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
        Set<Statement> rules = context.listRules().collect(Collectors.toSet());
        rules.forEach(s -> {
            Models.deleteAll(s.getObject().asResource());
            remove(s);
        });
        // delete declaration:
        Models.deleteAll(context);
    }

    /**
     * Returns all contexts that depend on the specified.
     * A context can be used as parameter in different function-calls, usually with predicate {@code spinmapl:context}.
     * There is one exclusion: {@code spinmap:targetResource}, it uses {@code spinmap:context} as predicate for argument with type {@code spinmap:Context}.
     *
     * @param context {@link MapContextImpl}
     * @return distinct stream of contexts
     */
    public Stream<MapContextImpl> listRelatedContexts(MapContextImpl context) {
        Stream<Resource> targetResourceExpressions = statements(null, RDF.type, SPINMAP.targetResource)
                .map(Statement::getSubject)
                .filter(s -> s.hasProperty(SPINMAP.context, context));
        Stream<Resource> otherExpressions = statements(null, SPINMAPL.context, context).map(Statement::getSubject);
        Stream<Resource> res = Stream.concat(targetResourceExpressions, otherExpressions)
                .filter(RDFNode::isAnon)
                .flatMap(e -> Stream.concat(Stream.of(e), listParents(e)))
                .flatMap(e -> Stream.concat(contextsByRuleExpression(e), contextsByTargetExpression(e)))
                .filter(RDFNode::isURIResource)
                .distinct();
        return asContextStream(res);
    }

    public Stream<Resource> contextsByTargetExpression(RDFNode expression) {
        return statements(null, SPINMAP.target, expression).map(Statement::getSubject)
                .filter(s -> s.hasProperty(RDF.type, SPINMAP.Context));
    }

    public Stream<Resource> contextsByRuleExpression(RDFNode expression) {
        return statements(null, SPINMAP.expression, expression).map(OntStatement::getSubject)
                .flatMap(s -> s.objects(SPINMAP.context, Resource.class));
    }

    public static Stream<Resource> subjects(RDFNode object) {
        return Iter.asStream(object.getModel().listResourcesWithProperty(null, object));
    }

    /**
     * Recursively lists all statements for specified subject.
     * Note: a possibility of StackOverflowError in case graph contains a recursion.
     * TODO: move to ONT-API?
     *
     * @param subject {@link RDFNode}, nullable
     * @return Stream of {@link Statement}s
     * @throws StackOverflowError in case graph contains recursion
     * @see Models#getAssociatedStatements(Resource)
     */
    public static Stream<Statement> listProperties(RDFNode subject) {
        if (subject == null || !subject.isAnon()) return Stream.empty();
        return Iter.asStream(subject.asResource().listProperties())
                .flatMap(s -> s.getObject().isAnon() ? listProperties(s.getObject().asResource()) : Stream.of(s));
    }

    /**
     * Recursively lists all parent resources for specified object node.
     * Note: a possibility of StackOverflowError in case graph contains a recursion.
     * TODO: move to ONT-API?
     *
     * @param object {@link RDFNode}
     * @return Stream of {@link Resource}s
     * @throws StackOverflowError in case graph contains recursion
     */
    public static Stream<Resource> listParents(RDFNode object) {
        return subjects(object).flatMap(s -> {
            Stream<Resource> r = Stream.of(s);
            return s.isAnon() ? Stream.concat(r, listParents(s)) : r;
        });
    }

    /**
     * Creates a {@code spinmap:Context} which binds specified class-expressions.
     * It also adds imports for ontologies where arguments are declared in.
     * In case {@link MapManagerImpl#generateNamedIndividuals()}{@code == true}
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
                .filter(m -> !Objects.equals(m, this))
                .filter(m -> MapModelImpl.this.imports().noneMatch(i -> Objects.equals(i.getID(), m.getID())))
                .peek(m -> {
                    if (!LOGGER.isDebugEnabled()) return;
                    LOGGER.debug("Import {}", m);
                })
                .forEach(MapModelImpl.this::addImport);
        Resource res = makeContext(source.asResource(), target.asResource());
        if (getManager().generateNamedIndividuals() && !findContext(target.asResource(), OWL.NamedIndividual).isPresent()) {
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
    public MapModelImpl bindContexts(Context left, Context right) {
        OntCE leftClass = left.getTarget();
        OntCE rightClass = right.getTarget();
        List<OntOPE> res = linkProperties(leftClass, rightClass).collect(Collectors.toList());
        if (res.isEmpty()) {
            throw ATTACHED_CONTEXT_TARGET_CLASS_NOT_LINKED.create().addContext(left).addContext(right).build();
        }
        if (res.size() != 1) {
            Exceptions.Builder err = ATTACHED_CONTEXT_AMBIGUOUS_CLASS_LINK.create()
                    .addContext(left)
                    .addContext(right);
            res.forEach(p -> err.add(Exceptions.Key.LINK_PROPERTY, ClassPropertyMap.toNamed(p).getURI()));
            throw err.build();
        }
        OntOPE p = res.get(0);
        if (isLinkProperty(p, leftClass, rightClass)) {
            left.attachContext(right, p);
        } else {
            right.attachContext(left, p);
        }
        return this;
    }

    @Override
    public OntGraphModel asOntModel() {
        return this;
    }

    /**
     * Returns a map-manager to which this model is associated.
     *
     * @return {@link MapManagerImpl}
     */
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
     *
     * @param uri String, not null.
     * @return {@link RDFDatatype} or null
     */
    public RDFDatatype getDatatype(String uri) {
        return Optional.ofNullable(getOntEntity(OntDT.class, uri)).map(OntDT::toRDFDatatype).orElse(null);
    }

    /**
     * Answers if specified property links classes together through domain and range axioms.
     * TODO: move to ONT-API?
     *
     * @param property {@link OntOPE} property to test
     * @param domain   {@link OntCE} domain candidate
     * @param range    {@link OntCE} range candidate
     * @return true if it is link property.
     */
    public static boolean isLinkProperty(OntOPE property, OntCE domain, OntCE range) {
        return property.domain().anyMatch(d -> Objects.equals(d, domain)) && property.range().anyMatch(r -> Objects.equals(r, range));
    }

    /**
     * Lists all linked properties.
     *
     * @param left  {@link OntCE}
     * @param right {@link OntCE}
     * @return Stream of {@link OntOPE}s
     */
    public Stream<OntOPE> linkProperties(OntCE left, OntCE right) {
        return ontObjects(OntOPE.class)
                .filter(p -> isLinkProperty(p, left, right) || isLinkProperty(p, right, left))
                .distinct();
    }
}

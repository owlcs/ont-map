package ru.avicomp.map.spin;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDFS;
import org.topbraid.spin.arq.ARQ2SPIN;
import org.topbraid.spin.vocabulary.*;
import ru.avicomp.map.Context;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapJenaException;
import ru.avicomp.map.MapModel;
import ru.avicomp.map.spin.impl.MapContextImpl;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.impl.OntGraphModelImpl;
import ru.avicomp.ontapi.jena.impl.conf.OntPersonality;
import ru.avicomp.ontapi.jena.model.*;
import ru.avicomp.ontapi.jena.utils.Graphs;
import ru.avicomp.ontapi.jena.utils.Models;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by @szuev on 10.04.2018.
 */
@SuppressWarnings("WeakerAccess")
public class MapModelImpl extends OntGraphModelImpl implements MapModel {
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
    public OntID getID() {
        return getNodeAs(Graphs.ontologyNode(getBaseGraph())
                .orElseGet(() -> createResource().addProperty(RDF.type, OWL.Ontology).asNode()), OntID.class);
    }

    @Override
    public OntID setID(String uri) {
        return getNodeAs(OntGraphModelImpl.createOntologyID(getBaseModel(), uri).asNode(), OntID.class);
    }

    @Override
    public Stream<OntGraphModel> imports(OntPersonality personality) {
        return imports(personality, false);
    }

    public Stream<OntGraphModel> imports(OntPersonality personality, boolean withLibrary) {
        return super.imports(personality).filter(model -> withLibrary || !SystemModels.graphs().keySet().contains(model.getID().getURI()));
    }

    @Override
    public Stream<Context> contexts() {
        return listContexts().map(Context.class::cast);
    }

    public Stream<MapContextImpl> listContexts() {
        return statements(null, RDF.type, SPINMAP.Context)
                .map(OntStatement::getSubject)
                .filter(s -> s.objects(SPINMAP.targetClass, OntClass.class).findAny().isPresent())
                .filter(s -> s.objects(SPINMAP.sourceClass, OntClass.class).findAny().isPresent())
                .map(this::asContext);
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

    public MapContextImpl asContext(Resource context) {
        return new MapContextImpl(context.asNode(), this);
    }

    @Override
    public MapModelImpl removeContext(Context context) {
        MapContextImpl c = ((MapContextImpl) MapJenaException.notNull(context, "Null context"));
        if (getManager().generateNamedIndividuals()) {
            statements(null, RDF.type, SPINMAP.Context)
                    .map(OntStatement::getSubject)
                    .filter(s -> s.hasProperty(SPINMAP.targetClass, OWL.NamedIndividual))
                    .filter(s -> s.hasProperty(SPINMAP.sourceClass, c.getTarget()))
                    .map(this::asContext)
                    .findFirst().ifPresent(this::deleteContext);
        }
        deleteContext(c);
        clearUnused();
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
                        .filter(OntObject::isLocal)
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
                .forEach(MapModelImpl.this::addImport);
        Resource res = makeContext(source.asResource(), target.asResource());
        if (getManager().generateNamedIndividuals()) {
            MapFunction.Call expr = getManager().getFunction(SPINMAPL.self.getURI()).createFunctionCall().build();
            asContext(makeContext(target.asResource(), OWL.NamedIndividual)).addExpression(expr);
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
        String iri = getID().getURI();
        Resource res = null;
        if (iri != null && !iri.contains("#")) {
            res = createResource(iri + "#" + String.format(CONTEXT_TEMPLATE, getLocalName(source), getLocalName(target)));
            if (containsResource(res)) { // found different resource with the same local name
                res = null;
            }
        }
        if (res == null) { // anonymous context
            res = createResource();
        }
        res.addProperty(RDF.type, SPINMAP.Context);
        res.addProperty(SPINMAP.sourceClass, source);
        res.addProperty(SPINMAP.targetClass, target);
        return res;
    }

    /**
     * Returns a map-manager to which this model is associated.
     *
     * @return {@link MapManagerImpl}
     */
    public MapManagerImpl getManager() {
        return manager;
    }

    /**
     * Finds or creates a mapping {@code spinmap:Mapping-$i-$j}, where {@code i >=0}, {@code j = 1}.
     * This mapping template is added directly to the base graph it is absent in the library.
     *
     * @param sources int
     * @param targets int
     * @return {@link Resource}
     * @throws IllegalArgumentException incorrect input
     * @throws MapJenaException         something goes wrong
     */
    public Resource getCommonMappingTemplate(int sources, int targets) throws IllegalArgumentException, MapJenaException {
        Resource res = SPINMAP.mapping(sources, targets).inModel(this);
        if (!contains(res, RDF.type, SPIN.ConstructTemplate)) {
            if (targets != 1) {
                throw new MapJenaException.Unsupported("Targets count != 1");
            }
            Model m = SpinModelConfig.createSpinModel(getGraph());
            res.addProperty(RDF.type, SPIN.ConstructTemplate)
                    .addProperty(RDFS.subClassOf, SPINMAP.Mapping_1)
                    .addProperty(SPIN.body, ARQ2SPIN.parseQuery(makeMappingConstructQuery(sources), m));
            res.addProperty(SPIN.constraint, createResource()
                    .addProperty(RDF.type, SPL.Argument)
                    .addProperty(SPL.predicate, SPINMAP.expression));
            IntStream.rangeClosed(1, sources)
                    .forEach(i -> res.addProperty(SPIN.constraint, createResource()
                            .addProperty(RDF.type, SPL.Argument).addProperty(SPL.predicate, getSourcePredicate(i))));
        }
        return res;
    }

    /**
     * Makes an inner spin select query to be used inside {@code spinmap:Mapping-i-j} template.
     *
     * @param argumentNum int
     * @return String
     */
    public static String makeMappingConstructQuery(int argumentNum) {
        StringBuilder query = new StringBuilder("CONSTRUCT {\n\t")
                .append("?target ?targetPredicate1 ?newValue")
                .append(" .\n}\nWHERE {\n");
        StringBuilder evalArgs = new StringBuilder("?expression");
        for (int i = 1; i <= argumentNum; i++) {
            query.append("\t?this ?sourcePredicate").append(i).append(" ?oldValue").append(i).append(" .\n");
            evalArgs.append(", sp:arg").append(i).append(", ?oldValue").append(i);
        }
        query.append("\tBIND (spin:eval(").append(evalArgs).append(") AS ?newValue) .\n" +
                "\tBIND (spinmap:targetResource(?this, ?context) AS ?target) .\n}");
        return query.toString();
    }

    /**
     * Returns {@code spinmap:sourcePredicate$i} argument property.
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
     * @param url String
     * @return {@link Property}
     */
    public Property createArgProperty(String url) {
        Property res = getProperty(url);
        if (!contains(res, RDF.type, RDF.Property)) {
            createResource(url, RDF.Property).addProperty(RDFS.subPropertyOf, SP.arg);
        }
        return res;
    }
}

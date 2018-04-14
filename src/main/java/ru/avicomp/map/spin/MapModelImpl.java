package ru.avicomp.map.spin;

import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.rdf.model.Resource;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.Context;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.MapModel;
import ru.avicomp.map.spin.model.MapContext;
import ru.avicomp.ontapi.jena.OntJenaException;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.impl.OntGraphModelImpl;
import ru.avicomp.ontapi.jena.impl.conf.OntPersonality;
import ru.avicomp.ontapi.jena.model.OntCE;
import ru.avicomp.ontapi.jena.model.OntID;
import ru.avicomp.ontapi.jena.model.OntObject;
import ru.avicomp.ontapi.jena.model.OntStatement;
import ru.avicomp.ontapi.jena.utils.Graphs;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Created by @szuev on 10.04.2018.
 */
public class MapModelImpl extends OntGraphModelImpl implements MapModel {
    private static final String CONTEXT_TEMPLATE = "Context-%s-%s";

    public MapModelImpl(UnionGraph base, OntPersonality personality) {
        super(base, personality);
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
    public Stream<Context> contexts() {
        return statements(null, RDF.type, SPINMAP.Context)
                .map(OntStatement::getSubject)
                .filter(s -> s.hasProperty(SPINMAP.targetClass))
                .filter(s -> s.hasProperty(SPINMAP.sourceClass))
                .map(s -> s.as(MapContext.class));
    }

    @Override
    public MapContext createContext(OntCE source, OntCE target) {
        return contexts()
                .filter(s -> Objects.equals(s.getSource(), source))
                .filter(s -> Objects.equals(s.getTarget(), target))
                .map(MapContext.class::cast)
                .findFirst().orElseGet(() -> makeContext(source, target));
    }

    public MapContext makeContext(OntCE source, OntCE target) {
        // ensue all related models are imported:
        Stream.of(source, target)
                .map(OntObject::getModel)
                .forEach(MapModelImpl.this::addImport);

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
        return res.as(MapContext.class);
    }

    @Override
    public MapManager getManager() {
        throw new UnsupportedOperationException();
    }


    public Resource getOntology(String iri) {
        return createResource(iri).addProperty(RDF.type, OWL.Ontology);
    }

    public Resource findOntology() throws OntJenaException {
        return Iter.asStream(listResourcesWithProperty(RDF.type, OWL.Ontology))
                .findFirst()
                .orElseThrow(() -> new OntJenaException("Can't find owl:Ontology"));
    }

    public MapModelImpl addImport(String uri) throws OntJenaException {
        findOntology().addProperty(OWL.imports, createResource(OntJenaException.notNull(uri, "No url.")));
        return this;
    }

    public MapModelImpl addImport(OntID other, boolean withNS) throws OntJenaException {
        String uri = other.getURI();
        addImport(uri);
        getGraph().addGraph(other.getModel().getGraph());
        if (withNS && !uri.contains("#")) {
            setNsPrefix(other.getLocalName(), uri + "#");
        }
        return this;
    }

    /**
     * Creates or finds a {@code spinmap:Context} resource for specified {@link OntCE OWL Class Expression}s.
     * <pre>{@code
     * _:x rdf:type spinmap:Context ;
     *   spinmap:sourceClass <src> ;
     *   spinmap:targetClass <dst> ;
     * }</pre>
     *
     * @param src {@link OntCE}
     * @param dst {@link OntCE}
     * @return {@link Resource}
     */
    @Deprecated
    public Resource getContext(OntCE src, OntCE dst) {
        Optional<Resource> res = Iter.asStream(listResourcesWithProperty(SPINMAP.sourceClass, src))
                .filter(s -> s.hasProperty(SPINMAP.targetClass, dst))
                .filter(s -> s.hasProperty(RDF.type, SPINMAP.Context))
                .findFirst();
        if (res.isPresent()) return res.get();
        String iri = findOntology().getURI();
        Resource context;
        if (iri != null && !iri.contains("#")) {
            context = createResource(iri + "#" + String.format(CONTEXT_TEMPLATE, getLocalName(src), getLocalName(dst)));
            if (containsResource(context)) { // found different context with the same local name
                context = createResource();
            }
        } else {
            context = createResource();
        }
        context.addProperty(RDF.type, SPINMAP.Context);
        context.addProperty(SPINMAP.sourceClass, src);
        context.addProperty(SPINMAP.targetClass, dst);
        return context;
    }

    public static String getLocalName(Resource resource) {
        return resource.isURIResource() ? resource.getLocalName() : resource.getId().getLabelString();
    }
}

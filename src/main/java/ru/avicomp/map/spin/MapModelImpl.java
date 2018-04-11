package ru.avicomp.map.spin;

import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.enhanced.Personality;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.impl.ModelCom;
import ru.avicomp.map.MapModel;
import ru.avicomp.map.spin.vocabulary.SPINMAP;
import ru.avicomp.ontapi.jena.OntJenaException;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.model.OntCE;
import ru.avicomp.ontapi.jena.model.OntID;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.Optional;

/**
 * Created by @szuev on 10.04.2018.
 */
public abstract class MapModelImpl extends ModelCom implements MapModel {
    private static final String CONTEXT_TEMPLATE = "Context-%s-%s";

    public MapModelImpl(UnionGraph base, Personality<RDFNode> personality) {
        super(base, personality);
    }

    @Override
    public UnionGraph getGraph() {
        return (UnionGraph) super.getGraph();
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

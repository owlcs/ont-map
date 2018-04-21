package ru.avicomp.map.spin;

import org.apache.jena.rdf.model.Resource;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.Context;
import ru.avicomp.map.MapJenaException;
import ru.avicomp.map.MapModel;
import ru.avicomp.map.spin.impl.MapContextImpl;
import ru.avicomp.map.utils.Models;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.impl.OntGraphModelImpl;
import ru.avicomp.ontapi.jena.impl.conf.OntPersonality;
import ru.avicomp.ontapi.jena.model.*;
import ru.avicomp.ontapi.jena.utils.Graphs;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.Objects;
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
    public Stream<OntGraphModel> imports(OntPersonality personality) {
        return imports(personality, false);
    }

    public Stream<OntGraphModel> imports(OntPersonality personality, boolean withLibrary) {
        return super.imports(personality).filter(model -> withLibrary || !SystemModels.graphs().keySet().contains(model.getID().getURI()));
    }

    @Override
    public Stream<Context> contexts() {
        return statements(null, RDF.type, SPINMAP.Context)
                .map(OntStatement::getSubject)
                .filter(s -> s.hasProperty(SPINMAP.targetClass))
                .filter(s -> s.hasProperty(SPINMAP.sourceClass))
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

    private MapContextImpl asContext(Resource context) {
        return new MapContextImpl(context.asNode(), this);
    }

    @Override
    public MapModelImpl removeContext(Context context) {
        Models.getAssociatedStatements(((MapContextImpl) MapJenaException.notNull(context, "Null context"))).forEach(this::remove);
        return this;
    }

    /**
     * Creates a {@code spinmap:Context} resource for specified {@link OntCE OWL Class Expression}s.
     * <pre>{@code
     * _:x rdf:type spinmap:Context ;
     *   spinmap:sourceClass <src> ;
     *   spinmap:targetClass <dst> ;
     * }</pre>
     *
     * @param source {@link OntCE}
     * @param target {@link OntCE}
     * @return {@link Resource}
     */
    public Resource makeContext(OntCE source, OntCE target) {
        // ensue all related models are imported:
        Stream.of(MapJenaException.notNull(source, "Null source CE"), MapJenaException.notNull(target, "Null target CE"))
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
        return res;
    }

    public static String getLocalName(Resource resource) {
        return resource.isURIResource() ? resource.getLocalName() : resource.getId().getLabelString();
    }

}

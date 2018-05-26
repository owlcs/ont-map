package ru.avicomp.map.utils;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import ru.avicomp.ontapi.jena.model.*;

import java.util.stream.Stream;

/**
 * An implementation of {@link ru.avicomp.map.ClassPropertyMap},
 * which provides access only to local and builtin ontology class and property expressions.
 * An object is local to an {@link OntGraphModel ontology} if its base graph contains a declaration statement for that object.
 * See {@link OntObject#isLocal()}
 * <p>
 * Created by @szuev on 26.05.2018.
 */
public class LocalClassPropertyMapImpl extends ClassPropertyMapImpl {
    private final OntGraphModel model;

    public LocalClassPropertyMapImpl(OntGraphModel model) {
        this.model = model;
    }

    @Override
    public Stream<Property> properties(OntCE ce) {
        Resource c = ce.inModel(model);
        return c.canAs(OntCE.class) ? super.properties(c.as(OntCE.class)).filter(this::isLocal) : Stream.empty();
    }

    @Override
    public Stream<OntCE> classes(OntPE pe) {
        Resource p = pe.inModel(model);
        return p.canAs(OntPE.class) ? super.classes(p.as(OntPE.class)).filter(this::isLocal) : Stream.empty();
    }

    public boolean isLocal(Property pe) {
        OntEntity e;
        return (e = pe.inModel(model).as(OntEntity.class)).isBuiltIn() || e.isLocal();
    }

    public boolean isLocal(OntCE ce) {
        return ce.canAs(OntClass.class) && ce.inModel(model).as(OntClass.class).isBuiltIn() ||
                ce.inModel(model).as(OntObject.class).isLocal();
    }
}

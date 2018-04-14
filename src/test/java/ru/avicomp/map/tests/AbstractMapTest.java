package ru.avicomp.map.tests;

import ru.avicomp.map.MapManager;
import ru.avicomp.map.MapModel;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntNDP;

import java.util.Collection;

/**
 * Created by @szuev on 14.04.2018.
 */
public abstract class AbstractMapTest {

    public abstract MapModel assembleMapping(MapManager manager, OntGraphModel src, OntGraphModel dst);

    public abstract OntGraphModel assembleSource();

    public abstract OntGraphModel assembleTarget();

    public String getNameSpace() {
        return String.format("http://example.com/%s", getClass().getSimpleName());
    }

    protected OntGraphModel createModel(String name) {
        OntGraphModel res = OntModelFactory.createModel();
        res.setNsPrefixes(OntModelFactory.STANDARD);
        res.setID(getNameSpace() + "/" + name);
        String ns = res.getID().getURI() + "#";
        res.setNsPrefix(name, ns);
        return res;
    }

    protected static OntGraphModel assemblePrimitiveOntology(AbstractMapTest base,
                                                             String name,
                                                             String className,
                                                             Collection<String> individuals,
                                                             Collection<String> dataProperties) {
        OntGraphModel m = base.createModel(name);
        String ns = m.getID().getURI() + "#";
        OntClass clazz = m.createOntEntity(OntClass.class, ns + className);
        dataProperties.forEach(s -> m.createOntEntity(OntNDP.class, ns + s).addDomain(clazz));
        individuals.forEach(s -> clazz.createIndividual(ns + s));
        return m;
    }
}

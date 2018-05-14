package ru.avicomp.map.tests;

import org.junit.Assert;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.MapModel;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntObject;
import ru.avicomp.ontapi.jena.model.OntStatement;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by @szuev on 14.04.2018.
 */
public abstract class AbstractMapTest {

    public abstract MapModel assembleMapping(MapManager manager, OntGraphModel src, OntGraphModel dst);

    public abstract OntGraphModel assembleSource();

    public abstract OntGraphModel assembleTarget();

    static String getNameSpace(Class clazz) {
        return String.format("http://example.com/%s", clazz.getSimpleName());
    }

    public String getDataNameSpace() {
        return getNameSpace(getClass());
    }

    public String getMapNameSpace() {
        return getNameSpace(getClass());
    }

    OntGraphModel createDataModel(String name) {
        OntGraphModel res = OntModelFactory.createModel();
        res.setNsPrefixes(OntModelFactory.STANDARD);
        res.setID(getDataNameSpace() + "/" + name);
        String ns = res.getID().getURI() + "#";
        res.setNsPrefix(name, ns);
        return res;
    }

    MapModel createMappingModel(MapManager manager, String description) {
        MapModel res = manager.createMapModel();
        // TopBraid Composer (gui, not spin) has difficulties with anonymous ontologies:
        res.setID(getMapNameSpace() + "/map")
                .addComment(description, null);
        return res;
    }

    static void commonValidate(OntGraphModel m) {
        // check there is no any garbage in the model:
        List<OntObject> undeclaredIndividuals = TestUtils.plainAssertions(m)
                .map(OntStatement::getSubject)
                .filter(o -> !o.types().findFirst().isPresent())
                .collect(Collectors.toList());
        Assert.assertEquals("Model has unattached assertions: " + undeclaredIndividuals, 0, undeclaredIndividuals.size());
    }
}

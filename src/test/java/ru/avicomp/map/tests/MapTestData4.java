package ru.avicomp.map.tests;

import org.apache.jena.riot.Lang;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.model.OntGraphModel;

import java.io.IOException;

/**
 * Created by @szuev on 17.05.2018.
 */
abstract class MapTestData4 extends AbstractMapTest {

    @Override
    public OntGraphModel assembleSource() {
        try {
            return TestUtils.load("/iswc.ttl", Lang.TURTLE);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public OntGraphModel assembleTarget() {
        return new RelatedContextMapTest().assembleTarget();
    }

}

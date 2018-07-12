package ru.avicomp.map.tests;

import org.junit.Assert;
import org.junit.Test;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.map.Managers;
import ru.avicomp.map.MapModel;
import ru.avicomp.map.OWLMapManager;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.NoOpReadWriteLock;
import ru.avicomp.ontapi.OntologyModel;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.utils.Graphs;
import ru.avicomp.ontapi.jena.utils.Models;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Created by @szuev on 21.06.2018.
 */
public class OWLAPITest {
    private static final Logger LOGGER = LoggerFactory.getLogger(OWLAPITest.class);

    @Test
    public void testLoadMapping() throws OWLOntologyCreationException {
        String uri = "http://test.com/some-function3";
        OntGraphModel m = new LoadMapTestData(uri, "data-x")
                .assembleMapping(Managers.createMapManager(), null, null)
                .asGraphModel();
        String s = TestUtils.asString(m);

        OWLMapManager manager = Managers.createOWLMapManager();
        OntologyModel o = manager.loadOntologyFromOntologyDocument(TestUtils.createTurtleDocumentSource(s));
        TestUtils.debug(o.asGraphModel());
        Assert.assertEquals(uri, manager.getFunction(uri).name());
        Assert.assertEquals(1, manager.ontologies().count());
        Assert.assertEquals(1, manager.mappings().count());
        manager.createMapModel();
        Assert.assertEquals(2, manager.mappings().count());
    }

    @Test
    public void testLoadFunction() {
        String uri = "http://test.com/some-function4";
        OntGraphModel m = new LoadMapTestData(uri, "data-x")
                .assembleMapping(Managers.createMapManager(), null, null)
                .asGraphModel();

        OWLMapManager manager = Managers.createOWLMapManager();
        manager.asMapModel(m);
        Assert.assertEquals(uri, manager.getFunction(uri).name());
        Assert.assertEquals(0, manager.ontologies().count());
        Assert.assertEquals(0, manager.mappings().count());
        manager.createMapModel();
        Assert.assertEquals(1, manager.mappings().count());
    }

    @Test
    public void testCreate() {
        OWLMapManager manager = Managers.createOWLMapManager();
        MapModel m1 = manager.createMapModel();
        TestUtils.debug(m1);
        Assert.assertEquals(1, manager.ontologies().count());
        Assert.assertEquals(1, manager.mappings().count());
        Assert.assertEquals(0, m1.rules().count());
        String iri = "http://x.com";
        m1.setID(iri);
        Assert.assertEquals(1, manager.ontologies().count());
        Assert.assertEquals(1, manager.mappings().count());
        OntologyModel o1 = manager.getOntology(IRI.create(iri));
        Assert.assertNotNull(o1);
        TestUtils.debug(o1.asGraphModel());

        OntClass class1 = o1.asGraphModel().createOntEntity(OntClass.class, iri + "#Class1");
        OntClass class2 = o1.asGraphModel().createOntEntity(OntClass.class, iri + "#Class2");
        Assert.assertEquals(2, o1.axioms().count());
        manager.asMapModel(o1.asGraphModel())
                .createContext(class1, class2, manager.getFunction(SPINMAPL.self).create().build());
        TestUtils.debug(m1);
        Assert.assertEquals(1, m1.rules().count());
        MapModel m2 = manager.mappings().findFirst().orElseThrow(AssertionError::new);
        OWLOntology o2 = manager.ontologies().findFirst().orElseThrow(AssertionError::new);
        Assert.assertEquals(1, m2.rules().count());
        Assert.assertEquals(2, o2.axioms().count());
    }

    @Test
    public void testInference() {
        testInference(NoOpReadWriteLock.NO_OP_RW_LOCK);
    }

    @Test
    public void testLockInference() {
        testInference(new ReentrantReadWriteLock());
    }

    private void testInference(ReadWriteLock lock) {
        OWLMapManager manager = Managers.createOWLMapManager(lock);
        PropertyChainMapTest test = new PropertyChainMapTest();
        OntologyModel src1 = manager.addOntology(test.assembleSource().getGraph());
        OntologyModel dst1 = manager.addOntology(test.assembleTarget().getGraph());
        MapModel m1 = test.assembleMapping(manager, src1.asGraphModel(), dst1.asGraphModel());

        Assert.assertEquals(3, manager.ontologies().count());
        Assert.assertEquals(1, manager.mappings().count());

        Assert.assertEquals(1, Models.flat(src1.asGraphModel()).count());
        Assert.assertEquals(1, Models.flat(dst1.asGraphModel()).count());

        Models.flat(m1.asGraphModel()).forEach(x -> LOGGER.debug("{}", x));
        Assert.assertEquals(30, Models.flat(m1.asGraphModel()).count());
        MapModel m2 = manager.mappings().findFirst().orElseThrow(AssertionError::new);
        String tree = Graphs.importsTreeAsString(m2.asGraphModel().getGraph());
        LOGGER.debug("Imports-tree: \n{}", tree);
        Assert.assertEquals(30, tree.split("\n").length);

        LOGGER.debug("Run");
        manager.getInferenceEngine().run(m2, src1.asGraphModel(), dst1.asGraphModel());
        LOGGER.debug("Validate");
        test.validate(dst1.asGraphModel());
        dst1.axioms().forEach(x -> LOGGER.debug("AXIOM: {}", x));
        // 6 property assertions
        Assert.assertEquals(6, dst1.axioms(AxiomType.DATA_PROPERTY_ASSERTION).count());
        // 3 named individual declarations
        Assert.assertEquals(3, dst1.individualsInSignature().count());

        // Add new individual and rerun inference
        LOGGER.debug("Add individual");
        OntologyModel src2 = manager.getOntology(src1.getOntologyID());
        Assert.assertNotNull(src2);
        PropertyChainMapTest.addDataIndividual(src2.asGraphModel(), "Tirpitz", new double[]{56.23, 34.2});

        LOGGER.debug("Re-Run");
        manager.getInferenceEngine().run(m1, src2.asGraphModel(), dst1.asGraphModel());
        LOGGER.debug("Validate");
        TestUtils.debug(dst1.asGraphModel());
        OntologyModel dst2 = manager.getOntology(dst1.getOntologyID());
        Assert.assertNotNull(dst2);
        dst2.axioms().forEach(x -> LOGGER.debug("AXIOM: {}", x));
        Assert.assertEquals(8, dst1.axioms(AxiomType.DATA_PROPERTY_ASSERTION).count());
        Assert.assertEquals(4, dst2.individualsInSignature().count());
    }
}

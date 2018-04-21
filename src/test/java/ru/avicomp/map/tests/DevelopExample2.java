package ru.avicomp.map.tests;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.shared.PrefixMapping;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPINMAPL;
import ru.avicomp.map.*;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntIndividual;
import ru.avicomp.ontapi.jena.model.OntNDP;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * TODO: For developing. Will be moved/renamed
 * Created by @szuev on 14.04.2018.
 */
public class DevelopExample2 extends AbstractMapTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(DevelopExample2.class);

    private static final String SEPARATOR = "=&=";
    private static final String TEMPLATE = "Individual-%s-%s";

    @Test
    public void testProcess() {
        OntGraphModel src = assembleSource();
        TestUtils.debug(LOGGER, src);
        List<OntNDP> props = src.listDataProperties().sorted(Comparator.comparing(Resource::getURI)).collect(Collectors.toList());
        List<OntIndividual.Named> individuals = src.listNamedIndividuals().sorted(Comparator.comparing(Resource::getURI)).collect(Collectors.toList());
        Assert.assertEquals(2, props.size());
        Assert.assertEquals(3, individuals.size());

        OntGraphModel dst = assembleTarget();
        TestUtils.debug(LOGGER, dst);
        OntNDP dstProp = dst.listDataProperties().findFirst().orElseThrow(AssertionError::new);

        MapManager manager = Managers.getMapManager();
        MapModel mapping = assembleMapping(manager, src, dst);
        TestUtils.debug(LOGGER, mapping);

        LOGGER.info("Run inference.");
        manager.getInferenceEngine().run(mapping, src, dst);
        TestUtils.debug(LOGGER, dst);

        LOGGER.info("Validate.");
        OntClass dstClass = dst.listClasses().findFirst().orElseThrow(AssertionError::new);
        // one individual is skipped from inferincing:
        Assert.assertEquals(2, dst.statements(null, RDF.type, dstClass).count());

        String v11 = getDataPropertyAssertionStringValue(individuals.get(0), props.get(0));
        String v12 = getDataPropertyAssertionStringValue(individuals.get(0), props.get(1));
        String v21 = getDataPropertyAssertionStringValue(individuals.get(1), props.get(0));
        String v22 = getDataPropertyAssertionStringValue(individuals.get(1), props.get(1));

        String ns = dst.getID().getURI() + "#";
        Resource i1 = ResourceFactory.createResource(ns + String.format(TEMPLATE, v11, v12).replace(" ", "_"));
        Resource i2 = ResourceFactory.createResource(ns + String.format(TEMPLATE, v21, v22).replace(" ", "_"));
        Literal v1 = ResourceFactory.createStringLiteral(v12 + SEPARATOR + v11);
        Literal v2 = ResourceFactory.createStringLiteral(v22 + SEPARATOR + v21);

        assertDataPropertyAssertion(dst, i1, dstProp, v1);
        assertDataPropertyAssertion(dst, i2, dstProp, v2);
    }

    private static String getDataPropertyAssertionStringValue(OntIndividual.Named individual, OntNDP property) {
        return individual.objects(property, Literal.class).map(Literal::getString).findFirst().orElseThrow(AssertionError::new);
    }

    private static void assertDataPropertyAssertion(OntGraphModel m, Resource i, OntNDP p, Literal v) {
        Assert.assertTrue("Can't find [" + m.shortForm(i.getURI()) + " " + m.shortForm(p.getURI()) + " '" + m.shortForm(v.toString()) + "']", m.contains(i, p, v));
    }

    @Override
    public MapModel assembleMapping(MapManager manager, OntGraphModel src, OntGraphModel dst) {
        OntClass srcClass = src.listClasses().findFirst().orElseThrow(AssertionError::new);
        OntClass dstClass = dst.listClasses().findFirst().orElseThrow(AssertionError::new);
        List<OntNDP> props = src.listDataProperties().sorted(Comparator.comparing(Resource::getURI)).collect(Collectors.toList());
        Assert.assertEquals(2, props.size());
        OntNDP dstProp = dst.listDataProperties().findFirst().orElseThrow(AssertionError::new);

        MapFunction.Call targetFunction = manager.getFunction(SPINMAPL.buildURI2.getURI())
                .createFunctionCall()
                .add(SP.arg1.getURI(), props.get(0).getURI())
                .add(SP.arg2.getURI(), props.get(1).getURI())
                .add(SPINMAPL.template.getURI(), "target:" + String.format(TEMPLATE, "{?1}", "{?2}"))
                .build();
        MapFunction.Call propertyFunction = manager.getFunction(SPINMAPL.concatWithSeparator.getURI())
                .createFunctionCall()
                .add(SP.arg1.getURI(), props.get(1).getURI())
                .add(SP.arg2.getURI(), props.get(0).getURI())
                .add(SPINMAPL.separator.getURI(), SEPARATOR)
                .build();

        PrefixMapping pm = PrefixMapping.Factory.create()
                .setNsPrefixes(manager.prefixes())
                .setNsPrefixes(srcClass.getModel())
                .setNsPrefixes(dstClass.getModel()).lock();

        TestUtils.debug(LOGGER, targetFunction, pm);
        TestUtils.debug(LOGGER, propertyFunction, pm);

        MapModel res = manager.createModel();
        res.setID(getNameSpace() + "/map");
        res.createContext(srcClass, dstClass).addExpression(targetFunction).addPropertyBridge(propertyFunction, dstProp);
        Assert.assertEquals(1, res.contexts().count());
        Assert.assertEquals(2, res.imports().count());
        Assert.assertEquals(srcClass, res.contexts().map(Context::getSource).findFirst().orElseThrow(AssertionError::new));
        Assert.assertEquals(dstClass, res.contexts().map(Context::getTarget).findFirst().orElseThrow(AssertionError::new));
        return res;
    }

    @Override
    public OntGraphModel assembleSource() {
        OntGraphModel m = createModel("source");
        String ns = m.getID().getURI() + "#";
        OntClass class1 = m.createOntEntity(OntClass.class, ns + "SourceClass1");
        OntNDP prop1 = m.createOntEntity(OntNDP.class, ns + "SourceDataProperty1");
        OntNDP prop2 = m.createOntEntity(OntNDP.class, ns + "SourceDataProperty2");
        prop1.addDomain(class1);
        prop2.addDomain(class1);
        OntIndividual.Named individual1 = class1.createIndividual(ns + "a");
        OntIndividual.Named individual2 = class1.createIndividual(ns + "b");
        class1.createIndividual(ns + "c");
        // data property assertions:
        individual1.addProperty(prop1, "x y z", "e");
        individual1.addProperty(prop2, ResourceFactory.createTypedLiteral(2));

        individual2.addProperty(prop1, "A");
        individual2.addProperty(prop2, "B");
        return m;
    }

    @Override
    public OntGraphModel assembleTarget() {
        OntGraphModel m = createModel("target");
        String ns = m.getID().getURI() + "#";
        OntClass clazz = m.createOntEntity(OntClass.class, ns + "TargetClass1");
        m.createOntEntity(OntNDP.class, ns + "TargetDataProperty2").addDomain(clazz);
        return m;
    }
}

/*
 * This file is part of the ONT MAP.
 * The contents of this file are subject to the Apache License, Version 2.0.
 * Copyright (c) 2019, Avicomp Services, AO
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.avicomp.map.tests.maps;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.XSD;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;
import ru.avicomp.map.*;
import ru.avicomp.map.spin.vocabulary.SPIF;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.model.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by @szuev on 26.04.2018.
 */
public class ConditionalMapTest extends MapTestData2 {
    private final Logger LOGGER = LoggerFactory.getLogger(ConditionalMapTest.class);

    @Test
    @Override
    public void testInference() {
        LOGGER.info("Assembly models.");
        OntGraphModel s = assembleSource();
        TestUtils.debug(s);
        OntGraphModel t = assembleTarget();
        TestUtils.debug(t);
        MapManager manager = manager();
        MapModel m = assembleMapping(manager, s, t);
        TestUtils.debug(m);

        LOGGER.info("Run inference.");
        manager.getInferenceEngine(m).run(s, t);
        TestUtils.debug(t);

        Assert.assertEquals(4, t.individuals().count());

        LOGGER.info("Re-run inference and validate.");
        manager.getInferenceEngine(m).run(s, t);

        validate(t);
    }

    @Override
    public void validate(OntGraphModel result) {
        List<OntIndividual> individuals = result.individuals().collect(Collectors.toList());
        Assert.assertEquals(4, individuals.size());

        OntNDP email = TestUtils.findOntEntity(result, OntNDP.class, "email");
        OntNDP phone = TestUtils.findOntEntity(result, OntNDP.class, "phone");
        OntNDP skype = TestUtils.findOntEntity(result, OntNDP.class, "skype");

        // Jane has only email as string
        OntIndividual.Named iJane = TestUtils.findOntEntity(result, OntIndividual.Named.class, "res-jane-contacts");
        Assert.assertEquals(1, iJane.positiveAssertions().count());
        String janeEmail = iJane.objects(email, Literal.class)
                .filter(l -> XSD.xstring.getURI().equals(l.getDatatypeURI()))
                .map(Literal::getString)
                .findFirst().orElseThrow(AssertionError::new);
        Assert.assertEquals(DATA_EMAIL_JANE, janeEmail);

        // Jhon has email and skype
        OntIndividual.Named iJhon = TestUtils.findOntEntity(result, OntIndividual.Named.class, "res-jhons");
        Assert.assertEquals(2, iJhon.positiveAssertions().count());
        String jhonEmail = iJhon.objects(email, Literal.class)
                .filter(l -> XSD.xstring.getURI().equals(l.getDatatypeURI()))
                .map(Literal::getString)
                .findFirst().orElseThrow(AssertionError::new);
        String jhonSkype = iJhon.objects(skype, Literal.class)
                .filter(l -> XSD.xstring.getURI().equals(l.getDatatypeURI()))
                .map(Literal::getString)
                .findFirst().orElseThrow(AssertionError::new);
        Assert.assertEquals(DATA_EMAIL_JHON, jhonEmail);
        Assert.assertEquals(DATA_SKYPE_JHON, jhonSkype);

        // Bob has email and phone
        OntIndividual.Named iBob = TestUtils.findOntEntity(result, OntIndividual.Named.class, "res-bobs");
        Assert.assertEquals(2, iBob.positiveAssertions().count());
        String bobEmail = iBob.objects(email, Literal.class)
                .filter(l -> XSD.xstring.getURI().equals(l.getDatatypeURI()))
                .map(Literal::getString)
                .findFirst().orElseThrow(AssertionError::new);
        String bobPhone = iBob.objects(phone, Literal.class)
                .filter(l -> XSD.xstring.getURI().equals(l.getDatatypeURI()))
                .map(Literal::getString)
                .findFirst().orElseThrow(AssertionError::new);
        Assert.assertEquals(DATA_EMAIL_BOB, bobEmail);
        Assert.assertEquals(DATA_PHONE_BOB, bobPhone);

        // Karl has no contacts:
        OntIndividual.Named iKarl = TestUtils.findOntEntity(result, OntIndividual.Named.class, "res-karls");
        Assert.assertEquals(0, iKarl.positiveAssertions().count());

    }

    @Override
    public MapModel assembleMapping(MapManager manager, OntGraphModel src, OntGraphModel dst) {
        OntClass srcClass = TestUtils.findOntEntity(src, OntClass.class, "Contact");
        OntClass dstClass = TestUtils.findOntEntity(dst, OntClass.class, "User");
        Map<OntDT, OntNDP> propsMap = src.datatypes()
                .map(Resource::getLocalName)
                .collect(Collectors.toMap(
                        s -> TestUtils.findOntEntity(src, OntDT.class, s),
                        s -> TestUtils.findOntEntity(dst, OntNDP.class, s)));
        OntNDP sourceProperty = TestUtils.findOntEntity(src, OntNDP.class, "info");

        MapFunction.Call targetFunctionCall = manager.getFunction(SPINMAPL.composeURI.getURI())
                .create()
                .add(SPINMAPL.template.getURI(), dst.getID().getURI() + "#res-{?1}")
                .build();
        MapFunction eq = manager.getFunction(SP.eq.getURI());
        MapFunction datatype = manager.getFunction(SP.resource("datatype").getURI());
        MapFunction cast = manager.getFunction(SPIF.cast.getURI());

        MapModel res = createMappingModel(manager, "Used functions: spinmapl:composeURI, sp:eq, sp:datatype, spif:cast");
        MapContext context = res.createContext(srcClass, dstClass, targetFunctionCall);
        propsMap.forEach((sourceDatatype, targetProperty) -> {
            MapFunction.Call filter = eq.create()
                    .add(SP.arg1.getURI(), sourceDatatype.getURI())
                    .add(SP.arg2.getURI(), datatype.create().add(SP.arg1.getURI(), sourceProperty.getURI())).build();
            MapFunction.Call mapping = cast.create()
                    .add(SP.arg1.getURI(), sourceProperty.getURI())
                    .add(SPIF.argDatatype.getURI(), XSD.xstring.getURI()).build();
            context.addPropertyBridge(filter, mapping, targetProperty);
        });
        return res;
    }


    @Test
    public void testValidateMapping() {
        MapModel map = assembleMapping();
        OntGraphModel m = map.asGraphModel();

        OntClass sC = notNull(m.getOntEntity(OntClass.class, m.expandPrefix("contacts:Contact")));
        OntClass tC = notNull(m.getOntEntity(OntClass.class, m.expandPrefix("users:User")));
        OntDT sDT1 = notNull(m.getOntEntity(OntDT.class, m.expandPrefix("contacts:skype")));
        OntDT sDT2 = notNull(m.getOntEntity(OntDT.class, m.expandPrefix("contacts:phone")));
        OntDT sDT3 = notNull(m.getOntEntity(OntDT.class, m.expandPrefix("contacts:email")));
        OntNDP sp1 = notNull(m.getOntEntity(OntNDP.class, m.expandPrefix("contacts:info")));
        OntNDP tp1 = notNull(m.getOntEntity(OntNDP.class, m.expandPrefix("users:skype")));
        OntNDP tp2 = notNull(m.getOntEntity(OntNDP.class, m.expandPrefix("users:phone")));
        OntNDP tp3 = notNull(m.getOntEntity(OntNDP.class, m.expandPrefix("users:email")));
        OntDT dt = notNull(map.asGraphModel().getOntEntity(OntDT.class, XSD.xstring));

        Assert.assertEquals(4, map.rules().count());
        MapContext context = map.contexts().findFirst().orElseThrow(AssertionError::new);
        Assert.assertEquals(sC, context.getSource());
        Assert.assertEquals("Context: " + context, tC, context.getTarget());

        Assert.assertEquals(String.format("spinmapl:composeURI(\"%s%s\")", tC.getNameSpace(), "res-{?1}"), context.getMapping().toString());
        Assert.assertNull(context.getFilter());

        PropertyBridge p1 = context.properties().filter(p -> tp1.equals(p.getTarget())).findFirst().orElseThrow(AssertionError::new);
        PropertyBridge p2 = context.properties().filter(p -> tp2.equals(p.getTarget())).findFirst().orElseThrow(AssertionError::new);
        PropertyBridge p3 = context.properties().filter(p -> tp3.equals(p.getTarget())).findFirst().orElseThrow(AssertionError::new);

        String propertyMappingFunc = String.format("spif:cast(?datatype=%s, ?arg1=%s)", m.shortForm(dt.getURI()), m.shortForm(sp1.getURI()));
        String filterMappingFuncTemplate = "sp:eq(?arg1=%s, ?arg2=sp:datatype(" + m.shortForm(sp1.getURI()) + "))";

        Assert.assertEquals(propertyMappingFunc, p1.getMapping().toString());
        Assert.assertEquals(String.format(filterMappingFuncTemplate, m.shortForm(sDT1.getURI())), p1.getFilter().toString());

        Assert.assertEquals(propertyMappingFunc, p2.getMapping().toString());
        Assert.assertEquals(String.format(filterMappingFuncTemplate, m.shortForm(sDT2.getURI())), p2.getFilter().toString());

        Assert.assertEquals(propertyMappingFunc, p3.getMapping().toString());
        Assert.assertEquals(String.format(filterMappingFuncTemplate, m.shortForm(sDT3.getURI())), p3.getFilter().toString());
    }

    private static <E extends OntEntity> E notNull(E e) {
        Assert.assertNotNull(e);
        return e;
    }
}

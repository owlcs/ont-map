/*
 * This file is part of the ONT MAP.
 * The contents of this file are subject to the Apache License, Version 2.0.
 * Copyright (c) 2019, The University of Manchester, owl.cs group.
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

package com.github.owlcs.map.utils;

import com.github.owlcs.map.MapFunction;
import com.github.owlcs.map.MapJenaException;
import com.github.owlcs.map.MapManager;
import com.github.owlcs.map.MapModel;
import com.github.owlcs.map.spin.*;
import com.github.owlcs.map.spin.vocabulary.SPINMAPL;
import com.github.owlcs.map.tests.ClassPropertiesTest;
import com.github.owlcs.ontapi.OntFormat;
import com.github.owlcs.ontapi.jena.OntModelFactory;
import com.github.owlcs.ontapi.jena.impl.OntObjectImpl;
import com.github.owlcs.ontapi.jena.impl.conf.OntModelConfig;
import com.github.owlcs.ontapi.jena.model.*;
import org.apache.jena.graph.Factory;
import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shared.PrefixMapping;
import org.junit.Assert;
import org.semanticweb.owlapi.io.OWLOntologyDocumentSource;
import org.semanticweb.owlapi.io.StringDocumentSource;
import org.semanticweb.owlapi.model.OWLDocumentFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import org.topbraid.spin.vocabulary.SPL;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Created by @szuev on 13.04.2018.
 */
@SuppressWarnings("WeakerAccess")
public class TestUtils {
    public static final PrefixMapping SPIN_MAP_PREFIXES = PrefixMapping.Factory.create()
            .setNsPrefixes(OntModelFactory.STANDARD)
            .setNsPrefix("sp", SP.NS)
            .setNsPrefix("spl", SPL.NS)
            .setNsPrefix("spin", SPIN.NS)
            .setNsPrefix("spinmap", SPINMAP.NS)
            .setNsPrefix("spinmapl", SPINMAPL.NS)
            .lock();

    private static final Logger LOGGER = LoggerFactory.getLogger(TestUtils.class);

    /**
     * Answers {@code true} if GeoSPARQL support is present in system.
     *
     * @return boolean
     */
    public static boolean withGeoSparql() {
        try {
            Class.forName("com.github.owlcs.map.spin.geos.GeoSInitExtension");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Creates a manager without any optimization.
     * For debugging.
     *
     * @param config {@link MapConfigImpl}, not {@code null}
     * @return {@link MapManager}
     * @see MapConfigImpl
     */
    public static MapManager withConfig(MapConfigImpl config) {
        return new MapManagerImpl(Factory.createGraphMem(),
                Factory::createGraphMem,
                new HashMap<>(),
                Objects.requireNonNull(config)) {
            @Override
            public String toString() {
                return String.format("TestManager[%s]", super.toString());
            }
        };
    }

    /**
     * Returns {@link MapConfig}.
     *
     * @param m {@link MapManager}, not {@code null}
     * @return {@link MapConfig}
     */
    public static MapConfig getMappingConfiguration(MapManager m) {
        return ((HasConfig) m).getMappingConfiguration();
    }

    /**
     * Creates an empty model for the given schema.
     *
     * @param m {@link OntModel}, not {@code null}
     * @return {@link OntModel}
     */
    public static OntModel forSchema(OntModel m) {
        return OntModelFactory.createModel().setNsPrefixes(m).addImport(m);
    }

    public static String asString(Model m) {
        StringWriter s = new StringWriter();
        m.write(s, "ttl");
        return s.toString();
    }

    public static void debug(Model m) {
        if (!LOGGER.isDebugEnabled()) return;
        LOGGER.debug("\n{}\n==========", TestUtils.asString(m));
    }

    public static void debug(MapModel m) {
        debug(m.asGraphModel());
    }

    public static void debug(MapFunction.Call func, PrefixMapping pm) {
        MapFunctionImpl.CallImpl call = (MapFunctionImpl.CallImpl) func;
        String name = pm.shortForm(call.getFunction().name());
        LOGGER.debug("Function: {}, Call: {}", name, call.toString(pm));
        call.asMap().forEach((arg, o) -> {
            String val = o instanceof String ? (String) o : ((MapFunction.Call) o).getFunction().name();
            LOGGER.debug("[{}] argument: {} => '{}'", name, pm.shortForm(arg.name()), pm.shortForm(val));
        });
    }

    public static Model getPrimaryGraph(MapManager manager) {
        return ModelFactory.createDefaultModel().setNsPrefixes(manager.prefixes())
                .add(ModelFactory.createModelForGraph(manager.getGraph()));
    }

    /**
     * Finds owl-entity by type and local name.
     * Priority for entities with the namespace matching ontology iri.
     * TODO: move to ONT-API Models ?
     *
     * @param m         {@link OntModel} for search in
     * @param type      Class
     * @param localName String local name, not null
     * @param <E>       subclass of {@link OntEntity}
     * @return OntEntity
     */
    public static <E extends OntEntity> E findOntEntity(OntModel m, Class<E> type, String localName) {
        Comparator<String> uriComparator = uriComparator(m.getID().getURI());
        return ontEntities(m, type)
                .filter(s -> s.getLocalName().equals(localName))
                .min((r1, r2) -> uriComparator.compare(r1.getNameSpace(), r2.getNameSpace()))
                .orElseThrow(() -> new AssertionError(String.format("Can't find [%s] <...#%s>",
                        OntObjectImpl.viewAsString(type), localName)));
    }

    @SuppressWarnings("unchecked")
    public static <E extends OntEntity> Stream<E> ontEntities(OntModel m, Class<E> type) {
        if (OntIndividual.Named.class == type) {
            return m.individuals().filter(RDFNode::isURIResource).map(x -> (E) x);
        }
        return m.ontEntities(type);
    }

    /**
     * To compare strings.
     * The strings that match the argument (@code first) have the highest priority, i.e. go first.
     *
     * @param first String
     * @return Comparator for Strings
     */
    public static Comparator<String> uriComparator(String first) {
        Comparator<String> res = Comparator.comparing(first::equals);
        return res.reversed().thenComparing(String::compareTo);
    }

    public static String getStringValue(OntIndividual i, OntDataProperty p) {
        return getLiteralValue(i, p, Literal::getString);
    }

    public static boolean getBooleanValue(OntIndividual i, OntDataProperty p) {
        return getLiteralValue(i, p, Literal::getBoolean);
    }

    public static double getDoubleValue(OntIndividual i, OntDataProperty p) {
        return getLiteralValue(i, p, Literal::getDouble);
    }

    public static <V> V getLiteralValue(OntIndividual i, OntDataProperty p, Function<Literal, V> map) {
        return i.statement(p).map(Statement::getObject).map(RDFNode::asLiteral).map(map).orElseThrow(AssertionError::new);
    }

    public static Stream<OntStatement> plainAssertions(OntModel m) {
        return m.ontObjects(OntProperty.class).filter(RDFNode::isURIResource)
                .map(p -> p.as(Property.class))
                .flatMap(p -> m.statements(null, p, null));
    }

    public static OntModel load(String file, Lang format) throws IOException {
        Graph g = Factory.createGraphMem();
        try (InputStream in = ClassPropertiesTest.class.getResourceAsStream(file)) {
            RDFDataMgr.read(g, in, null, format);
        }
        return OntModelFactory.createModel(g, OntModelConfig.ONT_PERSONALITY_LAX);
    }

    public static String toString(PrefixMapping pm, Statement s) {
        return String.format(s.getObject().isLiteral() ? "@%s <%s> '%s'" : "@%s <%s> @%s",
                toString(pm, s.getSubject()),
                pm.shortForm(s.getPredicate().getURI()),
                toString(pm, s.getObject()));
    }

    public static String toString(PrefixMapping pm, RDFNode r) {
        return r.isURIResource() ? pm.shortForm(r.asResource().getURI()) : r.isAnon() ? r.asResource().getId().toString() : r.toString();
    }

    public static OntModel createMapModel(String uri) {
        OntModel res = OntModelFactory.createModel(Factory.createGraphMem(), SpinModelConfig.ONT_LIB_PERSONALITY);
        res.setNsPrefixes(SPIN_MAP_PREFIXES);
        res.setID(uri).addImport(SPINMAPL.BASE_URI);
        return res;
    }

    public static OWLOntologyDocumentSource createTurtleDocumentSource(String txt) {
        return new StringDocumentSource(txt) {
            @Override
            public Optional<OWLDocumentFormat> getFormat() {
                return Optional.of(OntFormat.TURTLE.createOwlFormat());
            }
        };
    }

    public static void debug(MapJenaException j) {
        LOGGER.debug("Exception: {}", j.getMessage());
        Arrays.stream(j.getSuppressed()).forEach(e -> LOGGER.debug("Suppressed: {}", e.getMessage()));
    }

    public static void assertCode(Throwable j, Exceptions code) {
        Assert.assertTrue(j instanceof MapJenaException);
        debug((MapJenaException) j);
        Exceptions.SpinMapException s = (Exceptions.SpinMapException) j;
        Assert.assertEquals(code, s.getCode());
    }
}

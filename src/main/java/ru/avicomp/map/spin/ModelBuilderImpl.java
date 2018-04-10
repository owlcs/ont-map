package ru.avicomp.map.spin;

import org.apache.jena.graph.Factory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import ru.avicomp.map.*;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.spin.vocabulary.SP;
import ru.avicomp.map.spin.vocabulary.SPINMAP;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.model.OntCE;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntObject;
import ru.avicomp.ontapi.jena.model.OntPE;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * TODO: developing now.
 * Created by @szuev on 10.04.2018.
 */
public class ModelBuilderImpl implements ModelBuilder {
    private final MapManagerImpl manager;
    private Map<String, ClassBridge> contexts = new HashMap<>();
    private String iri;
    private static final String MAPPING_PREFIX = "mapping";
    private static final String CONTEXT_TEMPLATE = "Context-%s-%s";
    private static final String CONTEXT_DEFAULT_TEMPLATE = "Context-%s";
    private static final PrefixMapping PREFIXES = PrefixMapping.Factory.create()
            .setNsPrefixes(OntModelFactory.STANDARD)
            .setNsPrefix(SP.PREFIX, SP.NS)
            .setNsPrefix(SPINMAP.PREFIX, SPINMAP.NS)
            .lock();

    public ModelBuilderImpl(MapManagerImpl manager) {
        this.manager = Objects.requireNonNull(manager);
    }

    @Override
    public MapModel build() throws MapJenaException {
        UnionGraph g = new UnionGraph(Factory.createGraphMem());
        MapModelImpl res = new MapModelImpl(g, SpinModelConfig.SPIN_PERSONALITY) {

            @Override
            public MapManager getManager() {
                return manager;
            }
        };
        res.setNsPrefixes(PREFIXES);
        String ns = (iri == null ? AVC.BASE_URI : iri) + "#";
        res.setNsPrefix(MAPPING_PREFIX, ns);
        Resource ontology = res.createResource(iri).addProperty(RDF.type, OWL.Ontology);
        ontology.addProperty(OWL.imports, res.createResource(SPINMAPL.BASE_URI));

        for (String contextKey : contexts.keySet()) {
            ClassBridge bridge = contexts.get(contextKey);
            bridge.classes()
                    .map(OntObject::getModel)
                    .map(OntGraphModel::getID)
                    .forEach(id -> {
                        String uri = id.getURI();
                        ontology.addProperty(OWL.imports, res.createResource(uri));
                        g.addGraph(id.getModel().getGraph());
                        if (uri.contains("#")) return;
                        res.setNsPrefix(id.getLocalName(), uri + "#");
                    });

            OntCE src = bridge.source;
            OntCE dst = bridge.target;

            Resource context = res.createResource(ns + String.format(CONTEXT_TEMPLATE, getLocalName(src), getLocalName(dst)));
            if (res.containsResource(context)) {
                context = res.createResource(ns + String.format(CONTEXT_DEFAULT_TEMPLATE, UUID.randomUUID()));
            }
            context.addProperty(RDF.type, SPINMAP.Context);
            context.addProperty(SPINMAP.sourceClass, bridge.source);
            context.addProperty(SPINMAP.targetClass, bridge.target);
            Resource expression = res.createResource();
            Resource function = res.createResource(bridge.classRule.getFunction().name());
            expression.addProperty(RDF.type, function);
            // todo: process arguments
            context.addProperty(SPINMAP.target, expression);
            // todo: build rule. now it is only for testing
            Resource mapping01 = res.createResource().addProperty(RDF.type, SPINMAP.Mapping_0_1);
            src.inModel(res).addProperty(SPINMAP.rule, mapping01);
            mapping01.addProperty(SPINMAP.context, context);
            mapping01.addProperty(SPINMAP.expression, dst);
            mapping01.addProperty(SPINMAP.targetPredicate1, RDF.type);
        }
        // todo:
        return res;
    }

    @Override
    public ModelBuilder addName(String iri) {
        this.iri = iri;
        return this;
    }

    @Override
    public Context addClassBridge(OntCE src, OntCE dst, MapFunction.Call rule) {
        return contexts.computeIfAbsent(toStringID(src, dst), s -> new ClassBridge(src, dst, rule));
    }

    public class ClassBridge implements Context {
        private final OntCE source, target;
        private final MapFunction.Call classRule;
        private Map<String, PropertyBridge> properties = new HashMap<>();

        public ClassBridge(OntCE source, OntCE target, MapFunction.Call rule) {
            this.source = source;
            this.target = target;
            this.classRule = rule;
        }

        public Stream<OntCE> classes() {
            return Stream.of(source, target);
        }

        @Override
        public <P extends Property & OntPE> Context addPropertyBridge(P src, P dst, MapFunction.Call rule) {
            properties.computeIfAbsent(toStringID(src, dst), s -> new PropertyBridge(src, dst, rule));
            return this;
        }

        @Override
        public ModelBuilder back() {
            return ModelBuilderImpl.this;
        }

        public class PropertyBridge {
            private final OntPE sourceProperty, targetProperty;
            private final MapFunction.Call propertyRule;

            public PropertyBridge(OntPE sourceProperty, OntPE targetProperty, MapFunction.Call propertyRule) {
                this.sourceProperty = sourceProperty;
                this.targetProperty = targetProperty;
                this.propertyRule = propertyRule;
            }
        }
    }

    public static String toStringID(OntObject... objects) {
        return Arrays.stream(objects).map(ModelBuilderImpl::toStringID).collect(Collectors.joining("-"));
    }

    public static String toStringID(OntObject o) {
        return o.toString();
    }

    public static String getLocalName(Resource resource) {
        return resource.isURIResource() ? resource.getLocalName() : resource.getId().getLabelString();
    }
}

package ru.avicomp.map.spin;

import org.apache.jena.graph.Factory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.PrefixMapping;
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
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * TODO: developing now.
 * Created by @szuev on 10.04.2018.
 */
@Deprecated
public class ModelBuilderImpl implements ModelBuilder {
    private final MapManagerImpl manager;
    private Map<String, ClassBridge> contexts = new HashMap<>();
    private String iri;
    private static final String MAPPING_PREFIX = "mapping";
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
        res.getOntology(iri);
        res.addImport(SPINMAPL.BASE_URI);

        for (String contextKey : contexts.keySet()) {
            ClassBridge bridge = contexts.get(contextKey);
            MapFunction.Call rule = bridge.classRule.build();
            if (!rule.getFunction().isTarget()) {
                // TODO:
                throw new MapJenaException();
            }
            bridge.classes()
                    .map(OntObject::getModel)
                    .map(OntGraphModel::getID)
                    .forEach(id -> res.addImport(id, true));

            OntCE src = bridge.source;
            OntCE dst = bridge.target;

            Resource context = res.getContext(src, dst);
            Resource expression = buildExpression(res, rule);
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

    /**
     * todo: not ready.
     * <pre>{@code
     * [ a  spinmapl:buildURI2 ;
     *      sp:arg1            people:secondName ;
     *      sp:arg2            people:firstName ;
     *      spinmap:source     spinmap:_source ;
     *      spinmapl:template  "beings:Being-{?1}-{?2}"
     *  ] ;
     *  }</pre>
     *
     * @param model
     * @param func
     * @return
     */
    private static Resource buildExpression(MapModelImpl model, MapFunction.Call func) {
        Resource res = model.createResource();
        Resource function = model.createResource(func.getFunction().name());
        res.addProperty(RDF.type, function);
        return res;
    }


    @Override
    public ModelBuilder addName(String iri) {
        this.iri = iri;
        return this;
    }

    @Override
    public Context addClassBridge(OntCE src, OntCE dst, FunctionBuilder rule) {
        return contexts.computeIfAbsent(toStringID(src, dst), s -> new ClassBridge(src, dst, rule));
    }

    public class ClassBridge implements Context {
        private final OntCE source, target;
        private final FunctionBuilder classRule;
        private Map<String, PropertyBridge> properties = new HashMap<>();

        public ClassBridge(OntCE source, OntCE target, FunctionBuilder rule) {
            this.source = source;
            this.target = target;
            this.classRule = rule;
        }

        public Stream<OntCE> classes() {
            return Stream.of(source, target);
        }

        @Override
        public <P extends Property & OntPE> Context addPropertyBridge(P src, P dst, FunctionBuilder rule) {
            properties.computeIfAbsent(toStringID(src, dst), s -> new PropertyBridge(src, dst, rule));
            return this;
        }

        @Override
        public ModelBuilder back() {
            return ModelBuilderImpl.this;
        }

        public class PropertyBridge {
            private final OntPE sourceProperty, targetProperty;
            private final FunctionBuilder propertyRule;

            public PropertyBridge(OntPE sourceProperty, OntPE targetProperty, FunctionBuilder propertyRule) {
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

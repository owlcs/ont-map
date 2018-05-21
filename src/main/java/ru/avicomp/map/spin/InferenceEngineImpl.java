package ru.avicomp.map.spin;

import org.apache.jena.enhanced.BuiltinPersonalities;
import org.apache.jena.graph.Factory;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.GraphEventManager;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.impl.ModelCom;
import org.apache.jena.sparql.util.graph.GraphListenerBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.inference.DefaultSPINRuleComparator;
import org.topbraid.spin.inference.SPINExplanations;
import org.topbraid.spin.inference.SPINInferences;
import org.topbraid.spin.inference.SPINRuleComparator;
import org.topbraid.spin.progress.NullProgressMonitor;
import org.topbraid.spin.progress.ProgressMonitor;
import org.topbraid.spin.progress.SimpleProgressMonitor;
import org.topbraid.spin.util.CommandWrapper;
import org.topbraid.spin.util.SPINQueryFinder;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.MapJenaException;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.MapModel;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.utils.Graphs;

import java.util.*;

/**
 * Impl of inference-engine.
 */
@SuppressWarnings("WeakerAccess")
public class InferenceEngineImpl implements MapManager.InferenceEngine {
    static {
        // Warning: Jena stupidly allows to modify global personality (org.apache.jena.enhanced.BuiltinPersonalities#model),
        // what does SPIN API, which, also, implicitly requires that patched version everywhere.
        // It may be dangerous, increases the system load and may impact other jena-based tools.
        // but I don't think there is an easy good workaround, so it's better to put up with that modifying.
        SpinModelConfig.init(BuiltinPersonalities.model);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(InferenceEngineImpl.class);

    protected final MapManagerImpl manager;

    public InferenceEngineImpl(MapManagerImpl manager) {
        this.manager = manager;
    }

    @Override
    public void run(MapModel mapping, Graph source, Graph target) throws MapJenaException {
        // Reassembly a union graph (just in case, it should already contain everything needed):
        UnionGraph union = new UnionGraph(Factory.createGraphMem());
        // pass prefixes:
        union.getPrefixMapping().setNsPrefixes(mapping.asOntModel());
        // add everything from mapping:
        Graphs.flat(mapping.asOntModel().getGraph()).forEach(union::addGraph);
        // add everything from source:
        Graphs.flat(source).forEach(union::addGraph);
        // all from library with except of avc (also, just in case):
        Graphs.flat(manager.getMapLibraryGraph()).forEach(union::addGraph);

        Model s = SpinModelConfig.createSpinModel(union);
        Model t = new ModelCom(target);
        DebugLogListener logs = new DebugLogListener();
        GraphEventManager events = target.getEventManager();
        try {
            if (LOGGER.isDebugEnabled())
                events.register(logs);
            process(s, t, getMapComparator(s), getProgressMonitor(mapping));
        } finally {
            events.unregister(logs);
        }
    }

    public static ProgressMonitor getProgressMonitor(MapModel mapping) {
        final String name;
        return LOGGER.isDebugEnabled() ? new SimpleProgressMonitor(name = mapping.getID().getURI()) {
            @Override
            protected void println(String text) {
                LOGGER.debug("{}: {}", name, text);
            }
        } : new NullProgressMonitor();
    }

    /**
     * Creates a rule ({@code spin:rule}) comparator with special comparing order:
     * the class declaration map rules ({@code spinmap:rule}) go first,
     * the named individual declaration map rules go last,
     * all the rest in the middle,
     * the order for none-map rules is default, i.e. comparing by predicate and string representation of {@link CommandWrapper}s.
     * todo: will be changed: comparing main rule by target-source context classes
     *
     * @param model {@link Model} with rules, not null
     * @return {@link SPINRuleComparator} comparator
     */
    public static SPINRuleComparator getMapComparator(Model model) {
        Comparator<CommandWrapper> defaultComparator = new DefaultSPINRuleComparator(model);
        Comparator<Resource> mapRuleComparator = Comparator.comparing(SpinModels::isNamedIndividualSelfMapping)
                .thenComparing(Comparator.comparing(SpinModels::isDeclarationMapping).reversed())
                .thenComparing(r -> SpinModels.context(r).map(Resource::getURI).orElse(null));
        Comparator<CommandWrapper> res = (left, right) -> {
            Optional<Resource> r1 = SpinModels.rule(left);
            Optional<Resource> r2 = SpinModels.rule(right);
            return r1.isPresent() && r2.isPresent() ? mapRuleComparator.compare(r1.get(), r2.get()) : defaultComparator.compare(left, right);
        };
        return res::compare;
    }

    /**
     * Runs spin-inference on the specified mapping model and stores result to other specified model.
     * All rules are processed only once.
     *
     * @param mapping    {@link Model} with {@code spin:rule}
     * @param result     {@link Model} to store result
     * @param comparator {@link SPINRuleComparator} rule comparator
     * @param logs       {@link ProgressMonitor} to log
     * @see SPINInferences#run(Model, Property, Model, SPINExplanations, List, boolean, ProgressMonitor)
     */
    public static void process(Model mapping, Model result, SPINRuleComparator comparator, ProgressMonitor logs) {
        Map<Resource, List<CommandWrapper>> cls2Query = SPINQueryFinder.getClass2QueryMap(mapping, mapping, SPINMAP.rule, true, false);
        Map<Resource, List<CommandWrapper>> cls2Constructor =
                Collections.emptyMap();
        //SPINQueryFinder.getClass2QueryMap(mapping, mapping, SPIN.constructor, true, false);
        SPINInferences.run(mapping, result, cls2Query, cls2Constructor, null, null, true, SPINMAP.rule, comparator, logs);
    }

    public static class DebugLogListener extends GraphListenerBase {

        @Override
        public void notifyAddGraph(Graph g, Graph other) {
            other.find(Triple.ANY).forEachRemaining(this::addEvent);
        }

        @Override
        public void notifyDeleteGraph(Graph g, Graph other) {
            other.find(Triple.ANY).forEachRemaining(this::deleteEvent);
        }

        @Override
        protected void addEvent(Triple t) {
            LOGGER.debug("ADD: {}", t);
        }

        @Override
        protected void deleteEvent(Triple t) {
            LOGGER.debug("DELETE: {}", t);
        }
    }
}

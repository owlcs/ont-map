package ru.avicomp.map.spin;

import org.apache.jena.enhanced.BuiltinPersonalities;
import org.apache.jena.graph.Factory;
import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.impl.ModelCom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.inference.SPINInferences;
import org.topbraid.spin.progress.NullProgressMonitor;
import org.topbraid.spin.progress.ProgressMonitor;
import org.topbraid.spin.progress.SimpleProgressMonitor;
import ru.avicomp.map.MapJenaException;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.MapModel;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.utils.Graphs;

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

    private final MapManagerImpl manager;

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

        ProgressMonitor logs = createProgressMonitor(mapping);
        // todo: SPINRuleComparator to ensure classes-bridges go before property-btidges
        SPINInferences.run(s, t, null, null, false, logs);
    }

    protected ProgressMonitor createProgressMonitor(MapModel model) {
        final String name;
        return LOGGER.isDebugEnabled() ? new SimpleProgressMonitor(name = model.getID().getURI()) {
            @Override
            protected void println(String text) {
                LOGGER.debug("{}: {}", name, text);
            }
        } : new NullProgressMonitor();
    }
}

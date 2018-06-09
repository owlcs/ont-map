package ru.avicomp.map.tools;

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.topbraid.spin.vocabulary.SPL;
import ru.avicomp.map.spin.MapManagerImpl;
import ru.avicomp.map.spin.SpinModelConfig;
import ru.avicomp.map.spin.SystemModels;
import ru.avicomp.map.utils.AutoPrefixListener;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.impl.conf.OntModelConfig;
import ru.avicomp.ontapi.jena.impl.conf.OntPersonality;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.utils.Graphs;

/**
 * Created by @szuev on 09.06.2018.
 */
abstract class LibraryMaker {
    static Resource createConstraint(Model m, Property predicate, Resource returnType) {
        return m.createResource().addProperty(SPL.predicate, predicate).addProperty(SPL.valueType, returnType);
    }

    static OntGraphModel createModel(Graph graph) {
        OntPersonality p = OntModelConfig.ONT_PERSONALITY_BUILDER.build(SpinModelConfig.LIB_PERSONALITY, OntModelConfig.StdMode.LAX);
        OntGraphModel res = OntModelFactory.createModel(graph, p);
        AutoPrefixListener.addAutoPrefixListener((UnionGraph) res.getGraph(), MapManagerImpl.collectPrefixes(SystemModels.graphs().values()));
        return res;
    }

    static Graph getSPLGraph() {
        return Graphs.toUnion(SystemModels.get(SystemModels.Resources.SPL), SystemModels.graphs().values());
    }

    static Graph getSPINMAPGraph() {
        return Graphs.toUnion(SystemModels.get(SystemModels.Resources.SPINMAP), SystemModels.graphs().values());
    }

    static Graph getAVCGraph() {
        return Graphs.toUnion(SystemModels.get(SystemModels.Resources.AVC), SystemModels.graphs().values());
    }
}

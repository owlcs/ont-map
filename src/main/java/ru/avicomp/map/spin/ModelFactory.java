package ru.avicomp.map.spin;

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.impl.ModelCom;

/**
 * A factory to produce different kinds of {@link Model jena model}s
 * Created by @szuev on 09.04.2018.
 *
 * @see ru.avicomp.ontapi.jena.OntModelFactory
 * @see org.apache.jena.rdf.model.ModelFactory
 */
public class ModelFactory {

    public static Model createModel(Graph graph) {
        return new ModelCom(graph, SpinModelConfig.SPIN_PERSONALITY);
    }
}

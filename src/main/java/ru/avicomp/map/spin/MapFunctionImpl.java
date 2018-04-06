package ru.avicomp.map.spin;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.impl.ModelCom;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.spin.vocabulary.SP;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.utils.Graphs;

/**
 * Created by @szuev on 06.04.2018.
 */
public class MapFunctionImpl implements MapFunction {

    public static Model createLibraryModel() {
        UnionGraph g = Graphs.toUnion(SystemModels.graphs().get(SystemModels.Resources.SPINMAPL.getURI()), SystemModels.graphs().values());
        return new ModelCom(g, SP.SPIN_PERSONALITY);
    }
}

package ru.avicomp.map.spin;

import org.apache.jena.enhanced.Personality;
import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.impl.ModelCom;
import ru.avicomp.map.MapModel;

/**
 * Created by @szuev on 10.04.2018.
 */
public abstract class MapModelImpl extends ModelCom implements MapModel {

    public MapModelImpl(Graph base, Personality<RDFNode> personality) {
        super(base, personality);
    }
}

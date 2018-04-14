package ru.avicomp.map.spin.impl;

import org.apache.jena.enhanced.EnhGraph;
import org.apache.jena.graph.Node;
import org.topbraid.spin.model.impl.FunctionImpl;
import ru.avicomp.map.spin.model.MapTargetFunction;

/**
 * An implementation for {@code _:x rdf:type spinmap:TargetFunction}.
 * <p>
 * Created by @szuev on 07.04.2018.
 */
public class MapTargetFunctionImpl extends FunctionImpl implements MapTargetFunction {
    public MapTargetFunctionImpl(Node node, EnhGraph eg) {
        super(node, eg);
    }
}

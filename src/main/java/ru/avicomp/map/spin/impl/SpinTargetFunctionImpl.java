package ru.avicomp.map.spin.impl;

import org.apache.jena.enhanced.EnhGraph;
import org.apache.jena.graph.Node;
import org.topbraid.spin.model.impl.FunctionImpl;
import ru.avicomp.map.spin.model.SpinTargetFunction;

/**
 * An implementation for {@code _:x rdf:type spinmap:TargetFunction}.
 * <p>
 * Created by @szuev on 07.04.2018.
 */
public class SpinTargetFunctionImpl extends FunctionImpl implements SpinTargetFunction {
    public SpinTargetFunctionImpl(Node node, EnhGraph eg) {
        super(node, eg);
    }
}

package ru.avicomp.map.spin;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.topbraid.spin.vocabulary.SP;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapJenaException;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Created by @szuev on 21.05.2018.
 *
 * @see ru.avicomp.map.spin.vocabulary.AVC#groupConcat
 */
public class AdjustGroupConcatImpl implements AdjustFunctionBody {
    @Override
    public Boolean apply(Resource functionInModel, MapFunction.Call call) {
        Model m = MapJenaException.notNull(functionInModel, "Null function expression").getModel();
        MapFunction.Arg arg = call.getFunction().getArg(SPINMAPL.separator.getURI());
        String value = (String) MapJenaException.notNull(call.asMap().get(arg), "Null separator");
        List<Statement> prev = SpinModels.getFunctionBody(m, functionInModel)
                .stream()
                .filter(s -> Objects.equals(s.getPredicate(), SP.separator))
                .filter(s -> s.getObject().isLiteral())
                .collect(Collectors.toList());
        if (prev.size() != 1) throw new IllegalStateException("Expected single sp:separator literal inside expression");
        Statement s = prev.get(0);
        if (value.equals(s.getObject().asLiteral().getString())) return false;
        m.add(s.getSubject(), s.getPredicate(), value);
        m.remove(s);
        return true;
    }
}

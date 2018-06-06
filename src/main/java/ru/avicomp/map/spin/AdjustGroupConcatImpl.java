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
 * An implementation to adjust {@code avc:groupConcat} function body.
 * Created by @szuev on 21.05.2018.
 *
 * @see ru.avicomp.map.spin.vocabulary.AVC#groupConcat
 */
public class AdjustGroupConcatImpl implements AdjustFunctionBody {

    /**
     * Injects separator directly to group-concat function.
     *
     * @param model {@link Model}
     * @param call  {@link ru.avicomp.map.MapFunction.Call}
     * @return boolean
     * @throws MapJenaException in case something is wrong
     */
    @Override
    public Boolean apply(Model model, MapFunction.Call call) throws MapJenaException {
        MapFunction function = MapJenaException.notNull(call, "Null function call specified").getFunction();
        Resource resource = MapJenaException.notNull(model, "Null model specified").getResource(function.name());
        MapFunction.Arg arg = function.getArg(SPINMAPL.separator.getURI());
        Object value = call.get(arg);
        if (!(value instanceof String)) {
            throw new MapJenaException("Null or wrong value for separator");
        }
        String separator = (String) value;
        List<Statement> prev = SpinModels.getFunctionBody(model, resource)
                .stream()
                .filter(s -> Objects.equals(s.getPredicate(), SP.separator))
                .filter(s -> s.getObject().isLiteral())
                .collect(Collectors.toList());
        if (prev.size() != 1) throw new MapJenaException("Expected single sp:separator literal inside expression");
        Statement s = prev.get(0);
        if (separator.equals(s.getObject().asLiteral().getString())) return false;
        model.add(s.getSubject(), s.getPredicate(), separator).remove(s);
        return true;
    }
}

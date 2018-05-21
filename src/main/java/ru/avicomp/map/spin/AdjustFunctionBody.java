package ru.avicomp.map.spin;

import org.apache.jena.rdf.model.Resource;
import ru.avicomp.map.MapFunction;

import java.util.function.BiFunction;

/**
 * todo: description
 * Created by @szuev on 21.05.2018.
 *
 * @see ru.avicomp.map.spin.vocabulary.AVC#runtime
 */
public interface AdjustFunctionBody extends BiFunction<Resource, MapFunction.Call, Boolean> {

    Boolean apply(Resource functionInModel, MapFunction.Call args);

}

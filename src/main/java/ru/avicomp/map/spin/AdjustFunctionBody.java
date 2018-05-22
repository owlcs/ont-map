package ru.avicomp.map.spin;

import org.apache.jena.rdf.model.Model;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapJenaException;

import java.util.function.BiFunction;

/**
 * A common interface to allow accepting MapFunction arguments directly into function body,
 * not only in form of function-call expression.
 * Created by @szuev on 21.05.2018.
 *
 * @see ru.avicomp.map.spin.vocabulary.AVC#runtime
 */
public interface AdjustFunctionBody extends BiFunction<Model, MapFunction.Call, Boolean> {

    /**
     * Modifies a spin function body.
     *
     * @param model {@link Model} model containing spin-function body
     * @param args  {@link MapFunction.Call} function call to get arguments
     * @return true if map model graph has been changed
     * @throws MapJenaException if something is wrong with arguments or during operation
     */
    Boolean apply(Model model, MapFunction.Call args) throws MapJenaException;

}

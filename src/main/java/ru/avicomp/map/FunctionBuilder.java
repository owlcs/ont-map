package ru.avicomp.map;

/**
 * To build {@link MapFunction.Call function-call}.
 * <p>
 * Created by @szuev on 10.04.2018.
 */
public interface FunctionBuilder extends Builder<MapFunction.Call> {

    FunctionBuilder add(String predicate, String value) throws MapJenaException;

    FunctionBuilder add(String predicate, FunctionBuilder other) throws MapJenaException;

    MapFunction getFunction();

    default FunctionBuilder add(MapFunction.Arg arg, MapFunction.Call function) throws MapJenaException {
        return add(arg.name(), function.asUnmodifiableBuilder());
    }


}

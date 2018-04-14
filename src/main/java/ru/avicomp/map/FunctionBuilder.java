package ru.avicomp.map;

/**
 * To build {@link MapFunction.Call function-call}.
 * <p>
 * Created by @szuev on 10.04.2018.
 */
public interface FunctionBuilder extends Builder<MapFunction.Call> {

    FunctionBuilder add(MapFunction.Arg arg, String value) throws MapJenaException;

    FunctionBuilder add(MapFunction.Arg arg, FunctionBuilder other) throws MapJenaException;

    MapFunction getFunction();

    default FunctionBuilder add(MapFunction.Arg arg, MapFunction.Call function) throws MapJenaException {
        return add(arg, function.asUnmodifiableBuilder());
    }

    default FunctionBuilder add(String arg, String value) throws MapJenaException {
        return add(getFunction().getArg(arg), value);
    }

    default FunctionBuilder add(String arg, FunctionBuilder other) throws MapJenaException {
        return add(getFunction().getArg(arg), other);
    }
}

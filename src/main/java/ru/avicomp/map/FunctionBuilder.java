package ru.avicomp.map;

/**
 * To build {@link MapFunction.Call function-call}.
 * <p>
 * Created by @szuev on 10.04.2018.
 */
public interface FunctionBuilder {
    FunctionBuilder add(MapFunction.Arg arg, Object value);

    MapFunction.Call build();
}

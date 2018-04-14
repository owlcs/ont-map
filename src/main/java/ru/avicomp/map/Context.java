package ru.avicomp.map;

import ru.avicomp.ontapi.jena.model.OntCE;

/**
 * Created by @szuev on 14.04.2018.
 */
public interface Context {

    OntCE getSource();

    OntCE getTarget();

    Context addExpression(MapFunction.Call func) throws MapJenaException;

    /**
     * Validates a function-call against contains.
     *
     * @param func {@link MapFunction.Call} an expression.
     * @throws MapJenaException if something is wrong with function, e.g. wrong argument types.
     */
    void validate(MapFunction.Call func) throws MapJenaException;
}

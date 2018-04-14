package ru.avicomp.map;

import ru.avicomp.ontapi.jena.model.OntCE;

/**
 * Created by @szuev on 14.04.2018.
 */
public interface Context {

    OntCE getSource();

    OntCE getTarget();

    Context addExpression(MapFunction.Call func) throws MapJenaException;
}

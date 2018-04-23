package ru.avicomp.map;

import org.apache.jena.rdf.model.Property;
import ru.avicomp.ontapi.jena.model.OntCE;

import java.util.stream.Stream;

/**
 * A class expression binding, a primary component of mapping.
 * <p>
 * Created by @szuev on 14.04.2018.
 */
public interface Context {

    /**
     * Returns a source class expression.
     *
     * @return {@link OntCE}
     */
    OntCE getSource();

    /**
     * Returns a target class expression.
     *
     * @return {@link OntCE}
     */
    OntCE getTarget();

    /**
     * Answers a context' expression in form of function-call object.
     * A valid (ready to use) context must have one and only one target expression
     * (i.e {@code MapFunction#isTarget() == true}).
     *
     * @return {@link MapFunction.Call} or null in case context are not fully completed
     */
    MapFunction.Call getExpression();

    /**
     * Adds a primary rule to bind two class expressions.
     * If some rule is already present in the context, it will be replaced by the specified one.
     *
     * @param func {@link MapFunction.Call} a function call
     * @return this context object
     * @throws MapJenaException if something goes wrong (e.g. incompatible function specified)
     */
    Context addExpression(MapFunction.Call func) throws MapJenaException;

    /**
     * Adds a properties bridge. Source properties are specified by function call, the target goes explicitly.
     * All input properties must be an OWL2 entities with possible to assign data on individual,
     * i.e. named annotation and datatype property expressions.
     *
     * @param func   {@link ru.avicomp.map.MapFunction.Call}, expression
     * @param target property, either {@link ru.avicomp.ontapi.jena.model.OntNAP} or {@link ru.avicomp.ontapi.jena.model.OntNDP}
     * @return {@link PropertyBridge} a container with all input settings.
     * @throws MapJenaException if something goes wrong (e.g. incompatible function or property specified)
     */
    PropertyBridge addPropertyBridge(MapFunction.Call func, Property target) throws MapJenaException;

    /**
     * Lists all property bindings.
     *
     * @return Stream of {@link PropertyBridge}
     */

    Stream<PropertyBridge> properties();

    /**
     * Removes a property binding from this context.
     *
     * @param properties {@link PropertyBridge}
     * @return this context
     */
    Context removeProperties(PropertyBridge properties);

    /**
     * Validates a function-call against this contains.
     *
     * @param func {@link MapFunction.Call} an expression.
     * @throws MapJenaException if something is wrong with function, e.g. wrong argument types.
     */
    void validate(MapFunction.Call func) throws MapJenaException;

    /**
     * Answers iff this context is valid for (SPIN-)inference.
     *
     * @return boolean
     */
    default boolean isValid() {
        return getExpression() != null;
    }
}

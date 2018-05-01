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
     * Adds a properties bridge.
     * Source properties are specified by mapping and filter function calls, the target goes explicitly.
     * All input properties must be an OWL2 entities with possible to assign data on individual:
     * {@link ru.avicomp.ontapi.jena.model.OntNAP named annotation property} and
     * {@link ru.avicomp.ontapi.jena.model.OntNDP datatype property},
     * while {@link ru.avicomp.ontapi.jena.model.OntNOP object property} is used to bind contexts together, not to inference data.
     *
     * @param filterFunctionCall  {@link MapFunction.Call} function-call to filter data
     * @param mappingFunctionCall {@link MapFunction.Call} function-call to map data
     * @param target              property, either {@link ru.avicomp.ontapi.jena.model.OntNAP} or {@link ru.avicomp.ontapi.jena.model.OntNDP}
     * @return {@link PropertyBridge} a container with all input settings.
     * @throws MapJenaException if something goes wrong (e.g. incompatible function or property specified)
     */
    PropertyBridge addPropertyBridge(MapFunction.Call filterFunctionCall, MapFunction.Call mappingFunctionCall, Property target) throws MapJenaException;

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
     * Creates a context associated with this one through an object property expression.
     * It means that one class should have object property range axiom {@code P rdfs:domain C1},
     * and another should have object property domain axiom {@code P rdfs:range C2}.
     * Where {@code P} is an object property expression,
     * {@code C1} and {@code C2} - class property expressions.
     * If {@code C1} is the source of this context, and {@code C2} is the specified class,
     * then new context will be created with "related subject context" rule, otherwise with "related object context" rule.
     * If {@code C1} and {@code C2} are not linked to each other, an exception are expected.
     *
     * @param source {@link OntCE} source for the new context, the target is the same as in this context.
     * @return <b>new</b> context
     * @throws MapJenaException unable to make reference context
     */
    Context createRelatedContext(OntCE source) throws MapJenaException;

    /**
     * Adds a properties bridge without filtering.
     * This is commonly used method, while its overloaded companion is used less frequently to build specific ontology mappings.
     *
     * @param func   {@link ru.avicomp.map.MapFunction.Call}, expression
     * @param target property, either {@link ru.avicomp.ontapi.jena.model.OntNAP} or {@link ru.avicomp.ontapi.jena.model.OntNDP}
     * @return {@link PropertyBridge} a container with all input settings.
     * @throws MapJenaException if something goes wrong (e.g. incompatible function or property specified)
     */
    default PropertyBridge addPropertyBridge(MapFunction.Call func, Property target) throws MapJenaException {
        return addPropertyBridge(null, func, target);
    }

    /**
     * Validates a function-call against this context.
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

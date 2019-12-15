/*
 * This file is part of the ONT MAP.
 * The contents of this file are subject to the Apache License, Version 2.0.
 * Copyright (c) 2019, The University of Manchester, owl.cs group.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.owlcs.map;

import com.github.owlcs.ontapi.jena.model.OntClass;
import com.github.owlcs.ontapi.jena.model.OntObjectProperty;
import org.apache.jena.rdf.model.Property;

import java.util.stream.Stream;

/**
 * A class expressions mapping (class bridge), that is a primary component of a {@link MapModel mapping model}.
 * In GUI it can be represented as an arrow between source and target {@link OntClass class expression}s.
 * <p>
 * Created by @szuev on 14.04.2018.
 */
public interface MapContext extends MapResource {

    /**
     * Returns a source class expression.
     *
     * @return {@link OntClass}
     */
    OntClass getSource();

    /**
     * Returns a target class expression.
     *
     * @return {@link OntClass}
     */
    OntClass getTarget();

    /**
     * Answers a context' expression in the form of function-call object.
     * A valid (ready to use) context must have one and only one mapping function,
     * that must be a target (i.e. {@code this.getMapping().isTarget() = true}).
     *
     * @return {@link MapFunction.Call} or {@code null} in case the context are not fully completed
     * @see #isValid()
     * @see MapFunction#isTarget()
     */
    @Override
    MapFunction.Call getMapping();

    /**
     * Creates a bridge between classes using filter and mapping function calls.
     * The filter function call must be boolean (see {@link MapFunction#isBoolean()}.
     * The mapping function call must be target (see {@link MapFunction#isTarget()}.
     * This method deletes any previously added class connections,
     * but if the context contains any {@link #properties() property bridges} they stay untouched.
     *
     * @param filter  {@link MapFunction.Call}, a boolean function call to filter source individuals. Can be null
     * @param mapping {@link MapFunction.Call}, a target function call to map source individual to target. Not null
     * @return this context
     * @throws MapJenaException if something goes wrong
     */
    MapContext addClassBridge(MapFunction.Call filter, MapFunction.Call mapping) throws MapJenaException;

    /**
     * Adds a properties bridge.
     * Source properties are specified by mapping and filter function calls, the target goes explicitly.
     * All input properties must be an OWL2 entities with possible to assign data on individual:
     * {@link com.github.owlcs.ontapi.jena.model.OntAnnotationProperty} and
     * {@link com.github.owlcs.ontapi.jena.model.OntDataProperty},
     * while {@link com.github.owlcs.ontapi.jena.model.OntObjectProperty.Named}
     * is used to bind contexts together, not to inference data.
     *
     * @param filterFunctionCall  {@link MapFunction.Call} function-call to filter data
     * @param mappingFunctionCall {@link MapFunction.Call} function-call to map data
     * @param target              property, either {@link com.github.owlcs.ontapi.jena.model.OntAnnotationProperty}
     *                            or {@link com.github.owlcs.ontapi.jena.model.OntDataProperty}
     * @return {@link PropertyBridge} a container with all input settings.
     * @throws MapJenaException if something goes wrong (e.g. incompatible function or property specified)
     */
    PropertyBridge addPropertyBridge(MapFunction.Call filterFunctionCall,
                                     MapFunction.Call mappingFunctionCall,
                                     Property target) throws MapJenaException;

    /**
     * Lists all property bridges.
     *
     * @return {@code Stream} of {@link PropertyBridge}s
     */
    Stream<PropertyBridge> properties();

    /**
     * Deletes a property binding from this context.
     *
     * @param properties {@link PropertyBridge}
     * @return this context
     * @throws MapJenaException in case specified property bridge does not belong to this context
     */
    MapContext deletePropertyBridge(PropertyBridge properties) throws MapJenaException;

    /**
     * Creates a context with the same target class expression and with source linked to the source of this context.
     * Classes can be linked to each other through object property range and domain axioms,
     * see description for {@link #createRelatedContext(OntClass, OntObjectProperty)}.
     * Throws an exception in the case the link (object property) does not exist or it can not be uniquely determined.
     *
     * @param source {@link OntClass} source for the new context, the target is the same as in this context.
     * @return <b>new</b> context
     * @throws MapJenaException unable to make reference context
     */
    MapContext createRelatedContext(OntClass source) throws MapJenaException;

    /**
     * Creates a context associated with this one through the specified object property expression.
     * The object property acts as a link between source class expressions from this context and the returned new context.
     * This means that one class should have object property range axiom {@code P rdfs:domain C1},
     * and another should have object property domain axiom {@code P rdfs:range C2}.
     * Here {@code P} is an object property expression and {@code C1} and {@code C2} - class expressions.
     * If {@code C1} is the source of this context, and {@code C2} is the specified class,
     * then new context will be created with "related subject context" rule,
     * otherwise with "related object context" rule.
     * If {@code C1} and {@code C2} are not linked to each other, an exception are expected.
     *
     * @param source {@link OntClass} class expression, source for result context
     * @param link   {@link OntObjectProperty} object property expression, a link between {@code source} and {@link #getSource()}
     * @return <b>new</b> context with specified class as source and {@link #getTarget()} as target.
     * @throws MapJenaException unable to make reference context
     */
    MapContext createRelatedContext(OntClass source, OntObjectProperty link) throws MapJenaException;

    /**
     * Binds this context and the specified with a link-property.
     * On inference these contexts together will produce consistent data:
     * the result individuals will be bound in an object property assertion.
     *
     * @param other {@link MapContext} other context
     * @param link  {@link OntObjectProperty}, link property
     * @return {@link PropertyBridge} which connects this context and the specified
     * @throws MapJenaException if something goes wrong or input parameters are not correct
     */
    PropertyBridge attachContext(MapContext other, OntObjectProperty link) throws MapJenaException;

    /**
     * Lists all contexts that depend on this somehow.
     *
     * @return {@code Stream} of contexts
     */
    Stream<MapContext> dependentContexts();

    /**
     * Validates the specified function-call against this context.
     * If no exception occurs, then the function is considered as good enough to be used in a context.
     *
     * @param func {@link MapFunction.Call} a function-call to test, not {@code null}
     * @throws MapJenaException if something is wrong with the function, e.g. wrong argument types
     * @see MapModel#validate(MapFunction.Call)
     */
    void validate(MapFunction.Call func) throws MapJenaException;

    /**
     * Answers a context name,
     * that uniquely identifies it in the {@link MapModel mapping model}.
     * Also, ir is expected that the name equals to IRI of the corresponding {@link #asResource() resource}.
     *
     * @return String, not {@code null}
     */
    default String name() {
        return asResource().getURI();
    }

    /**
     * Adds a primary rule to bind two class expressions.
     * If some rule is already present in the context, it will be replaced by the new one.
     *
     * @param func {@link MapFunction.Call} a function call
     * @return this context object
     * @throws MapJenaException if something goes wrong (e.g. incompatible function specified)
     */
    default MapContext addClassBridge(MapFunction.Call func) throws MapJenaException {
        return addClassBridge(null, func);
    }

    /**
     * @param func   {@link MapFunction.Builder}
     * @param target {@link Property}
     * @return {@link PropertyBridge}
     * @throws MapJenaException unable to build a property bridge
     * @see #addPropertyBridge(MapFunction.Call, Property)
     */
    default PropertyBridge addPropertyBridge(MapFunction.Builder func, Property target) throws MapJenaException {
        return addPropertyBridge(func.build(), target);
    }

    /**
     * Adds a properties bridge without filtering.
     * This is commonly used method, while its overloaded companion is used less frequently to build specific ontology mappings.
     *
     * @param func   {@link MapFunction.Call}, expression
     * @param target property, either {@link com.github.owlcs.ontapi.jena.model.OntAnnotationProperty}
     *               or {@link com.github.owlcs.ontapi.jena.model.OntDataProperty}
     * @return {@link PropertyBridge} a container with all input settings
     * @throws MapJenaException if something goes wrong (e.g. incompatible function or property specified)
     * @see #addPropertyBridge(MapFunction.Call, MapFunction.Call, Property)
     */
    default PropertyBridge addPropertyBridge(MapFunction.Call func, Property target) throws MapJenaException {
        return addPropertyBridge(null, func, target);
    }

    /**
     * Answers {@code true} iff this context is valid for (SPIN-)map-inference.
     *
     * @return boolean
     */
    default boolean isValid() {
        return getMapping() != null;
    }
}

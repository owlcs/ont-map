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

package com.github.owlcs.map.spin.vocabulary;

import com.github.owlcs.map.spin.AdjustFunctionBody;
import com.github.owlcs.map.spin.MapConfigImpl;
import com.github.owlcs.map.spin.infer.InferenceEngineImpl;
import com.github.owlcs.map.spin.system.Resources;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 * The vocabulary that describes
 * <a href='https://github.com/avicomp/spin'>/etc/avc.spin.ttl</a> and <a href='https://github.com/avicomp/lib'>/etc/avc.lib.ttl</a> libraries,
 * which are complementary to the spin-family libraries,
 * in order to customize the behavior of the spin function and
 * to provide some advanced functionality needed for mapping OWL2 ontologies.
 * The first library contains customization and basic definitions,
 * while the second is a collection of several useful spin-functions.
 * <p>
 * Created by @szuev on 07.04.2018.
 */
public class AVC {
    public static final String BASE_URI = Resources.AVC_SPIN.getURI();
    public static final String LIB_URI = Resources.AVC_LIB.getURI();
    public static final String NS = BASE_URI + "#";
    public static final String DEFAULT_PREDICATE_SUFFIX = "DefaultValue";

    /**
     * A function-class to indicate that function is a "magic" and may not work in Composer for any reason.
     * Do not confuse with {@link org.topbraid.spin.vocabulary.SPIN#MagicProperties}.
     *
     * @see #currentIndividual
     */
    public static final Resource MagicFunctions = resource("MagicFunctions");

    /**
     * A function-class to indicate that spin-function uses SPARQL aggregate operators.
     *
     * @see #groupConcat
     */
    public static final Resource AggregateFunctions = resource("AggregateFunctions");

    /**
     * A class-indicator for functions that are intended to manage property mapping (construct template) calls.
     * Such a function accepts a {@link Property} that belongs to a context containing a property mapping,
     * which is managed by this function.
     * Also, a function, that is {@code rdfs:subClassOf} {@code PropertyFunctions}, cannot contain nested functions.
     * Do not confuse with {@link org.topbraid.spin.vocabulary.SPIN#MagicProperties}.
     *
     * @see #asIRI
     * @see #withDefault
     */
    public static final Resource PropertyFunctions = resource("PropertyFunctions");

    /**
     * A datatype, that is used as return type of function and argument value type in unclear case:
     * actually this means any rdf-node (either resource or literal).
     */
    public static final Resource undefined = resource("undefined");

    /**
     * A virtual numeric datatype to be used as function return type or argument value type restriction.
     * It corresponds {@code xs:numeric}
     *
     * @see <a href='https://www.w3.org/TR/sparql11-query/#operandDataTypes'>17.1 Operand Data Types</a>
     */
    public static final Resource numeric = resource("numeric");

    /**
     * An additional no-arg map-target function, for debugging and for convenience' sake.
     * It generates individuals with uri like {@code <urn:uuid:f3bf688d44e249fade9ca8ca23e29884>}.
     * Can be used instead of {@code spinmapl:self}.
     *
     * @see org.topbraid.spin.vocabulary.SPINMAP#TargetFunctions
     */
    public static final Resource UUID = resource("UUID");

    /**
     * An additional single-arg map-target function, for debugging and for convenience' sake.
     * It generates one individual with specified iri.
     *
     * @see org.topbraid.spin.vocabulary.SPINMAP#TargetFunctions
     */
    public static final Resource IRI = resource("IRI");

    /**
     * An additional technical no-arg map-target function,
     * that is used by the system to build and infer technical contexts,
     * deriving {@link com.github.owlcs.ontapi.jena.vocabulary.OWL#NamedIndividual owl:NamedIndividual} declarations.
     * It is a functional analogue of {@link SPINMAPL#self} but with restriction on IRI-resources.
     * It returns the resource itself, but only if resource is not blank-node.
     * This function is private (i.e. {@link org.topbraid.spin.vocabulary.SPIN#private_} is {@code true}),
     * since there is no any usages with except of described above.
     *
     * @see org.topbraid.spin.vocabulary.SPINMAP#TargetFunctions
     * @see SPINMAPL#self
     * @see MapConfigImpl#generateNamedIndividuals()
     */
    public static final Resource self = resource("self");

    /**
     * An additional map-property function, that is an analogue of {@code spl:object}, but with filter.
     *
     * @see org.topbraid.spin.vocabulary.SPL#OntologyFunctions
     */
    public static final Resource objectWithFilter = resource("objectWithFilter");

    /**
     * An additional map-property function that is used to configure mapping-template-call
     * in order to pass a default value in case there is no data assertion on individual.
     * This function may accept only properties belonging to context ({@code MapContext}).
     *
     * @see #PropertyFunctions
     */
    public static final Resource withDefault = resource("withDefault");

    /**
     * An additional map-property function that is used to configure mapping-template-call
     * in order to get a property (predicate) IRI as is,
     * where usually a value (object) from a data assertion is getting.
     * This function may accept only properties belonging to context ({@code MapContext}).
     *
     * @see #PropertyFunctions
     */
    public static final Resource asIRI = resource("asIRI");

    /**
     * A magic map-property function to get a current individual while inference.
     * This is an analogue of {@code ?this} variable,
     * but which is intended to work for any link from a function-chain and in all circumstances.
     *
     * @see InferenceEngineImpl.ProcessedQuery#run(Resource) explanation.
     * @see #MagicFunctions
     */
    public static final Resource currentIndividual = resource("currentIndividual");

    /**
     * An aggregate map-property function to concat values from an assertions with the same individual and property.
     *
     * @see #AggregateFunctions
     */
    public static final Resource groupConcat = resource("groupConcat");

    /**
     * A property-indicator to hide some spin-functions from usage.
     * A spin-family (mostly spif) contains several facilities which are incompatible by some reason with ONT-MAP.
     * This property serves to exclude them from consideration.
     */
    public static final Property hidden = property("hidden");

    /**
     * A property-indicator for a functions support varargs.
     */
    public static final Property vararg = property("vararg");

    /**
     * A property-indicator, which means the function require special treatment in runtime before inference.
     * The right part of a statement with this predicate must be a valid class-path to
     * a {@link AdjustFunctionBody} impl as a string (plain) literal.
     *
     * @see AdjustFunctionBody
     */
    public static final Property runtime = property("runtime");

    /**
     * A property-indicator to tell that SPARQL-based function has also an ARQ-optimization.
     * Function calls with this property can be executed in two ways: as SPARQL or as ARQ, depending on config setting.
     * The right part of a statement with this predicate must be a valid class-path to
     * a {@link org.apache.jena.sparql.function.Function} impl as a string (plain) literal.
     */
    public static final Property optimize = property("optimize");

    /**
     * Expression predicate to use in conditional templates as a filter.
     */
    public static final Property filter = property("filter");

    /**
     * A predicate to customise {@code spin:constraint} arguments for functions from the standard spin-family.
     * Sometimes these constraints contain wrong or insufficient information.
     * @see org.topbraid.spin.vocabulary.SPIN#constraint
     */
    public static final Property constraint = property("constraint");

    /**
     * A predicate to customise function return type, sometimes it is wrong in the standard spin library.
     * @see org.topbraid.spin.vocabulary.SPIN#returnType
     */
    public static final Property returnType = property("returnType");

    /**
     * A predicate to be used for function arguments ({@code spin:constraint}).
     * The object in SPO with this predicate must be a []-list with any values (RDF-nodes) or
     * a composite {@link com.github.owlcs.ontapi.jena.model.OntObject Ontology Object} with a reference to a []-list,
     * for example a {@link com.github.owlcs.ontapi.jena.model.OntDT Ontology Datatype}
     * that is equivalent to a {@link com.github.owlcs.ontapi.jena.model.OntDR.UnionOf Data Range Union}.
     * This is an indicator that function call for the argument can accept only one of the values from that list.
     */
    public static final Property oneOf = property("oneOf");

    public static String getURI() {
        return NS;
    }

    // an universal filtering mapping template name.
    public static Resource Mapping(String filters, String sources) {
        return createMapping("Mapping", filters, sources);
    }

    // a property mapping template name
    public static Resource PropertyMapping(String filters, String sources) {
        return createMapping("PropertyMapping", filters, sources);
    }

    private static Resource createMapping(String prefix, String filters, String sources) {
        return resource(String.format("%s-f%s-s%s-t%d", prefix, filters, sources, 1));
    }

    /**
     * Creates a prefix-property
     * that is used in custom mapping templates to provide a default value
     * if there is no desired assertion for the current individual.
     *
     * @param pref String prefix name
     * @return {@link Property}
     */
    public static Property predicateDefaultValue(String pref) {
        return property(pref + DEFAULT_PREDICATE_SUFFIX);
    }

    public static Resource resource(String local) {
        return ResourceFactory.createResource(NS + local);
    }

    public static Property property(String local) {
        return ResourceFactory.createProperty(NS + local);
    }


}

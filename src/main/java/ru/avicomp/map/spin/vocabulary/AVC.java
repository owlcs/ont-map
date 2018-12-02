/*
 * This file is part of the ONT MAP.
 * The contents of this file are subject to the Apache License, Version 2.0.
 * Copyright (c) 2018, Avicomp Services, AO
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

package ru.avicomp.map.spin.vocabulary;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import ru.avicomp.map.spin.SystemModels;

/**
 * The vocabulary that describes
 * <a href='http://avc.ru/spin'>/etc/avc.spin.ttl</a> and <a href='http://avc.ru/lib'>/etc/avc.lib.ttl</a> libraries,
 * which are complementary to the spin-family libraries,
 * in order to customize the behavior of the spin function and
 * to provide some advanced functionality needed for mapping OWL2 ontologies.
 * The first library contains customization and basic definitions,
 * while the second is a collection of several useful spin-functions.
 * <p>
 * Created by @szuev on 07.04.2018.
 */
public class AVC {
    public static final String BASE_URI = SystemModels.Resources.AVC.getURI();
    public static final String LIB_URI = SystemModels.Resources.AVC_LIB.getURI();
    public static final String NS = BASE_URI + "#";

    public static final String DEFAULT_PREDICATE_SUFFIX = "DefaultValue";

    /**
     * A function class-type to indicate that function is a "magic" and may not work as expected in Composer.
     */
    public static final Resource MagicFunctions = resource("MagicFunctions");
    /**
     * A function class-type to indicate that spin-function use SPARQL aggregate operators.
     */
    public static final Resource AggregateFunctions = resource("AggregateFunctions");

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
     */
    public static final Resource UUID = resource("UUID");

    /**
     * An additional single-arg map-target function, for debugging and for convenience' sake.
     * It generates one individual with specified iri.
     */
    public static final Resource IRI = resource("IRI");

    /**
     * An additional map-property function, that is an analogue of {@code spl:object}, but with filter.
     */
    public static final Resource objectWithFilter = resource("objectWithFilter");

    /**
     * An additional map-property function that is used to configure mapping-template-call
     * in order to pass a default value in case there is no data assertion on individual.
     *
     * @see #asIRI
     */
    public static final Resource withDefault = resource("withDefault");

    /**
     * An additional map-property function that is used to configure mapping-template-call
     * in order to get a property (predicate) IRI as is,
     * where usually a value (object) from a data assertion is getting.
     *
     * @see #withDefault
     */
    public static final Resource asIRI = resource("asIRI");

    /**
     * A magic map-property function to get a current individual while inference.
     * This is an analogue of {@code ?this} variable,
     * but which is intended to work for any link from a function-chain and in all circumstances.
     *
     * @see ru.avicomp.map.spin.infer.InferenceEngineImpl.ProcessedQuery#run(Resource) explanation.
     */
    public static final Resource currentIndividual = resource("currentIndividual");

    /**
     * An aggregate map-property function to concat values from an assertions with the same individual and property.
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
     * The right part of statement with this predicate must be a valid class-path to
     * {@link ru.avicomp.map.spin.AdjustFunctionBody} impl as a string (plain) literal.
     *
     * @see ru.avicomp.map.spin.AdjustFunctionBody
     */
    public static final Property runtime = property("runtime");

    /**
     * Expression predicate to use in conditional templates as a filter.
     */
    public static final Property filter = property("filter");

    /**
     * A predicate to customise {@code spin:constraint} arguments for functions from the standard spin-family.
     * Sometimes these constraints contain wrong or insufficient information.
     */
    public static final Property constraint = property("constraint");

    /**
     * A predicate to customise function return type, sometimes it is wrong in the standard spin library.
     */
    public static final Property returnType = property("returnType");

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

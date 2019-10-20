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

package ru.avicomp.map.spin;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryException;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDFS;
import org.topbraid.spin.arq.ARQ2SPIN;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import org.topbraid.spin.vocabulary.SPL;
import ru.avicomp.map.MapJenaException;
import ru.avicomp.map.spin.infer.InferenceEngineImpl;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.ontapi.jena.utils.Models;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Auxiliary class-helper to build a custom spin construct mapping template,
 * which extends {@code spinmap:Mapping-1} and is optimised to ONT-MAP API logic.
 * In addition to common inherited capabilities
 * the result template must be able to accept and evaluate filter expression and default values.
 * Also it should be able to deal with the right-part (target) property assertions,
 * but currently this is not supported (todo!).
 * <p>
 * Notice that provided functionality is absent in the standard spin-library supply:
 * a spinmap conditional mapping (see {@code spinmap:Conditional-Mapping-1-1}) can only accept ASK query,
 * not abstract expression;
 * no default values are supported by all others standard mappings (e.g. {@code spinmap:Mapping-2-1}) -
 * i.e. if there is no data assertion on source individual then no mapping is performed.
 * <p>
 * Created by @szuev on 05.05.2018.
 */
@SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
public class TemplateBuilder {
    public static final String SOURCE_VALUE = "value";
    public static final String RESULT_VALUE = "result";
    public static final String TARGET_VALUE = "target";

    // query variables and parameters:
    private Set<String> sourcePredicates = new LinkedHashSet<>();
    private String targetPredicate;
    private Map<String, String> defaultValues = new HashMap<>();
    private List<String> filterExpressionArguments = new ArrayList<>();
    private List<String> mappingExpressionArguments = new ArrayList<>();
    private String mappingExpression;
    private String filterExpression;
    private boolean requireClassAssertion;

    /**
     * Finds or creates a custom mapping template with possibility to filter and set default values.
     * The result template is written directly into the specified model graph.
     * If {@code isPropertyMapping = false} it produces an universal mapping ({@code avc:Mapping-...-t1}),
     * which can be used in any case - both for class and property mappings,
     * otherwise a more specific property template ({@code avc:PropertyMapping-...-t1})
     * with a class assertion filter in addition is provided.
     * Since the order of inference has been changed (see {@link InferenceEngineImpl}),
     * a property mapping is processed only after a corresponding individual is created by class-map rule.
     * Note, that for compatibility with TopBraid Composer Inference there is also a special setting
     * {@code spinmap:rule spin:rulePropertyMaxIterationCount "2"^^xsd:int} inside the graph model,
     * see {@link MapManagerImpl#setupMapModel(ru.avicomp.ontapi.jena.impl.OntGraphModelImpl)} for more details.
     *
     * @param model             {@link MapModelImpl}
     * @param isPropertyMapping if true a class assertion filter is added into the SPARQL construct
     * @param filterPredicates  List of predicates (i.e. {@code spinmap:sourcePredicate$i}),
     *                          which are used while filtering
     * @param sourcePredicates  List of predicates (i.e. {@code spinmap:sourcePredicate$i}),
     *                          which are used while mapping
     * @return {@link Resource} a fresh or found mapping template resource in model
     * @throws MapJenaException.IllegalState if something goes wrong
     */
    public static Resource createMappingTemplate(MapModelImpl model,
                                                 boolean isPropertyMapping,
                                                 List<Property> filterPredicates,
                                                 List<Property> sourcePredicates) throws MapJenaException.IllegalState {
        String filters = toShortString(filterPredicates);
        String sources = toShortString(sourcePredicates);
        Resource res = (isPropertyMapping ? AVC.PropertyMapping(filters, sources) : AVC.Mapping(filters, sources))
                .inModel(model);
        if (model.contains(res, RDF.type, SPIN.ConstructTemplate)) {
            return res;
        }
        TemplateBuilder query = new TemplateBuilder()
                .addMappingExpression(SPINMAP.expression.getLocalName())
                .addTargetPredicate(SPINMAP.targetPredicate1.getLocalName())
                .requireClassAssertion(isPropertyMapping);
        // mandatory mapping expression argument:
        res.addProperty(SPIN.constraint, model.createResource()
                .addProperty(RDF.type, SPL.Argument)
                .addProperty(SPL.predicate, SPINMAP.expression));
        // optional filter expression argument:
        query.addFilterExpression(AVC.filter.getLocalName());
        res.addProperty(SPIN.constraint, model.createResource()
                .addProperty(RDF.type, SPL.Argument)
                .addProperty(SPL.predicate, AVC.filter)
                .addProperty(SPL.optional, Models.TRUE));

        // process all predicates constraints:
        Stream.of(filterPredicates, sourcePredicates)
                .flatMap(Collection::stream)
                .sorted(Comparator.comparing(Resource::getURI))
                .distinct()
                .forEach(predicate -> {
                    Resource constraint = model.createResource();
                    res.addProperty(SPIN.constraint, constraint
                            .addProperty(RDF.type, SPL.Argument)
                            .addProperty(SPL.valueType, RDF.Property)
                            .addProperty(SPL.predicate, predicate));
                    if (sourcePredicates.contains(predicate)) {
                        query.addMappingArgument(predicate.getLocalName());
                    }
                    if (filterPredicates.contains(predicate)) {
                        query.addFilterArgument(predicate.getLocalName());
                        // filter predicate is optional:
                        if (!sourcePredicates.contains(predicate)) {
                            constraint.addProperty(SPL.optional, Models.TRUE);
                        }
                    } else {
                        // default value is optional:
                        Property defaultValue = model
                                .createArgProperty(AVC.predicateDefaultValue(predicate.getLocalName()).getURI());
                        res.addProperty(SPIN.constraint, model.createResource()
                                .addProperty(RDF.type, SPL.Argument)
                                .addProperty(SPL.predicate, defaultValue)
                                .addProperty(SPL.valueType, RDFS.Literal)
                                .addProperty(SPL.optional, Models.TRUE));
                        query.addSourceDefaultValue(predicate.getLocalName(), defaultValue.getLocalName());
                    }
                    query.addSourcePredicate(predicate.getLocalName());
                });
        res.addProperty(RDF.type, SPIN.ConstructTemplate)
                .addProperty(RDFS.subClassOf, SPINMAP.Mapping_1)
                .addProperty(SPIN.body, query.build(model));
        // spin:labelTemplate
        res.addProperty(SPIN.labelTemplate, query.label());
        return res;
    }

    public static String toShortString(Collection<Property> properties) {
        return properties.isEmpty() ? "0" : properties.stream()
                .map(p -> p.getLocalName().replace(SPINMAP.SOURCE_PREDICATE, ""))
                .collect(Collectors.joining("-"));
    }

    /**
     * Examples of template name:
     * {@code avc:Mapping-f1-1-s0-t1},
     * {@code avc:PropertyMapping-f0-s1-t1},
     * {@code avc:PropertyMapping-f3-s1-2-t1},
     * {@code avc:PropertyMapping-f1-s1-t1},
     * {@code avc:PropertyMapping-f0-s1-2-3-t1}
     *
     * @param name   String
     * @param filter boolean
     * @return List of ints
     */
    static int[] parsePredicatesFromTemplateName(String name, boolean filter) {
        String t = "^.+" + (filter ? "f" : "s") + "([\\d-]+)-\\w.*$";
        return Arrays.stream(name.replaceFirst(t, "$1").split("-"))
                .mapToInt(Integer::parseInt)
                .filter(i -> i != 0)
                .toArray();
    }

    public TemplateBuilder addSourceDefaultValue(String sourcePredicateVariable, String defaultValueVariable) {
        addSourcePredicate(sourcePredicateVariable);
        defaultValues.put(sourcePredicateVariable, defaultValueVariable);
        return this;
    }

    public TemplateBuilder addSourcePredicate(String sourcePredicateVariable) {
        sourcePredicates.add(sourcePredicateVariable);
        return this;
    }

    public TemplateBuilder addTargetPredicate(String targetPredicateVariable) {
        this.targetPredicate = targetPredicateVariable;
        return this;
    }

    public TemplateBuilder addFilterArgument(String filterPredicateVariable) {
        filterExpressionArguments.add(filterPredicateVariable);
        return this;
    }

    public TemplateBuilder addMappingArgument(String mapPredicateVariable) {
        mappingExpressionArguments.add(mapPredicateVariable);
        return this;
    }

    public TemplateBuilder addMappingExpression(String expression) {
        this.mappingExpression = expression;
        return this;
    }

    public TemplateBuilder addFilterExpression(String expression) {
        this.filterExpression = expression;
        return this;
    }

    public TemplateBuilder requireClassAssertion(boolean b) {
        this.requireClassAssertion = b;
        return this;
    }

    /**
     * Builds a mapping template expression (that is a construct SPARQL query) as a {@link RDFNode RDF Node}.
     *
     * @param model {@link MapModelImpl}
     * @return {@link RDFNode}
     * @throws MapJenaException.IllegalState wrong query
     */
    public RDFNode build(MapModelImpl model) throws MapJenaException.IllegalState {
        String query = build();
        Model m = SpinModelConfig.createSpinModel(model.getGraph());
        MapManagerImpl manager = model.getManager();
        try {
            Query arq = manager.getFactory().createQuery(query, manager.prefixes());
            return new ARQ2SPIN(m).createQuery(arq, null);
        } catch (QueryException q) {
            throw new MapJenaException.IllegalState("Unable to parse '" + query + "'", q);
        }
    }

    /**
     * Builds a construct SPARQL query.
     * Example:
     * <pre>{@code CONSTRUCT {
     * 	?target ?targetPredicate1 ?resValue .
     * }
     * WHERE {
     * 	OPTIONAL {
     * 		?this ?sourcePredicate1 ?value1 .
     * 	} .
     * 	OPTIONAL {
     * 		BIND (?defaultValue1 AS ?value1) .
     * 	} .
     * 	OPTIONAL {
     * 		?this ?sourcePredicate2 ?value2 .
     * 	} .
     * 	OPTIONAL {
     * 		BIND (?defaultValue2 AS ?value2) .
     * 	} .
     * 	OPTIONAL {
     * 		?this ?sourcePredicate3 ?value3 .
     * 	} .
     * 	BIND (spin:eval(?expression, sp:arg1, ?value1, sp:arg2, ?value2) AS ?resValue) .
     * 	BIND (spinmap:targetResource(?this, ?context) AS ?target) .
     * 	FILTER (!bound(?filter) || spin:eval(?filter, sp:arg1, ?value3)) .
     * }}</pre>
     *
     * @return String
     */
    public String build() {
        Objects.requireNonNull(mappingExpression, "Null expression variable name");
        Objects.requireNonNull(targetPredicate, "Null target predicate variable name");

        StringBuilder query = new StringBuilder("CONSTRUCT {\n\t")
                .append("?target ?").append(targetPredicate).append(" ?").append(RESULT_VALUE)
                .append(" .\n}\nWHERE {\n");

        int varIndex = 1;
        List<String> mappingVariables = new ArrayList<>();
        List<String> filterVariables = new ArrayList<>();
        for (String p : sourcePredicates) {
            String v = SOURCE_VALUE + varIndex++;
            String t = "?this ?" + p + " ?" + v + " .";
            query.append(asOptional(t)).append("\n");
            String d = defaultValues.get(p);
            if (d != null) {
                query.append(asOptional("BIND (?" + d + " AS ?" + v + ") .")).append("\n");
            }
            if (mappingExpressionArguments.contains(p)) {
                mappingVariables.add(v);
            }
            if (filterExpressionArguments.contains(p)) {
                filterVariables.add(v);
            }
        }
        query.append("\tBIND (").append(makeEvalCall(mappingExpression, mappingVariables))
                .append(" AS ?")
                .append(RESULT_VALUE).append(") .")
                .append("\n\tBIND (spinmap:targetResource(?this, ?context) AS ?").append(TARGET_VALUE).append(") .");
        if (requireClassAssertion) {
            query.append("\n\tFILTER EXISTS {\n\t\t?").append(TARGET_VALUE).append(" a ?any .\n\t} .");
        }
        if (filterExpression != null) {
            query.append("\n\tFILTER ")
                    .append("(!bound(?").append(filterExpression).append(") || ")
                    .append(makeEvalCall(filterExpression, filterVariables)).append(") .");
        }
        query.append("\n}");
        return query.toString();
    }

    public String label() {
        return String.format("%s%sMap into %s: derive %s from %s.",
                filterExpression != null ? "Filtering " : "",
                requireClassAssertion ? "Property " : "",
                asLabeledVariable("context"),
                asLabeledVariable(targetPredicate),
                sourcePredicates.isEmpty() ? "self" :
                        sourcePredicates.stream()
                                .map(TemplateBuilder::asLabeledVariable).collect(Collectors.joining(", ")));
    }

    private static String asOptional(String expr) {
        return String.format("\tOPTIONAL {\n\t\t%s\n\t} .", expr);
    }

    private static String makeEvalCall(String expressionVariable, List<String> argumentVariables) {
        if (argumentVariables.isEmpty()) {
            return "spin:eval(?" + expressionVariable + ", sp:arg1, ?this)";
        }
        return Stream.concat(Stream.of(expressionVariable), IntStream.rangeClosed(1, argumentVariables.size())
                .mapToObj(i -> "sp:arg" + i + ", ?" + argumentVariables.get(i - 1)))
                .collect(Collectors.joining(", ", "spin:eval(?", ")"));
    }

    private static String asLabeledVariable(String msg) {
        return "{?" + msg + "}";
    }
}

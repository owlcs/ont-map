package ru.avicomp.map.spin;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDFS;
import org.topbraid.spin.arq.ARQ2SPIN;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import org.topbraid.spin.vocabulary.SPL;
import ru.avicomp.map.MapJenaException;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Auxiliary class-helper just to build spin construct queries which is used in spin templates.
 * <p>
 * Created by @szuev on 27.04.2018.
 */
@SuppressWarnings({"WeakerAccess", "UnusedReturnValue", "SameParameterValue"})
class ConstructBuilder {
    private List<String> sourcePredicates = new ArrayList<>();
    private List<String> targetPredicates = new ArrayList<>();
    private String expression;
    private String filter;

    static Resource createMappingTemplate(MapModelImpl model, Resource template, Property filter, int sources) throws MapJenaException {
        Resource res = template.inModel(model);
        if (model.contains(res, RDF.type, SPIN.ConstructTemplate)) {
            return res;
        }
        ConstructBuilder query = new ConstructBuilder()
                .addExpression(SPINMAP.expression)
                .addTargetPredicate(SPINMAP.targetPredicate1);
        if (filter != null) {
            query.addFilter(filter);
            res.addProperty(SPIN.constraint, model.createResource()
                    .addProperty(RDF.type, SPL.Argument)
                    .addProperty(SPL.predicate, filter));
        }
        IntStream.rangeClosed(1, sources)
                .forEach(i -> {
                    Resource sourcePredicate = model.getSourcePredicate(i);
                    res.addProperty(SPIN.constraint, model.createResource()
                            .addProperty(RDF.type, SPL.Argument).addProperty(SPL.predicate, sourcePredicate));
                    query.addSourcePredicate(sourcePredicate);
                });
        Model m = SpinModelConfig.createSpinModel(model.getGraph());
        res.addProperty(RDF.type, SPIN.ConstructTemplate)
                .addProperty(RDFS.subClassOf, SPINMAP.Mapping_1)
                .addProperty(SPIN.body, ARQ2SPIN.parseQuery(query.build(), m));
        res.addProperty(SPIN.constraint, model.createResource()
                .addProperty(RDF.type, SPL.Argument)
                .addProperty(SPL.predicate, SPINMAP.expression));
        return res;
    }

    ConstructBuilder addSourcePredicate(String localName) {
        sourcePredicates.add(localName);
        return this;
    }

    ConstructBuilder addSourcePredicate(Resource uri) {
        return addSourcePredicate(uri.getLocalName());
    }

    ConstructBuilder addTargetPredicate(String localName) {
        targetPredicates.add(localName);
        return this;
    }

    ConstructBuilder addTargetPredicate(Resource uri) {
        return addTargetPredicate(uri.getLocalName());
    }

    ConstructBuilder addExpression(String expression) {
        this.expression = expression;
        return this;
    }

    ConstructBuilder addExpression(Resource uri) {
        return addExpression(uri.getLocalName());
    }

    ConstructBuilder addFilter(String expression) {
        this.filter = expression;
        return this;
    }

    ConstructBuilder addFilter(Resource uri) {
        return addFilter(uri.getLocalName());
    }

    public String build() {
        Objects.requireNonNull(expression, "Null expression predicate");
        if (sourcePredicates.isEmpty()) {
            throw new MapJenaException.Unsupported("Source count == 0");
        }
        if (targetPredicates.size() != 1) {
            throw new MapJenaException.Unsupported("Targets count != 1");
        }
        String eval = makeEvalCall(expression, sourcePredicates.size());
        StringBuilder query = new StringBuilder("CONSTRUCT {\n\t")
                .append("?target ?").append(targetPredicates.get(0)).append(" ?newValue")
                .append(" .\n}\nWHERE {\n");
        for (int i = 0; i < sourcePredicates.size(); i++) {
            query.append("\t?this ?").append(sourcePredicates.get(i))
                    .append(" ?oldValue")
                    .append(i + 1).append(" .\n");
        }
        query.append("\tBIND (").append(eval).append(" AS ?newValue) .\n" +
                "\tBIND (spinmap:targetResource(?this, ?context) AS ?target) .");
        if (filter != null) {
            query.append("\n\tFILTER ").append(makeEvalCall(filter, sourcePredicates.size())).append(" .");
        }
        query.append("\n}");
        return query.toString();
    }

    private static String makeEvalCall(String expressionVarName, int args) {
        String template = "sp:arg%s, ?oldValue%s";
        return Stream.concat(Stream.of(expressionVarName), IntStream.rangeClosed(1, args)
                .mapToObj(i -> String.format(template, i, i)))
                .collect(Collectors.joining(", ", "spin:eval(?", ")"));
    }

}

package ru.avicomp.map.utils;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.rdf.model.*;

import java.util.stream.Collectors;

/**
 * An utility to work with any {@link Model Jena Model}.
 * <p>
 * Created by @szuev on 10.04.2018.
 */
public class Models {

    public static final String STRING_VALUE_SEPARATOR = "\n";

    public static String getLangValue(Resource resource, Property predicate, String lang) {
        return getLangValue(resource, predicate, lang, STRING_VALUE_SEPARATOR);
    }

    public static String getLangValue(Resource resource, Property predicate, String lang, String separator) {
        return Iter.asStream(resource.listProperties(predicate))
                .map(Statement::getObject)
                .filter(RDFNode::isLiteral)
                .map(RDFNode::asLiteral)
                .filter(l -> filterByLang(l, lang))
                .map(Literal::getString)
                .collect(Collectors.joining(separator));
    }

    private static boolean filterByLang(Literal literal, String lang) {
        String other = literal.getLanguage();
        if (StringUtils.isEmpty(lang))
            return StringUtils.isEmpty(other);
        return lang.trim().equalsIgnoreCase(other);
    }
}

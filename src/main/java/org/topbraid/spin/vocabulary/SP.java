package org.topbraid.spin.vocabulary;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import ru.avicomp.map.spin.SpinModelConfig;
import ru.avicomp.map.spin.SystemModels;

/**
 * A modified copy-paste from a org.topbraid.spin.vocabulary.SP.
 * Two major differences:
 * <ul>
 *     <li>It does not modify {@link org.apache.jena.enhanced.BuiltinPersonalities#model the standard global jena personality}.</li>
 *     <li>Method {@link SP#getModel()}, which is called everywhere in spin, does not dive into the Internet if no /etc/sp.ttl resource found.</li>
 * </ul>
 * TODO: it seems using the same namespaces is dirty and dangerous solution.
 * TODO: need to exclude corresponding topbraid classes from a final jar by assembly plugin or somehow else,
 * TODO: or move these classes to another place and use jena's location mapper to be used invoked inside #getModel methods.
 * TODO: First solution seems to be preferable.
 * <p>
 * Created by @szuev on 05.04.2018.
 */
@SuppressWarnings({"WeakerAccess", "unused", "deprecation"})
public class SP {
    public static final String BASE_URI = SystemModels.Resources.SP.getURI();
    public static final String NS = BASE_URI + "#";
    public static final String PREFIX = "sp";

    public final static Resource Aggregation = resource("Aggregation");
    public final static Resource AltPath = resource("AltPath");
    public final static Resource Asc = resource("Asc");
    public final static Resource Ask = resource("Ask");
    public final static Resource Avg = resource("Avg");
    public final static Resource Bind = resource("Bind");
    public final static Resource Clear = resource("Clear");
    public final static Resource Command = resource("Command");
    public final static Resource Construct = resource("Construct");
    public final static Resource Count = resource("Count");
    public final static Resource Create = resource("Create");
    public final static Resource Delete = resource("Delete");
    public final static Resource DeleteData = resource("DeleteData");
    public final static Resource DeleteWhere = resource("DeleteWhere");
    public final static Resource Desc = resource("Desc");
    public final static Resource Describe = resource("Describe");
    public final static Resource Drop = resource("Drop");
    public final static Resource exists = resource("exists");
    public final static Resource Exists = resource("Exists");
    public final static Resource Expression = resource("Expression");
    public final static Resource Filter = resource("Filter");
    public final static Resource Insert = resource("Insert");
    public final static Resource InsertData = resource("InsertData");
    public final static Resource Let = resource("Let");
    public final static Resource Load = resource("Load");
    public final static Resource Max = resource("Max");
    public final static Resource Min = resource("Min");
    public final static Resource Modify = resource("Modify");
    public final static Resource ModPath = resource("ModPath");
    public final static Resource Minus = resource("Minus");
    public final static Resource NamedGraph = resource("NamedGraph");
    public final static Resource notExists = resource("notExists");
    public final static Resource NotExists = resource("NotExists");
    public final static Resource Optional = resource("Optional");
    public final static Resource Query = resource("Query");
    public final static Resource ReverseLinkPath = resource("ReverseLinkPath");
    public final static Resource ReversePath = resource("ReversePath");
    public final static Resource Select = resource("Select");
    public final static Resource Service = resource("Service");
    public final static Resource SeqPath = resource("SeqPath");
    public final static Resource SubQuery = resource("SubQuery");
    public final static Resource Sum = resource("Sum");
    public final static Resource Triple = resource("Triple");
    public final static Resource TriplePath = resource("TriplePath");
    public final static Resource TriplePattern = resource("TriplePattern");
    public final static Resource TripleTemplate = resource("TripleTemplate");
    public final static Resource undef = resource("undef");
    public final static Resource Union = resource("Union");
    public final static Resource Update = resource("Update");
    public final static Resource Values = resource("Values");
    public final static Resource Variable = resource("Variable");

    public final static Property all = property("all");
    public final static Property arg = property("arg");
    public final static Property arg1 = arg(1);
    public final static Property arg2 = arg(2);
    public final static Property arg3 = arg(3);
    public final static Property arg4 = arg(4);
    public final static Property arg5 = arg(5);
    public final static Property as = property("as");
    public final static Property bindings = property("bindings");
    public final static Property data = property("data");
    public final static Property default_ = property("default");
    public final static Property deletePattern = property("deletePattern");
    public final static Property distinct = property("distinct");
    public final static Property document = property("document");
    public final static Property elements = property("elements");
    public final static Property expression = property("expression");
    public final static Property from = property("from");
    public final static Property fromNamed = property("fromNamed");
    public final static Property graphIRI = property("graphIRI");
    public final static Property graphNameNode = property("graphNameNode");
    public final static Property groupBy = property("groupBy");
    public final static Property having = property("having");
    public final static Property insertPattern = property("insertPattern");
    public final static Property into = property("into");
    public final static Property limit = property("limit");
    public final static Property modMax = property("modMax");
    public final static Property modMin = property("modMin");
    public final static Property named = property("named");
    public final static Property node = property("node");
    public final static Property object = property("object");
    public final static Property offset = property("offset");
    public final static Property orderBy = property("orderBy");
    public final static Property path = property("path");
    public final static Property path1 = property("path1");
    public final static Property path2 = property("path2");
    public final static Property predicate = property("predicate");
    public final static Property query = property("query");
    public final static Property reduced = property("reduced");
    public final static Property resultNodes = property("resultNodes");
    public final static Property resultVariables = property("resultVariables");
    public final static Property separator = property("separator");
    public final static Property serviceURI = property("serviceURI");
    public final static Property silent = property("silent");
    public final static Property str = property("str");
    public final static Property strlang = property("strlang");
    public final static Property subject = property("subject");
    public final static Property subPath = property("subPath");
    public final static Property templates = property("templates");
    public final static Property text = property("text");
    public final static Property using = property("using");
    public final static Property usingNamed = property("usingNamed");
    public final static Property values = property("values");
    public final static Property variable = property("variable");
    public final static Property varName = property("varName");
    public final static Property varNames = property("varNames");
    public final static Property where = property("where");
    public final static Property with = property("with");

    public final static Resource bound = resource("bound");
    public final static Resource eq = resource("eq");
    public final static Resource not = resource("not");
    public final static Resource regex = resource("regex");
    public final static Resource sub = resource("sub");
    public final static Resource unaryMinus = resource("unaryMinus");

    public static Property arg(int i) {
        if (i <= 0) throw new IllegalArgumentException();
        return property("arg" + i);
    }

    public static Resource resource(String local) {
        return ResourceFactory.createResource(NS + local);
    }

    public static Property property(String local) {
        return ResourceFactory.createProperty(NS + local);
    }

    public static Model getModel() {
        return SpinModelConfig.createSpinModel(SystemModels.graphs().get(BASE_URI));
    }

}
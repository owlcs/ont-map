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

package org.topbraid.spin.vocabulary;

import org.apache.jena.rdf.model.*;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.RDF;
import org.topbraid.spin.model.Element;
import ru.avicomp.map.spin.SpinModelConfig;
import ru.avicomp.map.spin.system.Resources;

/**
 * A modified copy-paste from a org.topbraid.spin.vocabulary.SP.
 * Two major differences:
 * <ul>
 * <li>It does not modify {@link org.apache.jena.enhanced.BuiltinPersonalities#model the standard global jena personality}.</li>
 * <li>Method {@link SP#getModel()}, which is called everywhere in spin,
 * does not dive into the Internet if no /etc/sp.ttl resource found and does not reload data everytime from resources</li>
 * </ul>
 * WARNING: need to make sure that this class goes before or instead than corresponding TopBraid class in classpath.
 * To achieve this we use maven-dependency-plugin.
 * <p>
 * Created by @szuev on 05.04.2018.
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class SP {
    public static final String BASE_URI = Resources.SP.getURI();
    public static final String NS = BASE_URI + "#";
    public static final String PREFIX = "sp";

    public final static String ARG = "arg";

    public final static String VAR_NS = "http://spinrdf.org/var#";
    public final static String VAR_PREFIX = "var";

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
    public final static Property arg = property(ARG);
    public final static Property arg1 = getArgProperty(1);
    public final static Property arg2 = getArgProperty(2);
    public final static Property arg3 = getArgProperty(3);
    public final static Property arg4 = getArgProperty(4);
    public final static Property arg5 = getArgProperty(5);
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
    public final static Resource add = resource("add");
    public final static Resource mul = resource("mul");
    public final static Resource divide = resource("divide");
    public final static Resource unaryPlus = resource("unaryPlus");
    public final static Resource unaryMinus = resource("unaryMinus");
    public final static Resource ceil = resource("ceil");
    public final static Resource floor = resource("floor");
    public final static Resource round = resource("round");
    public final static Resource isNumeric = resource("isNumeric");
    public final static Resource tz = resource("tz");

    public static Property getArgProperty(int i) {
        if (i <= 0) throw new IllegalArgumentException();
        return getArgProperty(ARG + i);
    }

    public static Property getArgProperty(String varName) {
        return property(varName);
    }

    public static Integer getArgPropertyIndex(String varName) {
        if (varName == null) return null;
        if (varName.startsWith(ARG)) {
            return Integer.getInteger(varName.substring(3));
        }
        return null;
    }

    public static Resource resource(String local) {
        return ResourceFactory.createResource(NS + local);
    }

    public static Property property(String local) {
        return ResourceFactory.createProperty(NS + local);
    }

    public static Model getModel() {
        return SpinModelConfig.createSpinModel(Resources.SP.getGraph());
    }

    public static String getURI() {
        return NS;
    }

    /**
     * Not used by ONT-MAP api, but used by composer SPIN-API.
     * Copy-pasted description:
     * Checks whether the SP ontology is used in a given Model.
     * This is true if the model defines the SP namespace prefix  and also has sp:Query defined with an rdf:type.
     * The goal of this call is to be very fast when SP is not imported,
     * i.e. it checks the namespace first and can then omit the type query.
     *
     * @param model the Model to check
     * @return {@code true} if SP exists in model
     */
    public static boolean exists(Model model) {
        return model != null && SP.NS.equals(model.getNsPrefixURI(SP.PREFIX)) && model.contains(SP.Query, RDF.type, (RDFNode) null);
    }

    /**
     * Not used by ONT-MAP api, but present in SPIN-API (shacl-1.0.1).
     *
     * @param buffer   {@link StringBuffer}
     * @param resource {@link Resource}
     * @deprecated don't use.
     */
    public static void toStringElementList(StringBuffer buffer, Resource resource) {
        RDFList list = resource.as(RDFList.class);
        for (ExtendedIterator<RDFNode> it = list.iterator(); it.hasNext(); ) {
            Resource item = it.next().asResource();
            Element e = org.topbraid.spin.model.SPINFactory.asElement(item);
            buffer.append(e.toString());
            if (it.hasNext()) {
                buffer.append(" .\n");
            }
        }
    }
}
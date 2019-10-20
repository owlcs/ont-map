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

package ru.avicomp.map.spin.system;

import org.apache.jena.sparql.function.Function;
import org.apache.jena.sparql.pfunction.PropertyFunction;
import org.topbraid.spin.arq.functions.InvokeFunction;
import org.topbraid.spin.arq.functions.WalkObjectsFunction;
import ru.avicomp.map.spin.functions.spif.buildString;
import ru.avicomp.map.spin.functions.spif.buildStringFromRDFList;
import ru.avicomp.map.spin.functions.spif.buildURI;
import ru.avicomp.map.spin.functions.spif.buildUniqueURI;
import ru.avicomp.map.spin.vocabulary.SPIF;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A helper to register builtin {@code SPIF} functions,
 * that are mostly taken from Topbraid Composer Free Edition. ver 5.2.2
 * (see {@code org.topbraid.spin.functions_*.jar}, {@code org:topbraid:spin.functions} in the pom dependencies).
 * Note that up-to-date Composer no longer uses the java SPIN-API implementation,
 * but rather java SHACL-API as a successor, that has changes in namespaces.
 * TODO: move all of these functions directly into the API.
 * <p>
 * Created by @ssz on 20.12.2018.
 *
 * @see SPIF
 */
class SPIFFunctions {

    /**
     * Collection of {@link Function}s from {@code org:topbraid:spin.functions}, spinrdf and the api.
     */
    static final Map<String, Class<? extends Function>> FUNCTIONS =
            Collections.unmodifiableMap(new HashMap<String, Class<? extends Function>>() {

                {
                    // from org.topbraid.spin.arq.functions:
                    add("invoke", InvokeFunction.class);
                    add("walkObjects", WalkObjectsFunction.class);
                    // boolean:
                    add("hasAllObjects", "org.topbraid.spin.functions.internal.bool.HasAllObjectsFunction");
                    add("isReadOnlyTriple", "org.topbraid.spin.functions.internal.bool.IsReadOnlyTripleFunction");
                    // date:
                    add("currentTimeMillis", "org.topbraid.spin.functions.internal.date.CurrentTimeMillisFunction");
                    add("dateFormat", "org.topbraid.spin.functions.internal.date.DateFormatFunction");
                    add("parseDate", "org.topbraid.spin.functions.internal.date.ParseDateFunction");
                    add("timeMillis", "org.topbraid.spin.functions.internal.date.TimeMillisFunction");
                    // mathematical:
                    add("mod", "org.topbraid.spin.functions.internal.mathematical.ModFunction");
                    add("random", "org.topbraid.spin.functions.internal.mathematical.RandomFunction");
                    // misc
                    add("canInvoke", "org.topbraid.spin.functions.internal.misc.CanInvokeFunction");
                    add("cast", "org.topbraid.spin.functions.internal.misc.CastFunction");
                    add("countMatches", "org.topbraid.spin.functions.internal.misc.CountMatchesFunction");
                    add("countTransitiveSubjects", "org.topbraid.spin.functions.internal.misc.CountTransitiveSubjectsFunction");
                    add("shortestObjectsPath", "org.topbraid.spin.functions.internal.misc.ShortestObjectsPathFunction");
                    add("shortestSubjectsPath", "org.topbraid.spin.functions.internal.misc.ShortestSubjectsPathFunction");
                    // string:
                    add("buildStringFromRDFList", buildStringFromRDFList.class);
                    add("buildString", buildString.class);
                    add("camelCase", "org.topbraid.spin.functions.internal.string.CamelCaseFunction");
                    add("convertSPINRDFToString", "org.topbraid.spin.functions.internal.string.ConvertSPINRDFToStringFunction");
                    add("decimalFormat", "org.topbraid.spin.functions.internal.string.DecimalFormatFunction");
                    add("decodeURL", "org.topbraid.spin.functions.internal.string.DecodeURLFunction");
                    add("encodeURL", "org.topbraid.spin.functions.internal.string.EncodeURLFunction");
                    add("generateUUID", "org.topbraid.spin.functions.internal.string.GenerateUUIDFunction");
                    add("indexOf", "org.topbraid.spin.functions.internal.string.IndexOfFunction");
                    add("lastIndexOf", "org.topbraid.spin.functions.internal.string.LastIndexOfFunction");
                    add("lowerCamelCase", "org.topbraid.spin.functions.internal.string.LowerCamelCaseFunction");
                    add("lowerCase", "org.topbraid.spin.functions.internal.string.LowerCaseFunction");
                    add("lowerTitleCase", "org.topbraid.spin.functions.internal.string.LowerTitleCaseFunction");
                    add("name", "org.topbraid.spin.functions.internal.string.NameFunction");
                    add("regex", "org.topbraid.spin.functions.internal.string.RegexFunction");
                    add("replaceAll", "org.topbraid.spin.functions.internal.string.ReplaceAllFunction");
                    add("titleCase", "org.topbraid.spin.functions.internal.string.TitleCaseFunction");
                    add("toJavaIdentifier", "org.topbraid.spin.functions.internal.string.ToJavaIdentifierFunction");
                    add("trim", "org.topbraid.spin.functions.internal.string.TrimFunction");
                    add("upperCase", "org.topbraid.spin.functions.internal.string.UpperCaseFunction");
                    add("unCamelCase", "org.topbraid.spin.functions.internal.string.UnCamelCaseFunction");
                    add("buildURI", buildURI.class);
                    add("buildUniqueURI", buildUniqueURI.class);
                    // uri
                    add("isValidURI", "org.topbraid.spin.functions.internal.uri.IsValidURIFunction");
                }

                private void add(String name, String classPath) {
                    add(name, load(classPath));
                }

                private void add(String name, Class<? extends Function> impl) {
                    put(SPIF.NS + name, impl);
                    put(SPIF.SMF_NS + name, impl);
                }
            });

    /**
     * Property Functions are supplied just in case, there are no usage of them in the API.
     */
    static final Map<String, Class<? extends PropertyFunction>> PROPERTY_FUNCTIONS =
            Collections.unmodifiableMap(new HashMap<String, Class<? extends PropertyFunction>>() {
                {
                    add("evalPath", "org.topbraid.spin.functions.internal.magic.EvalPathPFunction");
                    add("for", "org.topbraid.spin.functions.internal.magic.ForPFunction");
                    add("foreach", "org.topbraid.spin.functions.internal.magic.ForeachPFunction");
                    add("labelTemplateSegment", "org.topbraid.spin.functions.internal.magic.LabelTemplateSegmentPFunction");
                    add("prefix", "org.topbraid.spin.functions.internal.magic.PrefixPFunction");
                    add("range", "org.topbraid.spin.functions.internal.magic.RangePFunction");
                    add("split", "org.topbraid.spin.functions.internal.magic.SplitPFunction");
                }

                private void add(String name, String classPath) {
                    add(name, load(classPath));
                }

                private void add(String name, Class<? extends PropertyFunction> impl) {
                    put(SPIF.NS + name, impl);
                    put("http://www.topbraid.org/tops#" + name, impl);
                }
            });

    @SuppressWarnings("unchecked")
    private static <X> Class<X> load(String classPath) {
        try {
            // Some of the SPIF classes above cannot be initialized before Jena starts
            // (e.g. ForPFunction has access to jena in static initialization block -
            // this leads to ExceptionInInitializerError in some circumstances)
            // So, load with postponed class initialization.
            // Also, use the current (the same) class loader.
            return (Class<X>) Class.forName(classPath, false, SPIFFunctions.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Can't load impl class " + classPath + ".", e);
        }
    }

}

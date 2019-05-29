/*
 * This file is part of the ONT MAP.
 * The contents of this file are subject to the Apache License, Version 2.0.
 * Copyright (c) 2019, Avicomp Services, AO
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

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.XSD;

/**
 * Auxiliary interface to provide a human readable serialization.
 * Created by @ssz on 29.05.2019.
 */
public interface ToString {

    /**
     * Returns a prefixed form of the given {@code uri} if possible,
     * otherwise a query form.
     *
     * @param pm  {@link PrefixMapping}
     * @param uri String, not {@code null}
     * @return String
     */
    static String getShortForm(PrefixMapping pm, String uri) {
        String res = pm.shortForm(uri);
        if (!res.equals(uri))
            return res;
        return "<" + uri + ">";
    }

    /**
     * Returns a human readable representation of the given {@link Literal} according to the {@link PrefixMapping}.
     *
     * @param pm  {@link PrefixMapping}
     * @param val {@link Literal}
     * @return String
     */
    static String getShortForm(PrefixMapping pm, Literal val) {
        String txt = val.getLexicalForm();
        String lang = val.getLanguage();
        if (lang != null && !lang.isEmpty()) {
            return String.format("%s@%s", txt, lang);
        }
        String uri = val.getDatatypeURI();
        if (uri == null) {
            uri = XSD.xstring.getURI();
        }
        if (XSD.xstring.getURI().equals(uri)) {
            return txt;
        }
        return String.format("%s^^%s", txt, getShortForm(pm, uri));
    }

    /**
     * Represents this instance in a human readable form according to the given {@link PrefixMapping prefixes}.
     *
     * @param pm {@link PrefixMapping}
     * @return {@code String}
     */
    String toString(PrefixMapping pm);

}

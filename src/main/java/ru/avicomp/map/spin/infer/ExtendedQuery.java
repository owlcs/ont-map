/*
 * This file is part of the ONT MAP.
 * The contents of this file are subject to the Apache License, Version 2.0.
 * Copyright (c) 2018, Avicomp Services, AO
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.avicomp.map.spin.infer;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.topbraid.spin.util.CommandWrapper;
import org.topbraid.spin.util.QueryWrapper;

import java.util.Comparator;
import java.util.Objects;

/**
 * An extended comparable {@link QueryWrapper SPIN Query},
 * that is a common super type for all mapping queries used while inference in this API.
 * Created by @ssz on 14.11.2018.
 */
@SuppressWarnings("WeakerAccess")
public class ExtendedQuery extends QueryWrapper implements Comparable<ExtendedQuery> {

    private static final Comparator<CommandWrapper> MAP_RULE_COMPARATOR = SPINInferenceHelper.createMapComparator();

    public ExtendedQuery(QueryWrapper qw) {
        super(qw.getQuery(),
                qw.getSource(),
                qw.getText(),
                qw.getSPINQuery(),
                qw.getLabel(),
                qw.getStatement(),
                qw.isThisUnbound(),
                qw.isThisDeep());
        setTemplateBinding(qw.getTemplateBinding());
    }

    @Override
    public String toString() {
        String res;
        if ((res = getLabel()) != null)
            return res;
        if ((res = getText()) != null)
            return res;
        Statement st = getStatement();
        if (st != null) return st.toString();
        return super.toString();
    }

    public Model getModel() {
        return getSPINCommand().getModel();
    }

    public Resource getSubject() {
        return getStatement().getSubject();
    }

    @Override
    public int compareTo(@SuppressWarnings("NullableProblems") ExtendedQuery o) {
        return MAP_RULE_COMPARATOR.compare(this, Objects.requireNonNull(o));
    }

}

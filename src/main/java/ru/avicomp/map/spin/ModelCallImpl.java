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

package ru.avicomp.map.spin;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.XSD;
import ru.avicomp.map.MapFunction;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A {@link MapFunction.Call} implementation that is attached to a {@link MapModelImpl model}.
 * <p>
 * Created by @ssz on 23.11.2018.
 */
@SuppressWarnings("WeakerAccess")
public class ModelCallImpl extends MapFunctionImpl.CallImpl {
    private final MapModelImpl model;

    public ModelCallImpl(MapModelImpl model, MapFunctionImpl function, Map<MapFunctionImpl.ArgImpl, Object> args) {
        super(function, args);
        this.model = Objects.requireNonNull(model);
    }

    @Override
    public String toString(PrefixMapping pm) {
        String name = model.shortForm(getFunction().name());
        List<MapFunctionImpl.ArgImpl> args = listArgs().collect(Collectors.toList());
        if (args.size() == 1) { // print without predicate
            return name + "(" + getStringValue(model, args.get(0)) + ")";
        }
        return args.stream().map(a -> toString(pm, a)).collect(Collectors.joining(", ", name + "(", ")"));
    }

    @Override
    protected String getStringValue(PrefixMapping pm, MapFunctionImpl.ArgImpl a) {
        Object v = get(a);
        if (!(v instanceof String)) {
            return super.getStringValue(pm, a);
        }
        RDFNode n = model.toNode((String) v);
        if (n.isLiteral()) {
            Literal l = n.asLiteral();
            String u = l.getDatatypeURI();
            if (XSD.xstring.getURI().equals(u)) {
                return l.getLexicalForm();
            }
            return String.format("%s^^%s", l.getLexicalForm(), model.shortForm(u));
        } else {
            return model.shortForm(n.asNode().toString());
        }
    }

    @Override
    protected String getStringKey(PrefixMapping pm, MapFunctionImpl.ArgImpl a) {
        return "?" + model.toNode(a.name()).asResource().getLocalName();
    }

    /**
     * Overridden {@code #toString()}, to produce a good-looking output, which can be used as a label.
     * <p>
     * Actually, it is not a very good idea to override {@code toString()},
     * there should be a special mechanism to print anything in ONT-MAP api.
     * But as temporary solution it is okay: it is not dangerous here.
     *
     * @return String
     */
    @Override
    public String toString() {
        return toString(model);
    }
}

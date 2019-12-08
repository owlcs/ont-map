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

package com.github.owlcs.map.spin;

import com.github.owlcs.map.MapContext;
import com.github.owlcs.map.PropertyBridge;
import com.github.owlcs.map.spin.vocabulary.AVC;
import com.github.owlcs.ontapi.jena.impl.OntObjectImpl;
import com.github.owlcs.ontapi.jena.utils.Iter;
import org.apache.jena.enhanced.EnhGraph;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.shared.PrefixMapping;
import org.topbraid.spin.vocabulary.SPINMAP;

import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of properties binding rule (for resource with type {@code spinmap:rule} related to properties map).
 * <p>
 * Created by @szuev on 16.04.2018.
 */
@SuppressWarnings("WeakerAccess")
public class PropertyBridgeImpl extends OntObjectImpl implements PropertyBridge, ToString {

    public PropertyBridgeImpl(Node n, EnhGraph m) {
        super(n, m);
    }

    @Override
    public PropertyBridgeImpl asResource() {
        return this;
    }

    @Override
    public MapModelImpl getModel() {
        return (MapModelImpl) super.getModel();
    }

    @Override
    public Stream<Property> sources() {
        return Iter.asStream(listProperties()
                .filterKeep(s -> SpinModels.isSourcePredicate(s.getPredicate()))
                .mapWith(Statement::getObject)
                .mapWith(s -> s.as(Property.class)));
    }

    @Override
    public Property getTarget() {
        return getRequiredProperty(SPINMAP.targetPredicate1).getObject().as(Property.class);
    }

    @Override
    public ModelCallImpl getMapping() {
        return getModel().parseExpression(this, getRequiredProperty(SPINMAP.expression).getObject(), false);
    }

    @Override
    public ModelCallImpl getFilter() {
        if (!hasProperty(AVC.filter)) return null;
        return getModel().parseExpression(this, getPropertyResourceValue(AVC.filter), true);
    }

    @Override
    public MapContext getContext() {
        return getModel().asContext(getRequiredProperty(SPINMAP.context).getObject().asResource());
    }

    @Override
    public String toString() {
        return toString(getModel());
    }

    @Override
    public String toString(PrefixMapping pm) {
        return toString(p -> ToString.getShortForm(pm, p.getURI()));
    }

    public String toString(Function<Property, String> map) {
        return String.format("Properties{%s => %s}",
                sources().map(map).collect(Collectors.joining(", ", "[", "]")),
                map.apply(getTarget()));
    }

}

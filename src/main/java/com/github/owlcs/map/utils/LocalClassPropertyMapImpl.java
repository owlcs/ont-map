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

package com.github.owlcs.map.utils;

import com.github.owlcs.map.ClassPropertyMap;
import com.github.owlcs.ontapi.jena.OntVocabulary;
import com.github.owlcs.ontapi.jena.model.OntClass;
import com.github.owlcs.ontapi.jena.model.OntModel;
import com.github.owlcs.ontapi.jena.model.OntObject;
import com.github.owlcs.ontapi.jena.model.OntProperty;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * An implementation of {@link ClassPropertyMap},
 * which provides access only to the local and builtin ontology OWL-classes and properties.
 * An object is local to an {@link OntModel ontology} if its base graph contains a declaration statement for that object.
 * <p>
 * Created by @szuev on 26.05.2018.
 *
 * @see OntObject#isLocal()
 */
@SuppressWarnings("WeakerAccess")
public class LocalClassPropertyMapImpl extends ClassPropertyMapImpl {
    private static final Set<Property> BUILT_IN_PROPERTIES = OntVocabulary.Factory.get().getBuiltinOWLProperties();
    private static final Set<Resource> BUILT_IN_CLASSES = OntVocabulary.Factory.get().getBuiltinClasses();

    private final OntModel toSearch;
    private final OntModel model;

    /**
     * Constructs an instance.
     * Accepts two models: the first one is to check locality, the last one is to performing searching.
     * This division into models is need for caching: only local-defined entities must be available for cache,
     * but they should be valid within the given parent model (i.e. {@code toSearch}).
     *
     * @param toSearch {@link OntModel} to search, not null
     * @param model    {@link OntModel} to check, not null
     */
    public LocalClassPropertyMapImpl(OntModel model, OntModel toSearch) {
        this.toSearch = Objects.requireNonNull(toSearch);
        this.model = Objects.requireNonNull(model);
    }

    @Override
    public Stream<Property> properties(OntClass ce) {
        Resource c = ce.inModel(toSearch);
        return c.canAs(OntClass.class) ? super.properties(c.as(OntClass.class))
                .filter(this::isLocal)
                : Stream.empty();
    }

    @Override
    public Stream<OntClass> classes(OntProperty pe) {
        Resource p = pe.inModel(toSearch);
        return p.canAs(OntProperty.class) ? super.classes(p.as(OntProperty.class))
                .filter(this::isLocal)
                : Stream.empty();
    }

    public boolean isLocal(Property pe) {
        return BUILT_IN_PROPERTIES.contains(pe) || pe.inModel(model).as(OntObject.class).isLocal();
    }

    public boolean isLocal(OntClass ce) {
        return BUILT_IN_CLASSES.contains(ce) || ce.inModel(model).as(OntObject.class).isLocal();
    }
}

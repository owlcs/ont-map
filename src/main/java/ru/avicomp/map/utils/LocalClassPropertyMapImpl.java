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

package ru.avicomp.map.utils;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import ru.avicomp.ontapi.jena.model.OntCE;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntObject;
import ru.avicomp.ontapi.jena.model.OntPE;
import ru.avicomp.ontapi.jena.utils.BuiltIn;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * An implementation of {@link ru.avicomp.map.ClassPropertyMap},
 * which provides access only to the local and builtin ontology OWL-classes and properties.
 * An object is local to an {@link OntGraphModel ontology} if its base graph contains a declaration statement for that object.
 * <p>
 * Created by @szuev on 26.05.2018.
 *
 * @see OntObject#isLocal()
 */
@SuppressWarnings("WeakerAccess")
public class LocalClassPropertyMapImpl extends ClassPropertyMapImpl {
    private static final Set<Property> BUILT_IN_PROPERTIES = BuiltIn.get().properties();
    private static final Set<Resource> BUILT_IN_CLASSES = BuiltIn.get().classes();

    private final OntGraphModel toSearch;
    private final OntGraphModel model;

    /**
     * Constructs an instance.
     * Accepts two models: the first one is to check locality, the last one is to performing searching.
     * This division into models is need for caching: only local-defined entities must be available for cache,
     * but they should be valid within the given parent model (i.e. {@code toSearch}).
     *
     * @param toSearch {@link OntGraphModel} to search, not null
     * @param model    {@link OntGraphModel} to check, not null
     */
    public LocalClassPropertyMapImpl(OntGraphModel model, OntGraphModel toSearch) {
        this.toSearch = Objects.requireNonNull(toSearch);
        this.model = Objects.requireNonNull(model);
    }

    @Override
    public Stream<Property> properties(OntCE ce) {
        Resource c = ce.inModel(toSearch);
        return c.canAs(OntCE.class) ? super.properties(c.as(OntCE.class))
                .filter(this::isLocal)
                : Stream.empty();
    }

    @Override
    public Stream<OntCE> classes(OntPE pe) {
        Resource p = pe.inModel(toSearch);
        return p.canAs(OntPE.class) ? super.classes(p.as(OntPE.class))
                .filter(this::isLocal)
                : Stream.empty();
    }

    public boolean isLocal(Property pe) {
        return BUILT_IN_PROPERTIES.contains(pe) || pe.inModel(model).as(OntObject.class).isLocal();
    }

    public boolean isLocal(OntCE ce) {
        return BUILT_IN_CLASSES.contains(ce) || ce.inModel(model).as(OntObject.class).isLocal();
    }
}

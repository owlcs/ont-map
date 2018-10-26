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

import org.apache.jena.graph.Triple;

import java.util.function.BiConsumer;

/**
 * Created by @szuev on 21.05.2018.
 */
public class GraphLogListener extends BaseGraphListener {

    private final BiConsumer<String, Triple> logger;

    public GraphLogListener(BiConsumer<String, Triple> logger) {
        this.logger = logger;
    }

    @Override
    protected void addEvent(Triple t) {
        logger.accept("ADD: {}", t);
    }

    @Override
    protected void deleteEvent(Triple t) {
        logger.accept("DELETE: {}", t);
    }
}

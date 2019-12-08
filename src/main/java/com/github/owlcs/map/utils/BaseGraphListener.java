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

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.GraphListener;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.util.graph.GraphListenerBase;

/**
 * An abstract Graph Listener that handle any change on graph.
 * Created by @szuev on 26.05.2018.
 *
 * @see GraphListenerBase
 */
public abstract class BaseGraphListener extends GraphListenerBase implements GraphListener {

    @Override
    protected abstract void addEvent(Triple t);

    @Override
    protected abstract void deleteEvent(Triple t);

    @Override
    public void notifyAddGraph(Graph g, Graph other) {
        other.find(Triple.ANY).forEachRemaining(this::addEvent);
    }

    @Override
    public void notifyDeleteGraph(Graph g, Graph other) {
        other.find(Triple.ANY).forEachRemaining(this::deleteEvent);
    }
}

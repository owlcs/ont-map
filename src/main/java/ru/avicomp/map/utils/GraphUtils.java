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

package ru.avicomp.map.utils;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.impl.WrappedGraph;
import ru.avicomp.ontapi.jena.utils.Graphs;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Collection of utils additional to the {@link Graphs ONT-API Graph Utils}.
 * NOTE: this class can be fully or partially moved to ONT-API.
 * <p>
 * Created by @szz on 20.11.2018.
 *
 * @see Graphs
 */
public class GraphUtils {

    /**
     * Answers {@code true} if the {@code left} composite graph
     * contains all components from the {@code right} composite graph.
     *
     * @param left  {@link Graph}, not {@code null}
     * @param right {@link Graph}, not {@code null}
     * @return boolean
     */
    public static boolean containsAll(Graph left, Graph right) {
        Set<Graph> set = Graphs.flat(left).collect(Collectors.toSet());
        return containsAll(right, set);
    }

    /**
     * Answers {@code true} if all parts of the {@code test} graph are containing in the given graph collection.
     *
     * @param test {@link Graph} to test, not {@code null}
     * @param in   Collection of {@link Graph}s
     * @return boolean
     */
    private static boolean containsAll(Graph test, Collection<Graph> in) {
        return Graphs.flat(test).allMatch(in::contains);
    }

    /**
     * Unwraps the given graph if it is {@link WrappedGraph}.
     *
     * @param g {@link Graph}
     * @return {@link Graph}, the same as input or base from the {@link WrappedGraph} container.
     * @see ReadOnlyGraph#wrap(Graph)
     */
    public static Graph unwrap(Graph g) {
        return g instanceof WrappedGraph ? ((WrappedGraph) g).getWrapped() : g;
    }
}

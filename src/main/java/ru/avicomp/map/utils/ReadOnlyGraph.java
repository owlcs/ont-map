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

import org.apache.jena.graph.*;
import org.apache.jena.shared.AccessDeniedException;
import org.apache.jena.shared.AddDeniedException;
import org.apache.jena.shared.DeleteDeniedException;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.graph.UnmodifiableGraph;

import java.util.Objects;

/**
 * Read-only graph implementation.
 * No modification (including changing prefixes) is not allowed for this graph.
 * <p>
 * Created by @szuev on 21.06.2018.
 */
public class ReadOnlyGraph extends UnmodifiableGraph {

    private static final Capabilities READ_ONLY_CAPABILITIES = new Capabilities() {
        @Override
        public boolean sizeAccurate() {
            return true;
        }

        @Override
        public boolean addAllowed() {
            return addAllowed(false);
        }

        @Override
        public boolean addAllowed(boolean every) {
            return false;
        }

        @Override
        public boolean deleteAllowed() {
            return deleteAllowed(false);
        }

        @Override
        public boolean deleteAllowed(boolean every) {
            return false;
        }

        @Override
        public boolean canBeEmpty() {
            return true;
        }

        @Override
        public boolean iteratorRemoveAllowed() {
            return false;
        }

        @Override
        public boolean findContractSafe() {
            return true;
        }

        @Override
        public boolean handlesLiteralTyping() {
            return true;
        }
    };

    protected PrefixMapping pm;

    public ReadOnlyGraph(Graph base) {
        super(Objects.requireNonNull(base, "Null graph"));
        this.pm = base.getPrefixMapping();
    }

    /**
     * Wraps the given graph as unmodifiable graph, if it is not already wrapped.
     *
     * @param g {@link Graph}, not {@code null}
     * @return {@link ReadOnlyGraph} around the given graph
     * @see GraphUtils#unwrap(Graph)
     */
    public static ReadOnlyGraph wrap(Graph g) {
        return g instanceof ReadOnlyGraph ? (ReadOnlyGraph) g : new ReadOnlyGraph(g);
    }

    @Override
    public void add(Triple t) {
        throw new AddDeniedException("Read only graph: can't add triple " + t);
    }

    @Override
    public void remove(Node s, Node p, Node o) {
        GraphUtil.remove(this, s, p, o);
    }

    @Override
    public void delete(Triple t) {
        throw new DeleteDeniedException("Read only graph: can't delete triple " + t);
    }

    @Override
    public void clear() {
        throw new AccessDeniedException("Read only graph: can't clear");
    }

    @Override
    public Capabilities getCapabilities() {
        return READ_ONLY_CAPABILITIES;
    }

    @Override
    public PrefixMapping getPrefixMapping() {
        return PrefixMapping.Factory.create().setNsPrefixes(pm).lock();
    }

}

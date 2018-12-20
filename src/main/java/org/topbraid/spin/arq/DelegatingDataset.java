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

package org.topbraid.spin.arq;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.LabelExistsException;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.TxnType;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.shared.Lock;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.util.Context;
import org.topbraid.spin.util.DatasetWrappingDatasetGraph;

import java.util.Iterator;

/**
 * A copy-paste in order to provide correct org.apache.jena.sparql.util.Context object and an upgrade to work with jena 3.7.0.
 * WARNING: need to make sure that this class goes before in classpath or replaces the original (topbraid) class.
 * To achieve this we use maven-dependency-plugin.
 * TODO: replace with <a href='https://github.com/spinrdf/spinrdf'>org.spinrdf:spinrdf</a> when it is available.
 * <p>
 * A Dataset that simply delegates all its calls, allowing to wrap an existing Dataset (e.g. the TopBraid Dataset).
 *
 * @author Holger Knublauch
 * Created by @szuev on 18.06.2018.
 */
public abstract class DelegatingDataset implements Dataset {

    private Dataset delegate;

    public DelegatingDataset(Dataset delegate) {
        this.delegate = delegate;
    }

    @Override
    public DatasetGraph asDatasetGraph() {
        return new DatasetWrappingDatasetGraph(this);
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean containsNamedModel(String uri) {
        return delegate.containsNamedModel(uri);
    }

    @Override
    public Model getDefaultModel() {
        return delegate.getDefaultModel();
    }

    @Override
    public Model getUnionModel() {
        return delegate.getUnionModel();
    }

    public Dataset getDelegate() {
        return delegate;
    }

    @Override
    public Lock getLock() {
        return delegate.getLock();
    }

    @Override
    public Model getNamedModel(String uri) {
        return delegate.getNamedModel(uri);
    }

    @Override
    public Iterator<String> listNames() {
        return delegate.listNames();
    }

    @Override
    public DelegatingDataset setDefaultModel(Model model) {
        delegate.setDefaultModel(model);
        return this;
    }

    @Override
    public DelegatingDataset addNamedModel(String uri, Model model) throws LabelExistsException {
        delegate.addNamedModel(uri, model);
        return this;
    }

    @Override
    public DelegatingDataset removeNamedModel(String uri) {
        delegate.removeNamedModel(uri);
        return this;
    }

    @Override
    public DelegatingDataset replaceNamedModel(String uri, Model model) {
        delegate.replaceNamedModel(uri, model);
        return this;
    }

    @Override
    public Context getContext() {
        return delegate.getContext();
    }

    @Override
    public boolean supportsTransactions() {
        return delegate.supportsTransactions();
    }

    @Override
    public boolean supportsTransactionAbort() {
        return delegate.supportsTransactionAbort();
    }

    @Override
    public void begin() {
        delegate.begin();
    }

    @Override
    public void begin(TxnType type) {
        delegate.begin(type);
    }

    @Override
    public void begin(ReadWrite readWrite) {
        delegate.begin(readWrite);
    }

    @Override
    public boolean promote() {
        return delegate.promote();
    }

    @Override
    public boolean promote(Promote mode) {
        return delegate.promote(mode);
    }

    @Override
    public void commit() {
        delegate.commit();
    }

    @Override
    public void abort() {
        delegate.abort();
    }

    @Override
    public boolean isInTransaction() {
        return delegate.isInTransaction();
    }

    @Override
    public void end() {
        delegate.end();
    }

    @Override
    public ReadWrite transactionMode() {
        return delegate.transactionMode();
    }

    @Override
    public TxnType transactionType() {
        return delegate.transactionType();
    }
}


/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jena.fuseki.access;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.apache.jena.graph.Node;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.sparql.util.Context;
import org.apache.jena.sparql.util.NodeUtils;
import org.apache.jena.tdb.TDBFactory;
import org.apache.jena.tdb2.DatabaseMgr;

/** A {@link SecurityPolicy} is the things actor (user, role) is allowed to do. 
 * Currently version: the set of graphs, by graph name, they can access.
 * It can be inverted into a "deny" policy with {@link Predicate#negate()}.
 */ 
public class SecurityPolicy {
    
    public static SecurityPolicy NONE = new SecurityPolicy();
    public static SecurityPolicy DFT_GRAPH = new SecurityPolicy(true);

    private final Collection<Node> graphNames = ConcurrentHashMap.newKeySet();
    private final boolean matchDefaultGraph;
    
    public SecurityPolicy() {
        this(false);
    }

    public SecurityPolicy(boolean matchDefaultGraph) {
        this.matchDefaultGraph = matchDefaultGraph;
    }

    public SecurityPolicy(String...graphNames) {
        this(NodeUtils.convertToSetNodes(graphNames));
    }

    public SecurityPolicy(Node...graphNames) {
        this(Arrays.asList(graphNames));
    }

    public SecurityPolicy(Collection<Node> graphNames) {
        this.graphNames.addAll(graphNames);
        this.matchDefaultGraph = graphNames.stream().anyMatch(Quad::isDefaultGraph);
    }
    
    /**
     * Apply a filter suitable for the TDB-backed {@link DatasetGraph}, to the {@link Context} of the
     * {@link QueryExecution}. This does not modify the {@link DatasetGraph}
     */
    public void filterTDB(DatasetGraph dsg, QueryExecution qExec) {
        GraphFilter<?> predicate = predicate(dsg);
        qExec.getContext().set(predicate.getContextKey(), predicate);
    }
    
    /** Modify the {@link Context} of the TDB-backed {@link DatasetGraph}. */
    public void filterTDB(DatasetGraph dsg) {
        GraphFilter<?> predicate = predicate(dsg);
        dsg.getContext().set(predicate.getContextKey(), predicate);
    }

    @Override
    public String toString() {
        return "dft:"+matchDefaultGraph+" / "+graphNames.toString();
    }

    /**
     * Create a GraphFilter for a TDB backed dataset.
     * 
     * @return GraphFilter
     * @throws IllegalArgumentException
     *             if not a TDB database, or a {@link DatasetGraphAccessControl} wrapped
     *             TDB database.
     */
    public GraphFilter<?> predicate(DatasetGraph dsg) {
        dsg = DatasetGraphAccessControl.unwrap(dsg);
        // dsg has to be the database dataset, not wrapped.
        //  DatasetGraphSwitchable is wrapped but should not be unwrapped. 
        if ( TDBFactory.isTDB1(dsg) )
            return filterTDB1(dsg);
        if ( DatabaseMgr.isTDB2(dsg) )
            return filterTDB2(dsg);
        throw new IllegalArgumentException("Not a TDB1 or TDB2 database: "+dsg.getClass().getSimpleName());
    }

    public GraphFilterTDB2 filterTDB2(DatasetGraph dsg) {
        GraphFilterTDB2 f = GraphFilterTDB2.graphFilter(dsg, graphNames, matchDefaultGraph);
        return f;
    }
    
    public GraphFilterTDB1 filterTDB1(DatasetGraph dsg) {
        GraphFilterTDB1 f = GraphFilterTDB1.graphFilter(dsg, graphNames, matchDefaultGraph);
        return f; 
    }
}

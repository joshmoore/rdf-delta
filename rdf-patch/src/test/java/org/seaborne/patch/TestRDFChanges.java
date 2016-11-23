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

package org.seaborne.patch;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.function.Consumer;

import org.apache.jena.atlas.lib.StrUtils;
import org.apache.jena.graph.Node;
import org.apache.jena.sparql.sse.SSE;
import org.junit.Test;
import org.seaborne.patch.changes.RDFChangesCollector;
import org.seaborne.patch.changes.RDFChangesCounter;
import org.seaborne.patch.changes.RDFChangesN;

public class TestRDFChanges {
    // Write read.
    
    private static Node g1 = SSE.parseNode(":g1"); 
    private static Node g2 = SSE.parseNode("_:g2"); 
    private static Node s1 = SSE.parseNode(":s1"); 
    private static Node s2 = SSE.parseNode("_:s2"); 
    private static Node p1 = SSE.parseNode("<http://example/p1>"); 
    private static Node p2 = SSE.parseNode(":p2"); 
    private static Node o1 = SSE.parseNode("<http://example/o1>");
    private static Node o2 = SSE.parseNode("123");
    
    private static RDFPatch makePatch(Consumer<RDFChanges> action) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RDFChangesCollector changes = new RDFChangesCollector();
        changes.start();
        action.accept(changes);
        changes.finish();
        RDFPatch patch = changes.getRDFPatch();
        return patch ;
    }

    private static byte[] write(Consumer<RDFChanges> action) {
        RDFPatch patch = makePatch(changes->action.accept(changes));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RDFPatchOps.write(out, patch);
        byte[] output = out.toByteArray();
        return output ;
    }

    
    // test basic mechanism
    @Test public void changes_write_read_01() {
        RDFChangesCollector changes = new RDFChangesCollector();
        changes.start();
        changes.txnBegin();
        changes.add(g1, s1, p1, o1);        
        changes.txnCommit();
        changes.finish();
    }
    
    @Test public void changes_write_read_02() {
        RDFPatch patch = makePatch(changes->{
            changes.add(g1, s1, p1, o1);
            changes.add(g1, s1, p1, o1);
        });

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RDFPatchOps.write(out, patch);
        byte[] output = out.toByteArray();
    }
    
    @Test public void changes_write_read_03() {
        RDFPatch patch = makePatch(changes->{
            changes.add(g1, s1, p1, o1);
            changes.add(g1, s1, p1, o1);
        });
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RDFPatchOps.write(out, patch);
        byte[] output = out.toByteArray();
        byte[] output2 = write(changes->{
            changes.add(g1, s1, p1, o1);
            changes.add(g1, s1, p1, o1);
        });
        assertArrayEquals(output, output2);
    }

    @Test public void changes_write_read_04() {
        byte[] output = write(changes->{});
        assertEquals(0, output.length) ;
        String x = StrUtils.fromUTF8bytes(output);
    }

    @Test public void changes_write_read_05() {
        byte[] output = write(changes->{
            changes.txnBegin();
            changes.txnCommit();
        });
        assertNotEquals(0, output.length) ;
        String x = StrUtils.fromUTF8bytes(output);
        assertEquals("TB .\nTC .\n", x);
    }

    @Test public void changes_write_read_06() {
        byte[] output = write(changes->{
            changes.txnBegin();
            changes.add(g1, s1, p1, o1);
            changes.delete(g1, s1, p1, o1);
            changes.txnCommit();
        });
        ByteArrayInputStream in = new ByteArrayInputStream(output);
        
        RDFPatch patch2 = RDFPatchOps.read(in);
        RDFChangesCounter changes2 = new RDFChangesCounter();
        
        patch2.apply(changes2);
        assertEquals(1, changes2.countAddQuad);
        assertEquals(1, changes2.countDeleteQuad);
        assertEquals(1, changes2.countTxnBegin);
        assertEquals(1, changes2.countTxnCommit);
        assertEquals(0, changes2.countTxnAbort);
        assertEquals(0, changes2.countAddPrefix);
        assertEquals(0, changes2.countDeletePrefix);
    }

    @Test public void changes_prefix_01() {
        RDFPatch patch = makePatch((x)->{
            x.txnBegin();
            x.deletePrefix(g2, "ex");
            x.addPrefix(g1, "ex", "http://example/");
            x.addPrefix(g2, "ex", "http://example/");
            x.txnCommit();
        });
        RDFChangesCounter changes = new RDFChangesCounter();
        patch.apply(changes);
        assertEquals(1, changes.countTxnBegin);
        assertEquals(1, changes.countTxnCommit);
        assertEquals(0, changes.countTxnAbort);
        assertEquals(2, changes.countAddPrefix);
        assertEquals(1, changes.countDeletePrefix);
    }

    // Specific implementations.
    
    @Test public void changesN_01() {
        RDFPatch patch = makePatch((x)->{
            x.txnBegin();
            x.add(g1, s1, p1, o1);
            x.add(g2, s1, p1, o1);
            x.txnCommit();
        });
        
        RDFChangesCounter c1 = new RDFChangesCounter();
        RDFChangesCounter c2 = new RDFChangesCounter();
        RDFChanges changes = new RDFChangesN(c1, c2);
        patch.apply(changes);
        assertEquals(1, c1.countTxnBegin);
        assertEquals(1, c1.countTxnCommit);
        assertEquals(0, c1.countTxnAbort);
        assertEquals(2, c1.countAddQuad);
        
        assertEquals(1, c2.countTxnBegin);
        assertEquals(1, c2.countTxnCommit);
        assertEquals(0, c2.countTxnAbort);
        assertEquals(2, c2.countAddQuad);
    }
}

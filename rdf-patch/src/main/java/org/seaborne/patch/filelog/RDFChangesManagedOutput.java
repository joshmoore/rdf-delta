/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  See the NOTICE file distributed with this work for additional
 *  information regarding copyright ownership.
 */

package org.seaborne.patch.filelog;

import java.io.OutputStream;

import org.apache.jena.atlas.io.IO;
import org.apache.jena.atlas.logging.Log;
import org.apache.jena.graph.Node;
import org.seaborne.patch.RDFPatchConst;
import org.seaborne.patch.filelog.rotate.ManagedOutput;
import org.seaborne.patch.system.URNs;
import org.seaborne.patch.text.RDFChangesWriterText;
import org.seaborne.patch.text.TokenWriter;
import org.seaborne.patch.text.TokenWriterText;

/** Log changes to a {@link ManagedOutput}.
 * {@link ManagedOutput} sections
 * */
public class RDFChangesManagedOutput extends RDFChangesWriterText {

    private final ManagedOutput managedOutput;
    private OutputStream currentStream = null;
    private Node previous = null;

    public RDFChangesManagedOutput(ManagedOutput output) {
        super(null);
        this.managedOutput = output;
    }

    @Override
    public void txnBegin() {
        startOutput();
        super.txnBegin();
    }

    @Override
    public void txnCommit() {
        super.txnCommit();
        finishOutput();
    }

    @Override
    public void txnAbort() {
        super.txnAbort();
        finishOutput();
    }

    @Override
    public void segment() {
        super.segment();
    }

    private void startOutput() {
        if ( currentStream != null ) {
            Log.warn(this, "Already writing");
            return;
        }
        currentStream = managedOutput.output();
        TokenWriter tokenWriter = TokenWriterText.create(currentStream);
        super.tok = tokenWriter;
        Node id = URNs.unique();
        header(RDFPatchConst.ID, id);
        if ( previous != null )
            header(RDFPatchConst.PREV, previous);
        previous = id;
    }

    private void finishOutput() {
        super.tok.flush();
        IO.close(currentStream);
        currentStream = null;
        super.tok = null;
    }
}

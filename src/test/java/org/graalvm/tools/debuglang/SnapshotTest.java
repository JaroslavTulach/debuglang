/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.tools.debuglang;

import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Collection;
import java.util.function.Function;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;
import org.netbeans.lib.profiler.heap.GCRoot;
import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.HeapFactory;

public class SnapshotTest {
    @Test
    public void processFib() throws Exception {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        Context c = Context.newBuilder().allowAllAccess(true).out(os).err(os).build();
        Source fibSource = Source.newBuilder("js",
                "(function fib(n) {\n"
                + "  if (n < 2) return 1;\n"
                + "  let n1 = { value : fib(n - 1), id : 'n1' };\n"
                + "  let n2 = { value : fib(n - 2), id : 'n2' };\n"
                + "  return n1.value + n2.value;\n"
                + "})\n",
                "fib.js"
        ).buildLiteral();

        Value fib = c.eval(fibSource);
        final Instrument insightInstrument = c.getEngine().getInstruments().get("insight");
        assertNotNull("insight instrument found", insightInstrument);
        Function<Source,AutoCloseable> insight = insightInstrument.lookup(Function.class);

        Source debugSource = Source.newBuilder("dbg",
            "at fib.js:5 snapshot", "debug.dbg"
        ).buildLiteral();

        insight.apply(debugSource);

        Value twentyOne = fib.execute(7);

        assertEquals(21, twentyOne.asInt());

        c.close();

        File hprof = File.createTempFile("mysnaps", ".hprof");
        try (FileOutputStream fos = new FileOutputStream(hprof)) {
            fos.write(os.toByteArray());
        }
        Heap heap = HeapFactory.createHeap(hprof);
        assertNotNull("Heap loaded", heap);

        Collection<GCRoot> roots = heap.getGCRoots();
        assertFalse("Some roots found: " + roots, roots.isEmpty());

        Source replaySource = Source.newBuilder("dbg", hprof).build();

        Context rectx = Context.newBuilder().allowAllAccess(true).build();
        final int[] allN = { 0, 0, 0 };
        Debugger dbg = Debugger.find(rectx.getEngine());
        DebuggerSession dbgSession = dbg.startSession((event) -> {
            DebugValue n = event.getTopStackFrame().getScope().getDeclaredValue("n");
            DebugValue n1 = event.getTopStackFrame().getScope().getDeclaredValue("n1");
            DebugValue n2 = event.getTopStackFrame().getScope().getDeclaredValue("n2");
            if (n.asInt() == 7) {
                allN[0] = n.asInt();
                allN[1] = n1.getProperty("value").asInt();
                allN[2] = n2.getProperty("value").asInt();
            } else {
                event.getSession().suspendNextExecution();
            }
        });
        dbgSession.suspendNextExecution();
        rectx.eval(replaySource);

        assertEquals("Replayed OK", 7, allN[0]);
        assertEquals(21, allN[1] + allN[2]);

    }
}

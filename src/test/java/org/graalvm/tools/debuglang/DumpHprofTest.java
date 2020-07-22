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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;
import org.netbeans.lib.profiler.heap.GCRoot;
import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.HeapFactory;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.JavaClass;
import org.netbeans.lib.profiler.heap.ThreadObjectGCRoot;

public class DumpHprofTest {
    @Test
    public void singleObject() throws IOException {
        File mydump = File.createTempFile("mydump", ".hprof");
        generateSingleObject(new FileOutputStream(mydump));
        Heap heap = HeapFactory.createHeap(mydump);
        List<JavaClass> allClasses = heap.getAllClasses();
        assertEquals(4, allClasses.size());
        assertEquals("java.lang.String", allClasses.get(0).getName());
        assertEquals("char[]", allClasses.get(1).getName());
        assertEquals("text.HelloWorld", allClasses.get(2).getName());

        Collection<GCRoot> roots = new ArrayList<>(heap.getGCRoots());
        assertEquals("Thread & two locals", 4, roots.size());
        roots.removeIf((t) -> {
            return ! (t instanceof ThreadObjectGCRoot);
        });
        assertEquals("Only one thread", 1, roots.size());
        final Iterator<GCRoot> it = roots.iterator();
        final Instance thread = it.next().getInstance();

        Object daemon = thread.getValueOfField("daemon");
        assertNotNull("daemon field found", daemon);
        Instance value = (Instance) thread.getValueOfField("name");
        assertNotNull("name assigned", value);
        assertEquals("java.lang.String", value.getJavaClass().getName());
        assertEquals(Boolean.class, daemon.getClass());
        assertFalse("It is not daemon", (Boolean)daemon);
    }


    private static void generateSingleObject(OutputStream os) throws IOException {
        HprofGenerator gen = new HprofGenerator(os);
        gen.writeHeapSegment(DumpHprofTest::sampleDumpMemory);
        gen.close();
    }

    private static void sampleDumpMemory(HprofGenerator.HeapSegment seg) throws IOException {
        int mainId = seg.dumpString("main");

        HprofGenerator.ClassInstance clazz = seg.newClass("text.HelloWorld")
                .addField("daemon", Boolean.TYPE)
                .addField("name", String.class)
                .addField("priority", int.class)
                .dumpClass();

        int threadOne = seg.dumpInstance(clazz);
        int threadTwo = seg.dumpInstance(clazz, "daemon", 0, "name", mainId, "priority", 10);

        seg.newThread("main")
                .addStackFrame("HelloWorld", "HelloWorld.js", 11, mainId, threadOne, threadTwo)
                .dumpThread();
    }
}

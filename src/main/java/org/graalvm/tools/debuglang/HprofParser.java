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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.netbeans.lib.profiler.heap.FieldValue;
import org.netbeans.lib.profiler.heap.GCRoot;
import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.HeapFactory;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.JavaFrameGCRoot;
import org.netbeans.lib.profiler.heap.ThreadObjectGCRoot;

final class HprofParser {
    static DbgProgramNode parse(DbgLanguage language, File hprof) throws IOException {
        Heap heap = HeapFactory.createHeap(hprof);
        Collection<GCRoot> roots = heap.getGCRoots();
        List<DbgAt> statements = new ArrayList<>();
        Map<ThreadObjectGCRoot, List<JavaFrameGCRoot>> map = new HashMap<>();
        for (GCRoot r : roots) {
            if (r instanceof ThreadObjectGCRoot) {
                ThreadObjectGCRoot t = (ThreadObjectGCRoot) r;
                StackTraceElement at = t.getStackTrace()[0];
                List<JavaFrameGCRoot> list = map.get(t);
                final String file = at.getFileName();
                final int line = at.getLineNumber();
                assert list != null;
                List<DbgAtWatch> copy = new ArrayList<>();
                for (JavaFrameGCRoot f : list) {
                    for (Object o : f.getInstance().getFieldValues()) {
                        FieldValue fieldValue = (FieldValue) o;
                        Instance value = (Instance) f.getInstance().getValueOfField(fieldValue.getField().getName());
                        copy.add(new DbgAtWatch(fieldValue.getField().getName(), new HprofInstance(value)));
                    }
                }
                statements.add(new DbgAt(file, line, copy));
            } else if (r instanceof JavaFrameGCRoot) {
                JavaFrameGCRoot f = (JavaFrameGCRoot) r;
                List<JavaFrameGCRoot> list = map.get(f.getThreadGCRoot());
                if (list == null) {
                    list = new ArrayList<>();
                    map.put(f.getThreadGCRoot(), list);
                }
                list.add(f);
            }
        }
        return  new DbgProgramNode(language, statements);
    }
}

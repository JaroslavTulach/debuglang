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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import org.graalvm.tools.debuglang.HprofGenerator.ClassInstance;

final class HprofDump {
    private final HprofGenerator generator;
    private final Map<TreeSet<String>,ClassInstance> classes = new HashMap<>();

    public HprofDump(OutputStream os) throws IOException {
        this.generator = new HprofGenerator(os);
    }

    @CompilerDirectives.TruffleBoundary
    void dumpFrame(String rootName, String src, int line, Object frame) throws IOException {
        generator.writeHeapSegment((seg) -> {
            try {
                InteropLibrary iop = InteropLibrary.getFactory().getUncached();
                int frameId = dumpObject(iop, seg, rootName, frame, 3);
                int threadId = seg.newThread("main#" + frameId)
                    .addStackFrame(rootName, src, line, frameId)
                    .dumpThread();
            } catch (InteropException ex) {
                throw new IOException(rootName + " frame " + frame, ex);
            }
        });
    }

    private int dumpObject(InteropLibrary iop, HprofGenerator.HeapSegment seg, String metaName, Object obj, int depth)
    throws UnknownIdentifierException, IOException, UnsupportedMessageException {
        if (depth <= 0) {
            return 0;
        }
        if (obj instanceof String) {
            return seg.dumpString((String) obj);
        }
        if (!(obj instanceof TruffleObject)) {
            return seg.dumpPrimitive(obj);
        }
        TreeSet<String> sortedNames = new TreeSet<>();
        ClassInstance clazz = findClass(iop, seg, metaName, sortedNames, obj);
        Object[] values = new Object[sortedNames.size() * 2];
        int cnt = 0;
        for (String n : sortedNames) {
            values[cnt] = n;
            final Object v = iop.readMember(obj, n);
            values[cnt + 1] = dumpObject(iop, seg, null, v, depth - 1);
            cnt += 2;
        }
        return seg.dumpInstance(clazz, values);
    }

    ClassInstance findClass(InteropLibrary iop, HprofGenerator.HeapSegment seg, String metaName, TreeSet<String> sortedNames, Object obj) throws IOException {
        try {
            Object names = iop.getMembers(obj);
            long len = iop.getArraySize(names);
            for (long i = 0; i < len; i++) {
                sortedNames.add(iop.readArrayElement(names, i).toString());
            }
        } catch (UnsupportedMessageException ex) {
            // no names
        } catch (InteropException ex) {
            throw new IOException("Object " + obj, ex);
        }

        ClassInstance clazz = classes.get(sortedNames);
        if (clazz == null) {
            if (metaName == null) {
                try {
                    Object meta = iop.getMetaObject(obj);
                    metaName = iop.asString(iop.getMetaQualifiedName(meta));
                } catch (UnsupportedMessageException unsupportedMessageException) {
                    metaName = "Frame";
                }
            }
            StringBuilder fullName = new StringBuilder(metaName);
            for (String n : sortedNames) {
                fullName.append('.').append(n);
            }
            HprofGenerator.HeapSegment.ClassBuilder builder = seg.newClass(fullName.toString());
            for (String n : sortedNames) {
                builder.addField(n, Object.class);
            }
            clazz = builder.dumpClass();
            classes.put(sortedNames, clazz);
        }
        return clazz;
    }

    void close() {
        try {
            generator.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}

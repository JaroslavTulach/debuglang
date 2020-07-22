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
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;
import org.netbeans.lib.profiler.heap.GCRoot;
import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.HeapFactory;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.JavaClass;

public class DumpHprofTest {
    static final String MAGIC_WITH_SEGMENTS = "JAVA PROFILE 1.0.2"; // NOI18N

    @Test
    public void singleObject() throws IOException {
        File mydump = File.createTempFile("mydump", ".hprof");
        generateSingleObject(new FileOutputStream(mydump));
        Heap heap = HeapFactory.createHeap(mydump);
        List<JavaClass> allClasses = heap.getAllClasses();
        assertEquals(3, allClasses.size());
        assertEquals("java.lang.String", allClasses.get(0).getName());
        assertEquals("char[]", allClasses.get(1).getName());
        assertEquals("text.HelloWorld", allClasses.get(2).getName());

        Collection<GCRoot> roots = heap.getGCRoots();
        assertEquals(1, roots.size());
        final Instance thread = roots.iterator().next().getInstance();

        Object daemon = thread.getValueOfField("daemon");
        assertNotNull("daemon field found", daemon);
        Instance value = (Instance) thread.getValueOfField("name");
        assertNotNull("name assigned", value);
        assertEquals("java.lang.String", value.getJavaClass().getName());
        assertEquals(Boolean.class, daemon.getClass());
        assertFalse("It is not daemon", (Boolean)daemon);
    }

    private static int stringCounter = 0;
    private static int writeString(String text, DataOutputStream os) throws IOException {
        os.writeByte(0x01);
        os.writeInt(0); // ms
        byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
        os.writeInt(4 + utf8.length);
        os.writeInt(stringCounter);
        os.write(utf8);
        return stringCounter++;
    }

    private static void writeThreadStarted(int id, String threadName, String groupName, int stackTraceId, DataOutputStream os) throws IOException {
        int threadNameId = writeString(threadName, os);
        int groupNameId = writeString(groupName, os);

        os.writeByte(0x0A);
        os.writeInt(0); // ms
        os.writeInt(6 * 4);
        os.writeInt(id); // serial number
        os.writeInt(id); // object id
        os.writeInt(stackTraceId); // stacktrace serial number
        os.writeInt(threadNameId);
        os.writeInt(groupNameId);
        os.writeInt(0); // parent group
    }

    private static void writeStackFrame(int id, String rootName, String sourceFile, int lineNumber, DataOutputStream os) throws IOException {
        int rootNameId = writeString(rootName, os);
        int signatureId = 0;
        int sourceFileId = writeString(sourceFile, os);

        os.writeByte(0x04);
        os.writeInt(0); // ms
        os.writeInt(6 * 4);
        os.writeInt(id);
        os.writeInt(rootNameId);
        os.writeInt(signatureId);
        os.writeInt(sourceFileId);
        os.writeInt(0);
        os.writeInt(lineNumber);
    }

    private static void writeStackTrace(int id, String rootName, String sourceFile, int lineNumber, DataOutputStream os) throws IOException {
        writeStackFrame(id, rootName, sourceFile, lineNumber, os);

        os.writeByte(0x05);
        os.writeInt(0); // ms
        os.writeInt(4 * 4);
        os.writeInt(id);
        os.writeInt(id);
        os.writeInt(1);
        os.writeInt(id);
    }

    private static void writeLoadClass(int id, int stackTrace, String className, DataOutputStream os) throws IOException {
        int classNameId = writeString(className, os);

        os.writeByte(0x02);
        os.writeInt(0); // ms
        os.writeInt(4 * 4);
        os.writeInt(id); // class serial number
        os.writeInt(id); // class object ID
        os.writeInt(stackTrace); // stack trace serial number
        os.writeInt(classNameId); // class name string ID
    }

    private static void generateSingleObject(OutputStream os) throws IOException {
        DataOutputStream dos = new DataOutputStream(os);
        dos.write(MAGIC_WITH_SEGMENTS.getBytes());
        dos.write(0);
        dos.writeInt(4);
        dos.writeLong(System.currentTimeMillis());
        int emptyStringId = writeString("", dos);
        assert emptyStringId == 0;
        writeThreadStarted(77, "main", "test", 22, dos);
        writeStackTrace(22, "HelloWorld", "HelloWorld.js", 11, dos);
        writeLoadClass(1, 22, "java.lang.String", dos);
        writeLoadClass(2, 22, "char[]", dos);
        writeLoadClass(55, 22, "text.HelloWorld", dos);
        sampleDumpMemory(dos);
        dos.close();
    }

    private static ClassBuilder string;
    private static void sampleDumpMemory(DataOutputStream whole) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        DataOutputStream heap = new DataOutputStream(os);

        string = ClassBuilder.newBuilder(1)
            .addField("value", Object.class)
            .addField("hash", Integer.TYPE)
            .dumpClass(whole, heap);

        ClassBuilder charArray = ClassBuilder.newBuilder(2)
                .dumpClass(whole, heap);

        int mainId = dumpString("main", whole, heap);
        ClassBuilder.newBuilder(55)
            .addField("daemon", Boolean.TYPE)
            .addField("name", String.class)
            .dumpClass(whole, heap)
            .dumpInstance(99, Collections.emptyMap(), heap)
            .dumpInstance(77, map("daemon", 0, "name", mainId), heap);
        genereateThreadDump(77, 22, heap);
        heap.close();

        whole.writeByte(0x1c);
        whole.writeInt(0); // ms
        whole.writeInt(os.toByteArray().length);
        whole.write(os.toByteArray());

        whole.writeByte(0x2C);
        whole.writeInt(0); // ms
        whole.writeInt(0); // end of message
    }

    private static final class ClassBuilder {
        private final int classId;
        private final Map<String,Class<?>> fieldNamesAndTypes = new LinkedHashMap<>();
        private int fieldBytes;

        private ClassBuilder(int id) {
            this.classId = id;
        }

        public static ClassBuilder newBuilder(int id) {
            return new ClassBuilder(id);
        }

        public ClassBuilder addField(String name, Class<?> type) {
            fieldNamesAndTypes.put(name, type);
            return this;
        }

        public ClassBuilder dumpClass(DataOutputStream os, DataOutputStream heap) throws IOException {
            heap.writeByte(0x20);
            heap.writeInt(classId); // class ID
            heap.writeInt(classId); // stacktrace serial number
            heap.writeInt(0); // superclass ID
            heap.writeInt(0); // classloader ID
            heap.writeInt(0); // signers ID
            heap.writeInt(0); // protection domain ID
            heap.writeInt(0); // reserved 1
            heap.writeInt(0); // reserved 2
            heap.writeInt(0); // instance size
            heap.writeShort(0); // # of constant pool entries
            heap.writeShort(0); // # of static fields
            heap.writeShort(fieldNamesAndTypes.size()); // # of instance fields
            fieldBytes = 0;
            for (Map.Entry<String, Class<?>> entry : fieldNamesAndTypes.entrySet()) {
                int nId = writeString(entry.getKey(), os);
                heap.writeInt(nId);
                if (entry.getValue().isPrimitive()) {
                    if (entry.getValue() == Integer.TYPE) {
                        heap.writeByte(0x0a); // int
                        fieldBytes += 4;
                    } else {
                        assert entry.getValue() == Boolean.TYPE : "Yet unsupported type: " + entry.getValue();
                        heap.writeByte(0x04); // boolean

                        fieldBytes++;
                    }
                } else {
                    heap.writeByte(0x02); // object
                    fieldBytes += 4;
                }
            }
            return this;
        }

        public ClassBuilder dumpInstance(int instanceId, Map<String,Integer> values, DataOutputStream os) throws IOException {
            os.writeByte(0x21);
            os.writeInt(instanceId);
            os.writeInt(instanceId); // serial number
            os.writeInt(classId);
            os.writeInt(fieldBytes);
            for (Map.Entry<String, Class<?>> entry : fieldNamesAndTypes.entrySet()) {
                Integer ref = values.get(entry.getKey());
                if (entry.getValue() == Boolean.TYPE) {
                    os.writeByte(ref == null ? 0 : ref.intValue());
                } else {
                    os.writeInt(ref == null ? 0 : ref.intValue());
                }
            }
            return this;
        }
    }

    private static int dumpString(String text, DataOutputStream os, DataOutputStream heap) throws IOException {
        int instanceId = stringCounter++;

        heap.writeByte(0x23);
        heap.writeInt(instanceId);
        heap.writeInt(instanceId); // serial number
        heap.writeInt(text.length()); // number of elements
        heap.writeByte(0x05); // char
        for (char ch : text.toCharArray()) {
            heap.writeChar(ch);
        }
        int stringId = stringCounter++;
        string.dumpInstance(stringId, map("value", instanceId, "hash", 0), heap);
        return stringId;
    }

    private static Map<String, Integer> map(Object... values) {
        Map<String,Integer> map = new HashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put((String) values[i], (Integer) values[i + 1]);

        }
        return map;
    }

    private static void genereateThreadDump(int id, int stackTraceId, DataOutputStream os) throws IOException {
        os.writeByte(0x08);
        os.writeInt(id); // object ID
        os.writeInt(id); // serial #
        os.writeInt(stackTraceId); // stacktrace #
    }


}

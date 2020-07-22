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
import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

final class HprofGenerator implements Closeable {
    private static final String MAGIC_WITH_SEGMENTS = "JAVA PROFILE 1.0.2";

    private final Map<String,Integer> strings = new HashMap<>();
    final DataOutputStream whole;
    private int objectCounter;
    private ClassInstance typeCharArray;
    private ClassInstance typeString;

    HprofGenerator(OutputStream os) throws IOException {
        this.whole = new DataOutputStream(os);
        whole.write(MAGIC_WITH_SEGMENTS.getBytes());
        whole.write(0);
        whole.writeInt(4);
        whole.writeLong(System.currentTimeMillis());
    }
    
    interface Generator<T> {
        void generate(T data) throws IOException;
    }
    
    public class HeapSegment {
        private final ByteArrayOutputStream arr = new ByteArrayOutputStream();
        final DataOutputStream heap = new DataOutputStream(arr);
        
        public ClassBuilder newClass(String name) throws IOException {
            int classId = writeLoadClass(0, name);
            return new ClassBuilder(classId, name);
        }

        private void close() throws IOException {
            whole.writeByte(0x1c);
            whole.writeInt(0); // ms
            final byte[] bytes = arr.toByteArray();
            whole.writeInt(bytes.length);
            whole.write(bytes);
        }

        public int dumpString(String text) throws IOException {
            int instanceId = ++objectCounter;

            heap.writeByte(0x23);
            heap.writeInt(instanceId);
            heap.writeInt(instanceId); // serial number
            heap.writeInt(text.length()); // number of elements
            heap.writeByte(0x05); // char
            for (char ch : text.toCharArray()) {
                heap.writeChar(ch);
            }
            return dumpInstance(typeString, "value", instanceId, "hash", 0);
        }

        public int dumpInstance(ClassInstance clazz, Object... stringIntSequence) throws IOException {
            HashMap<String,Integer> values = new HashMap<>();
            for (int i = 0; i < stringIntSequence.length; i += 2) {
                values.put((String) stringIntSequence[i], (Integer) stringIntSequence[i + 1]);
            }
            
            int instanceId = ++objectCounter;
            heap.writeByte(0x21);
            heap.writeInt(instanceId);
            heap.writeInt(instanceId); // serial number
            heap.writeInt(clazz.id);
            heap.writeInt(clazz.fieldBytes);
            for (Map.Entry<String, Class<?>> entry : clazz.fieldNamesAndTypes.entrySet()) {
                Integer ref = values.get(entry.getKey());
                if (entry.getValue() == Boolean.TYPE) {
                    heap.writeByte(ref == null ? 0 : ref.intValue());
                } else {
                    heap.writeInt(ref == null ? 0 : ref.intValue());
                }
            }
            return instanceId;
        }
        
        public final class ClassBuilder {
            private final int classId;
            private final String className;
            private TreeMap<String, Class<?>> fieldNamesAndTypes = new TreeMap<>();

            private ClassBuilder(int id, String name) {
                this.classId = id;
                this.className = name;
            }

            public ClassBuilder addField(String name, Class<?> type) {
                fieldNamesAndTypes.put(name, type);
                return this;
            }

            public ClassInstance dumpClass() throws IOException {
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
                int fieldBytes = 0;
                for (Map.Entry<String, Class<?>> entry : fieldNamesAndTypes.entrySet()) {
                    int nId = writeString(entry.getKey());
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
                ClassInstance inst = new ClassInstance(classId, className, fieldNamesAndTypes, fieldBytes);
                fieldNamesAndTypes = new TreeMap<>();
                return inst;
            }
        }
    }
    
    public final class ClassInstance {
        private final int id;
        private final TreeMap<String, Class<?>> fieldNamesAndTypes;
        private final int fieldBytes;

        private ClassInstance(int id, String className, TreeMap<String, Class<?>> fieldNamesAndTypes, int fieldBytes) {
            this.id = id;
            this.fieldNamesAndTypes = fieldNamesAndTypes;
            this.fieldBytes = fieldBytes;
        }
    }
    
    public void writeHeapSegment(Generator<HeapSegment> generator) throws IOException {
        HeapSegment seg = new HeapSegment();
        if (typeString == null) {
            typeString = seg.newClass("java.lang.String")
                    .addField("value", char[].class)
                    .addField("hash", Integer.TYPE)
                    .dumpClass();
            typeCharArray = seg.newClass("char[]")
                    .dumpClass();
        }
        generator.generate(seg);
        seg.close();
    }
    
    public void writeThreadStarted(int id, String threadName, String groupName, int stackTraceId) throws IOException {
        int threadNameId = writeString(threadName);
        int groupNameId = writeString(groupName);

        whole.writeByte(0x0A);
        whole.writeInt(0); // ms
        whole.writeInt(6 * 4);
        whole.writeInt(id); // serial number
        whole.writeInt(id); // object id
        whole.writeInt(stackTraceId); // stacktrace serial number
        whole.writeInt(threadNameId);
        whole.writeInt(groupNameId);
        whole.writeInt(0); // parent group
    }

    public void writeStackFrame(int id, String rootName, String sourceFile, int lineNumber) throws IOException {
        int rootNameId = writeString(rootName);
        int signatureId = 0;
        int sourceFileId = writeString(sourceFile);

        whole.writeByte(0x04);
        whole.writeInt(0); // ms
        whole.writeInt(6 * 4);
        whole.writeInt(id);
        whole.writeInt(rootNameId);
        whole.writeInt(signatureId);
        whole.writeInt(sourceFileId);
        whole.writeInt(0);
        whole.writeInt(lineNumber);
    }

    public void writeStackTrace(int id, String rootName, String sourceFile, int lineNumber) throws IOException {
        writeStackFrame(id, rootName, sourceFile, lineNumber);

        whole.writeByte(0x05);
        whole.writeInt(0); // ms
        whole.writeInt(4 * 4);
        whole.writeInt(id);
        whole.writeInt(id);
        whole.writeInt(1);
        whole.writeInt(id);
    }

    @Override
    public void close() throws IOException {
        whole.close();
    }

    // internal primitives
    
    private int writeLoadClass(int stackTrace, String className) throws IOException {
        int classId = ++objectCounter;
        int classNameId = writeString(className);
        
        whole.writeByte(0x02);
        whole.writeInt(0); // ms
        whole.writeInt(4 * 4);
        whole.writeInt(classId); // class serial number
        whole.writeInt(classId); // class object ID
        whole.writeInt(stackTrace); // stack trace serial number
        whole.writeInt(classNameId); // class name string ID
        
        return classId;
    }

    private int writeString(String text) throws IOException {
        Integer prevId = strings.get(text);
        if (prevId != null) {
            return prevId;
        }
        int stringId = ++objectCounter;
        whole.writeByte(0x01);
        whole.writeInt(0); // ms
        byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
        whole.writeInt(4 + utf8.length);
        whole.writeInt(stringId);
        whole.write(utf8);
        
        strings.put(text, stringId);
        return stringId;
    }
}

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class HprofGenerator implements Closeable {
    private static final String MAGIC_WITH_SEGMENTS = "JAVA PROFILE 1.0.2";

    private final Map<String,Integer> wholeStrings = new HashMap<>();
    private final Map<String,Integer> heapStrings = new HashMap<>();
    private final Map<Class<?>,ClassInstance> primitiveClasses = new HashMap<>();
    private final Map<Object,Integer> primitives = new HashMap<>();
    final DataOutputStream whole;
    private int objectCounter;
    private ClassInstance typeCharArray;
    private ClassInstance typeString;
    private ClassInstance typeThread;

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

        public ThreadBuilder newThread(String name) throws IOException {
            int classId = ++objectCounter;
            return new ThreadBuilder(classId, name);
        }

        private void close() throws IOException {
            whole.writeByte(0x1c);
            whole.writeInt(0); // ms
            final byte[] bytes = arr.toByteArray();
            whole.writeInt(bytes.length);
            whole.write(bytes);
        }

        public int dumpString(String text) throws IOException {
            if (text == null) {
                return 0;
            }
            Integer id = heapStrings.get(text);
            if (id != null) {
                return id;
            }
            
            int instanceId = ++objectCounter;

            heap.writeByte(0x23);
            heap.writeInt(instanceId);
            heap.writeInt(instanceId); // serial number
            heap.writeInt(text.length()); // number of elements
            heap.writeByte(0x05); // char
            for (char ch : text.toCharArray()) {
                heap.writeChar(ch);
            }
            int stringId = dumpInstance(typeString, "value", instanceId, "hash", 0);
            
            heapStrings.put(text, stringId);
            return stringId;
        }

        public int dumpInstance(ClassInstance clazz, Object... stringValueSeq) throws IOException {
            HashMap<String,Object> values = new HashMap<>();
            for (int i = 0; i < stringValueSeq.length; i += 2) {
                values.put((String) stringValueSeq[i], stringValueSeq[i + 1]);
            }
            
            int instanceId = ++objectCounter;
            heap.writeByte(0x21);
            heap.writeInt(instanceId);
            heap.writeInt(instanceId); // serial number
            heap.writeInt(clazz.id);
            heap.writeInt(clazz.fieldBytes);
            for (Map.Entry<String, Class<?>> entry : clazz.fieldNamesAndTypes.entrySet()) {
                final Class<?> type = entry.getValue();
                final Object ref = values.get(entry.getKey());
                if (type == Boolean.TYPE || type == Byte.TYPE) {
                    heap.writeByte(ref == null ? 0 : ((Number)ref).byteValue());
                } else if (entry.getValue() == Short.TYPE) {
                    heap.writeShort(ref == null ? 0 : ((Number)ref).shortValue());
                } else if (entry.getValue() == Long.TYPE) {
                    heap.writeLong(ref == null ? 0 : ((Number)ref).longValue());
                } else if (entry.getValue() == Float.TYPE) {
                    heap.writeFloat(ref == null ? 0 : ((Number)ref).floatValue());
                } else if (entry.getValue() == Double.TYPE) {
                    heap.writeDouble(ref == null ? 0 : ((Number)ref).doubleValue());
                } else if (entry.getValue() == Character.TYPE) {
                    heap.writeChar(ref == null ? 0 : ((Character)ref).charValue());
                } else {
                    heap.writeInt(ref == null ? 0 : ((Number)ref).intValue());
                }
            }
            return instanceId;
        }

        public int dumpPrimitive(Object obj) throws IOException {
            Integer id = primitives.get(obj);
            if (id != null) {
                return id;
            }
            
            final Class<? extends Object> clazz = obj.getClass();
            ClassInstance wrapperClass = primitiveClasses.get(clazz);
            if (wrapperClass == null) {
                try {
                    assert clazz.getName().startsWith("java.lang.");
                    Class<?> primitiveType = clazz.getDeclaredField("value").getType();
                    assert primitiveType.isPrimitive();

                    wrapperClass = newClass(clazz.getName())
                            .addField("value", primitiveType)
                            .dumpClass();
                    primitiveClasses.put(clazz, wrapperClass);
                } catch (ReflectiveOperationException ex) {
                    throw new IOException("Processing " + obj, ex);
                }
            }
            int instanceId = dumpInstance(wrapperClass, "value", obj);
            primitives.put(obj, instanceId);
            return instanceId;
        }

        public final class ThreadBuilder {
            private String groupName;
            private List<Object[]> stacks;
            private final String name;
            private final int classId;

            private ThreadBuilder(int classId, String name) {
                this.stacks = new ArrayList<>();
                this.classId = classId;
                this.name = name;
            }

            public ThreadBuilder group(String name) {
                this.groupName = name;
                return this;
            }

            public ThreadBuilder addStackFrame(String rootName, String sourceFile, int lineNumber, int... locals) {
                stacks.add(new Object[]{rootName, sourceFile, lineNumber, locals});
                return this;
            }

            public int dumpThread() throws IOException {
                if (typeThread == null) {
                    typeThread = newClass("java.lang.Thread")
                            .addField("daemon", Boolean.TYPE)
                            .addField("name", String.class)
                            .addField("priority", int.class)
                            .dumpClass();
                }
                int nameId = dumpString(name);
                int threadId = dumpInstance(typeThread, "daemon", 0, "name", nameId, "priority", 0);
                
                int[] frameIds = new int[stacks.size()];
                int cnt = 0;
                for (Object[] frame : stacks) {
                    frameIds[cnt++] = writeStackFrame((String) frame[0], (String) frame[1], (Integer) frame[2]);
                }
                int stackTraceId = writeStackTrace(threadId, frameIds);
                writeThreadStarted(threadId, name, groupName, stackTraceId);
                
                heap.writeByte(0x08);
                heap.writeInt(threadId); // object ID
                heap.writeInt(threadId); // serial #
                heap.writeInt(stackTraceId); // stacktrace #

                cnt = 0;
                for (Object[] frame : stacks) {
                    int[] locals = (int[]) frame[3];
                    for (int objId : locals) {
                        heap.writeByte(0x03); // frame GC root
                        heap.writeInt(objId);
                        heap.writeInt(threadId); // thread serial #
                        heap.writeInt(cnt); // frame number
                    }
                    cnt++;
                }
                
                return threadId;
            }
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
    
    @Override
    public void close() throws IOException {
        whole.close();
    }

    // internal primitives

    private void writeThreadStarted(int id, String threadName, String groupName, int stackTraceId) throws IOException {
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

    private int writeStackFrame(String rootName, String sourceFile, int lineNumber) throws IOException {
        int id = ++objectCounter;
        
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
        
        return id;
    }

    private int writeStackTrace(int threadId, int... frames) throws IOException {
        int id = ++objectCounter;
        
        whole.writeByte(0x05);
        whole.writeInt(0); // ms
        whole.writeInt(4 * 4);
        whole.writeInt(id);
        whole.writeInt(threadId);
        whole.writeInt(frames.length);
        for (int fId : frames) {
            whole.writeInt(fId);
        }
        
        return id;
    }
    
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
        if (text == null) {
            return 0;
        }
        Integer prevId = wholeStrings.get(text);
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
        
        wholeStrings.put(text, stringId);
        return stringId;
    }
}

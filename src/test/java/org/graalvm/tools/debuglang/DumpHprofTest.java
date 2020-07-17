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
import java.util.List;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.HeapFactory;
import org.netbeans.lib.profiler.heap.JavaClass;

public class DumpHprofTest {
    static final String MAGIC_WITH_SEGMENTS = "JAVA PROFILE 1.0.2"; // NOI18N

    @Test
    public void singleObject() throws IOException {
        File mydump = File.createTempFile("mydump", ".hprof");
        generateSingleObject(new FileOutputStream(mydump));
        Heap heap = HeapFactory.createHeap(mydump);
        List<JavaClass> allClasses = heap.getAllClasses();
        assertEquals(1, allClasses.size());
        assertEquals("text.HelloWorld", allClasses.get(0).getName());
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
        writeStackTrace(22, "HelloWorld", "HelloWorld.js", 11, dos);
        writeLoadClass(55, 22, "text.HelloWorld", dos);
        sampleDumpMemory(dos);
        dos.close();
    }

    private static void sampleDumpMemory(DataOutputStream whole) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(os);
        generateClassDump(55, dos);
        generateInstanceDump(99, 55, dos);
        dos.close();

        whole.writeByte(0x1c);
        whole.writeInt(0); // ms
        whole.writeInt(os.toByteArray().length);
        whole.write(os.toByteArray());

        whole.writeByte(0x2C);
        whole.writeInt(0); // ms
        whole.writeInt(0); // end of message
    }

    private static void generateClassDump(int id, DataOutputStream os) throws IOException {
        os.writeByte(0x20);
        os.writeInt(id); // class ID
        os.writeInt(id); // stacktrace serial number
        os.writeInt(0); // superclass ID
        os.writeInt(0); // classloader ID
        os.writeInt(0); // signers ID
        os.writeInt(0); // protection domain ID
        os.writeInt(0); // reserved 1
        os.writeInt(0); // reserved 2
        os.writeInt(0); // instance size
        os.writeShort(0); // # of constant pool entries
        os.writeShort(0); // # of static fields
        os.writeShort(0); // # of instance fields
    }

    private static void generateInstanceDump(int id, int classId, DataOutputStream os) throws IOException {
        os.writeByte(0x21);
        os.writeInt(id);
        os.writeInt(id); // serial number
        os.writeInt(classId);
        os.writeInt(0); // no fields
    }
}

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

import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

final class HprofGenerator implements Closeable {
    private static final String MAGIC_WITH_SEGMENTS = "JAVA PROFILE 1.0.2";

    final DataOutputStream whole;
    private int objectCounter;

    HprofGenerator(OutputStream os) throws IOException {
        this.whole = new DataOutputStream(os);
        whole.write(MAGIC_WITH_SEGMENTS.getBytes());
        whole.write(0);
        whole.writeInt(4);
        whole.writeLong(System.currentTimeMillis());
    }
    
    public int writeString(String text) throws IOException {
        int stringId = ++objectCounter;
        whole.writeByte(0x01);
        whole.writeInt(0); // ms
        byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
        whole.writeInt(4 + utf8.length);
        whole.writeInt(stringId);
        whole.write(utf8);
        return stringId;
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

    public void writeLoadClass(int id, int stackTrace, String className) throws IOException {
        int classNameId = writeString(className);

        whole.writeByte(0x02);
        whole.writeInt(0); // ms
        whole.writeInt(4 * 4);
        whole.writeInt(id); // class serial number
        whole.writeInt(id); // class object ID
        whole.writeInt(stackTrace); // stack trace serial number
        whole.writeInt(classNameId); // class name string ID
    }

    @Override
    public void close() throws IOException {
        whole.close();
    }
    
}

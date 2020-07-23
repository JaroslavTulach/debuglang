/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.debug.DebuggerTags;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import java.io.IOException;

final class DbgNodeAt extends RootNode {
    @Child
    Statement statement;
    private final String file;
    private final int line;

    DbgNodeAt(DbgLanguage lang, FrameDescriptor fd, String file, int line) {
        super(lang, fd);
        statement = new Statement();
        this.file = file;
        this.line = line;
    }

    @Override
    public String getName() {
        return file + ":" + line;
    }

    @Override
    public SourceSection getSourceSection() {
        CompilerAsserts.neverPartOfCompilation();
        TruffleLanguage.Env env = lookupContextReference(DbgLanguage.class).get().env;
        TruffleFile truffleFile = env.getPublicTruffleFile(file);
        Source src = null;
        String msg = "";
        try {
            if (truffleFile != null) {
                src = Source.newBuilder("dbg", truffleFile).build();
            }
        } catch (SecurityException | IOException ex) {
            msg = ex.getMessage();
        }
        if (src == null) {
            return Source.newBuilder("dbg", msg, file).build().createSection(1);
        }
        return src.createSection(line);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        DbgAt at = (DbgAt) frame.getArguments()[0];
        fillFrame(at, frame);
        statement.executeStatement(frame);
        return 0;
    }

    @CompilerDirectives.TruffleBoundary
    private void fillFrame(DbgAt at, Frame frame) {
        for (DbgAtWatch w : at.actions) {
            if (w.value != null) {
                FrameSlot slot = frame.getFrameDescriptor().findFrameSlot(w.variableName);
                frame.setInt(slot, w.value);
            }
        }
    }

    @GenerateWrapper
    static class Statement extends Node implements InstrumentableNode {
        @Override
        public SourceSection getSourceSection() {
            return getRootNode().getSourceSection();
        }

        void executeStatement(VirtualFrame frame) {
        }

        @Override
        public boolean isInstrumentable() {
            return true;
        }

        @Override
        public WrapperNode createWrapper(ProbeNode probe) {
            return new StatementWrapper(this, probe);
        }

        @Override
        public boolean hasTag(Class<? extends Tag> tag) {
            return
                DebuggerTags.AlwaysHalt.class == tag ||
                StandardTags.StatementTag.class == tag;
        }
    }
}

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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

@ExportLibrary(value = InteropLibrary.class)
final class DbgAt implements TruffleObject {

    final String file;
    final int line;
    final List<DbgAtWatch> actions;
    @CompilerDirectives.CompilationFinal
    private CallTarget target;

    DbgAt(String file, int line, List<DbgAtWatch> actions) {
        this.file = file;
        this.line = line;
        this.actions = actions;
    }

    final void register(Object argument) {
        InteropLibrary iop = InteropLibrary.getFactory().getUncached();
        try {
            iop.invokeMember(argument, "on", "enter", this, this);
        } catch (InteropException ex) {
            throw DbgLanguage.raise(RuntimeException.class, ex);
        }
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 67 * hash + Objects.hashCode(this.file);
        hash = 67 * hash + this.line;
        for (DbgAtWatch w : this.actions) {
            hash = 67 * hash + Objects.hashCode(w.variableName);
        }
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final DbgAt other = (DbgAt) obj;
        return variableNames(this.actions).equals(variableNames(other.actions));
    }

    static Set<String> variableNames(List<DbgAtWatch> all) {
        Set<String> set = new HashSet<>();
        for (DbgAtWatch w : all) {
            set.add(w.variableName);
        }
        return set;
    }

    static String findSrc(Object[] args) {
        try {
            InteropLibrary iop = InteropLibrary.getFactory().getUncached();
            Object src = iop.readMember(args[0], "source");
            return iop.asString(iop.readMember(src, "name"));
        } catch (InteropException ex) {
            throw DbgLanguage.raise(RuntimeException.class, ex);
        }
    }

    static int findLine(Object[] args) {
        try {
            InteropLibrary iop = InteropLibrary.getFactory().getUncached();
            return iop.asInt(iop.readMember(args[0], "line"));
        } catch (InteropException ex) {
            throw DbgLanguage.raise(RuntimeException.class, ex);
        }
    }

    @ExportMessage
    Object execute(Object[] args, @CachedContext(value = DbgLanguage.class) DbgLanguage.Data context, @CachedLibrary(limit = "3") InteropLibrary frameLib, @Cached(value = "findSrc(args)", allowUncached = true) String src, @Cached(value = "findLine(args)", allowUncached = true) int line) {
        if (this.line != line) {
            return this;
        }
        Object frame = args[1];
        boolean first = true;
        for (DbgAtWatch w : actions) {
            if (w.variableName == null) {
                continue;
            }
            Object value;
            try {
                value = frameLib.readMember(frame, w.variableName);
            } catch (InteropException ex) {
                continue;
            }
            if (first) {
                dumpPrologue(context.env, src, line);
                first = false;
            }
            dumpWatch(context.env, w, value);
        }
        return this;
    }

    @CompilerDirectives.TruffleBoundary
    private void dumpPrologue(TruffleLanguage.Env context, String src, int line1) {
        final String msg = String.format("at %s:%d\n", src, line1);
        try {
            final OutputStream out = context.out();
            out.write(msg.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
    @CompilerDirectives.TruffleBoundary
    private void dumpWatch(TruffleLanguage.Env context, DbgAtWatch w, Object value) {
        final String msg = String.format("  watch %s = %s\n", w.variableName, value);
        try {
            final OutputStream out = context.out();
            out.write(msg.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @ExportMessage
    boolean isExecutable() {
        return true;
    }

    @ExportMessage
    Object readMember(String member) {
        return "statements".equals(member);
    }

    @ExportMessage
    boolean isMemberReadable(String member) {
        return true;
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    Object getMembers(boolean include) {
        return this;
    }

    @ExportMessage
    Object readArrayElement(long index) {
        return "statements";
    }

    @ExportMessage
    boolean hasArrayElements() {
        return true;
    }

    @ExportMessage
    long getArraySize() {
        return 1;
    }

    @ExportMessage
    boolean isArrayElementReadable(long index) {
        return true;
    }

    void assignTarget(CallTarget target) {
        this.target = target;
    }

    void replay() {
        target.call(this);
    }
}

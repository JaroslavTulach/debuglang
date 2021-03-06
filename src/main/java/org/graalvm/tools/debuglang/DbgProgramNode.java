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
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class DbgProgramNode extends RootNode {
    private final DbgLanguage lang;
    private final List<DbgAt> statements;
    @CompilerDirectives.CompilationFinal
    private boolean callTargetsInitialized;

    DbgProgramNode(DbgLanguage language, List<DbgAt> statements) {
        super(language);
        this.lang = language;
        this.statements = statements;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final Object[] args = frame.getArguments();
        final Object insight = args.length > 0 ? args[0] : null;
        if (insight != null) {
            CompilerDirectives.transferToInterpreter();
            for (DbgAt at : statements) {
                at.register(insight);
            }
        } else {
            if (!callTargetsInitialized) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                generateCallTargets();
                callTargetsInitialized = true;
            }
            for (DbgAt at : statements) {
                at.replay();
            }
        }
        return 0;
    }

    private void generateCallTargets() {
        Map<DbgAt, CallTarget> similar = new HashMap<>();
        for (DbgAt at : statements) {
            CallTarget target = similar.get(at);
            if (target == null) {
                FrameDescriptor fd = new FrameDescriptor();
                for (DbgAtWatch w : at.actions) {
                    fd.addFrameSlot(w.variableName);
                }
                DbgNodeAt atNode = new DbgNodeAt(lang, fd, at.file, at.line);
                target = Truffle.getRuntime().createCallTarget(atNode);
                similar.put(at, target);
            }
            at.assignTarget(target);
        }
    }
}

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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.source.Source;
import java.io.IOException;
import org.graalvm.polyglot.Context;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ParseWithArgsTest {
    @Test
    public void initializeWithInsight() throws Exception {
        final Context.Builder builder = Context.newBuilder("dbg", "js");
        builder.allowExperimentalOptions(true);
        builder.allowAllAccess(true);
        Context ctx = builder.build();
        EvalWithArgsInstrument inst = ctx.getEngine().getInstruments().get("parsingInstrument").lookup(EvalWithArgsInstrument.class);
        assertNotNull(inst);

        boolean[] ok = { false };
        inst.onExec = () -> {
            try {
                Source source = Source.newBuilder("dbg", "at fib.js:4 watch n", "register.dbg").build();
                CallTarget target = inst.env.parse(source, "insight");

                Object res = target.call(new InsightAPI(ok));
                assertEquals(0, res);
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        };

        assertEquals("Enter the instrument", 42, ctx.eval("js", "6 * 7").asInt());
        assertTrue("Instrument executed fine", ok[0]);
    }

    @ExportLibrary(InteropLibrary.class)
    public class InsightAPI implements TruffleObject {
        final boolean[] ok;

        InsightAPI(boolean[] ok) {
            this.ok = ok;
        }

        @ExportMessage
        public Object invokeMember(String member, Object[] args) throws UnknownIdentifierException, UnsupportedMessageException {
            assertEquals("on", member);
            InteropLibrary iop = InteropLibrary.getFactory().getUncached();
            assertEquals(3, args.length);
            assertEquals("enter", args[0]);
            assertTrue(iop.isExecutable(args[1]));
            assertTrue((Boolean) iop.readMember(args[2], "statements"));
            ok[0] = true;
            return this;
        }

        @ExportMessage
        public boolean hasMembers() {
            return true;
        }

        @ExportMessage
        public Object getMembers(boolean includeInternal) {
            return this;
        }

        @ExportMessage
        public boolean isMemberInvocable(String member) {
            return "on".equals(member);
        }
    }

    @TruffleInstrument.Registration(id = "parsingInstrument", name = "Parsing Instrument", services = EvalWithArgsInstrument.class, version = "1.0")
    public static final class EvalWithArgsInstrument extends TruffleInstrument {
        private Env env;
        Runnable onExec;

        @Override
        protected void onCreate(Env env) {
            env.registerService(this);
            this.env = env;
            env.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.ANY, new ExecutionEventListener() {
                @Override
                public void onEnter(EventContext context, VirtualFrame frame) {
                    onExec.run();
                }

                @Override
                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                    onExec.run();
                }

                @Override
                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                    onExec.run();
                }
            });
        }
    }
}

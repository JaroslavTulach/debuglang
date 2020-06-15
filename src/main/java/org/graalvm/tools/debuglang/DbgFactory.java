package org.graalvm.tools.debuglang;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.RootNode;
import foundation.rpg.Match;
import foundation.rpg.Name;
import foundation.rpg.StartSymbol;
import foundation.rpg.parser.Token;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedList;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import static org.graalvm.tools.debuglang.DbgLanguage.raise;


public class DbgFactory {
    private final DbgLanguage language;

    DbgFactory(DbgLanguage language) {
        this.language = language;
    }

    @StartSymbol(parserClassName = "DbgParser")
    Program is (List<At> s) { return new Program(language, s); }
    List<At> is() { return Collections.emptyList(); }
    List<At> is(At at, List<At> end) {
        final LinkedList<At> l = new LinkedList<>(end);
        l.addFirst(at);
        return l;
    }
    At is(@Name("at") Keyword at, String file, Colon c, Integer line, List<Watch> actions) {
        return new At(file, line.intValue(), actions);
    }
    Integer integer(@Match("\\d+") Token t) {
        return Integer.parseInt(t.toString());
    }
    String id(@Match("[A-Za-z][A-Za-z0-9\\.]*") Token t) {
        return t.toString();
    }
    List<Watch> action(Watch a) {
        final LinkedList<Watch> l = new LinkedList<>();
        l.addFirst(a);
        return l;
    }
    List<Watch> action(List<Watch> prev, Watch a) {
        final LinkedList<Watch> l = new LinkedList<>(prev);
        l.addFirst(a);
        return l;
    }

    Watch is(@Name("watch") Keyword watch, String variableName) {
        return new Watch(variableName);
    }

    static void ignore(@Match("\\s+") WhiteSpace w) {}

    public static final class Program extends RootNode {
        private final List<At> statements;

        Program(DbgLanguage language, List<At> statements) {
            super(language);
            this.statements = statements;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            CompilerDirectives.transferToInterpreter();
            final Object[] args = frame.getArguments();
            final Object insight = args.length > 0 ? args[0] : null;
            if (insight != null) {
                for (At at : statements) {
                    at.register(insight);
                }
            }
            return 0;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class At implements TruffleObject {
        final String file;
        final int line;
        final List<Watch> actions;

        At(String file, int line, List<Watch> actions) {
            this.file = file;
            this.line = line;
            this.actions = actions;
        }

        final void register(Object argument) {
            InteropLibrary iop = InteropLibrary.getFactory().getUncached();
            try {
                iop.invokeMember(argument, "on", "enter", this, this);
            } catch (InteropException ex) {
                throw raise(RuntimeException.class, ex);
            }
        }

        static String findSrc(Object[] args) {
            try {
                InteropLibrary iop = InteropLibrary.getFactory().getUncached();
                Object src = iop.readMember(args[0], "source");
                return iop.asString(iop.readMember(src, "name"));
            } catch (InteropException ex) {
                throw raise(RuntimeException.class, ex);
            }
        }

        static int findLine(Object[] args) {
            try {
                InteropLibrary iop = InteropLibrary.getFactory().getUncached();
                return iop.asInt(iop.readMember(args[0], "line"));
            } catch (InteropException ex) {
                throw raise(RuntimeException.class, ex);
            }
        }

        @ExportMessage
        Object execute(Object[] args,
            @CachedContext(DbgLanguage.class) Env context,
            @CachedLibrary(limit = "3") InteropLibrary frameLib,
            @Cached(value = "findSrc(args)", allowUncached = true) String src,
            @Cached(value = "findLine(args)", allowUncached = true) int line
        ) {
            Object frame = args[1];
            for (Watch w : actions) {
                Object value;
                try {
                    value = frameLib.readMember(frame, w.variableName);
                } catch (InteropException ex) {
                    continue;
                }
                dumpOutLog(context, src, line, w, value);
            }
            return this;
        }

        @CompilerDirectives.TruffleBoundary
        private void dumpOutLog(Env context, String src, int line1, Watch w, Object value) {
            final String msg = String.format("at %s:%d watch %s = %s\n", src, line1, w.variableName, value);
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
    }

    public static final class Watch {
        final String variableName;

        public Watch(String variableName) {
            this.variableName = variableName;
        }
    }

    public static final class Keyword {
        Keyword(String k) {
        }
    }

    public static final class WhiteSpace {
        WhiteSpace(String spaces) {
        }
    }

    @Name(":")
    public static final class Colon {
        public Colon(String text) {
        }
    }
}
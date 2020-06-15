package org.graalvm.tools.debuglang;

import foundation.rpg.Match;
import foundation.rpg.Name;
import foundation.rpg.StartSymbol;
import foundation.rpg.parser.Token;
import java.util.Collections;
import java.util.LinkedList;

import java.util.List;


public class DbgFactory {
    @StartSymbol(parserClassName = "DbgParser")
    Program is (List<At> s) { return new Program(s); }
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

    Watch is(@Name("watch") Keyword watch, String variableName) {
        return new Watch(variableName);
    }

    static void ignore(@Match("\\s+") WhiteSpace w) {}

    public static final class Program {
        Program(List<At> s) {
        }

    }

    public static final class At {
        final String file;
        final int line;

        At(String file, int line, List<Watch> actions) {
            this.file = file;
            this.line = line;
        }
    }

    public static final class Watch {
        private final String variableName;

        public Watch(String variableName) {
            this.variableName = variableName;
        }
    }

    public static final class Keyword {
        Keyword(String k) {
            System.err.println("    K: " + k);
        }
    }

    public static final class WhiteSpace {
        WhiteSpace(String spaces) {
            System.err.println("whilte " + spaces);
        }
    }

    @Name(":")
    public static final class Colon {
        public Colon(String text) {
        }
    }
}
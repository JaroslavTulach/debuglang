package org.graalvm.tools.debuglang;

import foundation.rpg.Match;
import foundation.rpg.Name;
import foundation.rpg.StartSymbol;
import foundation.rpg.parser.End;
import foundation.rpg.parser.Token;
import java.util.Collections;
import java.util.LinkedList;

import java.util.List;


public class DbgFactory {
    @StartSymbol
    Program is (List<At> s, End e) { return new Program(s); }
    List<At> is() { return Collections.emptyList(); }
    List<At> is(At at, List<At> end) { final LinkedList<At> l = new LinkedList<>(end); l.addFirst(at); return l; }
    At is(String file, Integer line) { return new At(file, line.intValue()); }
    String file (@Match("\\w+") Token t) { return t.toString(); }
    Integer integer (@Match("\\d+") Token t) { return Integer.parseInt(t.toString()); }
    End end(@Match("$") Token t) { return null; }
    
    static void ignore(@Match("\\w*") WhiteSpace w) {}

    public static final class Program {
        Program(List<At> s) {
        }
        
    }
    public static final class At {
        final String file;
        final int line;

        At(String file, int line) {
            this.file = file;
            this.line = line;
        }
    }
    
    public static final class WhiteSpace {
        WhiteSpace(String spaces) {
        }
    }

    @Name(":")    
    public static final class Colon {
    }
}
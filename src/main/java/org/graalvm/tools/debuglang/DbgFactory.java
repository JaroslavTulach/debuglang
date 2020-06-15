package org.graalvm.tools.debuglang;

import foundation.rpg.StartSymbol;

import java.util.List;


public class DbgFactory {
    @StartSymbol
    Program is (List<At> s, End e) { return new Program(s); }

    static void ignore(WhiteSpace w) {}

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
    }

    public static final class End {
    }
}
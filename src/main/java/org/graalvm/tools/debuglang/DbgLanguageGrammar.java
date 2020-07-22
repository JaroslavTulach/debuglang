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

import foundation.rpg.Match;
import foundation.rpg.Name;
import foundation.rpg.StartSymbol;
import foundation.rpg.parser.Token;
import java.util.Collections;
import java.util.LinkedList;

import java.util.List;

final class DbgLanguageGrammar {
    private final DbgLanguage language;

    DbgLanguageGrammar(DbgLanguage language) {
        this.language = language;
    }

    @StartSymbol(parserClassName = "DbgParser")
    DbgProgramNode is (List<DbgAt> s) { return new DbgProgramNode(language, s); }
    List<DbgAt> is() { return Collections.emptyList(); }
    List<DbgAt> is(DbgAt at, List<DbgAt> end) {
        final LinkedList<DbgAt> l = new LinkedList<>(end);
        l.addFirst(at);
        return l;
    }
    DbgAt is(@Name("at") KeywordAt at, String file, Colon c, Integer line, List<DbgAtWatch> actions) {
        return new DbgAt(file, line, actions);
    }
    Integer integer(@Match("\\d+") Token t) {
        return Integer.parseInt(t.toString());
    }
    String id(@Match("[A-Za-z][A-Za-z0-9\\.]*") Token t) {
        return t.toString();
    }
    List<DbgAtWatch> action(DbgAtWatch a) {
        final LinkedList<DbgAtWatch> l = new LinkedList<>();
        l.addFirst(a);
        return l;
    }
    List<DbgAtWatch> action(List<DbgAtWatch> prev, DbgAtWatch a) {
        final LinkedList<DbgAtWatch> l = new LinkedList<>(prev);
        l.addFirst(a);
        return l;
    }

    List<DbgAtWatch> snapshot(@Name("snapshot") KeywordSnapshot snapshot) {
        final LinkedList<DbgAtWatch> l = new LinkedList<>();
        l.addFirst(new DbgAtWatch(null, null));
        return l;
    }

    DbgAtWatch is(@Name("watch") KeywordWatch watch, String variableName, Equals equals, Integer value) {
        return new DbgAtWatch(variableName, value);
    }

    DbgAtWatch is(@Name("watch") KeywordWatch watch, String variableName) {
        return new DbgAtWatch(variableName, null);
    }

    static void ignore(@Match("\\s+") WhiteSpace w) {}

    static final class KeywordAt {
        KeywordAt(String k) {}
    }

    static final class KeywordWatch {
        KeywordWatch(String k) {}
    }

    static final class KeywordSnapshot {
        KeywordSnapshot(String k) {}
    }

    static final class WhiteSpace {
        WhiteSpace(String spaces) {}
    }

    @Name(":")
    static final class Colon {
        Colon(String text) {}
    }

    @Name("=")
    static final class Equals {
        Equals(String text) {}
    }
}
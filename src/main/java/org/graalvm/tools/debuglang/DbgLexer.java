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

import org.netbeans.api.lexer.Token;
import org.netbeans.spi.lexer.Lexer;
import org.netbeans.spi.lexer.LexerInput;
import org.netbeans.spi.lexer.LexerRestartInfo;
import org.netbeans.spi.lexer.TokenFactory;

final class DbgLexer implements Lexer<DbgTokenId> {

    private static final int EOF = LexerInput.EOF;

    private final LexerInput input;
    private final TokenFactory<DbgTokenId> tokenFactory;

    DbgLexer(LexerRestartInfo<DbgTokenId> info) {
        this.input = info.input();
        this.tokenFactory = info.tokenFactory();
        assert (info.state() == null); // passed argument always null
    }

    @Override
    public Token<DbgTokenId> nextToken() {
        while (true) {
            int ch = input.read();
            switch (ch) {
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    return consumeNumber(ch);
                case ':':
                    return token(DbgTokenId.COLON);
                case EOF:
                    return null;
                default:
                    if (Character.isWhitespace(ch)) {
                        ch = input.read();
                        while (ch != EOF && Character.isWhitespace(ch)) {
                            ch = input.read();
                        }
                        input.backup(1);
                        return token(DbgTokenId.WHITESPACE);
                    }

                    return consumeIdentifier(ch);
            }
        }
    }

    @Override
    public Object state() {
        return null;
    }

    @Override
    public void release() {
    }

    private Token<DbgTokenId> token(DbgTokenId id) {
        return (id.fixedText() != null)
            ? tokenFactory.getFlyweightToken(id, id.fixedText())
            : tokenFactory.createToken(id);
    }

    private Token<DbgTokenId> consumeNumber(int first) {
        for (;;) {
            int ch = input.read();
            if (Character.isDigit(ch)) {
                continue;
            }
            input.backup(1);
            return token(DbgTokenId.NUMBER);
        }
    }

    private Token<DbgTokenId> consumeIdentifier(int first) {
        for (;;) {
            int ch = input.read();
            boolean end = Character.isWhitespace(ch) || ch == ':';
            if (!end) {
                continue;
            }
            DbgTokenId id;
            input.backup(1); // backup the extra char (or EOF)
            if (':' == ch) {
                id = DbgTokenId.IDENTIFIER;
            } else {
                switch (input.readText().toString()) {
                    case "at":
                        id = DbgTokenId.AT;
                        break;
                    case "watch":
                        id = DbgTokenId.WATCH;
                        break;
                    default:
                        id = DbgTokenId.IDENTIFIER;
                }
            }
            return token(id);
        }
    }

}

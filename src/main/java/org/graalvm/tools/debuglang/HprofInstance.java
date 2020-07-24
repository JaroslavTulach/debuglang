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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import java.util.ArrayList;
import java.util.List;
import org.netbeans.lib.profiler.heap.FieldValue;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.PrimitiveArrayInstance;

@ExportLibrary(InteropLibrary.class)
final class HprofInstance implements TruffleObject {

    private final Instance instance;

    HprofInstance(Instance instance) {
        this.instance = instance;
    }

    @ExportMessage
    boolean hasMembers() {
        return !isString();
    }

    @ExportMessage
    boolean isString() {
        final String type = instance.getJavaClass().getName();
        return "java.lang.String".equals(type);
    }

    @ExportMessage
    String asString() {
        Object array = instance.getValueOfField("value");
        return array.toString();
    }



    @ExportMessage
    boolean isNumber() {
        if (isString()) {
            return false;
        }
        final String type = instance.getJavaClass().getName();
        return type.startsWith("java.lang.");
    }

    @ExportMessage boolean fitsInByte() { return true; }
    @ExportMessage boolean fitsInShort() { return true; }
    @ExportMessage boolean fitsInInt() { return true; }
    @ExportMessage boolean fitsInLong() { return true; }
    @ExportMessage boolean fitsInFloat() { return true; }
    @ExportMessage boolean fitsInDouble() { return true; }
    @ExportMessage byte asByte() throws UnsupportedMessageException {
        if (isNumber()) {
            Object value = instance.getValueOfField("value");
            return ((Number) value).byteValue();
        }
        throw UnsupportedMessageException.create();
    }
    @ExportMessage short asShort() throws UnsupportedMessageException {
        if (isNumber()) {
            Object value = instance.getValueOfField("value");
            return ((Number) value).shortValue();
        }
        throw UnsupportedMessageException.create();
    }
    @ExportMessage long asLong() throws UnsupportedMessageException {
        if (isNumber()) {
            Object value = instance.getValueOfField("value");
            return ((Number) value).longValue();
        }
        throw UnsupportedMessageException.create();
    }
    @ExportMessage float asFloat() throws UnsupportedMessageException {
        if (isNumber()) {
            Object value = instance.getValueOfField("value");
            return ((Number) value).floatValue();
        }
        throw UnsupportedMessageException.create();
    }
    @ExportMessage double asDouble() throws UnsupportedMessageException {
        if (isNumber()) {
            Object value = instance.getValueOfField("value");
            return ((Number) value).doubleValue();
        }
        throw UnsupportedMessageException.create();
    }
    @ExportMessage int asInt() throws UnsupportedMessageException {
        if (isNumber()) {
            Object value = instance.getValueOfField("value");
            return ((Number) value).intValue();
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return HprofArray.wrap(fieldNames());
    }

    private List<String> fieldNames() {
        List<FieldValue> arr = instance.getFieldValues();
        List<String> names = new ArrayList<>();
        arr.stream().map(f -> f.getField().getName()).forEachOrdered(name -> {
            names.add(name);
        });
        return names;
    }

    @ExportMessage
    boolean isMemberReadable(String member) {
        return true;
    }

    @ExportMessage
    Object readMember(String member) throws UnsupportedMessageException {
        return readField(member);
    }

    @CompilerDirectives.TruffleBoundary
    private Object readField(String member) throws UnsupportedMessageException {
        final Object raw = this.instance.getValueOfField(member);
        if (raw instanceof Instance) {
            HprofInstance wrap = new HprofInstance((Instance) raw);
            return wrap.isNumber() ? wrap.asInt() : wrap;
        } else {
            return raw;
        }
    }

    @Override
    public String toString() {
        return toDisplayString(false);
    }

    @ExportMessage
    String toDisplayString(boolean allowSideEffects) {
        if (isString()) {
            PrimitiveArrayInstance pai = (PrimitiveArrayInstance) instance.getValueOfField("value");
            int len = pai.getLength();
            char[] arr = new char[len];
            for (int i = 0; i < len; i++) {
                String ith = (String) pai.getValues().get(i);
                arr[i] = ith.charAt(0);
            }
            return new String(arr);
        }
        if (isNumber()) {
            try {
                return "" + asInt();
            } catch (UnsupportedMessageException ex) {
                // go on
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(instance.getJavaClass().getName()).append("[");
        String sep = "";
        for (String name : fieldNames()) {
            sb.append(sep);
            sb.append(name).append("=");
            try {
                sb.append(readField(name));
            } catch (UnsupportedMessageException ex) {
                sb.append("?");
            }
            sep = ",";
        }
        return sb.append("]").toString();
    }
}

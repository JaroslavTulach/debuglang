package org.graalvm.tools.debuglang;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import java.util.List;
import org.netbeans.lib.profiler.heap.FieldValue;
import org.netbeans.lib.profiler.heap.Instance;

@ExportLibrary(InteropLibrary.class)
final class HprofInstance implements TruffleObject {

    private final Instance instance;

    HprofInstance(Instance instance) {
        this.instance = instance;
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    boolean isNumber() {
        return instance.getJavaClass().getName().startsWith("java.lang");
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
        List<FieldValue> arr = instance.getFieldValues();
        for (FieldValue f : arr) {
            String type = f.getField().getType().getName();
            String name = f.getField().getName();
            System.err.println(" ty: " + type + " na: " + name);
        }
        return null;
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
        Instance value = (Instance) this.instance.getValueOfField(member);
        HprofInstance wrap = new HprofInstance(value);
        return wrap.isNumber() ? wrap.asInt() : wrap;
    }

}

package org.graalvm.tools.debuglang;

import com.oracle.truffle.api.TruffleFile;
import java.io.IOException;
import java.nio.charset.Charset;

public final class DbgFileType implements TruffleFile.FileTypeDetector {
    static final String TYPE = "application/x-debug";

    @Override
    public String findMimeType(TruffleFile file) throws IOException {
        if (file.getName().endsWith(".dbg")) {
            return TYPE;
        }
        return null;
    }

    @Override
    public Charset findEncoding(TruffleFile file) throws IOException {
        return null;
    }
}

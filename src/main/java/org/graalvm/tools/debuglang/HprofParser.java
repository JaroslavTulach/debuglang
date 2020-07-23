package org.graalvm.tools.debuglang;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.netbeans.lib.profiler.heap.FieldValue;
import org.netbeans.lib.profiler.heap.GCRoot;
import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.HeapFactory;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.JavaFrameGCRoot;
import org.netbeans.lib.profiler.heap.ThreadObjectGCRoot;

final class HprofParser {
    static DbgProgramNode parse(DbgLanguage language, File hprof) throws IOException {
        Heap heap = HeapFactory.createHeap(hprof);
        Collection<GCRoot> roots = heap.getGCRoots();
        List<DbgAt> statements = new ArrayList<>();
        Map<ThreadObjectGCRoot, List<JavaFrameGCRoot>> map = new HashMap<>();
        for (GCRoot r : roots) {
            if (r instanceof ThreadObjectGCRoot) {
                ThreadObjectGCRoot t = (ThreadObjectGCRoot) r;
                StackTraceElement at = t.getStackTrace()[0];
                List<JavaFrameGCRoot> list = map.get(t);
                final String file = at.getFileName();
                final int line = at.getLineNumber();
                assert list != null;
                List<DbgAtWatch> copy = new ArrayList<>();
                for (JavaFrameGCRoot f : list) {
                    for (Object o : f.getInstance().getFieldValues()) {
                        FieldValue fieldValue = (FieldValue) o;
                        Instance value = (Instance) f.getInstance().getValueOfField(fieldValue.getField().getName());
                        copy.add(new DbgAtWatch(fieldValue.getField().getName(), new HprofInstance(value)));
                    }
                }
                statements.add(new DbgAt(file, line, copy));
            } else if (r instanceof JavaFrameGCRoot) {
                JavaFrameGCRoot f = (JavaFrameGCRoot) r;
                List<JavaFrameGCRoot> list = map.get(f.getThreadGCRoot());
                if (list == null) {
                    list = new ArrayList<>();
                    map.put(f.getThreadGCRoot(), list);
                }
                list.add(f);
            }
        }
        return  new DbgProgramNode(language, statements);
    }
}

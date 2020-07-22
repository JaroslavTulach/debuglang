# Post-Mortem Debugging with GraalVM

![Java CI with Maven](https://github.com/JaroslavTulach/debuglang/workflows/Java%20CI%20with%20Maven/badge.svg)

Demonstrating the tooling and polyglot capabilities of [GraalVM](http://graalvm.org).
Showing how to apply Insights over Ruby and JavaScript programs. 
Demonstrating usage of a dedicated "debug" language to specify
watchpoints easily. Using Chrome DevTools to replay the recorded execution.

[![Watch the video](https://img.youtube.com/vi/SGgDJkcEuaY/hqdefault.jpg)](https://www.youtube.com/watch?v=SGgDJkcEuaY)

# Try It!

Build the debug language with Maven and register it into GraalVM:

```bash
debuglang$ mvn clean install
debuglang$ mkdir $GRAALVM/jre/languages/debuglang/
debuglang$ cp $HOME/.m2/repository/org/graalvm/tools/debuglang/1.0-SNAPSHOT/debuglang-1.0-SNAPSHOT.jar $GRAALVM/jre/languages/debuglang/
```

then you shall be able to use the language to trace computations of your program:

```bash
$ cat >watchpoints.dbg
at some.js:3 watch x
at some.js:8 watch y watch z
$ $GRAALVM/bin/js --jvm --polyglot --insight=watchpoints.dbg some.js
```

You can copy the traces into a dedicated file `log.dbg` and replay them in Chrome DevTools:

```bash
$ GRAALVM/bin/polyglot --inspect --jvm log.dbg
```

Enjoy!

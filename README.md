# NodeJVM

This repository demonstrates how to use NodeJS/npm modules directly from Java and Kotlin. Why is it useful:

* Gain access to unique JavaScript modules, like the DAT peer to peer file sharing framework shown in the sample.
* Combine your existing NodeJS and Java servers together, eliminating the overheads of REST, serialisation, two separate
  virtual machines. Simplify your microservices architecture into being a polyglot architecture instead.
* Use it to start porting NodeJS apps to the JVM world and languages, incrementally, one chunk at a time, whilst always
  having a runnable app.
  
How does it work?

GraalVM is a modified version of OpenJDK 8 that includes the cutting edge Graal and Truffle compiler infrastructure.
It provides an advanced JavaScript engine that has competitive performance with V8, and also a modified version of
NodeJS 10 that swaps out V8 for this enhanced JVM. In this way you can fuse together NodeJS and the JVM, allowing apps
to smoothly access both worlds simultaneously.

# TODO

- Java documentation
- Gradle plugin?
- Windows support when GraalVM has caught up.
- Figure out lambda issue
- Can node_modules directories be packaged as JARs?

# Running polyglot programs

Build with Gradle: `gradle build`

Now add the `build/nodejvm` directory to your path, or copy the contents to somewhere on your path.

Start your Java programs as normal but run `nodejvm` instead of `java`, e.g.

`nodejvm -cp "libs/*.jar" my.main.Class arg1 arg2`

# Language injection

IntelliJ offers "language injection", which means a file can contain multiple languages at once. This is enabled
automatically when using NodeJS/J but to benefit you should change a setting first:

1. Open your preferences and go to Editor > Language Injection > Advanced
2. Under "Performance" select "Enable data flow analysis"

Any string passed to eval will now be highlighted and edited as JavaScript, not a Java/Kotlin/Scala/etc string literal.

# Usage from Kotlin

You may evaluate JavaScript when inside a `nodejs` block, like so:

```kotlin
val i: Int = nodejs {
    eval("2 + 2 + 4")
}
```

Kotlin's type inference combined with GraalJS and the Polyglot infrastructure ensures that you can take the result
of `eval` and stick it into a normal Kotlin variable most of the time.

If you don't want any return value, use `run` instead of `eval`:

```kotlin
nodejs {
    run("console.log('hi there, world')")
}
```

The `nodejs` block synchronises with the NodeJS event loop, thus making access to the JavaScript engine safe. The
lambda will be run on an alternative thread, and execution will block until the lambda returns.

**You must only access JavaScript types from inside nodejs blocks**. This is important. NodeJS will be using the JVM
heap so you can store references to JS objects wherever you like, however, due to the need to synchronize with the
NodeJS event loop, even something as simple as calling toString() on a JS type will fail unless you are inside the block.
JavaScript is not thread safe and implements rules similar to Visual Basic 6. [Learn more here](https://medium.com/graalvm/multi-threaded-java-javascript-language-interoperability-in-graalvm-2f19c1f9c37b). 

It's safe to put `nodejs` blocks anywhere. You can nest them inside each other, and if you run a `nodejs` block whilst
already on the event loop thread it will simply execute the code block immediately. Just remember not to block the
NodeJS main thread itself: everything in JavaScript land is event driven.

## Value

The native type you get out of `eval` is `Value`, which gives you a fairly standard stringly typed API:

```kotlin
nodejs {
    val v: Value = eval("process.memoryUsage()")
    val heapTotal: Long = v["heapTotal"]
    println("JS heap total size is $heapTotal")
}
``` 

When evaluated in NodeJS `process.memoryUsage()` will give you something like this:

```javascript
> process.memoryUsage()
{ rss: 22847488,
  heapTotal: 9682944,
  heapUsed: 6075560,
  external: 12318 }
```

So you can see how to use Kotlin's property access syntax in the same way you might in JS itself.

## Interface access

You can also request from `eval` an interface. Method calls are then turned into property and method accesses in the
obvious way:

```kotlin
interface MemoryUsage {
    fun rss(): Long
    fun heapTotal(): Long
}

nodejs {
    val rss = eval<MemoryUsage>("process.memoryUsage()").rss
}
```

Note you must use `fun` and not `val` because GraalJS doesn't currently understand JavaBean method naming conventions,
which the Kotlin compiler uses for `val` and `var`.

## Top level variable binding

You'll often want to pass Java/Kotlin objects into the JS world. You can do this by binding a JavaScript variable
to a Kotlin variable and then reading/writing to it as normal:

```kotlin
nodejs {
    var list: List<String> by bind(listOf("a", "b", "c"))
    run("console.log(list[0])")
    
    run("x = 5")
    val x by bind<Int>()
    println("$x == 5") 
}
```

`bind` is a function that optionally takes a default value and then connects the new variable to a top level JS
variable with the same name. 

Recall that in JavaScript `var` creates a locally scoped variable, so to interop like this you must define JS variables
at the top level without `var`. That's why we run `x = 5` above and not `var x = 5`.

## Callbacks and lambdas

You unfortunately cannot pass Kotlin lambdas straight into JavaScript due to [KT-30107](https://youtrack.jetbrains.com/issue/KT-301070).
So you have to use the Java functional types instead, like this:

```kotlin
nodejs {
    var callback1 by bind(Consumer { m: Map<String, Any?> ->
        val obj = m.asValue().cast<MemoryUsage>()
        println("rss is ${obj.rss()}")
    })
    run("callback1(process.memoryUsage())")
}
```

Unfortunately, for unclear reasons GraalJS always passes a JavaScript object into a callback as Map<String, Any?>, but
you can easily convert it to an interface as seen above.
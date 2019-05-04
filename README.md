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

# Running polyglot programs

Download the latest release from the [releases page](https://github.com/mikehearn/nodejvm/releases).

Now add the `nodejvm` directory to your path, or copy the contents to somewhere on your path.

Start your Java programs as normal but run `nodejvm` instead of `java`, e.g.

`nodejvm -cp "libs/*.jar" my.main.Class arg1 arg2`

# Using 

## Language injection

IntelliJ offers "language injection", which means a file can contain multiple languages at once. This is enabled
automatically when using NodeJVM but to benefit you should change a setting first:

1. Open your preferences and go to Editor > Language Injection > Advanced
2. Under "Performance" select "Enable data flow analysis"

Any string passed to `eval` will now be highlighted and edited as JavaScript, not a Java/Kotlin/Scala/etc string literal.

![Screenshot of language injection](language-embedding.png)

## Usage from Java

The `NodeJS` class gives you access to the JavaScript runtime:

```java
import net.plan99.nodejs.NodeJS;

public class Demo {
    public static void main(String[] args) {
        int result = NodeJS.runJS(() ->
            NodeJS.eval("return 2 + 3 + 4").asInt()
        );
        System.out.println(result);
    }
}
```

Evaluate JavaScript code with the `eval` static method. Before you can use it, you need to get yourself onto the
NodeJS main thread by providing a lambda to `NodeJS.runJS`. See below for more info on this.

What you get back from `eval` is a GraalVM Polyglot `Value` class ([javadoc](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html)). 
[Documentation for the Polyglot API is here.](http://www.graalvm.org/sdk/javadoc/)

`Value` is a pretty typical variant-type object. You can cast to various primitives, cast to interfaces (see below),
access members, execute it and so on. You may also cast JavaScript objects to `Map<String, Object>` and treat them
as dictionaries in the standard scripting language manner. 

There is also a `NodeJS.runJSAsync` method which returns a `CompletableFuture` with the result of the lambda, instead
of waiting, and an `Executor` that executes jobs on the NodeJS thread.

## Concurrency and access to the JavaScript world

**You must only access JavaScript types from the NodeJS thread**. This is important. NodeJS will be using the JVM
heap so you can store references to JS objects wherever you like, however, due to the need to synchronize with the
event loop, even something as simple as calling `toString()` on a JS type will fail unless you are on the right thread.
This is because JavaScript is not thread safe, does not provide any kind of shared memory concurrency and generally
implements rules similar to Visual Basic 6. [Learn more here](https://medium.com/graalvm/multi-threaded-java-javascript-language-interoperability-in-graalvm-2f19c1f9c37b). 

It's safe to enter the NodeJS thread with `runJS` anywhere. You can nest calls inside each other, as if you run 
a `NodeJS.runJS` block whilst already on the event loop thread it will simply execute the code block immediately. 
Just remember not to block the NodeJS main thread itself: everything in JavaScript land is event driven.

Whilst Java is usable, it's a lot more convenient to use a more modern language like Kotlin.

## Usage from Kotlin

You may evaluate JavaScript when inside a `nodejs` block, like so:

```kotlin
val i: Int = nodejs {
    eval("2 + 2 + 4")
}
```

Kotlin's type inference combined with GraalJS and the Polyglot infrastructure ensures that you can take the result
of `eval` and stick it into a normal Kotlin variable most of the time. Polyglot casts will be performed automatically.

If you don't want any return value, use `run` instead of `eval`:

```kotlin
nodejs {
    run("console.log('hi there, world')")
}
```

The `nodejs` block synchronises with the NodeJS event loop, thus making access to the JavaScript engine safe.

If you ask `eval` for a `Value`, you can use Kotlin's indexing operators to treat it as a dictionary:

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

### Interface access

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

### Top level variable binding

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

### Callbacks and lambdas

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

Due to a GraalJS bug, it always passes a JavaScript object into a lambda as Map<String, Any?>, but you can easily 
convert it to an interface as seen above. [Alternatively just make it a real public class](https://github.com/graalvm/graaljs/issues/120).

# TODO

- Gradle plugin?
- Windows support when GraalVM has caught up.
- Can node_modules directories be packaged as JARs?

# License

Apache 2.0

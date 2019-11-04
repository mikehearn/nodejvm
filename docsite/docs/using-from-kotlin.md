# Using from Kotlin

📚 **[Read the Kotlin API Docs](/kotlin-api/nodejs-interop/net.plan99.nodejs.kotlin/-node-j-s-a-p-i/index.html)**

Kotlin provides many features that make it much more convenient and pleasant to work with NodeJS from the JVM.
The API is available only inside a `nodejs { }` block. You may evaluate JavaScript when inside a `nodejs` block, like so:

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

Remember Kotlin supports **multi-line strings** using `"""`. This is extremely convenient for embedding
JavaScript into Kotlin files. If the string is passed in via a simple dataflow (with no intermediate methods)
then IntelliJ will properly code highlight and do auto-complete for the embedded JavaScript via the
[language injection](/language-injection) feature! Just watch out that a pointless `.trimIndent()` doesn't
sneak in there, which will break injection. 

## Values

`Value` is GraalVM's generic variant type. It can be used to represent any JavaScript value. If you ask `eval` 
for a `Value`, you can use Kotlin's indexing operators to treat it as a dictionary:

```kotlin
nodejs {
    val v: Value = eval("process.memoryUsage()")
    val heapTotal: Long = v["heapTotal"]
    println("JS heap total size is $heapTotal")
}
``` 

When evaluated in NodeJS `process.memoryUsage()` will give you something like this:

```
> process.memoryUsage()
{ rss: 22847488,
  heapTotal: 9682944,
  heapUsed: 6075560,
  external: 12318 }
```

So you can see how to use Kotlin's property access syntax in the same way you might in JS itself. 

But usually it's easier and better to cast a `Value` to some other more native type. 
Read about [type conversions](/types) to learn how what's possible. To cast, you can either just ensure the return
value of `eval` is assigned to the correct type, or you can use `.cast<T>` on a `Value`, like
this:

```kotlin
val str: String = value.cast()
val str2 = value.cast<String>()
``` 

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
at the top level without `var`. That's why we run `x = 5` above and not `var x = 5`. If we had used `var` then Kotlin
wouldn't be able to see it.

## Interfaces

It's highly convenient to cast `Value` to interfaces. NodeJVM adds some extra proxying on top of GraalVM Polyglot to
make Kotlin (i.e. JavaBean) style properties map to JavaScript properties correctly. Here's how you can use 
the `ora` module that provides fancy spinners using the support for interface casting:

```kotlin
interface Ora {
    fun start(text: String)
    fun info(text: String)
    fun warn(text: String)
    fun error(text: String)

    var text: String
    var prefixText: String?
    var color: String
}

fun main() {
    val spinner: Ora = nodejs { eval("require('ora')()") }

    nodejs {
        spinner.start("Loading unicorns ...")
    }

    // We don't want to block the nodeJS thread by sleeping inside a nodejs{} block.
    // This sleep represents some sort of "work".
    Thread.sleep(2000)

    // Change color and message.
    nodejs {
        spinner.color = "red"
        spinner.text = "Loading rainbows"
        spinner.prefixText = "Working"
    }
}
``` 

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

Due to a GraalJS bug, it always passes a JavaScript object into a lambda as Map<String, Any?>, but you can easily 
convert it to an interface as seen above. [Alternatively just make it a real public class](https://github.com/graalvm/graaljs/issues/120).

# Using from Java

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
[Documentation for the Polyglot API is here](http://www.graalvm.org/sdk/javadoc/).

There is also a `NodeJS.runJSAsync` method which returns a `CompletableFuture` with the result of the lambda, instead
of waiting, and an `Executor` that executes jobs on the NodeJS thread.

The executor can be useful if you don't want to return anything from the JS code:

```java
NodeJS.executor.execute(() -> {
    NodeJS.eval("require('some-module').doSomething()");
}
```

But you can also use it in many other ways, as the `Executor` type is a general way to schedule work onto other threads
in Java.

## Casting

It can be useful to convert `Value` to a more convenient type. Many such [type conversions](/types) are available.

The Polyglot API has conveniences for many of them on the `Value` object itself, like `Value.asBoolean()`, `Value.asString()`, 
`Value.asLong()` etc.

For objects, NodeJVM provides an extra bit of glue to make JavaBean style properties work. To benefit, you must 
unfortunately use a little boilerplate to ensure generics are preserved. Cast like this:

```java
MyInterface ora = NodeJS.castValue(v, new TypeLiteral<MyInterface>() {});
```

If you passed a Java object into JavaScript and have now got it back again, use `Value.asHostObject()`
to unwrap it.
 
## Bindings

You will often want to put a Java object into the JavaScript environment. That's done using the Polyglot
bindings API. The bindings are a string to object map which acts as a kind of transfer area. You insert
objects into the bindings with a name, and then in JavaScript use `Polyglot.import()` to retrieve it:  

```java
class Bindings {
    static void demo() {
        NodeJS.polyglotContext().getPolyglotBindings().putMember(
                "props", 
                System.getProperties()
        );
        NodeJS.executor.execute(() -> {
            NodeJS.eval(
                    "const props = Polyglot.import('props');" +
                    "console.log(props.get('java.version'));"
            );
        });
    }
}
```

This program will expose the Java system properties to JavaScript and then print the Java runtime version.
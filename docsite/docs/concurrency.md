# Concurrency and access to the JavaScript world

Java and JavaScript execute on different threads by default, and thus will execute concurrently.

!!! warning

    **You can only access JavaScript objects from the NodeJS thread.** 

This is important. NodeJS will use the JVM heap, so you can store *references* to JS objects wherever you like, 
however, due to the need to synchronize with the Node event loop, even something as simple as calling 
`toString()` on a JavaScript object will fail unless you are on the right thread.

To run NodeJS code you must therefore *enter the Node thread*. In Java this is done by passing a lambda into
`NodeJS.runJS` or `NodeJS.runJSAsync`. The calling Java thread will pause, wait for NodeJS to reach its main
loop (if it's doing something) and then the lambda will be executed on the node thread. You can then call in
and out of JavaScript to your hearts content:

```java
// Will block and wait for the JavaScript thread to become available.
int result = NodeJS.runJS(() ->
    NodeJS.eval("return 2 + 3 + 4").asInt()
);
```

In Kotlin, entering a `nodejs { }` block will synchronize with the NodeJS thread, and you can pass return
values out of this block.

It's safe to enter the NodeJS thread anywhere. You can nest entries inside each other, as if you enter
Node whilst already on the event loop thread it will simply execute the lambda/code block immediately.
 
Just remember not to block the NodeJS main thread itself: everything in JavaScript land is event driven. 
Things you might do that accidentally halt all JavaScript execution include:

* Reading or writing to a socket.
* Accessing a file (may be slow if it's over a network mount).
* Call Thread.sleep().
* Do a long and intensive calculation.

## Futures

A JavaScript Promise can be converted to a `CompletableFuture` by using `NodeJS.futureFromPromise` (in Java) or by
calling the `Value.toCompletableFuture()` extension method inside a `nodejs` block in Kotlin. When the promise is 
resolved the future is completed.

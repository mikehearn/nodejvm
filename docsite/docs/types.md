# Type conversions

The Graal API uses a variant type called `Value`.
 
JavaScript objects returned through eval are mapped to Java/JVM world objects in a fairly sophisticated way, as described
in the [Graal SDK documentation](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#as-java.lang.Class-).

Briefly:

* Strings and numbers work as you'd expect. JavaScript numbers can be larger than a Java number type,
  if the number wouldn't fit then `ClassCastException` is thrown.
* Date and time values are mapped to `java.time` types.
* Exceptions can be mapped to `PolyglotException`.
* Things with members i.e. objects can be mapped to `Map<String, Any>`
* JavaScript lists can be converted to `List<T>` or a Java array. It's more efficient to use `List<T>`.
* JavaScript functions can be converted to lambdas/functional interfaces.
* JavaScript objects can be converted to interfaces, which get special behaviours (see below).

You can also go the other way.

## Interfaces

You can cast a `Value` to an interface. Special rules apply that map JavaBean property name conventions
to JavaScript. If a method starts with "get" or "is" then calling it is treated as a property read.
If a method starts with "set" then calling it is treated as a property write, with the names being
mapped appropriately.

Kotlin `val` and `var` map to JavaBean style methods under the hood, so they should map transparently.

## Automatic conversion from TypeScript

There is a project called Dukat that is working on automatic conversion of TypeScript to Kotlin 
declarations. Because Kotlin code can also be accessed from Java. 
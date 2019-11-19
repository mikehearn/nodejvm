# NodeJVM

This tool starts up GraalVM in a way that gives you access to a genuine NodeJS instance. [GraalVM](https://www.graalvm.org) is a variant of OpenJDK with a fast, modern JavaScript engine capable of running all NodeJS modules. GraalVM gives you a version of NodeJS that can access Java classes, but what if you want the other way around - Java code accessing NodeJS modules?

NodeJVM is a thin wrapper script and JAR that sets up the JVM with full access to NodeJS. It provides a simple API for calling in and out of the Node event loop thread, in a thread-safe way. It also provides a Kotlin API that offers many conveniences.

Note: *NodeJVM is not a new JVM*. It's just a way to start up GraalVM, which is a JVM produced by Oracle with a set of patches on top of OpenJDK. 

# Why use NPM modules from Java?

* Gain access to unique JavaScript modules, like the DAT peer to peer file sharing framework shown in the sample.
* Combine your existing NodeJS and Java servers together, eliminating the overheads of REST, serialisation, two separate
  virtual machines. Simplify your microservices architecture into being a polyglot architecture instead.
* Use it to start porting NodeJS apps to the JVM world and languages, incrementally, one chunk at a time, whilst always
  having a runnable app.
  
# Documentation

📚 [Access the documentation site](https://mikehearn.github.io/nodejvm/)

# What does it look like?

IntelliJ Ultimate edition users can get integrated "language injection", in which a single editor tab can use syntax highlighting/code completion/refactoring support from multiple languages simultaneously. Combined with Kotlin's multi-line string syntax, it looks like this:

![Screenshot of language injection](docsite/docs/language-embedding.png)

# TODO

- Gradle plugin?
- Windows support when GraalVM has caught up.
- Can node_modules directories be packaged as JARs?

# License

Apache 2.0

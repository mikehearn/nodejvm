# Introduction

This repository demonstrates how to use NodeJS/npm modules directly from Java and Kotlin. Why is it useful:

* Gain access to unique JavaScript modules, like the DAT peer to peer file sharing framework shown in the samples.
* Combine your existing NodeJS and Java servers together, eliminating the overheads of REST, serialisation, two separate
  virtual machines. Simplify your microservices architecture into being a polyglot architecture instead.
* Use it to start porting NodeJS apps to the JVM world and languages, incrementally, one chunk at a time, whilst always
  having a runnable app.
  
How does it work?

GraalVM is a modified version of OpenJDK that includes the cutting edge Graal and Truffle compiler infrastructure.
It provides an advanced JavaScript engine that has competitive performance with V8, and also a modified version of
NodeJS 10 that swaps out V8 for this enhanced JVM. In this way you can fuse together NodeJS and the JVM, allowing apps
to smoothly access both worlds simultaneously with full JIT compilation.

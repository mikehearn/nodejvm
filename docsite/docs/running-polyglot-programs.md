# Running polyglot programs

Download the latest release from the [releases page](https://github.com/mikehearn/nodejvm/releases).

Now add the `nodejvm` directory to your path, or copy the contents to somewhere on your path.

Start your Java programs as normal but run `nodejvm` instead of `java`, e.g.

`nodejvm -cp "libs/*.jar" my.main.Class arg1 arg2`

## Running the samples

Check out the NodeJVM repository. Then try:

```
gradle dat-sample:run
```

It should join the DAT network and might print some peer infos, depending on your luck.

Also try something a bit less Gradley:

```
gradle build spinners-sample:shadowJar
../build/nodejvm/nodejvm -jar build/libs/spinners-sample-*-all.jar
```

## From Gradle

The easiest way is to adjust your JavaCompile tasks to run `nodejvm` instead of `java`:

```
tasks.withType<JavaExec> {
    executable("nodejvm")
}
```

This requires `nodejvm` to be on your PATH and JAVA_HOME to be pointed at GraalVM.
There's no support for automatically downloading NodeJVM or Graal itself at this
time.

If you use the `application` plugin to generate startup scripts, then at the moment
you will have to edit the script by hand because it really wants `nodejvm` to be
called `java`. Alternatively you could symlink `nodejvm` to be named `java` and
put that on your PATH so it overrides the default Java install, but again, this
would be up to your users to do.

This is an area of focus for future improvement.
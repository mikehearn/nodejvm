# Running polyglot programs

Download the latest release from the [releases page](https://github.com/mikehearn/nodejvm/releases).

Now add the `nodejvm` directory to your path, or copy the contents to somewhere on your path.

Make sure you've got GraalVM and set the location as either `JAVA_HOME` or `GRAALVM_PATH`. Or make sure
its `bin` directory is on your path.

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

## From your own Gradle projects

Firstly, add my Maven repository for the interop API JAR (this step will become obsolete soon as it'll be in JCenter):

```
import java.net.URI

repositories {
    maven {
        url = URI("https://dl.bintray.com/mikehearn/open-source")
    }
}

dependencies {
    implementation("net.plan99:nodejvm:1.1")
}
```

(these all use Kotlin DSL syntax)

Then adjust your JavaCompile tasks to run `nodejvm` instead of `java`:

```
tasks.withType<JavaExec> {
    executable("nodejvm")
}
```

This requires `nodejvm` to be on your PATH and JAVA_HOME to be pointed at GraalVM.
There's no support for automatically downloading NodeJVM or Graal itself at this
time.

## Packaging

If you use the `application` plugin to generate startup scripts, then you will need a bit of extra code to edit the
script as it really wants the Java runner to be called `java` and not anything else. Try adding this code to your
build script (you may need to run `gradle installDist --rerun-tasks` after):

```kotlin
tasks.startScripts {
    val script = outputDir!!.resolve("doppelganger")
    doLast {
        script.writeText(script.readText().replace("JAVACMD=java", "JAVACMD=nodejvm"))
    }
}
```

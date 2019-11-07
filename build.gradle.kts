import org.apache.tools.ant.filters.ReplaceTokens
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    kotlin("jvm") version "1.3.20"
}

group = "net.plan99.nodejs"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.jetbrains:annotations:16.0.2")
    compileOnly(kotlin("stdlib-jdk8"))
    api("org.graalvm.sdk:graal-sdk:19.2.0.1")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

val genNodeJVMScript = task<Copy>("genNodeJVMScript") {
    val bootjs = "src/main/resources/boot.js"
    inputs.file(bootjs)
    from("src/main/resources/nodejvm")
    into("$buildDir/nodejvm")
    filter(ReplaceTokens::class, mapOf("tokens" to mapOf("bootjs" to file(bootjs).readText())))
    fileMode = 0x000001ed  // rwxr-xr-x permissions, kotlin doesn't support octal literals
}

val copyInteropJar = task<Copy>("copyInteropJar") {
    dependsOn(":jar")
    from("$buildDir/libs/nodejs-interop-$version.jar")
    into("$buildDir/nodejvm")
}

tasks["build"].dependsOn(genNodeJVMScript, copyInteropJar)
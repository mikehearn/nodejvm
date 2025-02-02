import org.apache.tools.ant.filters.ReplaceTokens
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.dokka.plugability.configuration
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI
import java.net.URL

plugins {
    java
    kotlin("jvm") version "1.9.25"
    `maven-publish`
    id("org.jetbrains.dokka") version "1.9.20"
}

group = "net.plan99.nodejs"
version = "1.2"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.jetbrains:annotations:16.0.2")
    compileOnly(kotlin("stdlib-jdk8"))
    api("org.graalvm.polyglot:polyglot:24.1.1")
}

kotlin {
    jvmToolchain(21)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile> {
    // Not strictly needed but it's nice for Java users to always have parameter reflection info.
    options.compilerArgs.add("-parameters")
}

val genNodeJVMScript = task<Copy>("genNodeJVMScript") {
    val bootjs = "src/main/resources/boot.js"
    inputs.file(bootjs)
    from("src/main/resources/nodejvm")
    into(layout.buildDirectory.dir("nodejvm"))
    filter(ReplaceTokens::class, mapOf(
        "tokens" to mapOf(
            "bootjs" to file(bootjs).readText(),
            "ver" to version.toString()
        )
    ))
    filePermissions {
        unix(0x000001ed)  // rwxr-xr-x permissions, kotlin doesn't support octal literals
    }
}

val copyInteropJar = task<Copy>("copyInteropJar") {
    dependsOn(":jar")
    from(layout.buildDirectory.file("libs/nodejs-interop-$version.jar"))
    into(layout.buildDirectory.dir("nodejvm"))
}

tasks["build"].dependsOn(genNodeJVMScript, copyInteropJar)

tasks.register<Jar>("sourcesJar") {
    from(sourceSets.main.get().allJava)
    archiveClassifier.set("sources")
}

tasks.register<Jar>("javadocJar") {
    from(tasks.javadoc)
    archiveClassifier.set("javadoc")
}

publishing {
    publications {
        create<MavenPublication>("api") {
            from(components["java"])
            groupId = "net.plan99"
            artifactId = "nodejvm"

            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])

            pom {
                name.set("NodeJVM")
                description.set("Easier NodeJS interop for GraalVM")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("mike")
                        name.set("Mike Hearn")
                        email.set("mike@plan99.net")
                    }
                }
            }
        }
    }

    repositories {
        maven {
            // change to point to your repo, e.g. http://my.org/repo
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}

tasks.dokkaHtml {
    outputDirectory.set(layout.projectDirectory.dir("docsite/docs/kotlin-api"))
    dokkaSourceSets.configureEach {
        externalDocumentationLink {
            url = URI("https://www.graalvm.org/sdk/javadoc/").toURL()
            packageListUrl = URI("https://www.graalvm.org/sdk/javadoc/package-list").toURL()
        }
        reportUndocumented = true

        // Exclude the Java API.
        perPackageOption {
            matchingRegex.set("net\\.plan99\\.nodejs\\.java.*")
            suppress = true
        }
    }
}

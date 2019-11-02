import org.apache.tools.ant.filters.ReplaceTokens
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    kotlin("jvm") version "1.3.50"
    `maven-publish`
    id("com.jfrog.bintray") version "1.8.4"
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

tasks.withType<JavaCompile> {
    // Not strictly needed but it's nice for Java users to always have parameter reflection info.
    options.compilerArgs.add("-parameters")
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
            url = uri("$buildDir/repo")
        }
    }
}

bintray {
    user = "mikehearn"
    key = System.getenv("BINTRAY_KEY")
    pkg.apply {
        repo = "open-source"
        name = "nodejvm"
        setLicenses("Apache-2.0")
        vcsUrl = "https://github.com/mikehearn/graviton-browser.git"
        version.apply {
            name = project.version.toString()
            desc = "NodeJS interop for GraalVM"
        }
    }
    setPublications("api")
}
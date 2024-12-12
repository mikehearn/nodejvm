import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    kotlin("jvm")
    application
    id("com.github.johnrengelman.shadow") version("8.1.1")
}

group = "net.plan99.nodejs"
version = "1.0"

application {
    mainClass.set("net.plan99.nodejs.sample.spinners.SpinnerDemoKt")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(rootProject)   // This would be:   implementation("net.plan99.nodejs:nodejs-interop:1.0") in a real project
}

tasks.withType<JavaExec> {
    dependsOn(":build")
    executable("${rootProject.buildDir}/nodejvm/nodejvm")
}

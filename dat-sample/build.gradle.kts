import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI

plugins {
    java
    kotlin("jvm")
    application
    id("com.github.johnrengelman.shadow") version("5.1.0")
}

group = "net.plan99.nodejs"
version = "1.0"

application {
    mainClassName = "net.plan99.nodejs.sample.DatExplorer"
}

repositories {
    mavenCentral()
    jcenter()
    maven {
        url = URI("https://dl.bintray.com/mikehearn/open-source")
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(rootProject)

    // In your programs, you'd write something like:
    // implementation("net.plan99:nodejvm:1.1")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.withType<JavaExec> {
    dependsOn(":build")
    executable("${rootProject.buildDir}/nodejvm/nodejvm")
}
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
    implementation(kotlin("stdlib-jdk8"))
    compileOnly("org.graalvm.sdk:graal-sdk:1.0.0-rc12")
    testImplementation("org.junit.jupiter:junit-jupiter:5.4.0")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}
tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

task<Exec>("startjs") {
    dependsOn("build")
    setCommandLine(
        "/Users/mike/graalvm-ce-1.0.0-rc12/Contents/Home/bin/node",
        "--jvm",
        "--jvm.cp", sourceSets["main"].runtimeClasspath.asPath,
        "--experimental-worker",
        "src/main/resources/boot.js",
        "one",
        "two",
        "three"
    )
}
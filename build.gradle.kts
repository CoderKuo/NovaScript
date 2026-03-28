import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8

plugins {
    java
    id("org.jetbrains.kotlin.jvm") version "2.2.0" apply false
    id("io.izzel.taboolib") version "2.0.31" apply false
}

subprojects {
    apply(plugin = "java")

    repositories {
        mavenCentral()
        mavenLocal()
        maven("https://jitpack.io")
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

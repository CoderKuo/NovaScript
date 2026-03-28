plugins {
    id("org.jetbrains.kotlin.jvm")
    `maven-publish`
}

dependencies {
    compileOnly(kotlin("stdlib"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = rootProject.group.toString()
            artifactId = "novascript-api"
            version = rootProject.version.toString()
            from(components["java"])
        }
    }
}

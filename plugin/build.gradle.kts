import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import io.izzel.taboolib.gradle.*
import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8

plugins {
    id("io.izzel.taboolib")
    id("org.jetbrains.kotlin.jvm")
}

taboolib {
    env {
        install(Basic)
        install(BukkitFakeOp)
        install(BukkitHook)
        install(BukkitNMS)
        install(BukkitNMSDataSerializer)
        install(BukkitNMSItemTag)
        install(BukkitNMSUtil)
        install(BukkitUI)
        install(BukkitUtil)
        install(Database)
        install(LettuceRedis)
        install(CommandHelper)
        install(I18n)
        install(Metrics)
        install(MinecraftChat)
        install(MinecraftEffect)
        install(Bukkit)
    }
    description {
        name = "NovaScript"
        contributors {
            name("dakuo")
        }
        dependencies {
            name("Vault").optional(true)
        }
    }
    version { taboolib = "6.2.4-99fb800" }
    relocate("com.novalang.", "com.dakuo.novascript.novalang.")
    relocate("com.dakuo.rulib.", "com.dakuo.novascript.rulib.")
}

dependencies {
    taboo(project(":api"))
    compileOnly("ink.ptms.core:v12004:12004:mapped")
    compileOnly("ink.ptms.core:v12004:12004:universal")
    compileOnly(kotlin("stdlib"))
    compileOnly(fileTree("libs"))
    taboo("com.github.CoderKuo.NovaLang:nova-runtime-all:0.1.12")
    taboo("com.github.CoderKuo.NovaLang:nova-bukkit:0.1.12")
    taboo("com.dakuo.rulib:Rulib:1.0.0")
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JVM_1_8)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

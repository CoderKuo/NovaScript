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
    val novaGroup = if (project.property("novaSource") == "local") "com.novalang" else "com.github.CoderKuo.NovaLang"
    val novaVer = project.property("novaVersion") as String
    taboo("$novaGroup:nova-runtime-all:$novaVer")
    taboo("$novaGroup:nova-bukkit:$novaVer")
    taboo("com.github.CoderKuo:Rulib:v1.0.0")
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JVM_1_8)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

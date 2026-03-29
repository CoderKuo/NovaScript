package com.dakuo.novascript.module

import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning
import java.io.File
import java.net.URLClassLoader
import java.util.jar.JarFile
import com.dakuo.novascript.NovaScriptAPI
import com.dakuo.novascript.GlobalScriptSetup

/**
 * 扩展模块管理器。
 *
 * 扫描 plugins/NovaScript/modules/ 下的 jar 文件，
 * 查找带 @NovaModule 注解且实现 NovaScriptModule 的类，
 * 使用独立 ClassLoader 加载并调用 onEnable。
 */
object ModuleManager {

    class LoadedModule(
        val name: String,
        val version: String,
        val description: String,
        val instance: NovaScriptModule,
        val classLoader: URLClassLoader,
        val registries: MutableList<com.dakuo.novascript.ScriptRegistry> = mutableListOf()
    )

    private val modules = linkedMapOf<String, LoadedModule>()
    private lateinit var modulesDir: File

    fun init(dataFolder: File) {
        modulesDir = File(dataFolder, "modules")
        if (!modulesDir.exists()) modulesDir.mkdirs()
        loadAll()
    }

    private fun loadAll() {
        val jars = modulesDir.listFiles { f -> f.isFile && f.extension == "jar" } ?: return
        for (jar in jars.sortedBy { it.name }) {
            try {
                loadModule(jar)
            } catch (e: Exception) {
                warning("[NovaScript] 加载模块 '${jar.name}' 失败: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun loadModule(jarFile: File) {
        // 1. 独立 ClassLoader，parent = NovaScript 的 ClassLoader（能访问 API + Bukkit）
        val classLoader = URLClassLoader(
            arrayOf(jarFile.toURI().toURL()),
            ModuleManager::class.java.classLoader
        )

        // 2. 扫描 jar 中的类，查找 @NovaModule + NovaScriptModule
        val entry = findModuleEntry(jarFile, classLoader)
        if (entry == null) {
            warning("[NovaScript] modules/${jarFile.name}: 未找到 @NovaModule 标记的 NovaScriptModule 实现")
            classLoader.close()
            return
        }

        val (moduleClass, annotation) = entry

        // 检查重名
        if (modules.containsKey(annotation.name)) {
            warning("[NovaScript] 模块名冲突: '${annotation.name}'（${jarFile.name}），跳过")
            classLoader.close()
            return
        }

        // 3. 实例化并调用 onEnable
        val instance = moduleClass.getDeclaredConstructor().newInstance() as NovaScriptModule
        val globalSetup = GlobalScriptSetup()
        val registry = NovaScriptAPI.createRegistry(annotation.name)
        instance.onEnable(NovaScriptAPI, globalSetup, registry)

        val loadedModule = LoadedModule(
            annotation.name,
            annotation.version,
            annotation.description,
            instance,
            classLoader
        )
        loadedModule.registries.add(registry)
        modules[annotation.name] = loadedModule
        info("[NovaScript] 已加载模块: ${annotation.name} v${annotation.version}")
    }

    private fun findModuleEntry(jarFile: File, classLoader: URLClassLoader): Pair<Class<*>, NovaModule>? {
        val jar = JarFile(jarFile)
        try {
            val entries = jar.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!entry.name.endsWith(".class")) continue
                // 跳过内部类
                if (entry.name.contains('$')) continue

                val className = entry.name
                    .removeSuffix(".class")
                    .replace('/', '.')

                try {
                    val cls = classLoader.loadClass(className)
                    val ann = cls.getAnnotation(NovaModule::class.java) ?: continue
                    if (!NovaScriptModule::class.java.isAssignableFrom(cls)) {
                        warning("[NovaScript] $className 标记了 @NovaModule 但未实现 NovaScriptModule 接口")
                        continue
                    }
                    return Pair(cls, ann)
                } catch (_: Throwable) {
                    // 类加载失败（缺少依赖等），跳过
                }
            }
        } finally {
            jar.close()
        }
        return null
    }

    fun unloadAll() {
        for ((name, module) in modules) {
            try {
                module.instance.onDisable()
            } catch (e: Exception) {
                warning("[NovaScript] 模块 '$name' onDisable 失败: ${e.message}")
            }
            // 自动清理模块创建的所有注册表
            module.registries.forEach { try { it.close() } catch (_: Exception) {} }
            try {
                module.classLoader.close()
            } catch (_: Exception) {}
        }
        modules.clear()
    }

    fun reload() {
        unloadAll()
        loadAll()
    }

    /** 将注册表关联到指定模块，模块卸载时自动清理 */
    fun trackRegistry(moduleName: String, registry: com.dakuo.novascript.ScriptRegistry) {
        modules[moduleName]?.registries?.add(registry)
    }

    fun getModules(): List<LoadedModule> = modules.values.toList()

    fun getModuleNames(): List<String> = modules.keys.toList()
}

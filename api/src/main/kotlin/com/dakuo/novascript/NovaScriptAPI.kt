package com.dakuo.novascript

import java.io.File

/**
 * NovaScript 公共 API。
 * 第三方 Bukkit 插件和扩展模块通过此单例注册、管理和调用 NovaLang 脚本。
 *
 * 所有方法使用 pluginName（字符串）标识归属插件，无需依赖 Bukkit API。
 *
 * ```kotlin
 * // Bukkit 插件中使用
 * NovaScriptAPI.register(name, "rewards", File(dataFolder, "rewards.nova"))
 * NovaScriptAPI.callFunction(name, "rewards", "calc", player, 100)
 *
 * // 或直接用命名空间
 * NovaScriptAPI.callFunction("myPlugin:rewards", "calc", player, 100)
 * ```
 */
object NovaScriptAPI {

    interface Delegate {
        fun register(pluginName: String, name: String, file: File, scriptDir: File, mode: RunMode, setup: ScriptConfigurer?, namespace: String?): Boolean
        fun registerCode(pluginName: String, name: String, code: String, scriptDir: File, mode: RunMode, setup: ScriptConfigurer?, namespace: String?): Boolean
        fun unregister(pluginName: String, name: String): Boolean
        fun unregisterAll(pluginName: String)
        fun reload(pluginName: String, name: String): Boolean
        fun callFunction(pluginName: String, scriptName: String, functionName: String, args: Array<out Any?>): Any?
        fun callFunctionNs(namespace: String, functionName: String, args: Array<out Any?>): Any?
        fun eval(code: String): Any?
        fun compile(code: String, setup: ScriptConfigurer?): NovaCompiled
        fun compileToBytecode(code: String, setup: ScriptConfigurer?): NovaCompiled
        fun hasFunction(pluginName: String, name: String, functionName: String): Boolean
        fun hasFunctionNs(namespace: String, functionName: String): Boolean
        fun isCallback(value: Any?): Boolean
        fun invokeCallback(pluginName: String, scriptName: String, callback: Any?, args: Array<out Any?>): Any?
        fun invokeCallbackNs(namespace: String, callback: Any?, args: Array<out Any?>): Any?
        fun isLoaded(pluginName: String, name: String): Boolean
        fun getScripts(pluginName: String): List<String>
        fun defineLibrary(pluginName: String, name: String, setup: ScriptConfigurer)
        fun removeLibrary(pluginName: String, name: String)
        fun getLibraries(pluginName: String): List<String>
        fun createRegistry(name: String): ScriptRegistry
    }

    @JvmStatic
    lateinit var delegate: Delegate

    // ── 脚本注册 ──

    /** 从文件注册脚本 */
    @JvmStatic @JvmOverloads
    fun register(pluginName: String, name: String, file: File, scriptDir: File = file.parentFile,
                 mode: RunMode = RunMode.BYTECODE, setup: ScriptConfigurer? = null, namespace: String? = null): Boolean =
        delegate.register(pluginName, name, file, scriptDir, mode, setup, namespace)

    /** 从代码字符串注册脚本 */
    @JvmStatic @JvmOverloads
    fun register(pluginName: String, name: String, code: String, scriptDir: File,
                 mode: RunMode = RunMode.BYTECODE, setup: ScriptConfigurer? = null, namespace: String? = null): Boolean =
        delegate.registerCode(pluginName, name, code, scriptDir, mode, setup, namespace)

    /** 卸载脚本 */
    @JvmStatic fun unregister(pluginName: String, name: String): Boolean = delegate.unregister(pluginName, name)

    /** 卸载插件的所有脚本 */
    @JvmStatic fun unregisterAll(pluginName: String) = delegate.unregisterAll(pluginName)

    /** 重载脚本（保留 setup 和运行模式） */
    @JvmStatic fun reload(pluginName: String, name: String): Boolean = delegate.reload(pluginName, name)

    // ── 函数调用 ──

    /** 通过插件名+脚本名调用函数 */
    @JvmStatic fun callFunction(pluginName: String, scriptName: String, functionName: String, vararg args: Any?): Any? =
        delegate.callFunction(pluginName, scriptName, functionName, args)

    /** 通过完整命名空间调用函数 "pluginName:scriptName" */
    @JvmStatic fun call(namespace: String, functionName: String, vararg args: Any?): Any? =
        delegate.callFunctionNs(namespace, functionName, args)

    // ── 执行/编译 ──

    /** 一次性执行代码片段 */
    @JvmStatic fun eval(code: String): Any? = delegate.eval(code)

    /** 预编译（AST + 解释模式） */
    @JvmStatic @JvmOverloads fun compile(code: String, setup: ScriptConfigurer? = null): NovaCompiled = delegate.compile(code, setup)

    /** 预编译（JVM 字节码，最高性能） */
    @JvmStatic @JvmOverloads fun compileToBytecode(code: String, setup: ScriptConfigurer? = null): NovaCompiled = delegate.compileToBytecode(code, setup)

    // ── 函数/回调检查 ──

    @JvmStatic fun hasFunction(pluginName: String, name: String, functionName: String): Boolean = delegate.hasFunction(pluginName, name, functionName)
    @JvmStatic fun hasFunction(namespace: String, functionName: String): Boolean = delegate.hasFunctionNs(namespace, functionName)
    @JvmStatic fun isCallback(value: Any?): Boolean = delegate.isCallback(value)
    @JvmStatic fun invokeCallback(pluginName: String, scriptName: String, callback: Any?, vararg args: Any?): Any? =
        delegate.invokeCallback(pluginName, scriptName, callback, args)
    @JvmStatic fun invokeCallback(namespace: String, callback: Any?, vararg args: Any?): Any? =
        delegate.invokeCallbackNs(namespace, callback, args)

    // ── 查询 ──

    @JvmStatic fun isLoaded(pluginName: String, name: String): Boolean = delegate.isLoaded(pluginName, name)
    @JvmStatic fun getScripts(pluginName: String): List<String> = delegate.getScripts(pluginName)

    // ── 全局函数库 ──

    @JvmStatic fun defineLibrary(pluginName: String, name: String, setup: ScriptConfigurer) = delegate.defineLibrary(pluginName, name, setup)
    @JvmStatic fun removeLibrary(pluginName: String, name: String) = delegate.removeLibrary(pluginName, name)
    @JvmStatic fun getLibraries(pluginName: String): List<String> = delegate.getLibraries(pluginName)

    // ── 脚本注册表 ──

    @JvmStatic fun createRegistry(name: String): ScriptRegistry = delegate.createRegistry(name)
}

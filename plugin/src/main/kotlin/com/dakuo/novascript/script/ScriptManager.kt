package com.dakuo.novascript.script

import com.novalang.bukkit.BukkitSchedulers
import com.novalang.runtime.Nova
import com.novalang.runtime.NovaScheduler
import com.novalang.runtime.NovaScheduler.Cancellable
import com.novalang.runtime.interpreter.LibraryBuilder
import taboolib.common.platform.event.ProxyListener
import taboolib.common.platform.function.info
import taboolib.common.platform.function.unregisterListener
import taboolib.common.platform.function.warning
import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import javax.sql.DataSource
import com.dakuo.novascript.bridge.NovaScriptExpansion
import com.dakuo.novascript.bridge.ForgeEventBridge
import com.dakuo.novascript.bridge.FabricEventBridge
import com.dakuo.novascript.ScriptSetupImpl
import com.dakuo.novascript.ScriptConfigurer
import com.dakuo.novascript.ScriptSetup
import com.dakuo.novascript.LibrarySetupImpl
import com.dakuo.novascript.SharedLibrarySetupImpl
import com.dakuo.novascript.RunMode

/**
 * 单个脚本的执行上下文，持有 Nova 实例和所有注册的资源。
 * 卸载时自动清理所有资源。
 */
class ScriptContext(
    val namespace: String,
    val pluginName: String,
    val name: String,
    val file: File,
    val nova: Nova,
    val scriptDir: File,
    val runMode: RunMode = RunMode.BYTECODE
) {
    var bindings: Map<String, Any?> = emptyMap()
    /** 原始代码（仅 code 注册时保存，供 reload 使用） */
    var sourceCode: String? = null
    /** 自定义 API 注入回调（reload 时自动重新调用） */
    var setupCallback: ScriptConfigurer? = null
    /** 变量绑定命名空间（用于隔离同名定义） */
    var bindingNamespace: String? = null
    /** 字节码编译结果（用于直接调用编译函数） */
    var compiled: com.novalang.runtime.CompiledNova? = null
    /** 卸载标志，阻止异步任务在卸载后继续调度 */
    @Volatile var unloaded = false
    val registeredListeners = mutableListOf<ProxyListener>()
    val registeredCommands = mutableListOf<String>()
    val scheduledTasks = mutableListOf<Cancellable>()
    val registeredPlaceholders = mutableListOf<String>()
    val dataSources = mutableListOf<DataSource>()
    val forgeListenerHandles = mutableListOf<ForgeEventBridge.ListenerHandle>()
    val fabricListenerHandles = mutableListOf<FabricEventBridge.ListenerHandle>()

    fun unload() {
        unloaded = true
        registeredListeners.forEach {
            try { unregisterListener(it) } catch (_: Exception) {}
        }
        registeredCommands.forEach {
            try { ScriptApi.unregisterCommand(it) } catch (_: Exception) {}
        }
        scheduledTasks.forEach {
            try { it.cancel() } catch (_: Exception) {}
        }
        NovaScriptExpansion.unregisterAll(registeredPlaceholders)
        dataSources.forEach {
            try { (it as? java.io.Closeable)?.close() } catch (_: Exception) {}
        }
        forgeListenerHandles.forEach { it.deactivate() }
        fabricListenerHandles.forEach { it.deactivate() }
        // 关闭脚本级 ClassLoader，释放类和 JAR 句柄
        val cl = nova.scriptClassLoader
        if (cl is URLClassLoader) {
            try { cl.close() } catch (_: Exception) {}
        }
        registeredListeners.clear()
        registeredCommands.clear()
        scheduledTasks.clear()
        registeredPlaceholders.clear()
        dataSources.clear()
        forgeListenerHandles.clear()
        fabricListenerHandles.clear()
    }
}

/**
 * 代理 NovaScheduler，自动将创建的任务记录到 ScriptContext 中，
 * 以便脚本卸载时能取消所有定时任务。
 */
class TrackingScheduler(
    private val delegate: NovaScheduler,
    private val context: ScriptContext
) : NovaScheduler {

    override fun mainExecutor(): Executor = Executor { task ->
        delegate.mainExecutor().execute(guarded(task))
    }

    override fun asyncExecutor(): Executor? = delegate.asyncExecutor()?.let { exec ->
        Executor { task -> exec.execute(guarded(task)) }
    }

    override fun isMainThread(): Boolean = delegate.isMainThread

    private val NOOP = object : Cancellable {
        override fun cancel() {}
        override fun isCancelled() = true
    }

    private fun guarded(task: Runnable) = Runnable {
        if (!context.unloaded) task.run()
    }

    override fun scheduleLater(delayMs: Long, task: Runnable): Cancellable {
        if (context.unloaded) return NOOP
        return delegate.scheduleLater(delayMs, guarded(task)).also { context.scheduledTasks.add(it) }
    }

    override fun scheduleRepeat(delayMs: Long, periodMs: Long, task: Runnable): Cancellable {
        if (context.unloaded) return NOOP
        return delegate.scheduleRepeat(delayMs, periodMs, guarded(task)).also { context.scheduledTasks.add(it) }
    }
}

/**
 * 脚本管理器，负责脚本的加载、卸载、重载和执行。
 * 支持命名空间（pluginName:scriptName）以区分不同插件注册的脚本。
 */
object ScriptManager {

    internal const val SELF_PLUGIN = "novascript"

    /** key = namespace ("pluginName:scriptName") */
    private val scripts = ConcurrentHashMap<String, ScriptContext>()
    /** key = library name, value = (pluginName, configurer) */
    private val libraries = ConcurrentHashMap<String, Pair<String, ScriptConfigurer>>()
    lateinit var baseScheduler: NovaScheduler
        private set
    lateinit var scriptsDir: File
        private set

    fun makeNamespace(pluginName: String, scriptName: String): String = "$pluginName:$scriptName"

    /**
     * 创建完整初始化的 Nova + ScriptContext。
     * 统一的脚本引擎工厂，注入 Scheduler + ScriptApi + 全局函数库。
     *
     * @param name 脚本/模块名（用于命名空间）
     * @param scriptDir 脚本数据目录
     * @param entryFile 入口文件（用于日志标识）
     */
    fun createEngine(name: String, scriptDir: File, entryFile: File = scriptDir): Pair<Nova, ScriptContext> {
        val nova = Nova()
        val ns = makeNamespace(SELF_PLUGIN, name)
        val ctx = ScriptContext(ns, SELF_PLUGIN, name, entryFile, nova, scriptDir)
        nova.setScheduler(TrackingScheduler(baseScheduler, ctx))
        ScriptApi.inject(nova, ctx)
        injectLibraries(nova)
        return Pair(nova, ctx)
    }

    fun init(scriptsDir: File) {
        this.scriptsDir = scriptsDir
        if (!scriptsDir.exists()) scriptsDir.mkdirs()
        baseScheduler = BukkitSchedulers.register(taboolib.platform.BukkitPlugin.getInstance())
    }

    // ========== NovaScript 自身脚本 ==========

    fun loadAll() {
        val dirs = scriptsDir.listFiles { f -> f.isDirectory } ?: emptyArray()
        dirs.filter { File(it, "main.nova").exists() }
            .sortedBy { it.name }
            .forEach { load(it.name) }

        // 第二轮：扫描外层单文件 .nova（跳过已加载的同名脚本）
        val files = scriptsDir.listFiles { f -> f.isFile && f.extension == "nova" } ?: emptyArray()
        files.sortedBy { it.name }.forEach { file ->
            val name = file.nameWithoutExtension
            val ns = makeNamespace(SELF_PLUGIN, name)
            if (!scripts.containsKey(ns)) {
                load(name)
            }
        }
    }

    fun load(name: String): Boolean {
        val ns = makeNamespace(SELF_PLUGIN, name)
        if (scripts.containsKey(ns)) {
            warning("[NovaScript] 脚本 '$name' 已加载")
            return false
        }

        val dir = File(scriptsDir, name)
        val mainFile = File(dir, "main.nova")
        val singleFile = File(scriptsDir, "$name.nova")

        val entryFile: File
        val scriptDir: File
        val isMultiFile: Boolean

        if (dir.isDirectory && mainFile.exists()) {
            entryFile = mainFile
            scriptDir = dir
            isMultiFile = true
        } else if (singleFile.exists()) {
            entryFile = singleFile
            scriptDir = File(scriptsDir, name)
            isMultiFile = false
        } else {
            warning("[NovaScript] 脚本不存在: $name（需要 $name.nova 或 $name/main.nova）")
            return false
        }

        val nova = Nova()
        val context = ScriptContext(ns, SELF_PLUGIN, name, entryFile, nova, scriptDir, RunMode.BYTECODE)

        nova.setScheduler(TrackingScheduler(baseScheduler, context))
        ScriptApi.inject(nova, context)
        injectLibraries(nova)

        return try {
            if (isMultiFile) {
                nova.getInterpreter().setScriptBasePath(dir.toPath())
            }
            val code = entryFile.readText(Charsets.UTF_8)
            MavenDependencyProcessor.resolve(code)?.let { nova.setScriptClassLoader(it) }
            val compiled = nova.compileToBytecode(code, entryFile.name)
            compiled.run()
            context.bindings = compiled.getBindings()
            context.compiled = compiled
            scripts[ns] = context
            // 注册到上下文注册表，供 LSP 补全查询
            com.novalang.runtime.http.NovaContextRegistry.register("script", ns, nova)
            val mode = if (isMultiFile) "多文件" else "单文件"
            info("[NovaScript] 已加载脚本: $name ($mode)")
            true
        } catch (e: Exception) {
            warning("[NovaScript] 加载脚本 '$name' 失败: ${e.message}")
            logScriptError(e)
            context.unload()
            false
        }
    }

    fun unload(name: String): Boolean {
        val ns = makeNamespace(SELF_PLUGIN, name)
        return unloadByNamespace(ns)
    }

    fun reload(name: String? = null) {
        if (name != null) {
            unload(name)
            load(name)
        } else {
            val selfScripts = scripts.values.filter { it.pluginName == SELF_PLUGIN }.map { it.name }
            selfScripts.forEach { unload(it) }
            loadAll()
        }
    }

    // ========== 外部插件脚本 ==========

    /**
     * 加载外部插件的脚本文件。
     */
    fun loadExternal(
        pluginName: String,
        name: String,
        file: File,
        scriptDir: File = file.parentFile,
        setup: ScriptConfigurer? = null,
        mode: RunMode = RunMode.BYTECODE,
        bindingNamespace: String? = null
    ): Boolean {
        val ns = makeNamespace(pluginName, name)
        if (scripts.containsKey(ns)) {
            warning("[NovaScript] 脚本 '$ns' 已加载")
            return false
        }
        if (!file.exists()) {
            warning("[NovaScript] 脚本文件不存在: ${file.absolutePath}")
            return false
        }

        val code = file.readText(Charsets.UTF_8)
        return loadFromCode(pluginName, name, code, file, scriptDir, file.parentFile, setup, mode, bindingNamespace)
    }

    /**
     * 从代码字符串加载外部插件的脚本。
     */
    fun loadExternalFromCode(
        pluginName: String,
        name: String,
        code: String,
        scriptDir: File,
        setup: ScriptConfigurer? = null,
        mode: RunMode = RunMode.BYTECODE,
        bindingNamespace: String? = null
    ): Boolean {
        val ns = makeNamespace(pluginName, name)
        if (scripts.containsKey(ns)) {
            warning("[NovaScript] 脚本 '$ns' 已加载")
            return false
        }
        return loadFromCode(pluginName, name, code, null, scriptDir, null, setup, mode, bindingNamespace)
    }

    private fun loadFromCode(
        pluginName: String,
        name: String,
        code: String,
        file: File?,
        scriptDir: File,
        basePath: File?,
        setup: ScriptConfigurer?,
        mode: RunMode,
        bindingNamespace: String? = null
    ): Boolean {
        val ns = makeNamespace(pluginName, name)
        val nova = Nova()
        val entryFile = file ?: File(scriptDir, "$name.nova")
        val context = ScriptContext(ns, pluginName, name, entryFile, nova, scriptDir, mode)
        context.bindingNamespace = bindingNamespace

        nova.setScheduler(TrackingScheduler(baseScheduler, context))
        ScriptApi.inject(nova, context)
        injectLibraries(nova)

        // code 注册时保存源码供 reload 使用
        if (file == null) {
            context.sourceCode = code
        }
        // 自定义 API 注入（保存回调供 reload 重新调用）
        context.setupCallback = setup
        if (setup != null) {
            setup.configure(ScriptSetupImpl(nova, bindingNamespace))
        }

        return try {
            MavenDependencyProcessor.resolve(code)?.let { nova.setScriptClassLoader(it) }
            if (basePath != null) {
                nova.getInterpreter().setScriptBasePath(basePath.toPath())
            }
            when (mode) {
                RunMode.BYTECODE -> {
                    val compiled = if (bindingNamespace != null)
                        nova.compileToBytecode(code, entryFile.name, bindingNamespace)
                    else nova.compileToBytecode(code, entryFile.name)
                    compiled.run()
                    context.bindings = compiled.getBindings()
                    context.compiled = compiled
                }
                RunMode.COMPILED -> {
                    val compiled = if (bindingNamespace != null)
                        nova.compile(code, entryFile.name, bindingNamespace)
                    else nova.compile(code, entryFile.name)
                    compiled.run()
                }
                RunMode.INTERPRETED -> {
                    if (bindingNamespace != null) nova.eval(code, bindingNamespace)
                    else nova.eval(code)
                }
            }
            scripts[ns] = context
            com.novalang.runtime.http.NovaContextRegistry.register("script", ns, nova)
            true
        } catch (e: Exception) {
            warning("[NovaScript] 加载外部脚本 '$ns' 失败: ${e.message}")
            logScriptError(e)
            context.unload()
            false
        }
    }

    // ========== 通用操作 ==========

    /**
     * 按命名空间卸载脚本。
     */
    fun unloadByNamespace(namespace: String): Boolean {
        val context = scripts.remove(namespace) ?: return false
        context.unload()
        com.novalang.runtime.http.NovaContextRegistry.unregister("script", namespace)
        info("[NovaScript] 已卸载脚本: $namespace")
        return true
    }

    /**
     * 卸载某个插件的所有脚本。
     */
    fun unloadAll(pluginName: String) {
        scripts.values
            .filter { it.pluginName == pluginName }
            .forEach { unloadByNamespace(it.namespace) }
    }

    /**
     * 卸载所有脚本。
     */
    fun unloadAll() {
        scripts.keys.toList().forEach { unloadByNamespace(it) }
    }

    // ========== 全局函数库 ==========

    /**
     * 注册全局函数库，所有后续加载的脚本都可以通过 libName.funcName() 调用。
     */
    fun registerLibrary(pluginName: String, name: String, setup: ScriptConfigurer) {
        libraries[name] = Pair(pluginName, setup)
        // 同步写入 shared() 全局注册表（命名空间 = 库名）
        com.novalang.runtime.NovaRuntime.shared().defineLibrary(name) { sharedBuilder ->
            setup.configure(SharedLibrarySetupImpl(sharedBuilder))
        }
    }

    /**
     * 注销全局函数库。
     */
    fun unregisterLibrary(name: String) {
        libraries.remove(name)
        com.novalang.runtime.NovaRuntime.shared().unregisterNamespace(name)
    }

    /**
     * 注销某个插件注册的所有函数库。
     */
    fun unregisterAllLibraries(pluginName: String) {
        val toRemove = libraries.entries.filter { it.value.first == pluginName }.map { it.key }
        libraries.entries.removeIf { it.value.first == pluginName }
        toRemove.forEach { com.novalang.runtime.NovaRuntime.shared().unregisterNamespace(it) }
    }

    /**
     * 获取某个插件注册的函数库名列表。
     */
    fun getLibraryNames(pluginName: String): List<String> =
        libraries.entries.filter { it.value.first == pluginName }.map { it.key }

    /**
     * 将所有已注册的全局函数库注入到 Nova 实例中。
     */
    fun injectLibraries(nova: Nova) {
        for ((name, pair) in libraries) {
            val (_, setup) = pair
            nova.defineLibrary(name) { builder: LibraryBuilder ->
                setup.configure(LibrarySetupImpl(builder))
            }
        }
    }

    /**
     * 调用脚本中导出的函数。
     * - 字节码模式：从 bindings 中取 FunctionN 调用
     * - 解释/预编译模式：通过 nova.call() 调用解释器中的函数
     */
    fun callFunction(namespace: String, funcName: String, vararg args: Any?): Any? {
        val context = scripts[namespace] ?: run {
            warning("[NovaScript] callFunction 失败: 脚本未加载 '$namespace'")
            return null
        }
        return when (context.runMode) {
            RunMode.BYTECODE -> {
                val func = context.bindings[funcName]
                if (func != null) {
                    ScriptApi.invokeCallback(func, context, *args)
                } else if (context.compiled?.hasFunction(funcName) == true) {
                    // 纯函数脚本（无顶层代码）：通过编译类直接调用静态方法
                    context.compiled!!.call(funcName, *args)
                } else {
                    warning("[NovaScript] callFunction 失败: 函数 '$funcName' 未定义 (脚本: $namespace)")
                    null
                }
            }
            RunMode.COMPILED, RunMode.INTERPRETED -> {
                if (!context.nova.hasFunction(funcName)) {
                    warning("[NovaScript] callFunction 失败: 函数 '$funcName' 未定义 (脚本: $namespace)")
                    return null
                }
                context.nova.call(funcName, *args)
            }
        }
    }

    fun getScript(namespace: String): ScriptContext? = scripts[namespace]

    fun getScriptsByPlugin(pluginName: String): List<ScriptContext> =
        scripts.values.filter { it.pluginName == pluginName }

    fun list(): Collection<ScriptContext> = scripts.values

    fun eval(code: String): Any? {
        val nova = Nova()
        nova.setScheduler(baseScheduler)
        return try {
            MavenDependencyProcessor.resolve(code)?.let { nova.setScriptClassLoader(it) }
            nova.eval(code)
        } catch (e: Exception) {
            warning("[NovaScript] 执行失败: ${e.message}")
            null
        }
    }

    /**
     * 在指定脚本的上下文中执行调试代码（解释模式，无内存泄漏）。
     * 能访问该脚本的所有 API、变量和函数。
     */
    fun evalInScript(namespace: String, code: String): Any? {
        val context = scripts[namespace] ?: run {
            warning("[NovaScript] eval 失败: 脚本未加载 '$namespace'")
            return null
        }
        // 将字节码模式的 bindings 注入回 Nova 解释器，使 eval 可见
        if (context.runMode == RunMode.BYTECODE) {
            for ((k, v) in context.bindings) {
                context.nova.set(k, v)
            }
        }
        return try {
            context.nova.eval(code)
        } catch (e: Exception) {
            warning("[NovaScript] eval 失败: ${e.message}")
            logScriptError(e)
            null
        }
    }

    /**
     * 获取 NovaScript 自身已加载脚本的名称列表。
     */
    fun getScriptNames(): List<String> =
        scripts.values.filter { it.pluginName == SELF_PLUGIN }.map { it.name }

    /**
     * 获取 NovaScript 自身未加载脚本的名称列表。
     */
    fun getUnloadedScriptNames(): List<String> {
        val loaded = scripts.values
            .filter { it.pluginName == SELF_PLUGIN }
            .map { it.name }
            .toSet()
        val names = mutableSetOf<String>()

        val dirs = scriptsDir.listFiles { f -> f.isDirectory } ?: emptyArray()
        dirs.filter { File(it, "main.nova").exists() }
            .forEach { names.add(it.name) }

        val files = scriptsDir.listFiles { f -> f.isFile && f.extension == "nova" } ?: emptyArray()
        files.forEach { names.add(it.nameWithoutExtension) }

        return names.filter { it !in loaded }.sorted()
    }

    /**
     * 脚本错误日志：已知脚本异常只打简短消息，未知异常打完整堆栈。
     */
    private fun logScriptError(e: Exception) {
        if (isScriptError(e)) return
        e.printStackTrace()
    }

    private fun isScriptError(e: Throwable): Boolean {
        var cause: Throwable? = e
        while (cause != null) {
            val name = cause.javaClass.name
            if (name.endsWith("ParseException") || name.endsWith("NovaException")
                || name.endsWith("NovaRuntimeException")) {
                return true
            }
            cause = cause.cause
        }
        return false
    }
}

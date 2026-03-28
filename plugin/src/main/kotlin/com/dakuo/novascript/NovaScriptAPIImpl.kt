package com.dakuo.novascript

import com.dakuo.novascript.script.ScriptApi
import com.dakuo.novascript.script.ScriptContext
import com.dakuo.novascript.script.ScriptManager
import com.dakuo.novascript.script.TrackingScheduler
import com.novalang.runtime.Nova
import com.novalang.runtime.Function0 as NvFunction0
import com.novalang.runtime.Function1 as NvFunction1
import com.novalang.runtime.Function2 as NvFunction2
import com.novalang.runtime.Function3 as NvFunction3
import com.novalang.runtime.Function4 as NvFunction4
import com.novalang.runtime.NovaCallable
import com.novalang.runtime.interpreter.LibraryBuilder
import java.io.File

/**
 * NovaScriptAPI.Delegate 的实现。
 * 在插件启动时注入: NovaScriptAPI.delegate = NovaScriptAPIDelegateImpl()
 */
class NovaScriptAPIDelegateImpl : NovaScriptAPI.Delegate {

    override fun register(pluginName: String, name: String, file: File, scriptDir: File,
                          mode: RunMode, setup: ScriptConfigurer?, namespace: String?): Boolean =
        ScriptManager.loadExternal(pluginName, name, file, scriptDir, setup, mode, namespace)

    override fun registerCode(pluginName: String, name: String, code: String, scriptDir: File,
                              mode: RunMode, setup: ScriptConfigurer?, namespace: String?): Boolean =
        ScriptManager.loadExternalFromCode(pluginName, name, code, scriptDir, setup, mode, namespace)

    override fun unregister(pluginName: String, name: String): Boolean {
        val ns = ScriptManager.makeNamespace(pluginName, name)
        return ScriptManager.unloadByNamespace(ns)
    }

    override fun unregisterAll(pluginName: String) = ScriptManager.unloadAll(pluginName)

    override fun reload(pluginName: String, name: String): Boolean {
        val ns = ScriptManager.makeNamespace(pluginName, name)
        val context = ScriptManager.getScript(ns) ?: return false
        val file = context.file
        val scriptDir = context.scriptDir
        val setup = context.setupCallback
        val mode = context.runMode
        val sourceCode = context.sourceCode
        val nsParam = context.bindingNamespace
        ScriptManager.unloadByNamespace(ns)
        return if (sourceCode != null) {
            ScriptManager.loadExternalFromCode(pluginName, name, sourceCode, scriptDir, setup, mode, nsParam)
        } else {
            ScriptManager.loadExternal(pluginName, name, file, scriptDir, setup, mode, nsParam)
        }
    }

    override fun callFunction(pluginName: String, scriptName: String, functionName: String, args: Array<out Any?>): Any? {
        val ns = ScriptManager.makeNamespace(pluginName, scriptName)
        return ScriptManager.callFunction(ns, functionName, *args)
    }

    override fun callFunctionNs(namespace: String, functionName: String, args: Array<out Any?>): Any? =
        ScriptManager.callFunction(namespace, functionName, *args)

    override fun eval(code: String): Any? = ScriptManager.eval(code)

    override fun compile(code: String, setup: ScriptConfigurer?): NovaCompiled {
        val (nova, context) = prepareNova("<compile>", setup)
        return NovaCompiledImpl(nova.compile(code, "<compiled>"), context)
    }

    override fun compileToBytecode(code: String, setup: ScriptConfigurer?): NovaCompiled {
        val (nova, context) = prepareNova("<compile>", setup)
        return NovaCompiledImpl(nova.compileToBytecode(code, "<compiled>"), context)
    }

    private fun prepareNova(name: String, setup: ScriptConfigurer?): Pair<Nova, ScriptContext> {
        val scriptDir = File(ScriptManager.scriptsDir, "_compiled/$name")
        val (nova, context) = ScriptManager.createEngine("_compiled_$name", scriptDir)
        setup?.configure(ScriptSetupImpl(nova))
        return Pair(nova, context)
    }

    override fun hasFunction(pluginName: String, name: String, functionName: String): Boolean {
        val ns = ScriptManager.makeNamespace(pluginName, name)
        return hasFunctionNs(ns, functionName)
    }

    override fun hasFunctionNs(namespace: String, functionName: String): Boolean {
        val context = ScriptManager.getScript(namespace) ?: return false
        return when (context.runMode) {
            RunMode.BYTECODE -> isCallback(context.bindings[functionName])
                    || context.compiled?.hasFunction(functionName) == true
            RunMode.COMPILED, RunMode.INTERPRETED -> context.nova.hasFunction(functionName)
        }
    }

    override fun isCallback(value: Any?): Boolean {
        return value is NovaCallable ||
            value is NvFunction0<*> ||
            value is NvFunction1<*, *> ||
            value is NvFunction2<*, *, *> ||
            value is NvFunction3<*, *, *, *>
    }

    override fun invokeCallback(pluginName: String, scriptName: String, callback: Any?, args: Array<out Any?>): Any? {
        val ns = ScriptManager.makeNamespace(pluginName, scriptName)
        return invokeCallbackNs(ns, callback, args)
    }

    override fun invokeCallbackNs(namespace: String, callback: Any?, args: Array<out Any?>): Any? {
        val context = ScriptManager.getScript(namespace) ?: return null
        if (!isCallback(callback)) return null
        return ScriptApi.invokeCallback(callback!!, context, *args)
    }

    override fun isLoaded(pluginName: String, name: String): Boolean {
        val ns = ScriptManager.makeNamespace(pluginName, name)
        return ScriptManager.getScript(ns) != null
    }

    override fun getScripts(pluginName: String): List<String> =
        ScriptManager.getScriptsByPlugin(pluginName).map { it.name }

    override fun defineLibrary(pluginName: String, name: String, setup: ScriptConfigurer) =
        ScriptManager.registerLibrary(pluginName, name, setup)

    override fun removeLibrary(pluginName: String, name: String) = ScriptManager.unregisterLibrary(name)

    override fun getLibraries(pluginName: String): List<String> = ScriptManager.getLibraryNames(pluginName)

    override fun createRegistry(name: String): ScriptRegistry = ScriptRegistryImpl(name)
}

// ── 内部实现类 ──────────────────────────────

/**
 * NovaCompiled 的实现，包装 nova.runtime.CompiledNova。
 */
internal class NovaCompiledImpl(
    private val compiled: com.novalang.runtime.CompiledNova,
    private val context: ScriptContext? = null
) : NovaCompiled {
    override fun close() { context?.unload() }
    override fun run(): Any? = compiled.run()
    override fun run(vararg kvBindings: Any?): Any? = compiled.run(*kvBindings)
    override fun call(funcName: String, vararg args: Any?): Any? = compiled.call(funcName, *args)
    override fun hasFunction(funcName: String): Boolean = compiled.hasFunction(funcName)
    override fun set(name: String, value: Any?): NovaCompiled { compiled.set(name, value); return this }
    override fun get(name: String): Any? = compiled.get(name)
    override fun getBindings(): Map<String, Any?> = compiled.getBindings()
}

/**
 * ScriptRegistry 的实现。一个 Nova 引擎 + 多个命名 CompiledNova。
 */
internal class ScriptRegistryImpl(name: String) : ScriptRegistry {
    private val scripts = mutableMapOf<String, com.novalang.runtime.CompiledNova>()
    private val nova: Nova
    private val context: ScriptContext
    private var closed = false

    init {
        val (n, c) = ScriptManager.createEngine("_registry_$name", File(ScriptManager.scriptsDir, "_registry/$name"))
        nova = n; context = c
    }

    override fun compile(name: String, code: String): ScriptRegistry {
        check(!closed) { "ScriptRegistry 已关闭" }
        scripts[name] = nova.compileToBytecode(code.trim(), "reg_$name.nova")
        return this
    }

    override fun call(name: String, vararg kvBindings: Any?): Any? {
        check(!closed) { "ScriptRegistry 已关闭" }
        val compiled = scripts[name] ?: throw IllegalArgumentException("脚本未注册: $name")
        return try {
            if (kvBindings.isNotEmpty()) compiled.run(*kvBindings) else compiled.run()
        } catch (e: Exception) {
            taboolib.common.platform.function.warning("[NovaScript] registry 脚本 '$name' 执行错误: ${e.message}")
            null
        }
    }

    override fun has(name: String): Boolean = scripts.containsKey(name)
    override fun remove(name: String) { scripts.remove(name) }
    override fun names(): List<String> = scripts.keys.toList()
    override fun close() { if (closed) return; closed = true; scripts.clear(); context.unload() }
}

/**
 * ScriptSetup → Nova 实例委托。
 */
internal class ScriptSetupImpl(
    private val nova: Nova,
    private val namespace: String? = null
) : ScriptSetup {
    override fun defineFunction(name: String, handler: ScriptHandler0) {
        val func = NvFunction0 { handler.handle() }
        if (namespace != null) nova.defineFunction(name, func, namespace) else nova.defineFunction(name, func)
    }
    override fun defineFunction(name: String, handler: ScriptHandler1) {
        val func = NvFunction1 { a: Any -> handler.handle(a) }
        if (namespace != null) nova.defineFunction(name, func, namespace) else nova.defineFunction(name, func)
    }
    override fun defineFunction(name: String, handler: ScriptHandler2) {
        val func = NvFunction2 { a: Any, b: Any -> handler.handle(a, b) }
        if (namespace != null) nova.defineFunction(name, func, namespace) else nova.defineFunction(name, func)
    }
    override fun defineFunction(name: String, handler: ScriptHandler3) {
        val func = NvFunction3 { a: Any, b: Any, c: Any -> handler.handle(a, b, c) }
        if (namespace != null) nova.defineFunction(name, func, namespace) else nova.defineFunction(name, func)
    }
    override fun defineFunction(name: String, handler: ScriptHandler4) {
        val func = NvFunction4<Any, Any, Any, Any, Any> { a, b, c, d -> handler.handle(a, b, c, d) }
        if (namespace != null) nova.defineFunction(name, func, namespace) else nova.defineFunction(name, func)
    }
    @Suppress("UNCHECKED_CAST")
    override fun defineFunctionVararg(name: String, handler: ScriptHandlerVararg) {
        val func: (Array<Any?>) -> Any? = { args -> handler.handle(args) }
        if (namespace != null) nova.defineFunctionVararg(name, func, namespace) else nova.defineFunctionVararg(name, func)
    }
    override fun set(name: String, value: Any?) {
        if (namespace != null) nova.set(name, value, namespace) else nova.set(name, value)
    }
}

/**
 * ScriptSetup → LibraryBuilder 适配器。
 */
internal class LibrarySetupImpl(private val builder: LibraryBuilder) : ScriptSetup {
    override fun defineFunction(name: String, handler: ScriptHandler0) { builder.defineFunction(name, NvFunction0 { handler.handle() }) }
    override fun defineFunction(name: String, handler: ScriptHandler1) { builder.defineFunction(name, NvFunction1 { a: Any -> handler.handle(a) }) }
    override fun defineFunction(name: String, handler: ScriptHandler2) { builder.defineFunction(name, NvFunction2 { a: Any, b: Any -> handler.handle(a, b) }) }
    override fun defineFunction(name: String, handler: ScriptHandler3) { builder.defineFunction(name, NvFunction3 { a: Any, b: Any, c: Any -> handler.handle(a, b, c) }) }
    override fun defineFunction(name: String, handler: ScriptHandler4) { builder.defineFunction(name, NvFunction4<Any, Any, Any, Any, Any> { a, b, c, d -> handler.handle(a, b, c, d) }) }
    @Suppress("UNCHECKED_CAST")
    override fun defineFunctionVararg(name: String, handler: ScriptHandlerVararg) { builder.defineFunctionVararg(name) { args -> handler.handle(args as Array<Any?>) } }
    override fun set(name: String, value: Any?) { builder.defineVal(name, value) }
}

/**
 * ScriptSetup → NovaRuntime.shared() 全局注册表适配器。
 */
internal class GlobalScriptSetup : ScriptSetup {
    private val rt = com.novalang.runtime.NovaRuntime.shared()
    override fun defineFunction(name: String, handler: ScriptHandler0) { rt.register(name, NvFunction0 { handler.handle() }, null, null, null) }
    override fun defineFunction(name: String, handler: ScriptHandler1) { rt.register(name, NvFunction1 { a: Any -> handler.handle(a) }, null, null, null) }
    override fun defineFunction(name: String, handler: ScriptHandler2) { rt.register(name, NvFunction2 { a: Any, b: Any -> handler.handle(a, b) }, null, null, null) }
    override fun defineFunction(name: String, handler: ScriptHandler3) { rt.register(name, NvFunction3 { a: Any, b: Any, c: Any -> handler.handle(a, b, c) }, null, null, null) }
    override fun defineFunction(name: String, handler: ScriptHandler4) {
        rt.registerVararg(name, NvFunction1<Array<Any>, Any?> { args -> handler.handle(args[0], args[1], args[2], args[3]) })
    }
    @Suppress("UNCHECKED_CAST")
    override fun defineFunctionVararg(name: String, handler: ScriptHandlerVararg) {
        rt.registerVararg(name, NvFunction1<Array<Any>, Any?> { args -> handler.handle(args as Array<Any?>) })
    }
    override fun set(name: String, value: Any?) { rt.set(name, value, null, null, null) }
}

/**
 * ScriptSetup → NovaRuntime.LibraryBuilder 适配器（shared() 全局注册表用）。
 */
internal class SharedLibrarySetupImpl(
    private val builder: com.novalang.runtime.NovaRuntime.LibraryBuilder
) : ScriptSetup {
    override fun defineFunction(name: String, handler: ScriptHandler0) { builder.function(name, NvFunction0 { handler.handle() }) }
    override fun defineFunction(name: String, handler: ScriptHandler1) { builder.function(name, NvFunction1 { a: Any -> handler.handle(a) }) }
    override fun defineFunction(name: String, handler: ScriptHandler2) { builder.function(name, NvFunction2 { a: Any, b: Any -> handler.handle(a, b) }) }
    override fun defineFunction(name: String, handler: ScriptHandler3) { builder.function(name, NvFunction3 { a: Any, b: Any, c: Any -> handler.handle(a, b, c) }) }
    override fun defineFunction(name: String, handler: ScriptHandler4) { builder.functionVararg(name) { args -> handler.handle(args[0], args[1], args[2], args[3]) } }
    @Suppress("UNCHECKED_CAST")
    override fun defineFunctionVararg(name: String, handler: ScriptHandlerVararg) { builder.functionVararg(name) { args -> handler.handle(args as Array<Any?>) } }
    override fun set(name: String, value: Any?) { builder.set(name, value) }
}

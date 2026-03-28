package com.dakuo.novascript.bridge

import com.novalang.runtime.AbstractNovaValue
import com.novalang.runtime.Function2
import com.novalang.runtime.NovaCallable
import com.novalang.runtime.NovaScriptContext
import org.bukkit.entity.Player
import taboolib.platform.compat.PlaceholderExpansion
import com.dakuo.novascript.script.ScriptContext

/**
 * TabooLib PlaceholderAPI 扩展实现。
 * 汇总所有脚本注册的占位符，统一通过 "novascript" 前缀访问。
 *
 * 用法: %novascript_<标识符>% 或 %novascript_<标识符>_<参数>%
 */
object NovaScriptExpansion : PlaceholderExpansion {

    override val identifier: String = "novascript"

    override val autoReload: Boolean = true

    /** 脚本注册的占位符：id -> (callback, context) */
    private val handlers = mutableMapOf<String, Pair<Any, ScriptContext>>()

    /** 配置式占位符（预编译字节码）：id -> evaluator */
    private val configHandlers = mutableMapOf<String, (Player?) -> String>()

    // ── 脚本占位符 ──────────────────────────

    fun register(id: String, callback: Any, context: ScriptContext) {
        handlers[id] = Pair(callback, context)
    }

    fun unregister(id: String) {
        handlers.remove(id)
    }

    fun unregisterAll(ids: Collection<String>) {
        ids.forEach { handlers.remove(it) }
    }

    // ── 配置式占位符 ────────────────────────

    fun registerConfig(id: String, evaluator: (Player?) -> String) {
        configHandlers[id] = evaluator
    }

    fun unregisterConfig(id: String) {
        configHandlers.remove(id)
    }

    fun unregisterAllConfig() {
        configHandlers.clear()
    }

    // ── 请求处理 ────────────────────────────

    override fun onPlaceholderRequest(player: Player?, args: String): String {
        // 先精确匹配配置式占位符（支持带连字符的 key 如 server-online）
        configHandlers[args]?.let { return it(player) }

        // args 格式: "<identifier>_<params>" 或 "<identifier>"
        val separatorIndex = args.indexOf('_')
        val id: String
        val params: String
        if (separatorIndex == -1) {
            id = args
            params = ""
        } else {
            id = args.substring(0, separatorIndex)
            params = args.substring(separatorIndex + 1)

            // 尝试用完整 args（含下划线）匹配配置式占位符
            // 例如 player-pos 不应被拆成 id=player params=pos
            configHandlers[id]?.let { return it(player) }
        }

        val (callback, context) = handlers[id] ?: return ""

        return try {
            val result = if (callback is NovaCallable) {
                callback.call(context.nova.getInterpreter(), listOf(
                    AbstractNovaValue.fromJava(player),
                    AbstractNovaValue.fromJava(params)
                )).toJavaValue()
            } else {
                @Suppress("UNCHECKED_CAST")
                val needsContext = !NovaScriptContext.isActive()
                if (needsContext) {
                    NovaScriptContext.init(context.bindings as Map<String, Any>)
                }
                try {
                    (callback as Function2<Any?, Any?, Any?>).invoke(player, params)
                } finally {
                    if (needsContext) {
                        NovaScriptContext.clear()
                    }
                }
            }
            result?.toString() ?: ""
        } catch (_: Exception) {
            ""
        }
    }
}

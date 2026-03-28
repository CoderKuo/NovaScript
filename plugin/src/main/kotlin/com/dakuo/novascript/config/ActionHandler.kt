package com.dakuo.novascript.config

import com.dakuo.novascript.script.ScriptManager
import com.novalang.runtime.CompiledNova
import taboolib.common.platform.function.warning

/**
 * 配置式动作处理器。
 * 支持两种模式：内嵌代码（预编译字节码）或引用已加载脚本的函数。
 *
 * 配置格式：
 * - 内嵌代码: "player.msg(\"hello\")" 或多行 code
 * - 脚本引用: "script:脚本名:函数名"
 */
sealed class ActionHandler(val label: String) {

    /** 预编译的内嵌代码 */
    class Compiled(label: String, val compiled: CompiledNova) : ActionHandler(label)

    /** 引用 scripts/ 中的脚本函数 */
    class ScriptRef(label: String, val namespace: String, val functionName: String) : ActionHandler(label)

    /**
     * 执行动作，传入运行时变量（key-value 交替）。
     */
    fun execute(vararg kvBindings: Any?) {
        try {
            when (this) {
                is Compiled -> compiled.run(*kvBindings)
                is ScriptRef -> {
                    // 从 kvBindings 提取参数值（跳过 key）
                    val args = Array(kvBindings.size / 2) { i -> kvBindings[i * 2 + 1] }
                    ScriptManager.callFunction(namespace, functionName, *args)
                }
            }
        } catch (e: Exception) {
            warning("[NovaScript] 动作 '$label' 执行错误: ${e.message}")
        }
    }

    companion object {

        private val SCRIPT_PREFIX = "script:"

        /**
         * 解析配置值，返回 ActionHandler。
         *
         * @param value 配置值：内嵌代码或 "script:脚本名:函数名"
         * @param label 标识名（用于日志）
         * @param compiler 编译内嵌代码的回调（传入代码返回 CompiledNova）
         */
        fun parse(value: String, label: String, compiler: (String) -> CompiledNova): ActionHandler {
            val trimmed = value.trim()
            if (trimmed.startsWith(SCRIPT_PREFIX)) {
                // script:scriptName:functionName
                val parts = trimmed.removePrefix(SCRIPT_PREFIX).split(":", limit = 2)
                if (parts.size != 2) {
                    throw IllegalArgumentException("脚本引用格式错误: $trimmed（应为 script:脚本名:函数名）")
                }
                val scriptName = parts[0]
                val funcName = parts[1]
                val ns = ScriptManager.makeNamespace(ScriptManager.SELF_PLUGIN, scriptName)
                return ScriptRef(label, ns, funcName)
            }
            return Compiled(label, compiler(trimmed))
        }
    }
}

package com.dakuo.novascript

import org.bukkit.event.server.PluginDisableEvent
import taboolib.common.platform.Plugin
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning
import java.io.File
import com.dakuo.novascript.script.ScriptApi
import com.dakuo.novascript.script.ScriptManager
import com.dakuo.novascript.config.PlaceholderManager
import com.dakuo.novascript.config.EventManager
import com.dakuo.novascript.config.TaskManager
import com.dakuo.novascript.config.ItemScriptManager
import com.dakuo.novascript.config.BlockScriptManager
import com.dakuo.novascript.config.CommandManager
import com.dakuo.novascript.module.ModuleManager
import com.dakuo.novascript.bridge.RulibBridge

object NovaScript : Plugin() {

    private val EXAMPLES = listOf(
        "welcome.nova",
        "shop.nova",
        "tpa.nova",
        "chatformat.nova",
        "auction.nova",
        "rpg.nova",
        "lottery.nova",
        "arena.nova"
    )

    override fun onLoad() {
        // 尽早注入 API 实现，确保其他插件 onLoad 中调用 NovaScriptAPI 不会报错
        NovaScriptAPI.delegate = NovaScriptAPIDelegateImpl()
    }

    override fun onEnable() {

        // 注册全局 API 到 NovaRuntime.shared()（所有脚本自动可见）
        ScriptApi.injectGlobal()
        RulibBridge.injectGlobal()

        val scriptsDir = File(getDataFolder(), "scripts")
        ScriptManager.init(scriptsDir)
        releaseExamples(scriptsDir)

        // 先加载模块（让模块注入的函数在脚本编译前可见）
        ModuleManager.init(getDataFolder())

        ScriptManager.loadAll()
        PlaceholderManager.init(getDataFolder())
        EventManager.init(getDataFolder())
        TaskManager.init(getDataFolder())
        CommandManager.init(getDataFolder())
        ItemScriptManager.init(getDataFolder())
        BlockScriptManager.init(getDataFolder())
        val count = ScriptManager.list().size
        info("[NovaScript] 插件已启用! 已加载 $count 个脚本")
    }

    private fun releaseExamples(scriptsDir: File) {
        val examplesDir = File(scriptsDir, "examples")
        if (examplesDir.exists()) return
        examplesDir.mkdirs()
        for (name in EXAMPLES) {
            val input = javaClass.classLoader.getResourceAsStream("examples/$name") ?: continue
            File(examplesDir, name).writeBytes(input.readBytes())
        }
    }

    override fun onDisable() {
        BlockScriptManager.unloadAll()
        ItemScriptManager.unloadAll()
        CommandManager.unloadAll()
        TaskManager.unloadAll()
        EventManager.unloadAll()
        PlaceholderManager.unloadAll()
        ScriptManager.unloadAll()
        ModuleManager.unloadAll()
        // 停止运行时 API 服务
        com.novalang.runtime.http.NovaApiServer.stopDefault()
        // 清空全局注册表
        com.novalang.runtime.NovaRuntime.shared().clearAll()
        info("[NovaScript] 插件已禁用")
    }

    /**
     * 监听其他插件卸载事件，自动清理其注册的脚本。
     */
    @SubscribeEvent
    fun onPluginDisable(event: PluginDisableEvent) {
        val pluginName = event.plugin.name
        if (pluginName != "NovaScript") {
            ScriptManager.unloadAll(pluginName)
            ScriptManager.unregisterAllLibraries(pluginName)
        }
    }
}

package com.dakuo.novascript.config

import com.dakuo.novascript.script.ScriptContext
import com.dakuo.novascript.script.ScriptManager
import com.dakuo.novascript.script.ScriptApi
import com.novalang.runtime.CompiledNova
import org.bukkit.command.Command
import org.bukkit.command.CommandMap
import org.bukkit.command.CommandSender
import org.bukkit.Bukkit
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning
import taboolib.module.configuration.Configuration
import java.io.File

/**
 * 配置式命令管理器。
 *
 * 读取 commands/ 目录下的 yml 文件，每个文件定义若干命令。
 * 支持权限、Tab 补全、别名、内嵌代码和脚本引用。
 *
 * 配置格式（commands/server.yml）:
 * ```yaml
 * spawn:
 *   code: |
 *     player.tp(Bukkit.getWorld("world").getSpawnLocation())
 *     player.msg("&a已传送到出生点")
 *   permission: "server.spawn"
 *   permission-message: "&c你没有权限传送到出生点"
 *   aliases:
 *     - "s"
 *     - "home"
 *   tab:
 *     - "set"
 *     - "tp"
 *
 * fly:
 *   code: "script:utils:toggleFly"
 *   permission: "server.fly"
 * ```
 */
object CommandManager {

    private var context: ScriptContext? = null
    private lateinit var commandsDir: File
    private val registeredNames = mutableListOf<String>()

    fun init(dataFolder: File) {
        commandsDir = File(dataFolder, "commands")
        if (!commandsDir.exists()) {
            commandsDir.mkdirs()
            File(commandsDir, "example.yml").writeText(DEFAULT_CONFIG)
        }
        reload()
    }

    fun reload() {
        // 卸载旧命令
        context?.unload()
        context = null
        registeredNames.clear()

        val files = commandsDir.listFiles { f -> f.isFile && f.extension == "yml" } ?: return
        if (files.isEmpty()) return

        val (nova, ctx) = ScriptManager.createEngine("_commands", File(commandsDir, "_data"), commandsDir)

        var loaded = 0
        for (file in files.sortedBy { it.name }) {
            try {
                val config = Configuration.loadFromFile(file)
                for (key in config.getKeys(false)) {
                    if (!config.isConfigurationSection(key)) continue
                    val section = config.getConfigurationSection(key)!!

                    val value = section.getString("code") ?: continue
                    val permission = section.getString("permission")
                    val permMsg = section.getString("permission-message")
                    val aliases = section.getStringList("aliases")
                    val tabList = section.getStringList("tab")

                    val label = "${file.nameWithoutExtension}/$key"
                    val handler = ActionHandler.parse(value, label) { code ->
                        nova.compileToBytecode(code, "${file.nameWithoutExtension}_$key.nova")
                    }

                    val cmd = ConfigCommand(key, handler, permission, permMsg, tabList)
                    if (aliases.isNotEmpty()) cmd.aliases = aliases

                    ScriptApi.commandMap.register("nova", cmd)
                    registeredNames.add(key)
                    ctx.registeredCommands.add(key)
                    loaded++
                }
            } catch (e: Exception) {
                warning("[NovaScript] 命令配置 '${file.name}' 加载失败: ${e.message}")
            }
        }

        if (loaded > 0) {
            context = ctx
            info("[NovaScript] 已加载 $loaded 个配置式命令")
        }
    }

    private class ConfigCommand(
        name: String,
        private val handler: ActionHandler,
        perm: String?,
        private val permMsg: String?,
        private val tabList: List<String>
    ) : Command(name) {

        init {
            if (perm != null) permission = perm
        }

        override fun execute(sender: CommandSender, label: String, args: Array<String>): Boolean {
            if (permission != null && !sender.hasPermission(permission!!)) {
                sender.sendMessage(permMsg ?: "§c你没有权限使用此命令")
                return true
            }
            handler.execute("player", sender, "sender", sender, "args", args.toList())
            return true
        }

        override fun tabComplete(sender: CommandSender, alias: String, args: Array<String>): List<String> {
            if (tabList.isEmpty()) return emptyList()
            if (args.size != 1) return emptyList()
            val input = args[0].lowercase()
            return tabList.filter { it.lowercase().startsWith(input) }
        }
    }

    fun unloadAll() {
        context?.unload()
        context = null
        registeredNames.clear()
    }

    fun getCount(): Int = registeredNames.size

    private val DEFAULT_CONFIG = """
# NovaScript 配置式命令
# 无需编写 .nova 脚本，直接在配置中定义命令
#
# 格式:
#   命令名:
#     code: |                          # 内嵌代码或 "script:脚本名:函数名"
#       ...
#     permission: "权限节点"            # 可选，权限节点
#     permission-message: "&c无权限"    # 可选，无权限提示
#     aliases:                         # 可选，别名列表
#       - "别名1"
#     tab:                             # 可选，第一个参数的补全列表
#       - "子命令1"
#       - "子命令2"
#
# 可用变量: player/sender（命令执行者）, args（参数列表）

# spawn:
#   code: |
#     player.tp(Bukkit.getWorld("world").getSpawnLocation())
#     player.msg("&a已传送到出生点!")
#   permission: "server.spawn"
#   permission-message: "&c你没有传送权限"
#   aliases:
#     - "s"

# fly:
#   code: "script:utils:toggleFly"
#   permission: "server.fly"
""".trimIndent()
}

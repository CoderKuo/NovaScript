package com.dakuo.novascript.config

import com.novalang.runtime.CompiledNova
import org.bukkit.Bukkit
import org.bukkit.event.Event
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.function.info
import taboolib.common.platform.function.registerBukkitListener
import taboolib.common.platform.function.warning
import taboolib.module.configuration.Configuration
import java.io.File
import com.dakuo.novascript.script.ScriptManager
import com.dakuo.novascript.script.ScriptContext
import com.dakuo.novascript.script.ScriptApi

/**
 * 配置式事件监听管理器。
 *
 * 读取 events.yml，每个事件处理器用 compileToBytecode 预编译，
 * 事件触发时通过 compiled.run("event", event) 传入事件对象执行。
 * 所有脚本 API（broadcast/sendTitle/player.msg() 等）均可使用。
 *
 * 配置格式:
 * ```yaml
 * 玩家加入:
 *   event: PlayerJoinEvent
 *   code: |
 *     val player = event.getPlayer()
 *     broadcast("&a+ &e" + player.getName() + " &7加入了服务器")
 *
 * 玩家死亡:
 *   event: PlayerDeathEvent
 *   priority: HIGH
 *   code: |
 *     val killer = event.getEntity().getKiller()
 *     if (killer != null) {
 *       killer.msg("&a击杀 +1")
 *     }
 * ```
 */
object EventManager {

    private lateinit var configFile: File
    private var context: ScriptContext? = null

    fun init(dataFolder: File) {
        configFile = File(dataFolder, "events.yml")
        if (!configFile.exists()) {
            configFile.parentFile.mkdirs()
            configFile.writeText(DEFAULT_CONFIG)
        }
        reload()
    }

    fun reload() {
        // 卸载旧的监听器
        context?.unload()
        context = null

        if (!configFile.exists()) return

        val config = Configuration.loadFromFile(configFile)
        val keys = config.getKeys(false).filter { config.isConfigurationSection(it) }
        if (keys.isEmpty()) return

        val (nova, ctx) = ScriptManager.createEngine("_events", File(configFile.parentFile, "events"), configFile)

        var loaded = 0
        for (key in keys) {
            val section = config.getConfigurationSection(key)!!
            val eventName = section.getString("event") ?: run {
                warning("[NovaScript] 事件 '$key' 缺少 event 字段")
                continue
            }
            val code = section.getString("code") ?: run {
                warning("[NovaScript] 事件 '$key' 缺少 code 字段")
                continue
            }
            val priorityName = section.getString("priority", "NORMAL")!!

            try {
                val eventClass = ScriptApi.resolveEventClass(eventName)
                val priority = try {
                    EventPriority.valueOf(priorityName.uppercase())
                } catch (_: Exception) {
                    EventPriority.NORMAL
                }

                val compiled = nova.compileToBytecode(code.trim(), "ev_$key.nova")

                @Suppress("UNCHECKED_CAST")
                val listener = registerBukkitListener(
                    eventClass as Class<Event>,
                    priority,
                    false
                ) { event ->
                    try {
                        compiled.run("event", event)
                    } catch (e: Exception) {
                        warning("[NovaScript] 配置事件 '$key' 执行错误: ${e.message}")
                    }
                }

                ctx.registeredListeners.add(listener)
                loaded++
            } catch (e: Exception) {
                warning("[NovaScript] 配置事件 '$key' 加载失败: ${e.message}")
            }
        }

        context = ctx

        if (loaded > 0) {
            info("[NovaScript] 已加载 $loaded 个配置式事件监听")
        }
    }

    fun unloadAll() {
        context?.unload()
        context = null
    }

    fun getCount(): Int = context?.registeredListeners?.size ?: 0

    private val DEFAULT_CONFIG = """
# NovaScript 配置式事件监听
# 使用预编译字节码执行，支持全部脚本 API
#
# 格式:
#   名称:                    # 自定义名称（仅用于标识）
#     event: 事件类名         # Bukkit 事件（短名或全限定名）
#     priority: NORMAL       # 可选，优先级: LOWEST/LOW/NORMAL/HIGH/HIGHEST/MONITOR
#     code: |                # NovaLang 代码
#       ...
#
# 可用变量:
#   event         - 事件对象
#   Bukkit/server - 服务器实例
#   所有脚本 API  - broadcast/sendTitle/buildItem/getPlayer 等
#   扩展函数      - player.msg()/player.给予() 等
#
# 示例:

玩家加入欢迎:
  event: PlayerJoinEvent
  code: |
    val player = event.getPlayer()
    val online = server.getOnlinePlayers().size()
    broadcast("&a+ &e" + player.getName() + " &7加入了服务器 &8(&f" + online + "&8)")
    sendTitle(player, "&6欢迎回来", "&e祝你游戏愉快")

玩家退出提示:
  event: PlayerQuitEvent
  code: |
    val name = event.getPlayer().getName()
    val online = server.getOnlinePlayers().size() - 1
    broadcast("&c- &e" + name + " &7离开了服务器 &8(&f" + online + "&8)")
""".trimIndent()
}

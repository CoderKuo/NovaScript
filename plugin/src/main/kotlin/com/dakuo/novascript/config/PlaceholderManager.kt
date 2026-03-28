package com.dakuo.novascript.config

import com.novalang.runtime.CompiledNova
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning
import taboolib.module.configuration.Configuration
import java.io.File
import com.dakuo.novascript.script.ScriptManager
import com.dakuo.novascript.bridge.NovaScriptExpansion
import java.util.concurrent.ConcurrentHashMap

/**
 * 配置式 PAPI 占位符管理器。
 *
 * 支持两种配置格式：
 *
 * 简写（无缓存）:
 * ```yaml
 * player-health: "player.getHealth().toInt()"
 * ```
 *
 * 完整（带缓存，单位毫秒）:
 * ```yaml
 * server-online:
 *   code: "Bukkit.getOnlinePlayers().size()"
 *   cache: 1000
 * ```
 */
object PlaceholderManager {

    private class CompiledPlaceholder(
        val name: String,
        val compiled: CompiledNova,
        val hasPlayer: Boolean,
        val cacheTtl: Long
    ) {
        // hasPlayer 时按玩家名缓存，否则全局单值缓存
        private var globalCache: String? = null
        private var globalExpiry: Long = 0
        private val playerCache = if (hasPlayer) ConcurrentHashMap<String, Pair<String, Long>>() else null

        fun evaluate(player: Player?): String {
            val now = System.currentTimeMillis()

            // 有缓存且未过期 → 直接返回
            if (cacheTtl > 0) {
                if (hasPlayer && player != null) {
                    playerCache?.get(player.name)?.let { (value, expiry) ->
                        if (now < expiry) return value
                    }
                } else if (!hasPlayer && globalCache != null && now < globalExpiry) {
                    return globalCache!!
                }
            }

            // 执行
            val result = try {
                val raw = if (hasPlayer && player != null) {
                    compiled.run("player", player)
                } else {
                    compiled.run()
                }
                raw?.toString() ?: ""
            } catch (e: Exception) {
                warning("[NovaScript] 占位符 '$name' 执行错误: ${e.message}")
                ""
            }

            // 写入缓存
            if (cacheTtl > 0) {
                val expiry = now + cacheTtl
                if (hasPlayer && player != null) {
                    playerCache?.put(player.name, Pair(result, expiry))
                } else if (!hasPlayer) {
                    globalCache = result
                    globalExpiry = expiry
                }
            }

            return result
        }
    }

    private val placeholders = mutableMapOf<String, CompiledPlaceholder>()
    private lateinit var configFile: File

    fun init(dataFolder: File) {
        configFile = File(dataFolder, "placeholders.yml")
        if (!configFile.exists()) {
            configFile.parentFile.mkdirs()
            configFile.writeText(DEFAULT_CONFIG)
        }
        reload()
    }

    fun reload() {
        for (id in placeholders.keys) {
            NovaScriptExpansion.unregisterConfig(id)
        }
        placeholders.clear()

        if (!configFile.exists()) return
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return

        val config = Configuration.loadFromFile(configFile)
        val keys = config.getKeys(false)
        if (keys.isEmpty()) return

        var loaded = 0
        for (key in keys) {
            try {
                // 解析两种配置格式
                val code: String
                val cacheTtl: Long

                if (config.isConfigurationSection(key)) {
                    // 完整格式: { code: "...", cache: 1000 }
                    val section = config.getConfigurationSection(key)!!
                    code = section.getString("code") ?: continue
                    cacheTtl = section.getLong("cache", 0)
                } else {
                    // 简写格式: "expression"
                    code = config.getString(key) ?: continue
                    cacheTtl = 0
                }

                val cp = compile(key, code.trim(), cacheTtl)
                placeholders[key] = cp
                NovaScriptExpansion.registerConfig(key) { player -> cp.evaluate(player) }
                loaded++
            } catch (e: Exception) {
                warning("[NovaScript] 占位符 '$key' 编译失败: ${e.message}")
            }
        }

        if (loaded > 0) {
            info("[NovaScript] 已加载 $loaded 个配置式占位符")
        }
    }

    private fun compile(name: String, code: String, cacheTtl: Long): CompiledPlaceholder {
        val (nova, _) = ScriptManager.createEngine("_ph_$name", File(configFile.parentFile, "placeholders"))

        val compiled = nova.compileToBytecode(code, "ph_$name.nova")
        val hasPlayer = "player" in code

        return CompiledPlaceholder(name, compiled, hasPlayer, cacheTtl)
    }

    fun unloadAll() {
        NovaScriptExpansion.unregisterAllConfig()
        placeholders.clear()
    }

    fun getNames(): List<String> = placeholders.keys.sorted()

    private val DEFAULT_CONFIG = """
# NovaScript 配置式占位符
# 使用: %novascript_占位符名%
#
# 可用变量:
#   player        - 请求占位符的玩家
#   Bukkit/server - Bukkit 服务器实例
#
# ═══ 两种格式 ═══
#
# 简写（无缓存，每次请求都执行）:
#   占位符名: "表达式"
#
# 完整（带缓存，适合高频调用如记分板）:
#   占位符名:
#     code: "表达式"
#     cache: 1000    # 缓存毫秒数
#
# ═══ 示例 ═══

# 在线人数（缓存 1 秒）— %novascript_server-online%
server-online:
  code: "Bukkit.getOnlinePlayers().size()"
  cache: 1000

# 玩家生命（无缓存）— %novascript_player-health%
player-health: "player.getHealth().toInt()"

# 玩家坐标 — %novascript_player-pos%
player-pos: |
  val loc = player.getLocation()
  loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()
""".trimIndent()
}

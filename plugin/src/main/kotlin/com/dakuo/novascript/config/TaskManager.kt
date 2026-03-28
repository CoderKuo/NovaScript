package com.dakuo.novascript.config

import com.novalang.runtime.CompiledNova
import org.bukkit.Bukkit
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning
import taboolib.module.configuration.Configuration
import java.io.File
import com.dakuo.novascript.script.ScriptManager
import com.dakuo.novascript.script.ScriptContext

/**
 * 配置式定时任务管理器。
 *
 * 读取 tasks.yml，每个任务用 compileToBytecode 预编译，
 * 按配置的 delay/period 周期执行。
 *
 * 配置格式:
 * ```yaml
 * 自动公告:
 *   period: 6000       # 执行间隔（tick，20tick=1秒）
 *   delay: 100         # 首次延迟（tick，可选，默认=period）
 *   async: false       # 是否异步（可选，默认 false）
 *   code: |
 *     broadcast("&6[公告] &f欢迎来到服务器!")
 *
 * 自动保存:
 *   period: 12000
 *   async: true
 *   code: |
 *     log("正在自动保存...")
 *     Bukkit.getWorlds().forEach { it.save() }
 * ```
 */
object TaskManager {

    private lateinit var configFile: File
    private var context: ScriptContext? = null

    fun init(dataFolder: File) {
        configFile = File(dataFolder, "tasks.yml")
        if (!configFile.exists()) {
            configFile.parentFile.mkdirs()
            configFile.writeText(DEFAULT_CONFIG)
        }
        reload()
    }

    fun reload() {
        context?.unload()
        context = null

        if (!configFile.exists()) return

        val config = Configuration.loadFromFile(configFile)
        val keys = config.getKeys(false).filter { config.isConfigurationSection(it) }
        if (keys.isEmpty()) return

        val (nova, ctx) = ScriptManager.createEngine("_tasks", File(configFile.parentFile, "tasks"), configFile)

        var loaded = 0
        for (key in keys) {
            val section = config.getConfigurationSection(key)!!
            val code = section.getString("code") ?: run {
                warning("[NovaScript] 定时任务 '$key' 缺少 code 字段")
                continue
            }
            val period = section.getLong("period", 1200)
            val delay = section.getLong("delay", period)
            val async = section.getBoolean("async", false)

            try {
                val compiled = nova.compileToBytecode(code.trim(), "task_$key.nova")

                val task = if (async) {
                    Bukkit.getScheduler().runTaskTimerAsynchronously(
                        taboolib.platform.BukkitPlugin.getInstance(),
                        Runnable {
                            if (ctx.unloaded) return@Runnable
                            try { compiled.run() }
                            catch (e: Exception) { warning("[NovaScript] 定时任务 '$key' 执行错误: ${e.message}") }
                        },
                        delay, period
                    )
                } else {
                    Bukkit.getScheduler().runTaskTimer(
                        taboolib.platform.BukkitPlugin.getInstance(),
                        Runnable {
                            if (ctx.unloaded) return@Runnable
                            try { compiled.run() }
                            catch (e: Exception) { warning("[NovaScript] 定时任务 '$key' 执行错误: ${e.message}") }
                        },
                        delay, period
                    )
                }

                // 用 Cancellable 包装 BukkitTask 以便 unload 时取消
                ctx.scheduledTasks.add(object : com.novalang.runtime.NovaScheduler.Cancellable {
                    override fun cancel() { task.cancel() }
                    override fun isCancelled() = task.isCancelled
                })

                loaded++
            } catch (e: Exception) {
                warning("[NovaScript] 定时任务 '$key' 加载失败: ${e.message}")
            }
        }

        context = ctx

        if (loaded > 0) {
            info("[NovaScript] 已加载 $loaded 个配置式定时任务")
        }
    }

    fun unloadAll() {
        context?.unload()
        context = null
    }

    fun getCount(): Int = context?.scheduledTasks?.size ?: 0

    private val DEFAULT_CONFIG = """
# NovaScript 配置式定时任务
# 支持全部脚本 API，预编译字节码执行
#
# 格式:
#   名称:
#     period: 1200      # 执行间隔（tick，20tick = 1秒）
#     delay: 100        # 首次延迟（tick，可选，默认等于 period）
#     async: false      # 是否异步执行（可选，默认 false）
#     code: |           # NovaLang 代码
#       ...
#
# 示例:

# 每 5 分钟自动公告
#自动公告:
#  period: 6000
#  code: |
#    broadcast("&6[公告] &f欢迎来到服务器! 输入 /help 查看帮助")

# 每 30 秒显示在线人数
#在线提示:
#  period: 600
#  delay: 200
#  code: |
#    val count = Bukkit.getOnlinePlayers().size()
#    if (count > 0) {
#      broadcast("&7当前在线: &f" + count + " &7人")
#    }
""".trimIndent()
}

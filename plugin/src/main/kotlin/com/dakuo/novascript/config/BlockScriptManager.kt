package com.dakuo.novascript.config

import com.dakuo.novascript.script.ScriptContext
import com.dakuo.novascript.script.ScriptManager
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.function.info
import taboolib.common.platform.function.registerBukkitListener
import taboolib.common.platform.function.warning
import taboolib.module.configuration.Configuration
import java.io.File

/**
 * 配置式方块脚本管理器。
 *
 * 读取 blocks/ 目录下的 yml 文件，每个文件定义若干个方块触发点。
 * 支持单坐标和多坐标绑定，支持内嵌代码和脚本引用。
 *
 * 配置格式:
 * ```yaml
 * # 单坐标
 * 传送门:
 *   location: "world:100,64,200"
 *   action: right-click
 *   code: "player.msg('hello')"
 *
 * # 多坐标
 * 传送网络:
 *   locations:
 *     - "world:100,64,200"
 *     - "world:200,64,300"
 *     - "world:300,64,400"
 *   action: step
 *   code: "script:teleport:onStep"
 * ```
 */
object BlockScriptManager {

    data class BlockEntry(
        val name: String,
        val fileName: String,
        val locations: List<String>,   // "world:x,y,z" 原始格式
        val actionType: String,
        val handler: ActionHandler,
        val permission: String? = null,
        val permissionMessage: String? = null
    )

    // key = "world:x:y:z" → 绑定的 entries
    private val locationIndex = mutableMapOf<String, MutableList<BlockEntry>>()
    // 所有 entries（用于查询/列表）
    private val allEntries = mutableListOf<BlockEntry>()
    private var context: ScriptContext? = null
    private lateinit var blocksDir: File

    fun init(dataFolder: File) {
        blocksDir = File(dataFolder, "blocks")
        if (!blocksDir.exists()) {
            blocksDir.mkdirs()
            File(blocksDir, "example.yml").writeText(DEFAULT_CONFIG)
        }
        reload()
    }

    fun reload() {
        context?.unload()
        context = null
        locationIndex.clear()
        allEntries.clear()

        val files = blocksDir.listFiles { f -> f.isFile && f.extension == "yml" } ?: return
        if (files.isEmpty()) return

        val (nova, ctx) = ScriptManager.createEngine("_blocks", File(blocksDir, "_data"), blocksDir)

        var loaded = 0
        for (file in files.sortedBy { it.name }) {
            try {
                val config = Configuration.loadFromFile(file)
                for (key in config.getKeys(false)) {
                    if (!config.isConfigurationSection(key)) continue
                    val section = config.getConfigurationSection(key)!!

                    val value = section.getString("code") ?: continue
                    val actionType = section.getString("action", "right-click")!!.lowercase()

                    // 支持 location（单个）和 locations（列表）
                    val rawLocations = mutableListOf<String>()
                    section.getString("location")?.let { rawLocations.add(it) }
                    section.getStringList("locations").let { rawLocations.addAll(it) }
                    if (rawLocations.isEmpty()) {
                        warning("[NovaScript] 方块脚本 '${file.name}/$key' 缺少 location/locations")
                        continue
                    }

                    val permission = section.getString("permission")
                    val permMsg = section.getString("permission-message")

                    val label = "${file.nameWithoutExtension}/$key"
                    val handler = ActionHandler.parse(value, label) { code ->
                        nova.compileToBytecode(code, "${file.nameWithoutExtension}_$key.nova")
                    }

                    val entry = BlockEntry(key, file.nameWithoutExtension, rawLocations, actionType, handler, permission, permMsg)
                    allEntries.add(entry)

                    // 索引每个坐标
                    for (locStr in rawLocations) {
                        val parsed = parseLocation(locStr)
                        if (parsed == null) {
                            warning("[NovaScript] 方块脚本 '${file.name}/$key' 坐标格式错误: $locStr")
                            continue
                        }
                        val locKey = "${parsed.world}:${parsed.x}:${parsed.y}:${parsed.z}"
                        locationIndex.getOrPut(locKey) { mutableListOf() }.add(entry)
                    }
                    loaded++
                }
            } catch (e: Exception) {
                warning("[NovaScript] 方块脚本 '${file.name}' 加载失败: ${e.message}")
            }
        }

        if (loaded > 0) {
            @Suppress("UNCHECKED_CAST")
            val listener = registerBukkitListener(
                PlayerInteractEvent::class.java as Class<Event>,
                EventPriority.NORMAL, false
            ) { event ->
                handleInteract(event as PlayerInteractEvent)
            }
            ctx.registeredListeners.add(listener)

            val hasStep = allEntries.any { it.actionType in listOf("step", "walk", "all") }
            if (hasStep) {
                @Suppress("UNCHECKED_CAST")
                val moveListener = registerBukkitListener(
                    PlayerMoveEvent::class.java as Class<Event>,
                    EventPriority.NORMAL, false
                ) { event ->
                    handleMove(event as PlayerMoveEvent)
                }
                ctx.registeredListeners.add(moveListener)
            }

            context = ctx
            info("[NovaScript] 已加载 $loaded 个方块脚本 (${locationIndex.size} 个坐标点)")
        }
    }

    private fun acceptsRight(type: String) = type in listOf("right-click", "both", "right", "all")
    private fun acceptsLeft(type: String) = type in listOf("left-click", "both", "left", "all")
    private fun acceptsStep(type: String) = type in listOf("step", "walk", "all")

    private fun handleInteract(event: PlayerInteractEvent) {
        val block = event.clickedBlock ?: return
        val loc = block.location
        val locKey = "${loc.world?.name}:${loc.blockX}:${loc.blockY}:${loc.blockZ}"

        val entries = locationIndex[locKey] ?: return

        val isRight = event.action == Action.RIGHT_CLICK_BLOCK
        val isLeft = event.action == Action.LEFT_CLICK_BLOCK
        if (!isRight && !isLeft) return

        for (entry in entries) {
            if ((isRight && acceptsRight(entry.actionType)) || (isLeft && acceptsLeft(entry.actionType))) {
                if (entry.permission != null && !event.player.hasPermission(entry.permission)) {
                    entry.permissionMessage?.let { event.player.sendMessage(it.replace("&", "§")) }
                    continue
                }
                entry.handler.execute("player", event.player, "block", block, "event", event)
            }
        }
    }

    private fun handleMove(event: PlayerMoveEvent) {
        val from = event.from
        val to = event.to ?: return
        if (from.blockX == to.blockX && from.blockY == to.blockY && from.blockZ == to.blockZ) return

        val footBlock = to.clone().subtract(0.0, 1.0, 0.0)
        val locKey = "${to.world?.name}:${footBlock.blockX}:${footBlock.blockY}:${footBlock.blockZ}"
        val entries = locationIndex[locKey] ?: return

        for (entry in entries) {
            if (acceptsStep(entry.actionType)) {
                if (entry.permission != null && !event.player.hasPermission(entry.permission)) {
                    entry.permissionMessage?.let { event.player.sendMessage(it.replace("&", "§")) }
                    continue
                }
                val block = to.world?.getBlockAt(footBlock.blockX, footBlock.blockY, footBlock.blockZ) ?: continue
                entry.handler.execute("player", event.player, "block", block, "event", event)
            }
        }
    }

    private fun parseLocation(str: String): LocationData? {
        return try {
            val parts = str.trim().split(":")
            if (parts.size != 2) return null
            val world = parts[0]
            val coords = parts[1].split(",")
            if (coords.size != 3) return null
            LocationData(world, coords[0].trim().toInt(), coords[1].trim().toInt(), coords[2].trim().toInt())
        } catch (_: Exception) {
            null
        }
    }

    private data class LocationData(val world: String, val x: Int, val y: Int, val z: Int)

    // ── 查询 API ─────────────────────────────

    fun unloadAll() {
        context?.unload()
        context = null
        locationIndex.clear()
        allEntries.clear()
    }

    fun getCount(): Int = allEntries.size

    fun getLocationCount(): Int = locationIndex.size

    /** 获取所有方块脚本条目（用于列表展示） */
    fun getEntries(): List<BlockEntry> = allEntries.toList()

    /** 获取指定坐标上绑定的脚本 */
    fun getEntriesAt(world: String, x: Int, y: Int, z: Int): List<BlockEntry> {
        return locationIndex["$world:$x:$y:$z"] ?: emptyList()
    }

    /** 根据名称查找脚本（支持模糊搜索） */
    fun findEntries(keyword: String): List<BlockEntry> {
        val lower = keyword.lowercase()
        return allEntries.filter { it.name.lowercase().contains(lower) || it.fileName.lowercase().contains(lower) }
    }

    /** 获取所有可绑定的脚本名称（文件名/条目名） */
    fun getBindableNames(): List<String> = allEntries.map { "${it.fileName}:${it.name}" }

    // ── 坐标绑定/解绑 API ────────────────────

    /**
     * 将坐标绑定到指定脚本条目，写入配置文件并热重载。
     * @param fileName 配置文件名（不含 .yml）
     * @param entryName 条目名
     * @param locStr 坐标格式 "world:x,y,z"
     * @return 是否成功
     */
    fun bindLocation(fileName: String, entryName: String, locStr: String): Boolean {
        val file = File(blocksDir, "$fileName.yml")
        if (!file.exists()) return false

        val config = Configuration.loadFromFile(file)
        if (!config.isConfigurationSection(entryName)) return false

        val section = config.getConfigurationSection(entryName)!!

        // 获取现有坐标列表
        val existing = mutableListOf<String>()
        section.getString("location")?.let { existing.add(it) }
        existing.addAll(section.getStringList("locations"))

        // 避免重复
        if (locStr in existing) return false

        existing.add(locStr)

        // 写回配置：统一用 locations 列表
        section.set("location", null)
        section.set("locations", existing)
        config.saveToFile(file)

        reload()
        return true
    }

    /**
     * 从指定脚本条目中移除坐标绑定，写入配置文件并热重载。
     */
    fun unbindLocation(fileName: String, entryName: String, locStr: String): Boolean {
        val file = File(blocksDir, "$fileName.yml")
        if (!file.exists()) return false

        val config = Configuration.loadFromFile(file)
        if (!config.isConfigurationSection(entryName)) return false

        val section = config.getConfigurationSection(entryName)!!

        val existing = mutableListOf<String>()
        section.getString("location")?.let { existing.add(it) }
        existing.addAll(section.getStringList("locations"))

        if (!existing.remove(locStr)) return false

        section.set("location", null)
        section.set("locations", existing)
        config.saveToFile(file)

        reload()
        return true
    }

    private val DEFAULT_CONFIG = """
# NovaScript 方块脚本
# 当玩家在指定坐标交互方块时触发
#
# 格式:
#   名称:
#     location: "世界名:x,y,z"         # 单坐标
#     # 或多坐标:
#     locations:
#       - "world:100,64,200"
#       - "world:200,64,300"
#     action: right-click               # right-click / left-click / both / step / all
#     code: |                           # 内嵌代码
#       ...
#     # 或引用脚本函数:
#     code: "script:脚本名:函数名"
#
# 可用变量: player, block, event

# 传送网络（多坐标绑定同一脚本）:
#传送网络:
#  locations:
#    - "world:100,64,200"
#    - "world:200,64,300"
#    - "world:300,64,400"
#  action: step
#  code: |
#    player.msg("&a传送中...")
#    player.tp(Bukkit.getWorld("world").getSpawnLocation())

# 单坐标:
#公告牌:
#  location: "world:50,70,50"
#  action: right-click
#  code: |
#    player.msg("&e欢迎来到大厅!")
""".trimIndent()
}

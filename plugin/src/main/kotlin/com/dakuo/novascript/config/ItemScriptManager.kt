package com.dakuo.novascript.config

import com.dakuo.novascript.script.ScriptContext
import com.dakuo.novascript.script.ScriptManager
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.function.info
import taboolib.common.platform.function.registerBukkitListener
import taboolib.common.platform.function.warning
import taboolib.module.configuration.Configuration
import java.io.File

/**
 * 配置式物品脚本管理器。
 *
 * 读取 items/ 目录下的 yml 文件，每个文件定义一组物品触发规则。
 * 支持内嵌代码和引用 scripts/ 中的脚本函数。
 *
 * 配置格式（items/magic_sword.yml）:
 * ```yaml
 * match: "name:&b魔法剑,material:DIAMOND_SWORD"
 *
 * # 内嵌代码
 * right-click: |
 *   player.msg("&b释放魔法!")
 *
 * # 调用 scripts/ 中的脚本函数（格式: script:脚本名:函数名）
 * left-click: "script:rpg:onSwordClick"
 *
 * swap: |
 *   player.msg("&e切换模式")
 * ```
 */
object ItemScriptManager {

    private data class ItemAction(
        val match: String,
        val handler: ActionHandler,
        val permission: String?,
        val permissionMessage: String?
    )

    private val rightClickActions = mutableListOf<ItemAction>()
    private val leftClickActions = mutableListOf<ItemAction>()
    private val swapActions = mutableListOf<ItemAction>()

    private var context: ScriptContext? = null
    private lateinit var itemsDir: File
    private val matcher = com.dakuo.rulib.common.item.matcher.ItemTextMatcher()

    fun init(dataFolder: File) {
        itemsDir = File(dataFolder, "items")
        if (!itemsDir.exists()) {
            itemsDir.mkdirs()
            File(itemsDir, "example.yml").writeText(DEFAULT_CONFIG)
        }
        reload()
    }

    fun reload() {
        context?.unload()
        context = null
        rightClickActions.clear()
        leftClickActions.clear()
        swapActions.clear()

        val files = itemsDir.listFiles { f -> f.isFile && f.extension == "yml" } ?: return
        if (files.isEmpty()) return

        val (nova, ctx) = ScriptManager.createEngine("_items", File(itemsDir, "_data"), itemsDir)

        var loaded = 0
        for (file in files.sortedBy { it.name }) {
            try {
                val config = Configuration.loadFromFile(file)
                val matchExpr = config.getString("match") ?: continue
                val baseName = file.nameWithoutExtension
                val permission = config.getString("permission")
                val permMsg = config.getString("permission-message")

                for ((key, actionList) in mapOf(
                    "right-click" to rightClickActions,
                    "left-click" to leftClickActions,
                    "swap" to swapActions
                )) {
                    val value = config.getString(key) ?: continue
                    val label = "${baseName}/$key"
                    val handler = ActionHandler.parse(value, label) { code ->
                        nova.compileToBytecode(code, "${baseName}_$key.nova")
                    }
                    actionList.add(ItemAction(matchExpr, handler, permission, permMsg))
                    loaded++
                }
            } catch (e: Exception) {
                warning("[NovaScript] 物品脚本 '${file.name}' 加载失败: ${e.message}")
            }
        }

        if (loaded > 0) {
            @Suppress("UNCHECKED_CAST")
            val interactListener = registerBukkitListener(
                PlayerInteractEvent::class.java as Class<Event>,
                EventPriority.NORMAL, false
            ) { event ->
                handleInteract(event as PlayerInteractEvent)
            }
            ctx.registeredListeners.add(interactListener)

            @Suppress("UNCHECKED_CAST")
            val swapListener = registerBukkitListener(
                PlayerSwapHandItemsEvent::class.java as Class<Event>,
                EventPriority.NORMAL, false
            ) { event ->
                handleSwap(event as PlayerSwapHandItemsEvent)
            }
            ctx.registeredListeners.add(swapListener)

            context = ctx
            info("[NovaScript] 已加载 $loaded 个物品脚本动作")
        }
    }

    private fun handleInteract(event: PlayerInteractEvent) {
        val item = event.item ?: return
        val actions = when (event.action) {
            Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK -> rightClickActions
            Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK -> leftClickActions
            else -> return
        }
        executeMatching(actions, item, event.player, event)
    }

    private fun handleSwap(event: PlayerSwapHandItemsEvent) {
        val item = event.mainHandItem ?: return
        executeMatching(swapActions, item, event.player, event)
    }

    private fun executeMatching(actions: List<ItemAction>, item: ItemStack, player: Player, event: Event) {
        for (action in actions) {
            if (matcher.matches(item, action.match)) {
                if (action.permission != null && !player.hasPermission(action.permission)) {
                    action.permissionMessage?.let { player.sendMessage(it.replace("&", "§")) }
                    continue
                }
                action.handler.execute("player", player, "item", item, "event", event)
            }
        }
    }

    fun unloadAll() {
        context?.unload()
        context = null
        rightClickActions.clear()
        leftClickActions.clear()
        swapActions.clear()
    }

    fun getCount(): Int = rightClickActions.size + leftClickActions.size + swapActions.size

    private val DEFAULT_CONFIG = """
# NovaScript 物品脚本
# 当玩家手持匹配物品执行指定动作时触发
#
# match: 物品匹配表达式（name/lore/material/nbt 组合）
# 动作: right-click / left-click / swap（F键）
#
# 两种写法:
#   内嵌代码:  right-click: "player.msg('hello')"
#   脚本引用:  right-click: "script:脚本名:函数名"
#
# 可选权限:
#   permission: "物品.使用"               # 权限节点
#   permission-message: "&c你没有权限"     # 无权限提示（不设置则静默忽略）
#
# 内嵌代码可用变量: player, item, event
# 脚本引用会传入参数: function(player, item, event)

# match: "name:&b&l魔法之剑,material:DIAMOND_SWORD"
#
# # 内嵌代码
# right-click: |
#   player.msg("&b释放魔法!")
#
# # 调用 scripts/rpg.nova 中的 onSwordClick 函数
# left-click: "script:rpg:onSwordClick"
#
# swap: |
#   player.msg("&e切换模式")
""".trimIndent()
}

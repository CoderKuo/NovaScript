package com.dakuo.novascript.script

import com.novalang.runtime.AbstractNovaValue
import com.novalang.runtime.Function0
import com.novalang.runtime.Function1
import com.novalang.runtime.Function2
import com.novalang.runtime.Function3
import com.novalang.runtime.Nova
import com.novalang.runtime.NovaCallable
import com.dakuo.novascript.define
import com.dakuo.novascript.ext
import com.novalang.runtime.NovaScriptContext
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.command.Command
import org.bukkit.command.CommandMap
import org.bukkit.command.CommandSender
import org.bukkit.entity.HumanEntity
import org.bukkit.entity.Player
import org.bukkit.event.Event
import taboolib.common.platform.event.EventPriority
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.registerBukkitListener
import taboolib.common.platform.function.warning
import taboolib.expansion.dispatchCommandAsOp
import taboolib.library.xseries.XMaterial
import taboolib.module.chat.colored
import taboolib.module.configuration.Configuration
import taboolib.module.database.HostSQL
import taboolib.module.database.HostSQLite
import taboolib.module.nms.inputSign
import taboolib.module.nms.getItemTag
import taboolib.module.nms.ItemTag
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import taboolib.platform.compat.depositBalance
import taboolib.platform.compat.getBalance
import taboolib.platform.compat.replacePlaceholder
import taboolib.platform.compat.withdrawBalance
import taboolib.platform.util.ItemBuilder
import taboolib.platform.util.actionBar
import taboolib.platform.util.giveItem
import taboolib.platform.util.nextChat
import taboolib.platform.util.title
import java.io.File
import com.dakuo.novascript.bridge.ForgeEventBridge
import com.dakuo.novascript.bridge.FabricEventBridge
import com.dakuo.novascript.bridge.NovaScriptExpansion
import com.dakuo.novascript.ScriptHandler2
import com.dakuo.novascript.ScriptConfigurer
import com.dakuo.novascript.ScriptSetup

/**
 * 脚本 API 注入器。
 * 所有 API 通过 nova.defineFunction 注册，同时支持解释模式和字节码编译模式。
 */
object ScriptApi {

    // ========== 回调调用辅助 ==========

    /**
     * 统一调用脚本回调，兼容两种模式：
     * - 解释模式：callback 是 NovaCallable，通过 .call(interpreter, args) 调用
     * - 编译模式：callback 是 FunctionN，重建 NovaScriptContext 后调用
     */
    @Suppress("UNCHECKED_CAST")
    internal fun invokeCallback(callback: Any, context: ScriptContext, vararg args: Any?): Any? {
        if (callback is NovaCallable) {
            val novaArgs = args.map { AbstractNovaValue.fromJava(it) }
            return callback.call(context.nova.interpreter, novaArgs).toJavaValue()
        }
        // 字节码模式：重建 NovaScriptContext 使回调可以访问脚本绑定（函数/变量）
        // 如果已有活跃上下文（脚本加载期间），不覆盖也不清除，避免破坏外层上下文
        val needsContext = !NovaScriptContext.isActive()
        if (needsContext) {
            NovaScriptContext.init(context.bindings as Map<String, Any>)
        }
        try {
            return when (args.size) {
                0 -> (callback as Function0<Any?>).invoke()
                1 -> (callback as Function1<Any?, Any?>).invoke(args[0])
                2 -> (callback as Function2<Any?, Any?, Any?>).invoke(args[0], args[1])
                3 -> (callback as Function3<Any?, Any?, Any?, Any?>).invoke(args[0], args[1], args[2])
                else -> {
                    warning("[NovaScript] 不支持的回调参数数量: ${args.size}")
                    null
                }
            }
        } finally {
            if (needsContext) {
                NovaScriptContext.clear()
            }
        }
    }

    // ========== 事件类解析 ==========

    private val EVENT_PACKAGES = listOf(
        "org.bukkit.event.player",
        "org.bukkit.event.block",
        "org.bukkit.event.entity",
        "org.bukkit.event.inventory",
        "org.bukkit.event.server",
        "org.bukkit.event.world",
        "org.bukkit.event.weather",
        "org.bukkit.event.vehicle",
        "org.bukkit.event.enchantment",
        "org.bukkit.event.hanging",
        "org.bukkit.event.raid"
    )

    internal fun resolveEventClass(name: String): Class<out Event> {
        // 包含 '.' 视为全限定名，直接加载（支持第三方插件事件）
        if ('.' in name) {
            return Class.forName(name, true, Bukkit.getServer().javaClass.classLoader)
                .asSubclass(Event::class.java)
        }
        // 短名：在 Bukkit 内置事件包中搜索
        for (pkg in EVENT_PACKAGES) {
            try {
                return Class.forName("$pkg.$name").asSubclass(Event::class.java)
            } catch (_: ClassNotFoundException) {}
        }
        throw IllegalArgumentException("找不到事件类: $name（提示：第三方插件事件请使用全限定类名）")
    }

    // ========== 命令动态注册 ==========

    internal val commandMap: CommandMap by lazy {
        try {
            Bukkit.getServer().javaClass.getMethod("getCommandMap").invoke(Bukkit.getServer()) as CommandMap
        } catch (_: Exception) {
            val field = Bukkit.getServer().javaClass.getDeclaredField("commandMap")
            field.isAccessible = true
            field.get(Bukkit.getServer()) as CommandMap
        }
    }

    private fun findField(clazz: Class<*>, name: String): java.lang.reflect.Field? {
        var cls: Class<*>? = clazz
        while (cls != null) {
            try {
                val f = cls.getDeclaredField(name)
                f.isAccessible = true
                return f
            } catch (_: NoSuchFieldException) {}
            cls = cls.superclass
        }
        return null
    }

    fun unregisterCommand(name: String) {
        try {
            val field = findField(commandMap.javaClass, "knownCommands")
                ?: throw NoSuchFieldException("knownCommands")
            @Suppress("UNCHECKED_CAST")
            val knownCommands = field.get(commandMap) as MutableMap<String, Command>
            val cmd = knownCommands.remove(name)
            knownCommands.remove("nova:$name")
            cmd?.unregister(commandMap)
        } catch (e: Exception) {
            warning("[NovaScript] 注销命令 '$name' 失败: ${e.message}")
        }
    }

    // ========== GUI 菜单辅助类 ==========

    class ScriptMenu(
        private val title: String,
        private val rows: Int,
        private val context: ScriptContext
    ) {
        private val items = mutableMapOf<Int, ItemStack>()
        private val clickHandlers = mutableMapOf<Int, Any>()

        fun set(slot: Int, item: ItemStack) {
            items[slot] = item
        }

        fun onClick(slot: Int, handler: Any) {
            clickHandlers[slot] = handler
        }

        fun open(player: Player) {
            player.openMenu<Chest>(title) {
                rows(this@ScriptMenu.rows)
                for ((slot, item) in this@ScriptMenu.items) {
                    val handler = this@ScriptMenu.clickHandlers[slot]
                    if (handler != null) {
                        set(slot, item) {
                            try {
                                invokeCallback(handler, context, clicker)
                            } catch (e: Exception) {
                                clicker.sendMessage("§c[NovaScript] 菜单错误: ${e.message}")
                            }
                        }
                    } else {
                        set(slot, item)
                    }
                }
            }
        }
    }

    // ========== API 注入 ==========

    /**
     * 将无状态的全局 API 注册到 NovaRuntime.shared()。
     * 只需在插件启用时调用一次，所有 Nova 实例自动可见。
     */
    fun injectGlobal() {
        val rt = com.novalang.runtime.NovaRuntime.shared()

        // ── 常量 ──
        rt.set("Bukkit", Bukkit.getServer(), null, null, "Bukkit 服务器实例 (Server)")
        rt.set("server", Bukkit.getServer(), null, null, "服务器实例，同 Bukkit")
        rt.set("pluginManager", Bukkit.getPluginManager(), null, null, "插件管理器 (PluginManager)")
        rt.set("consoleSender", Bukkit.getConsoleSender(), null, null, "控制台命令发送者 (ConsoleCommandSender)")
        rt.set("Material", org.bukkit.Material::class.java, null, null, "材质枚举类，如 Material.DIAMOND_SWORD")
        rt.set("Sound", org.bukkit.Sound::class.java, null, null, "音效枚举类，如 Sound.ENTITY_PLAYER_LEVELUP")
        rt.set("GameMode", org.bukkit.GameMode::class.java, null, null, "游戏模式枚举类，如 GameMode.CREATIVE")
        rt.set("ChatColor", org.bukkit.ChatColor::class.java, null, null, "聊天颜色枚举类，如 ChatColor.RED")

        // ── 工具函数 ──
        rt.register("broadcast",
            com.novalang.runtime.Function1<Any?, Any?> { msg -> Bukkit.broadcastMessage("$msg".colored()); null },
            null, null, "broadcast(msg) — 向所有在线玩家发送消息，支持颜色代码")
        rt.register("log",
            com.novalang.runtime.Function1<Any?, Any?> { msg -> taboolib.common.platform.function.info("$msg"); null },
            null, null, "log(msg) — 输出信息日志到控制台")
        rt.register("warn",
            com.novalang.runtime.Function1<Any?, Any?> { msg -> warning("$msg"); null },
            null, null, "warn(msg) — 输出警告日志到控制台")
        rt.register("colorize",
            com.novalang.runtime.Function1<Any?, Any?> { text -> "$text".colored() },
            null, null, "colorize(text) — 将 &颜色代码 转换为实际颜色，返回处理后的字符串")
        rt.register("runCommand",
            com.novalang.runtime.Function2<Any?, Any?, Any?> { sender, cmd -> Bukkit.dispatchCommand(sender as CommandSender, "$cmd") },
            null, null, "runCommand(sender, cmd) — 以指定身份执行服务器命令")
        rt.register("getPlayer",
            com.novalang.runtime.Function1<Any?, Any?> { name -> Bukkit.getPlayer("$name") },
            null, null, "getPlayer(name) — 根据名称获取在线玩家，不在线返回 null")
        rt.register("getOnlinePlayers",
            com.novalang.runtime.Function0<Any?> { Bukkit.getOnlinePlayers().toList() },
            null, null, "getOnlinePlayers() — 获取所有在线玩家列表")

        // ── 物品 JSON 还原 ──
        rt.register("itemFromJson",
            com.novalang.runtime.Function1<Any?, Any?> { json -> ItemTag.toItem("$json") },
            null, null, "itemFromJson(json) — 从 JSON 字符串还原 ItemStack")

        // ── 玩家工具函数 ──
        rt.register("sendTitle",
            com.novalang.runtime.Function3<Any?, Any?, Any?, Any?> { p, t, s ->
                (p as HumanEntity).title("$t".colored(), "$s".colored()); null },
            null, null, "sendTitle(player, title, subtitle) — 向玩家发送大标题和副标题")
        rt.register("sendActionBar",
            com.novalang.runtime.Function2<Any?, Any?, Any?> { p, msg ->
                (p as HumanEntity).actionBar("$msg".colored()); null },
            null, null, "sendActionBar(player, msg) — 向玩家发送动作栏消息")
        rt.register("giveItem",
            com.novalang.runtime.Function2<Any?, Any?, Any?> { p, item ->
                (p as HumanEntity).giveItem(item as ItemStack); null },
            null, null, "giveItem(player, item) — 给玩家添加物品到背包")

        // ── String 扩展 ──
        rt.registerExt(String::class.java, "color",
            com.novalang.runtime.Function1<Any?, Any?> { str -> (str as String).colored() })

        // ── Player 扩展 ──
        rt.registerExt(Player::class.java, "msg",
            com.novalang.runtime.Function2<Any?, Any?, Any?> { p, msg -> (p as Player).sendMessage("$msg".colored()); null })
        rt.registerExt(Player::class.java, "title",
            com.novalang.runtime.Function3<Any?, Any?, Any?, Any?> { p, t, s -> (p as HumanEntity).title("$t".colored(), "$s".colored()); null })
        rt.registerExt(Player::class.java, "actionBar",
            com.novalang.runtime.Function2<Any?, Any?, Any?> { p, msg -> (p as HumanEntity).actionBar("$msg".colored()); null })
        rt.registerExt(Player::class.java, "give",
            com.novalang.runtime.Function2<Any?, Any?, Any?> { p, item -> (p as HumanEntity).giveItem(item as ItemStack); null })
        @Suppress("DEPRECATION")
        rt.registerExt(Player::class.java, "heal",
            com.novalang.runtime.Function1<Any?, Any?> { p -> (p as Player).health = p.maxHealth })
        rt.registerExt(Player::class.java, "feed",
            com.novalang.runtime.Function1<Any?, Any?> { p -> (p as Player).foodLevel = 20 })
        val execFn = com.novalang.runtime.Function2<Any?, Any?, Any?> { p, cmd -> Bukkit.dispatchCommand(p as Player, "$cmd") }
        rt.registerExt(Player::class.java, "exec", execFn)
        rt.registerExt(Player::class.java, "cmd", execFn)
        val execOpFn = com.novalang.runtime.Function2<Any?, Any?, Any?> { p, cmd -> (p as Player).dispatchCommandAsOp("$cmd") }
        rt.registerExt(Player::class.java, "execOp", execOpFn)
        rt.registerExt(Player::class.java, "sudocmd", execOpFn)

        // ── Vault 扩展（可选） ──
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            rt.registerExt(Player::class.java, "balance",
                com.novalang.runtime.Function1<Any?, Any?> { p -> (p as OfflinePlayer).getBalance() })
            rt.registerExt(Player::class.java, "pay",
                com.novalang.runtime.Function2<Any?, Any?, Any?> { p, amount -> (p as OfflinePlayer).withdrawBalance((amount as Number).toDouble()).transactionSuccess() })
            rt.registerExt(Player::class.java, "earn",
                com.novalang.runtime.Function2<Any?, Any?, Any?> { p, amount -> (p as OfflinePlayer).depositBalance((amount as Number).toDouble()).transactionSuccess() })
        }

        // ── Player 属性缩写 ──
        rt.registerExt(Player::class.java, "loc",
            com.novalang.runtime.Function1<Any?, Any?> { p -> (p as Player).location })
        rt.registerExt(Player::class.java, "hand",
            com.novalang.runtime.Function1<Any?, Any?> { p -> (p as Player).inventory.itemInMainHand })
        @Suppress("DEPRECATION")
        rt.registerExt(Player::class.java, "hp",
            com.novalang.runtime.Function1<Any?, Any?> { p -> (p as Player).health })
        rt.registerExt(Player::class.java, "perm",
            com.novalang.runtime.Function2<Any?, Any?, Any?> { p, node -> (p as Player).hasPermission("$node") })
        rt.registerExt(Player::class.java, "inv",
            com.novalang.runtime.Function1<Any?, Any?> { p -> (p as Player).inventory })
        rt.registerExt(Player::class.java, "gm",
            com.novalang.runtime.Function1<Any?, Any?> { p -> (p as Player).gameMode })

        // ── Player 动作缩写 ──
        rt.registerExt(Player::class.java, "sound",
            com.novalang.runtime.Function2<Any?, Any?, Any?> { p, sound ->
                val player = p as Player
                when (sound) {
                    is org.bukkit.Sound -> player.playSound(player.location, sound, 1f, 1f)
                    is String -> player.playSound(player.location, org.bukkit.Sound.valueOf(sound.uppercase()), 1f, 1f)
                    else -> null
                }
            })
        rt.registerExt(Player::class.java, "kick",
            com.novalang.runtime.Function2<Any?, Any?, Any?> { p, reason -> (p as Player).kickPlayer("$reason".replace("&", "§")); null })
        rt.registerExt(Player::class.java, "fly",
            com.novalang.runtime.Function2<Any?, Any?, Any?> { p, enabled ->
                val player = p as Player; val on = enabled as Boolean
                player.allowFlight = on; player.isFlying = on; null
            })
        rt.registerExt(Player::class.java, "speed",
            com.novalang.runtime.Function2<Any?, Any?, Any?> { p, spd -> (p as Player).walkSpeed = (spd as Number).toFloat(); null })
        rt.registerExt(Player::class.java, "clearEffects",
            com.novalang.runtime.Function1<Any?, Any?> { p ->
                val player = p as Player; player.activePotionEffects.forEach { player.removePotionEffect(it.type) }; null
            })

        // ── ItemStack 扩展 ──
        @Suppress("DEPRECATION")
        rt.registerExt(ItemStack::class.java, "name",
            com.novalang.runtime.Function1<Any?, Any?> { item -> (item as ItemStack).itemMeta?.displayName })
        rt.registerExt(ItemStack::class.java, "lore",
            com.novalang.runtime.Function1<Any?, Any?> { item -> (item as ItemStack).itemMeta?.lore })
        rt.registerExt(ItemStack::class.java, "type",
            com.novalang.runtime.Function1<Any?, Any?> { item -> (item as ItemStack).type.name })
        rt.registerExt(ItemStack::class.java, "amount",
            com.novalang.runtime.Function1<Any?, Any?> { item -> (item as ItemStack).amount })

        rt.registerExt(ItemStack::class.java, "copy",
            com.novalang.runtime.Function1<Any?, Any?> { item -> (item as ItemStack).clone() })

        // ── ItemStack NBT 扩展 ──
        rt.registerExt(ItemStack::class.java, "nbt",
            com.novalang.runtime.Function1<Any?, Any?> { item -> (item as ItemStack).getItemTag() })
        rt.registerExt(ItemStack::class.java, "getNbt",
            com.novalang.runtime.Function2<Any?, Any?, Any?> { item, key ->
                (item as ItemStack).getItemTag().getDeep("$key")?.unsafeData()
            })
        rt.registerExt(ItemStack::class.java, "setNbt",
            com.novalang.runtime.Function3<Any?, Any?, Any?, Any?> { item, key, value ->
                val tag = (item as ItemStack).getItemTag()
                tag.putDeep("$key", value)
                tag.saveTo(item as ItemStack)
            })
        rt.registerExt(ItemStack::class.java, "hasNbt",
            com.novalang.runtime.Function2<Any?, Any?, Any?> { item, key ->
                (item as ItemStack).getItemTag().getDeep("$key") != null
            })
        rt.registerExt(ItemStack::class.java, "removeNbt",
            com.novalang.runtime.Function2<Any?, Any?, Any?> { item, key ->
                val tag = (item as ItemStack).getItemTag()
                tag.removeDeep("$key")
                tag.saveTo(item as ItemStack)
            })
        rt.registerExt(ItemStack::class.java, "nbtJson",
            com.novalang.runtime.Function1<Any?, Any?> { item -> (item as ItemStack).getItemTag().toJson() })
        rt.registerExt(ItemStack::class.java, "toJson",
            com.novalang.runtime.Function1<Any?, Any?> { item -> ItemTag.toJson(item as ItemStack) })

        // ── PAPI 扩展 ──
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            rt.registerExt(Player::class.java, "papi",
                com.novalang.runtime.Function2<Any?, Any?, Any?> { p, text ->
                    "$text".replacePlaceholder(p as Player)
                })
        }
    }

    /**
     * 注入依赖 ScriptContext 的每脚本 API（事件、命令、GUI、配置等）。
     * 每个脚本加载时调用。全局 API 已通过 shared() 自动可见，无需重复注入。
     */
    fun inject(nova: Nova, context: ScriptContext) {
        injectEventApi(nova, context)
        injectForgeEventApi(nova, context)
        injectFabricEventApi(nova, context)
        injectCommandApi(nova, context)
        injectGuiApi(nova, context)
        injectPlaceholderApi(nova, context)
        injectItemApi(nova, context)
        injectInputApi(nova, context)
        injectConfigApi(nova, context)
        injectDatabaseApi(nova, context)
        // 以下已通过 shared() 全局注册，但保留实例级注入以便脚本覆盖
        injectUtilApi(nova)
        injectPlayerApi(nova)
        injectFakeOpApi(nova)
        injectVaultApi(nova)
        injectConstants(nova)
        injectExtensions(nova, context)
    }

    /**
     * 事件监听 API
     *
     * 用法:
     *   on("PlayerJoinEvent") { event -> ... }
     */
    private fun injectEventApi(nova: Nova, context: ScriptContext) {
        nova.define<String, Any>("on") { eventName, callback ->
            @Suppress("UNCHECKED_CAST")
            val eventClass = resolveEventClass(eventName) as Class<Event>
            val listener = registerBukkitListener(eventClass, EventPriority.NORMAL, false) { event ->
                try { invokeCallback(callback, context, event) }
                catch (e: Exception) { warning("[NovaScript] 脚本 '${context.namespace}' 事件处理错误 ($eventName): ${e.message}") }
            }
            context.registeredListeners.add(listener); null
        }
    }

    /**
     * Forge/NeoForge 事件监听 API
     *
     * 用法（需要在 Forge/NeoForge 混合核心上运行）：
     *   forge.on("com.pixelmonmod.pixelmon.api.events.CaptureEvent$SuccessfulCapture") { event ->
     *       var pokemon = event.getPokemon()
     *       broadcast(pokemon.getSpecies().getName() + " 被捕获了!")
     *   }
     *
     *   forge.on("com.tacz.guns.api.event.common.EntityKillByGunEvent") { event ->
     *       var attacker = event.getAttacker()
     *       log(attacker.getName().getString() + " 击杀了 " + event.getKilledEntity().getName().getString())
     *   }
     *
     * 脚本卸载时监听器自动停用。
     */
    private fun injectForgeEventApi(nova: Nova, context: ScriptContext) {
        nova.defineLibrary("forge") { lib ->
            lib.defineFunction("on", Function2<Any?, Any?, Any?> { eventClassName, callback ->
                if (!ForgeEventBridge.isAvailable) {
                    warning("[NovaScript] forge.on() 需要 Forge/NeoForge 环境，当前不可用")
                    return@Function2 null
                }
                val handle = ForgeEventBridge.listen(eventClassName.toString()) { event ->
                    try { invokeCallback(callback, context, event) }
                    catch (e: Exception) {
                        warning("[NovaScript] 脚本 '${context.namespace}' Forge 事件处理错误 ($eventClassName): ${e.message}")
                    }
                }
                if (handle != null) {
                    context.forgeListenerHandles.add(handle)
                }
                null
            })
            lib.defineFunction("available", Function0<Any?> {
                ForgeEventBridge.isAvailable
            })
            lib.defineFunction("busType", Function0<Any?> {
                if (ForgeEventBridge.isAvailable) ForgeEventBridge.getBusType() else "none"
            })
            lib.defineFunction("classExists", Function1<Any?, Any?> { className ->
                ForgeEventBridge.classExists(className.toString())
            })
        }
    }

    /**
     * Fabric API 事件监听
     *
     * 用法（需要在 Fabric 服务端运行）：
     *   fabric.on("net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents",
     *             "JOIN", fun(handler, sender, server) {
     *       log("Player joined!")
     *   })
     *
     * 回调参数与 Fabric 回调接口一一对应，脚本卸载时自动停用。
     */
    private fun injectFabricEventApi(nova: Nova, context: ScriptContext) {
        nova.defineLibrary("fabric") { lib ->
            lib.defineFunction("on", Function3<Any?, Any?, Any?, Any?> { className, fieldName, callback ->
                if (!FabricEventBridge.isAvailable) {
                    warning("[NovaScript] fabric.on() 需要 Fabric API 环境，当前不可用")
                    return@Function3 null
                }
                val handle = FabricEventBridge.listen(className.toString(), fieldName.toString()) { args ->
                    invokeCallback(callback, context, *args)
                }
                if (handle != null) {
                    context.fabricListenerHandles.add(handle)
                }
                null
            })
            lib.defineFunction("available", Function0<Any?> {
                FabricEventBridge.isAvailable
            })
            lib.defineFunction("classExists", Function1<Any?, Any?> { className ->
                FabricEventBridge.classExists(className.toString())
            })
        }
    }

    /**
     * 命令注册 API
     *
     * 用法:
     *   command("hello") { sender, args -> ... }
     *   tabComplete("hello") { sender, args -> ["sub1", "sub2"] }
     *
     * 链式 API（推荐）:
     *   command("kit") { sender, args ->
     *       sender.sendMessage("Hello!")
     *   }.tab { sender, args ->
     *       if (args.size() == 1) ["warrior", "mage", "archer"]
     *       else []
     *   }
     *
     * 分离 API:
     *   command("kit") { sender, args -> ... }
     *   tabComplete("kit") { sender, args -> [...] }
     */
    private fun injectCommandApi(nova: Nova, context: ScriptContext) {
        nova.define<String, Any>("command") { name, callback ->
            val cmd = ScriptCommand(name, callback, context)
            commandMap.register("nova", cmd)
            context.registeredCommands.add(name)
            cmd  // 返回 ScriptCommand 支持 .tab { } 链式调用
        }

        nova.define<String, Any>("tabComplete") { name, callback ->
            val cmd = findCommand(name)
            if (cmd is ScriptCommand) {
                cmd.tabCallback = callback
            } else {
                warning("[NovaScript] tabComplete 失败: 命令 '$name' 未注册或非脚本命令")
            }
            null
        }
    }

    /**
     * 脚本命令，支持执行回调和链式 Tab 补全。
     *
     * .tab() 接受 ScriptHandler2（SAM 接口），NovaLang 自动将 lambda 转换：
     *   command("test") { sender, args -> ... }.tab { sender, args -> ["a", "b"] }
     */
    class ScriptCommand(
        name: String,
        private val executeCallback: Any,
        private val context: ScriptContext
    ) : Command(name) {

        var tabCallback: Any? = null
        private var permissionMessage: String? = null

        /**
         * 设置 Tab 补全回调（支持 SAM 转换）。
         */
        fun tab(handler: ScriptHandler2): ScriptCommand {
            this.tabCallback = handler
            return this
        }

        /**
         * 设置权限节点（链式调用）。
         * command("fly") { ... }.permission("server.fly")
         * command("fly") { ... }.permission("server.fly", "&c你没有权限使用此命令")
         */
        fun permission(perm: String): ScriptCommand {
            this.permission = perm
            return this
        }

        fun permission(perm: String, message: String): ScriptCommand {
            this.permission = perm
            this.permissionMessage = message
            return this
        }

        override fun execute(sender: CommandSender, label: String, args: Array<String>): Boolean {
            if (permission != null && !sender.hasPermission(permission!!)) {
                sender.sendMessage(permissionMessage ?: "§c你没有权限使用此命令")
                return true
            }
            try {
                invokeCallback(executeCallback, context, sender, args.toList())
            } catch (e: Exception) {
                sender.sendMessage("§c[NovaScript] 命令执行错误: ${e.message}")
                warning("[NovaScript] 脚本 '${context.namespace}' 命令 '$name' 执行错误: ${e.message}")
            }
            return true
        }

        override fun tabComplete(sender: CommandSender, alias: String, args: Array<String>): List<String> {
            val cb = tabCallback ?: return emptyList()
            return try {
                val result = if (cb is ScriptHandler2) {
                    cb.handle(sender, args.toList())
                } else {
                    invokeCallback(cb, context, sender, args.toList())
                }
                @Suppress("UNCHECKED_CAST")
                when (result) {
                    is List<*> -> result.filterNotNull().map { it.toString() }
                    else -> emptyList()
                }
            } catch (e: Exception) {
                warning("[NovaScript] 脚本 '${context.namespace}' 命令 '$name' 补全错误: ${e.message}")
                emptyList()
            }
        }
    }

    private fun findCommand(name: String): Command? {
        return try {
            val field = findField(commandMap.javaClass, "knownCommands") ?: return null
            @Suppress("UNCHECKED_CAST")
            val knownCommands = field.get(commandMap) as Map<String, Command>
            knownCommands[name] ?: knownCommands["nova:$name"]
        } catch (_: Exception) { null }
    }

    /**
     * GUI 菜单 API
     *
     * 用法:
     *   val menu = createMenu("标题", 3)
     *   menu.set(slot, itemStack)
     *   menu.onClick(slot, { clicker -> ... })
     *   menu.open(player)
     */
    private fun injectGuiApi(nova: Nova, context: ScriptContext) {
        nova.define<String, Number>("createMenu") { title, rows -> ScriptMenu(title, rows.toInt(), context) }
    }

    /**
     * PlaceholderAPI 集成（通过 TabooLib PlaceholderExpansion）
     *
     * 用法:
     *   placeholder("level") { player, params -> "42" }
     *
     * 访问: %novascript_level% 或 %novascript_level_参数%
     */
    private fun injectPlaceholderApi(nova: Nova, context: ScriptContext) {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return
        nova.define<String, Any>("placeholder") { identifier, callback ->
            NovaScriptExpansion.register(identifier, callback, context)
            context.registeredPlaceholders.add(identifier); null
        }
    }

    /**
     * 工具函数 API
     */
    private fun injectUtilApi(nova: Nova) {
        nova.define<String>("broadcast") { msg -> Bukkit.broadcastMessage(msg.colored()); null }
        nova.define<Any>("log") { msg -> taboolib.common.platform.function.info("$msg"); null }
        nova.define<Any>("warn") { msg -> warning("$msg"); null }
        nova.define<String>("colorize") { text -> text.colored() }
        nova.define<CommandSender, String>("runCommand") { sender, cmd -> Bukkit.dispatchCommand(sender, cmd) }
        nova.define<String>("getPlayer") { name -> Bukkit.getPlayer(name) }
        nova.define("getOnlinePlayers", fn0 = { Bukkit.getOnlinePlayers().toList() })
    }

    /**
     * 物品构建 API
     *
     * 用法:
     *   val item = buildItem("DIAMOND_SWORD") { builder ->
     *       builder.setName("&b传说之剑")
     *       builder.getLore().add("&7描述")
     *       builder.colored()
     *   }
     */
    private fun injectItemApi(nova: Nova, context: ScriptContext) {
        nova.define<String, Any>("buildItem") { material, callback ->
            val xMat = XMaterial.matchXMaterial(material)
                .orElseThrow { IllegalArgumentException("未知材质: $material") }
            val builder = ItemBuilder(xMat)
            invokeCallback(callback, context, builder)
            builder.build()
        }
    }

    /**
     * 玩家工具 API
     *
     * 用法:
     *   sendTitle(player, "&6欢迎", "&e副标题")
     *   sendActionBar(player, "&e消息")
     *   giveItem(player, item)
     */
    private fun injectPlayerApi(nova: Nova) {
        nova.define<HumanEntity, String, String>("sendTitle") { player, title, subtitle ->
            player.title(title.colored(), subtitle.colored()); null
        }
        nova.define<HumanEntity, String>("sendActionBar") { player, msg -> player.actionBar(msg.colored()); null }
        nova.define<HumanEntity, ItemStack>("giveItem") { player, item -> player.giveItem(item); null }
    }

    /**
     * 输入捕获 API
     *
     * 用法:
     *   nextChat(player) { message -> ... }
     *   inputSign(player) { lines -> ... }
     */
    private fun injectInputApi(nova: Nova, context: ScriptContext) {
        nova.define<Player, Any>("nextChat") { player, callback ->
            player.nextChat { message -> invokeCallback(callback, context, message) }; null
        }
        nova.define<Player, Any>("inputSign") { player, callback ->
            player.inputSign { lines -> invokeCallback(callback, context, lines as Any) }; null
        }
    }

    /**
     * Vault 经济 API
     *
     * 用法:
     *   val balance = getBalance(player)
     *   withdraw(player, 100.0)
     *   deposit(player, 50.0)
     */
    private fun injectVaultApi(nova: Nova) {
        nova.define<OfflinePlayer>("getBalance") { player -> player.getBalance() }
        nova.define<OfflinePlayer, Number>("withdraw") { player, amount -> player.withdrawBalance(amount.toDouble()).transactionSuccess() }
        nova.define<OfflinePlayer, Number>("deposit") { player, amount -> player.depositBalance(amount.toDouble()).transactionSuccess() }
    }

    /**
     * Fake OP API
     *
     * 用法:
     *   fakeOp(player, "some_command arg1 arg2")
     */
    private fun injectFakeOpApi(nova: Nova) {
        nova.define<Player, String>("fakeOp") { player, command -> player.dispatchCommandAsOp(command) }
    }

    /**
     * 配置文件 API
     *
     * 用法:
     *   val config = loadConfig("config.yml")
     *   val value = config.getString("key")
     *   config.set("key", "value")
     *   saveConfig(config)
     */
    private fun injectConfigApi(nova: Nova, context: ScriptContext) {
        nova.define<String>("loadConfig") { filename ->
            val file = File(context.scriptDir, filename)
            if (!file.exists()) { file.parentFile.mkdirs(); file.createNewFile() }
            Configuration.loadFromFile(file)
        }
        nova.define<Configuration>("saveConfig") { config -> config.saveToFile(); null }
    }

    /**
     * 数据库 API
     *
     * 用法:
     *   val db = connectSQLite("data.db")
     *   val db = connectMySQL("localhost", 3306, "root", "password", "dbname")
     */
    private fun injectDatabaseApi(nova: Nova, context: ScriptContext) {
        nova.define<String>("connectSQLite") { filename ->
            val file = File(context.scriptDir, filename)
            file.parentFile.mkdirs()
            val host = HostSQLite(file)
            val ds = host.createDataSource()
            context.dataSources.add(ds); ds
        }
        nova.define<String, Number, String, String, String>("connectMySQL") { host, port, user, password, database ->
            val hostSQL = HostSQL(host, port.toInt().toString(), user, password, database)
            val ds = hostSQL.createDataSource()
            context.dataSources.add(ds); ds
        }
    }

    /**
     * 常量注入
     */
    private fun injectConstants(nova: Nova) {
        nova.set("Bukkit", Bukkit.getServer())
        nova.set("server", Bukkit.getServer())
        nova.set("pluginManager", Bukkit.getPluginManager())
        nova.set("consoleSender", Bukkit.getConsoleSender())

        // Java 类注入（通过 NovaDynamic 支持静态字段/方法访问）
        nova.set("Material", org.bukkit.Material::class.java)
        nova.set("Sound", org.bukkit.Sound::class.java)
        nova.set("GameMode", org.bukkit.GameMode::class.java)
        nova.set("ChatColor", org.bukkit.ChatColor::class.java)
    }

    /**
     * 扩展函数注入
     *
     * 让脚本可以直接在对象上调用方法：
     *   "&6Hello".color()
     *   player.msg("&a你好")
     *   player.给予(item)
     */
    private fun injectExtensions(nova: Nova, context: ScriptContext) {
        // ── String 扩展 ──────────────────────────
        nova.ext<String>(String::class.java, "color", "着色") { str -> str.colored() }

        // ── Player 扩展 ──────────────────────────
        nova.ext<Player, String>(Player::class.java, "msg", "消息") { p, msg -> p.sendMessage(msg.colored()) }
        nova.ext<HumanEntity, String, String>(Player::class.java, "title", "标题") { p, t, s -> p.title(t.colored(), s.colored()) }
        nova.ext<HumanEntity, String>(Player::class.java, "actionBar", "动作栏") { p, msg -> p.actionBar(msg.colored()); null }
        nova.ext<HumanEntity, ItemStack>(Player::class.java, "give", "给予") { p, item -> p.giveItem(item); null }
        nova.ext<Player, Any>(Player::class.java, "tp", "传送") { p, target ->
            when (target) {
                is Player -> p.teleport(target)
                is org.bukkit.Location -> p.teleport(target)
                else -> false
            }
        }
        @Suppress("DEPRECATION")
        nova.ext<Player>(Player::class.java, "heal", "满血") { p -> p.health = p.maxHealth }
        nova.ext<Player>(Player::class.java, "feed", "满饱") { p -> p.foodLevel = 20 }
        nova.ext<Player, String>(Player::class.java, "exec", "执行", "cmd") { p, cmd -> Bukkit.dispatchCommand(p, cmd) }
        nova.ext<Player, String>(Player::class.java, "execOp", "以管理员执行", "sudocmd") { p, cmd -> p.dispatchCommandAsOp(cmd) }

        // ── Vault 经济扩展（可选） ──────────────
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            nova.ext<OfflinePlayer>(Player::class.java, "balance", "余额") { p -> p.getBalance() }
            nova.ext<OfflinePlayer, Number>(Player::class.java, "pay", "扣款") { p, amount -> p.withdrawBalance(amount.toDouble()).transactionSuccess() }
            nova.ext<OfflinePlayer, Number>(Player::class.java, "earn", "收入") { p, amount -> p.depositBalance(amount.toDouble()).transactionSuccess() }
        }

        // ── Player 属性缩写 ──────────────────
        nova.ext<Player>(Player::class.java, "loc", "位置") { p -> p.location }
        nova.ext<Player>(Player::class.java, "hand", "手持") { p -> p.inventory.itemInMainHand }
        @Suppress("DEPRECATION")
        nova.ext<Player>(Player::class.java, "hp", "血量") { p -> p.health }
        nova.ext<Player, String>(Player::class.java, "perm", "权限") { p, node -> p.hasPermission(node) }
        nova.ext<Player>(Player::class.java, "inv", "背包") { p -> p.inventory }
        nova.ext<Player>(Player::class.java, "gm", "模式") { p -> p.gameMode }

        // ── Player 动作缩写 ──────────────────
        nova.ext<Player, Any>(Player::class.java, "sound", "音效") { p, sound ->
            when (sound) {
                is org.bukkit.Sound -> p.playSound(p.location, sound, 1f, 1f)
                is String -> p.playSound(p.location, org.bukkit.Sound.valueOf(sound.uppercase()), 1f, 1f)
                else -> null
            }
        }
        val effectFn = { p: Player, type: Any, duration: Number, amplifier: Number ->
            val effectType = when (type) {
                is org.bukkit.potion.PotionEffectType -> type
                is String -> org.bukkit.potion.PotionEffectType.getByName(type.uppercase())
                else -> null
            }
            if (effectType != null) {
                p.addPotionEffect(org.bukkit.potion.PotionEffect(effectType, duration.toInt() * 20, amplifier.toInt()))
            }
        }
        nova.ext<Player, Any, Number, Number>(Player::class.java, "effect") { p, type, d, a -> effectFn(p, type, d, a) }
        nova.ext<Player, Any, Number, Number>(Player::class.java, "药水") { p, type, d, a -> effectFn(p, type, d, a) }
        nova.ext<Player>(Player::class.java, "clearEffects", "清除药水") { p ->
            p.activePotionEffects.forEach { p.removePotionEffect(it.type) }
        }
        nova.ext<Player, String>(Player::class.java, "kick", "踢出") { p, reason -> p.kickPlayer(reason.colored()) }
        nova.ext<Player, Boolean>(Player::class.java, "fly", "飞行") { p, enabled ->
            p.allowFlight = enabled; p.isFlying = enabled
        }
        nova.ext<Player, Number>(Player::class.java, "speed", "速度") { p, spd -> p.walkSpeed = spd.toFloat() }

        // ── ItemStack 扩展 ────────────────────
        @Suppress("DEPRECATION")
        nova.ext<ItemStack>(ItemStack::class.java, "name", "名称") { item -> item.itemMeta?.displayName }
        nova.ext<ItemStack>(ItemStack::class.java, "lore", "描述") { item -> item.itemMeta?.lore }
        nova.ext<ItemStack>(ItemStack::class.java, "type", "类型") { item -> item.type.name }
        nova.ext<ItemStack>(ItemStack::class.java, "amount", "数量") { item -> item.amount }

        // item.copy() → 深拷贝
        nova.ext<ItemStack>(ItemStack::class.java, "copy", "拷贝") { item -> item.clone() }

        // ── ItemStack NBT 扩展（TabooLib ItemTag）──
        nova.ext<ItemStack>(ItemStack::class.java, "nbt") { item -> item.getItemTag() }
        nova.ext<ItemStack, String>(ItemStack::class.java, "getNbt", "获取NBT") { item, key ->
            item.getItemTag().getDeep(key)?.unsafeData()
        }
        nova.ext<ItemStack, String, Any>(ItemStack::class.java, "setNbt", "设置NBT") { item, key, value ->
            val tag = item.getItemTag(); tag.putDeep(key, value); tag.saveTo(item)
        }
        nova.ext<ItemStack, String>(ItemStack::class.java, "hasNbt", "有NBT") { item, key ->
            item.getItemTag().getDeep(key) != null
        }
        nova.ext<ItemStack, String>(ItemStack::class.java, "removeNbt", "移除NBT") { item, key ->
            val tag = item.getItemTag(); tag.removeDeep(key); tag.saveTo(item)
        }
        nova.ext<ItemStack>(ItemStack::class.java, "nbtJson") { item -> item.getItemTag().toJson() }
        nova.ext<ItemStack>(ItemStack::class.java, "toJson", "转JSON") { item -> ItemTag.toJson(item) }

        // ── PAPI 扩展 ────────────────────────
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            nova.ext<Player, String>(Player::class.java, "papi") { p, text ->
                text.replacePlaceholder(p)
            }
        }
    }
}

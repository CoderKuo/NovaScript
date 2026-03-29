package com.dakuo.novascript.command

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand
import taboolib.expansion.createHelper
import com.dakuo.novascript.script.ScriptManager
import com.dakuo.novascript.config.PlaceholderManager
import com.dakuo.novascript.config.EventManager
import com.dakuo.novascript.config.TaskManager
import com.dakuo.novascript.config.ItemScriptManager
import com.dakuo.novascript.config.BlockScriptManager
import com.dakuo.novascript.config.CommandManager
import com.dakuo.novascript.module.ModuleManager

@CommandHeader(
    name = "nova",
    description = "NovaScript 脚本管理",
    permission = "novascript.admin"
)
object MainCommand {

    @CommandBody
    val main = mainCommand {
        createHelper()
    }

    @CommandBody
    val load = subCommand {
        dynamic("脚本名") {
            suggestion<CommandSender>(uncheck = true) { _, _ ->
                ScriptManager.getUnloadedScriptNames()
            }
            execute<CommandSender> { sender, context, _ ->
                val name = context["脚本名"]
                if (ScriptManager.load(name)) {
                    sender.sendMessage("§a[NovaScript] 已加载脚本: $name")
                } else {
                    sender.sendMessage("§c[NovaScript] 加载脚本失败: $name")
                }
            }
        }
    }

    @CommandBody
    val unload = subCommand {
        dynamic("脚本名") {
            suggestion<CommandSender>(uncheck = false) { _, _ ->
                ScriptManager.getScriptNames()
            }
            execute<CommandSender> { sender, context, _ ->
                val name = context["脚本名"]
                if (ScriptManager.unload(name)) {
                    sender.sendMessage("§a[NovaScript] 已卸载脚本: $name")
                } else {
                    sender.sendMessage("§c[NovaScript] 脚本未加载: $name")
                }
            }
        }
    }

    @CommandBody
    val reload = subCommand {
        dynamic("脚本名") {
            suggestion<CommandSender>(uncheck = false) { _, _ ->
                ScriptManager.getScriptNames()
            }
            execute<CommandSender> { sender, context, _ ->
                val name = context["脚本名"]
                ScriptManager.reload(name)
                sender.sendMessage("§a[NovaScript] 已重载脚本: $name")
            }
        }
        execute<CommandSender> { sender, _, _ ->
            ScriptManager.reload()
            sender.sendMessage("§a[NovaScript] 已重载所有脚本")
        }
    }

    @CommandBody
    val list = subCommand {
        execute<CommandSender> { sender, _, _ ->
            val scripts = ScriptManager.list()
            if (scripts.isEmpty()) {
                sender.sendMessage("§7[NovaScript] 没有已加载的脚本")
                return@execute
            }

            sender.sendMessage("§e[NovaScript] 已加载的脚本 (${scripts.size}):")

            // 按插件名分组显示
            val grouped = scripts.groupBy { it.pluginName }
            for ((pluginName, pluginScripts) in grouped.entries.sortedBy { it.key }) {
                sender.sendMessage("§6  [$pluginName]")
                for (ctx in pluginScripts.sortedBy { it.name }) {
                    val events = ctx.registeredListeners.size
                    val forgeEvents = ctx.forgeListenerHandles.size
                    val fabricEvents = ctx.fabricListenerHandles.size
                    val commands = ctx.registeredCommands.size
                    val tasks = ctx.scheduledTasks.size
                    val mode = ctx.runMode.name.lowercase()
                    val modEvents = listOfNotNull(
                        if (forgeEvents > 0) "Forge: $forgeEvents" else null,
                        if (fabricEvents > 0) "Fabric: $fabricEvents" else null
                    ).joinToString(", ")
                    val modInfo = if (modEvents.isNotEmpty()) ", mod事件: $modEvents" else ""
                    sender.sendMessage("§7    - §f${ctx.name} §7[$mode] (事件: $events$modInfo, 命令: $commands, 任务: $tasks)")
                }
            }
        }
    }

    @CommandBody
    val papi = subCommand {
        execute<CommandSender> { sender, _, _ ->
            PlaceholderManager.reload()
            val names = PlaceholderManager.getNames()
            sender.sendMessage("§a[NovaScript] 已重载 ${names.size} 个配置式占位符")
            if (names.isNotEmpty()) {
                sender.sendMessage("§7  ${names.joinToString(", ")}")
            }
        }
    }

    @CommandBody
    val events = subCommand {
        execute<CommandSender> { sender, _, _ ->
            EventManager.reload()
            sender.sendMessage("§a[NovaScript] 已重载 ${EventManager.getCount()} 个配置式事件监听")
        }
    }

    @CommandBody
    val tasks = subCommand {
        execute<CommandSender> { sender, _, _ ->
            TaskManager.reload()
            sender.sendMessage("§a[NovaScript] 已重载 ${TaskManager.getCount()} 个配置式定时任务")
        }
    }

    @CommandBody
    val commands = subCommand {
        execute<CommandSender> { sender, _, _ ->
            CommandManager.reload()
            sender.sendMessage("§a[NovaScript] 已重载 ${CommandManager.getCount()} 个配置式命令")
        }
    }

    @CommandBody
    val items = subCommand {
        execute<CommandSender> { sender, _, _ ->
            ItemScriptManager.reload()
            sender.sendMessage("§a[NovaScript] 已重载 ${ItemScriptManager.getCount()} 个物品脚本动作")
        }
    }

    @CommandBody
    val blocks = subCommand {
        // /nova blocks — 列出所有方块脚本
        execute<CommandSender> { sender, _, _ ->
            val entries = BlockScriptManager.getEntries()
            if (entries.isEmpty()) {
                sender.sendMessage("§7[NovaScript] 没有已加载的方块脚本")
                return@execute
            }
            sender.sendMessage("§e[NovaScript] 方块脚本 (${entries.size} 个, ${BlockScriptManager.getLocationCount()} 个坐标点):")
            for (entry in entries) {
                val locs = entry.locations.joinToString(", ")
                val mode = if (entry.handler is com.dakuo.novascript.config.ActionHandler.ScriptRef) "§b脚本引用" else "§a内嵌代码"
                sender.sendMessage("§6  ${entry.name} §7[${entry.fileName}] §8- §f${entry.actionType} $mode")
                sender.sendMessage("§7    坐标: §f$locs")
            }
        }

        // /nova blocks reload — 重载
        literal("reload") {
            execute<CommandSender> { sender, _, _ ->
                BlockScriptManager.reload()
                sender.sendMessage("§a[NovaScript] 已重载 ${BlockScriptManager.getCount()} 个方块脚本 (${BlockScriptManager.getLocationCount()} 个坐标点)")
            }
        }

        // /nova blocks look — 查看脚准星指向方块的脚本
        literal("look") {
            execute<CommandSender> { sender, _, _ ->
                if (sender !is Player) {
                    sender.sendMessage("§c仅玩家可用")
                    return@execute
                }
                val block = sender.getTargetBlockExact(10)
                if (block == null) {
                    sender.sendMessage("§c未指向方块")
                    return@execute
                }
                val loc = block.location
                val entries = BlockScriptManager.getEntriesAt(loc.world?.name ?: "", loc.blockX, loc.blockY, loc.blockZ)
                if (entries.isEmpty()) {
                    sender.sendMessage("§7[NovaScript] §f${loc.world?.name}:${loc.blockX},${loc.blockY},${loc.blockZ} §7上没有绑定脚本")
                    return@execute
                }
                sender.sendMessage("§e[NovaScript] 该方块绑定的脚本:")
                for (entry in entries) {
                    sender.sendMessage("§6  ${entry.name} §7[${entry.fileName}] §8- §f${entry.actionType}")
                }
            }
        }

        // /nova blocks here — 复制当前坐标为配置格式
        literal("here") {
            execute<CommandSender> { sender, _, _ ->
                if (sender !is Player) {
                    sender.sendMessage("§c仅玩家可用")
                    return@execute
                }
                val loc = sender.location
                val locStr = "${loc.world?.name}:${loc.blockX},${loc.blockY},${loc.blockZ}"
                sender.sendMessage("§a[NovaScript] 当前坐标: §f$locStr §7(可复制上方文本)")
            }
        }

        // /nova blocks at <坐标> — 查看指定坐标的脚本
        literal("at") {
            dynamic("坐标") {
                execute<CommandSender> { sender, context, _ ->
                    val locStr = context["坐标"]
                    val parts = locStr.split(":")
                    if (parts.size != 2) {
                        sender.sendMessage("§c格式: world:x,y,z")
                        return@execute
                    }
                    val coords = parts[1].split(",")
                    if (coords.size != 3) {
                        sender.sendMessage("§c格式: world:x,y,z")
                        return@execute
                    }
                    try {
                        val entries = BlockScriptManager.getEntriesAt(
                            parts[0], coords[0].trim().toInt(), coords[1].trim().toInt(), coords[2].trim().toInt()
                        )
                        if (entries.isEmpty()) {
                            sender.sendMessage("§7[NovaScript] §f$locStr §7上没有绑定脚本")
                        } else {
                            sender.sendMessage("§e[NovaScript] §f$locStr §e绑定的脚本:")
                            for (entry in entries) {
                                sender.sendMessage("§6  ${entry.name} §7[${entry.fileName}] §8- §f${entry.actionType}")
                                sender.sendMessage("§7    全部坐标: §f${entry.locations.joinToString(", ")}")
                            }
                        }
                    } catch (_: NumberFormatException) {
                        sender.sendMessage("§c坐标格式错误")
                    }
                }
            }
        }

        // /nova blocks bind <文件名:条目名> — 将准星方块绑定到指定脚本
        literal("bind") {
            dynamic("脚本") {
                suggestion<CommandSender>(uncheck = false) { _, _ ->
                    BlockScriptManager.getBindableNames()
                }
                execute<CommandSender> { sender, context, _ ->
                    if (sender !is Player) {
                        sender.sendMessage("§c仅玩家可用")
                        return@execute
                    }
                    val block = sender.getTargetBlockExact(10)
                    if (block == null) {
                        sender.sendMessage("§c未指向方块")
                        return@execute
                    }

                    val ref = context["脚本"]
                    val parts = ref.split(":", limit = 2)
                    if (parts.size != 2) {
                        sender.sendMessage("§c格式: 文件名:条目名")
                        return@execute
                    }

                    val loc = block.location
                    val locStr = "${loc.world?.name}:${loc.blockX},${loc.blockY},${loc.blockZ}"

                    if (BlockScriptManager.bindLocation(parts[0], parts[1], locStr)) {
                        sender.sendMessage("§a[NovaScript] 已将 §f$locStr §a绑定到 §e${parts[1]} §7[${parts[0]}]")
                    } else {
                        sender.sendMessage("§c[NovaScript] 绑定失败（脚本不存在或坐标已绑定）")
                    }
                }
            }
        }

        // /nova blocks unbind — 解绑准星方块上的脚本
        literal("unbind") {
            execute<CommandSender> { sender, _, _ ->
                if (sender !is Player) {
                    sender.sendMessage("§c仅玩家可用")
                    return@execute
                }
                val block = sender.getTargetBlockExact(10)
                if (block == null) {
                    sender.sendMessage("§c未指向方块")
                    return@execute
                }

                val loc = block.location
                val locStr = "${loc.world?.name}:${loc.blockX},${loc.blockY},${loc.blockZ}"
                val entries = BlockScriptManager.getEntriesAt(loc.world?.name ?: "", loc.blockX, loc.blockY, loc.blockZ)

                if (entries.isEmpty()) {
                    sender.sendMessage("§7[NovaScript] 该方块上没有绑定脚本")
                    return@execute
                }

                var removed = 0
                for (entry in entries) {
                    if (BlockScriptManager.unbindLocation(entry.fileName, entry.name, locStr)) {
                        sender.sendMessage("§a[NovaScript] 已从 §e${entry.name} §7[${entry.fileName}] §a解绑 §f$locStr")
                        removed++
                        break // unbind 会触发 reload，entries 已失效
                    }
                }
                if (removed == 0) {
                    sender.sendMessage("§c[NovaScript] 解绑失败")
                }
            }
        }
    }

    @CommandBody
    val modules = subCommand {
        execute<CommandSender> { sender, _, _ ->
            val mods = ModuleManager.getModules()
            if (mods.isEmpty()) {
                sender.sendMessage("§7[NovaScript] 没有已加载的模块")
            } else {
                sender.sendMessage("§e[NovaScript] 已加载模块 (${mods.size}):")
                mods.forEach { m ->
                    val desc = if (m.description.isNotEmpty()) " §7- ${m.description}" else ""
                    sender.sendMessage("§7  - §f${m.name} §8v${m.version}$desc")
                }
            }
        }

        literal("reload") {
            execute<CommandSender> { sender, _, _ ->
                ModuleManager.reload()
                sender.sendMessage("§a[NovaScript] 已重载 ${ModuleManager.getModuleNames().size} 个模块")
            }
        }
    }

    /** 从 RegisteredEntry 提取函数签名，如 broadcast(msg) */
    private fun funcSignature(entry: com.novalang.runtime.NovaRuntime.RegisteredEntry): String {
        val paramCount = entry.paramTypes?.size ?: run {
            // 从 FunctionN 类型推断参数数量
            val fn = entry.function ?: return entry.name
            val cls = fn.javaClass
            for (iface in cls.interfaces) {
                val match = Regex("""Function(\d+)""").find(iface.simpleName)
                if (match != null) return@run match.groupValues[1].toInt()
            }
            return entry.name
        }
        if (paramCount == 0) return "${entry.name}()"
        val params = (1..paramCount).joinToString(", ") { "arg$it" }
        return "${entry.name}($params)"
    }

    /** 构建函数条目的 hover 文本 */
    private fun funcHover(entry: com.novalang.runtime.NovaRuntime.RegisteredEntry, prefix: String = ""): String {
        return buildString {
            append("§b$prefix${funcSignature(entry)}")
            val desc = entry.description
            if (!desc.isNullOrEmpty()) append("\n§7$desc")
            if (entry.source != null) append("\n§8来源: ${entry.source}")
        }
    }

    @CommandBody
    val api = subCommand {
        // /nova api — 总览
        execute<CommandSender> { sender, _, _ ->
            val rt = com.novalang.runtime.NovaRuntime.shared()
            val functions = rt.listFunctions().filter { it.namespace == null && it.isFunction }
            val variables = rt.listFunctions().filter { it.namespace == null && !it.isFunction }
            val namespaces = rt.listNamespaces()
            val proxySender = taboolib.common.platform.function.adaptCommandSender(sender)

            sender.sendMessage("§8═══════════ §6NovaScript API §8═══════════")
            sender.sendMessage("")

            // 全局函数 — 可点击查看
            taboolib.module.chat.Components.empty()
                .append("  §e§l全局函数 §7(${functions.size})")
                .hoverText("§7点击查看全部全局函数")
                .clickRunCommand("/nova api functions")
                .append(" §8[点击查看]")
                .sendTo(proxySender)

            // 全局变量 — 可点击查看
            taboolib.module.chat.Components.empty()
                .append("  §e§l全局变量 §7(${variables.size})")
                .hoverText("§7点击查看全部全局变量")
                .clickRunCommand("/nova api variables")
                .append(" §8[点击查看]")
                .sendTo(proxySender)

            // 函数库列表 — 每个可点击展开
            if (namespaces.isNotEmpty()) {
                sender.sendMessage("")
                sender.sendMessage("  §e§l函数库 §7(${namespaces.size})")
                for (ns in namespaces.sorted()) {
                    val count = rt.listFunctions(ns).size
                    taboolib.module.chat.Components.empty()
                        .append("    §6$ns §7($count 个)")
                        .hoverText("§7点击查看 §b$ns §7的全部函数")
                        .clickRunCommand("/nova api lib $ns")
                        .sendTo(proxySender)
                }
            }

            // 扩展函数 — 按类型分组
            val extAll = rt.getExtensionRegistry().getAll()
            if (extAll.isNotEmpty()) {
                sender.sendMessage("")
                val totalExt = extAll.values.sumOf { it.values.sumOf { l -> l.size } }
                sender.sendMessage("  §e§l扩展函数 §7($totalExt)")
                for ((type, methods) in extAll.entries.sortedBy { it.key.simpleName }) {
                    val typeName = type.simpleName
                    val count = methods.values.sumOf { it.size }
                    taboolib.module.chat.Components.empty()
                        .append("    §d$typeName §7($count 个)")
                        .hoverText("§7点击查看 §d$typeName §7的扩展函数")
                        .clickRunCommand("/nova api ext $typeName")
                        .sendTo(proxySender)
                }
            }

            sender.sendMessage("")
            sender.sendMessage("§8════════════════════════════════════")
        }

        // /nova api ext <类型> — 查看指定类型的扩展函数
        literal("ext") {
            dynamic("类型") {
                suggestion<CommandSender>(uncheck = true) { _, _ ->
                    com.novalang.runtime.NovaRuntime.shared().getExtensionRegistry().getAll().keys.map { it.simpleName }
                }
                execute<CommandSender> { sender, context, _ ->
                    val typeName = context["类型"]
                    val rt = com.novalang.runtime.NovaRuntime.shared()
                    val extAll = rt.getExtensionRegistry().getAll()
                    val entry = extAll.entries.find { it.key.simpleName == typeName }
                    val proxySender = taboolib.common.platform.function.adaptCommandSender(sender)

                    if (entry == null) {
                        sender.sendMessage("§7[NovaScript] 类型 '$typeName' 没有扩展函数")
                        return@execute
                    }

                    val (type, methods) = entry
                    val total = methods.values.sumOf { it.size }
                    sender.sendMessage("§8═══════════ §d${type.simpleName} §7扩展函数 ($total) §8═══════════")

                    for ((methodName, overloads) in methods.entries.sortedBy { it.key }) {
                        for (ext in overloads) {
                            val paramCount = ext.paramTypes?.size ?: 0
                            val sig = if (paramCount == 0) "$methodName()" else {
                                val params = (1..paramCount).joinToString(", ") { "arg$it" }
                                "$methodName($params)"
                            }
                            taboolib.module.chat.Components.empty()
                                .append("  §a${type.simpleName}.§f$sig")
                                .hoverText("§b${type.simpleName}.$sig\n§7参数: $paramCount 个")
                                .sendTo(proxySender)
                        }
                    }
                    sender.sendMessage("§8════════════════════════════════════")
                }
            }
        }

        // /nova api functions — 全局函数列表
        literal("functions") {
            execute<CommandSender> { sender, _, _ ->
                val rt = com.novalang.runtime.NovaRuntime.shared()
                val functions = rt.listFunctions().filter { it.namespace == null && it.isFunction }.sortedBy { it.name }
                val proxySender = taboolib.common.platform.function.adaptCommandSender(sender)

                sender.sendMessage("§8═══════════ §e全局函数 §7(${functions.size}) §8═══════════")
                for (fn in functions) {
                    val sig = funcSignature(fn)
                    val desc = fn.description ?: ""
                    taboolib.module.chat.Components.empty()
                        .append("  §a$sig")
                        .hoverText(funcHover(fn))
                        .append(" §7$desc")
                        .sendTo(proxySender)
                }
                sender.sendMessage("§8════════════════════════════════════")
            }
        }

        // /nova api variables — 全局变量列表
        literal("variables") {
            execute<CommandSender> { sender, _, _ ->
                val rt = com.novalang.runtime.NovaRuntime.shared()
                val variables = rt.listFunctions().filter { it.namespace == null && !it.isFunction }.sortedBy { it.name }
                val proxySender = taboolib.common.platform.function.adaptCommandSender(sender)

                sender.sendMessage("§8═══════════ §e全局变量 §7(${variables.size}) §8═══════════")
                for (v in variables) {
                    val typeName = v.returnType?.simpleName ?: "Any"
                    val hover = buildString {
                        append("§b${v.name} §8: §f$typeName")
                        if (v.description != null) append("\n§7${v.description}")
                    }
                    taboolib.module.chat.Components.empty()
                        .append("  §b${v.name}")
                        .hoverText(hover)
                        .append(" §8: §f$typeName")
                        .sendTo(proxySender)
                }
                sender.sendMessage("§8════════════════════════════════════")
            }
        }

        // /nova api lib <名称> — 查看指定函数库
        literal("lib") {
            dynamic("库名") {
                suggestion<CommandSender>(uncheck = true) { _, _ ->
                    com.novalang.runtime.NovaRuntime.shared().listNamespaces()
                }
                execute<CommandSender> { sender, context, _ ->
                    val ns = context["库名"]
                    val rt = com.novalang.runtime.NovaRuntime.shared()
                    val entries = rt.listFunctions(ns).sortedBy { it.name }
                    val proxySender = taboolib.common.platform.function.adaptCommandSender(sender)

                    if (entries.isEmpty()) {
                        sender.sendMessage("§7[NovaScript] 函数库 '$ns' 不存在或为空")
                        return@execute
                    }

                    sender.sendMessage("§8═══════════ §6$ns §7(${entries.size}) §8═══════════")
                    for (fn in entries) {
                        val desc = fn.description ?: ""
                        val displayName = if (fn.isFunction) "§a${funcSignature(fn)}" else "§b${fn.name}"
                        val suffix = if (!fn.isFunction) " §8: §f${fn.returnType?.simpleName ?: "Any"}" else ""
                        taboolib.module.chat.Components.empty()
                            .append("  $displayName")
                            .hoverText(funcHover(fn, "$ns."))
                            .append("$suffix §7$desc")
                            .sendTo(proxySender)
                    }
                    sender.sendMessage("§8════════════════════════════════════")
                }
            }
        }
    }

    @CommandBody
    val eval = subCommand {
        dynamic("参数") {
            suggestion<CommandSender>(uncheck = true) { _, _ ->
                ScriptManager.getScriptNames()
            }
            // /nova eval <脚本名> <代码> — 在脚本上下文中执行
            dynamic("代码") {
                execute<CommandSender> { sender, context, _ ->
                    val name = context["参数"]
                    val code = context["代码"]
                    val ns = ScriptManager.makeNamespace(ScriptManager.SELF_PLUGIN, name)
                    val result = ScriptManager.evalInScript(ns, code)
                    sender.sendMessage("§a[NovaScript] §f$result")
                }
            }
            // /nova eval <代码> — 直接执行代码（独立上下文）
            execute<CommandSender> { sender, context, _ ->
                val code = context["参数"]
                val result = ScriptManager.eval(code)
                sender.sendMessage("§a[NovaScript] §f$result")
            }
        }
    }
}

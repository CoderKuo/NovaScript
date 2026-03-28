# 脚本 API 参考

## 事件监听

| 函数 | 说明 |
|------|------|
| `on(eventName, callback)` | 监听 Bukkit 事件，支持短名和全限定名 |
| `forge.on(className, callback)` | 监听 Forge/NeoForge 事件（混合核心） |
| `fabric.on(className, fieldName, callback)` | 监听 Fabric 事件 |

```javascript
on("PlayerJoinEvent") { event -> event.getPlayer().msg("&b欢迎!") }
on("com.example.CustomEvent") { event -> log("触发") }
forge.on("com.pixelmonmod.pixelmon.api.events.CaptureEvent$SuccessfulCapture") { event -> ... }
```

## 命令注册

| 函数 | 说明 |
|------|------|
| `command(name, callback)` | 注册命令，返回 ScriptCommand 对象 |
| `.tab(callback)` | 链式设置 Tab 补全（返回建议列表） |
| `.permission(perm)` | 链式设置权限节点 |
| `.permission(perm, msg)` | 设置权限节点和无权限提示 |
| `tabComplete(name, callback)` | 分离式 Tab 补全 |

```javascript
command("kit") { sender, args ->
    when (args.get(0)) {
        "warrior" -> sender.msg("&c战士套装")
        "mage"    -> sender.msg("&9法师套装")
        else      -> sender.msg("&c未知")
    }
}.tab { sender, args ->
    if (args.size() == 1) ["warrior", "mage", "archer"]
    else []
}

command("fly") { sender, args ->
    sender.setFlying(!sender.isFlying())
    sender.msg("&a飞行模式已切换")
}.permission("server.fly", "&c你没有飞行权限")
 .tab { sender, args -> ["on", "off"] }
```

## 工具函数

| 函数 | 说明 |
|------|------|
| `broadcast(msg)` | 全服广播（支持颜色代码） |
| `log(msg)` | 输出信息日志到控制台 |
| `warn(msg)` | 输出警告日志到控制台 |
| `colorize(text)` | 将 `&颜色代码` 转换为实际颜色 |
| `runCommand(sender, cmd)` | 以指定身份执行服务器命令 |
| `getPlayer(name)` | 根据名称获取在线玩家，不在线返回 null |
| `getOnlinePlayers()` | 获取所有在线玩家列表 |

```javascript
broadcast("&a服务器公告: &f今晚 8 点活动!")
log("这是一条日志")
warn("这是一条警告")
val colored = colorize("&a绿色文字")
runCommand(player, "spawn")
val target = getPlayer("Steve")
val players = getOnlinePlayers()
```

## 物品构建

| 函数 | 说明 |
|------|------|
| `buildItem(material, callback)` | 创建物品，材质名参考 [XMaterial](https://github.com/CryptoMorin/XSeries/wiki/XMaterial) |

```javascript
val sword = buildItem("DIAMOND_SWORD") { b ->
    b.setName("&b&l传说之剑")
    b.getLore().add("&7一把神秘的剑")
    b.colored()
}
```

## 玩家消息

| 函数 | 说明 |
|------|------|
| `sendTitle(player, title, subtitle)` | 发送大标题和副标题 |
| `sendActionBar(player, msg)` | 发送动作栏消息 |
| `giveItem(player, item)` | 给玩家添加物品到背包 |

## 输入捕获

| 函数 | 说明 |
|------|------|
| `nextChat(player, callback)` | 捕获玩家下一条聊天消息 |
| `inputSign(player, callback)` | 打开告示牌输入界面 |

```javascript
command("setname") { sender, args ->
    sender.msg("&e请输入昵称:")
    nextChat(sender) { message ->
        sender.msg("&a昵称已设为: &f" + message)
    }
}

command("sign") { sender, args ->
    inputSign(sender) { lines ->
        sender.msg("&a第一行: &f" + lines[0])
    }
}
```

## 经济系统（需要 Vault）

| 函数 | 说明 |
|------|------|
| `getBalance(player)` | 获取玩家余额 |
| `withdraw(player, amount)` | 扣款，返回是否成功 |
| `deposit(player, amount)` | 存款，返回是否成功 |
| `fakeOp(player, command)` | 以临时 OP 权限执行命令 |

## GUI 菜单

| 函数 | 说明 |
|------|------|
| `createMenu(title, rows)` | 创建箱子菜单，返回 ScriptMenu 对象 |
| `menu.set(slot, item)` | 设置指定槽位的物品 |
| `menu.onClick(slot, callback)` | 设置点击回调 |
| `menu.open(player)` | 为玩家打开菜单 |

## 配置文件 & 数据库

| 函数 | 说明 |
|------|------|
| `loadConfig(filename)` | 加载配置文件（不存在自动创建） |
| `saveConfig(config)` | 保存配置到文件 |
| `connectSQLite(filename)` | 连接 SQLite 数据库 |
| `connectMySQL(host, port, user, password, database)` | 连接 MySQL 数据库 |

## PlaceholderAPI（需要安装）

| 函数 | 说明 |
|------|------|
| `placeholder(id, callback)` | 注册占位符，通过 `%novascript_id%` 访问 |

## 全局常量

| 对象 | 类型 | 说明 |
|------|------|------|
| `Bukkit` / `server` | Server | 服务器实例 |
| `pluginManager` | PluginManager | 插件管理器 |
| `consoleSender` | ConsoleCommandSender | 控制台发送者 |
| `Material` | Class | 材质枚举，如 `Material.DIAMOND` |
| `Sound` | Class | 音效枚举 |
| `GameMode` | Class | 游戏模式枚举 |
| `ChatColor` | Class | 颜色枚举 |
| `Cooldown` | Class | 冷却管理类 |
| `Duration` | Class | 时间段类 |
| `Countdown` | Class | 倒计时类 |

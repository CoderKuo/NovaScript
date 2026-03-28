# 配置式功能

无需编写 `.nova` 脚本文件，直接在 YAML 中用 NovaLang 表达式配置。预编译为字节码执行。

所有配置式功能支持两种代码写法：
- **内嵌代码**：直接写 NovaLang 表达式
- **脚本引用**：`"script:脚本名:函数名"` 调用 scripts/ 中已加载脚本的函数

## 配置式占位符（placeholders.yml）

```yaml
# 简写 — 无缓存
player-health: "player.getHealth().toInt()"

# 完整 — 带缓存（毫秒），适合记分板等高频调用
server-online:
  code: "Bukkit.getOnlinePlayers().size()"
  cache: 1000

# 多行表达式
player-pos: |
  val loc = player.getLocation()
  loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()
```

使用 `%novascript_server-online%` 等访问。`/nova papi` 热重载。

## 配置式事件监听（events.yml）

```yaml
玩家加入欢迎:
  event: PlayerJoinEvent
  code: |
    val player = event.getPlayer()
    broadcast("&a+ &e" + player.getName() + " &7加入了服务器")

自定义事件:
  event: com.example.MyEvent
  priority: HIGH                  # 可选：LOWEST/LOW/NORMAL/HIGH/HIGHEST/MONITOR
  code: "log(event.toString())"
```

`/nova events` 热重载。

## 配置式定时任务（tasks.yml）

```yaml
自动公告:
  period: 6000        # 执行间隔（tick，20tick = 1秒）
  code: |
    broadcast("&6[公告] &f欢迎来到服务器!")

自动保存:
  period: 12000
  delay: 200          # 首次延迟（tick，可选）
  async: true         # 异步执行（可选，默认 false）
  code: |
    log("正在自动保存...")
    Bukkit.getWorlds().forEach { it.save() }
```

`/nova tasks` 热重载。

## 配置式命令（commands/）

```yaml
# commands/server.yml
spawn:
  code: |
    player.tp(Bukkit.getWorld("world").getSpawnLocation())
    player.msg("&a已传送到出生点!")
  permission: "server.spawn"
  permission-message: "&c你没有传送权限"
  aliases:
    - "s"
  tab:
    - "set"
    - "tp"

fly:
  code: "script:utils:toggleFly"
  permission: "server.fly"
```

| 字段 | 说明 |
|------|------|
| `code` | NovaLang 代码或 `"script:脚本名:函数名"` |
| `permission` | 可选，权限节点 |
| `permission-message` | 可选，无权限提示 |
| `aliases` | 可选，命令别名列表 |
| `tab` | 可选，第一参数补全列表 |

可用变量：`player`/`sender`、`args`。`/nova commands` 热重载。

## 配置式物品脚本（items/）

```yaml
# items/magic_sword.yml
match: "name:&b&l魔法之剑,material:DIAMOND_SWORD"
permission: "item.magic_sword"
permission-message: "&c你没有权限使用此物品"

right-click: |
  player.msg("&b释放魔法!")

left-click: "script:rpg:onSwordClick"

swap: |
  player.msg("&e切换模式")
```

物品匹配语法：精确 `name:xxx` / 模糊 `name:*xxx*` / 正则 `name:/xxx/` / 组合 `name:xxx,material:xxx`

可用变量：`player`、`item`、`event`。`/nova items` 热重载。

## 配置式方块脚本（blocks/）

```yaml
# blocks/teleporters.yml

# 单坐标
传送门:
  location: "world:100,64,200"
  action: right-click
  permission: "block.teleport"
  permission-message: "&c你没有传送权限"
  code: |
    player.msg("&a传送中...")

# 多坐标
传送网络:
  locations:
    - "world:100,64,200"
    - "world:200,64,300"
  action: step
  code: "script:teleport:onStep"
```

| 字段 | 说明 |
|------|------|
| `location` | 单坐标 `"world:x,y,z"` |
| `locations` | 多坐标列表 |
| `action` | `right-click` / `left-click` / `both` / `step` / `all` |
| `code` | NovaLang 代码或 `"script:脚本名:函数名"` |
| `permission` | 可选，权限节点 |
| `permission-message` | 可选，无权限提示 |

可用变量：`player`、`block`、`event`。

**命令式坐标绑定**：

```
/nova blocks bind teleporters:传送门    # 对着方块绑定
/nova blocks unbind                     # 对着方块解绑
/nova blocks look                       # 查看准星方块的脚本
/nova blocks here                       # 复制当前坐标
/nova blocks at world:100,64,200        # 查看指定坐标
```

`/nova blocks reload` 热重载。

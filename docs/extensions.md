# 扩展函数参考

所有扩展函数支持中文别名，可直接在对象上链式调用。

## String 扩展

| 方法 | 别名 | 说明 |
|------|------|------|
| `.color()` | `.着色()` | 将 `&颜色代码` 转换为实际颜色 |

```javascript
"&6金色文字".color()
"&a绿色".着色()
```

## Player 扩展

### 消息 & 交互

| 方法 | 别名 | 说明 |
|------|------|------|
| `.msg(text)` | `.消息(text)` | 发送消息（自动着色） |
| `.title(title, subtitle)` | `.标题(title, subtitle)` | 发送大标题 |
| `.actionBar(msg)` | `.动作栏(msg)` | 发送动作栏消息 |
| `.give(item)` | `.给予(item)` | 给予物品 |
| `.tp(target)` | `.传送(target)` | 传送到玩家或坐标（支持 Player/Location） |
| `.kick(reason)` | `.踢出(reason)` | 踢出玩家 |

### 属性缩写

| 方法 | 别名 | 说明 |
|------|------|------|
| `.loc()` | `.位置()` | 获取位置 Location |
| `.hand()` | `.手持()` | 获取手持物品 ItemStack |
| `.hp()` | `.血量()` | 获取当前生命值 |
| `.perm(node)` | `.权限(node)` | 检查权限，返回 boolean |
| `.inv()` | `.背包()` | 获取背包 Inventory |
| `.gm()` | `.模式()` | 获取游戏模式 |

### 状态控制

| 方法 | 别名 | 说明 |
|------|------|------|
| `.heal()` | `.满血()` | 回满生命值 |
| `.feed()` | `.满饱()` | 回满饥饿值 |
| `.fly(true/false)` | `.飞行(true/false)` | 切换飞行模式 |
| `.speed(0.2)` | `.速度(0.2)` | 设置移动速度（默认 0.2） |
| `.effect(type, sec, amp)` | `.药水(type, sec, amp)` | 添加药水效果（类型/秒/等级） |
| `.clearEffects()` | `.清除药水()` | 清除所有药水效果 |
| `.sound(sound)` | `.音效(sound)` | 播放音效（支持 Sound 枚举或字符串） |

### 命令执行

| 方法 | 别名 | 说明 |
|------|------|------|
| `.exec(cmd)` | `.cmd(cmd)` `.执行(cmd)` | 以玩家身份执行命令 |
| `.execOp(cmd)` | `.sudocmd(cmd)` `.以管理员执行(cmd)` | 以 OP 身份执行命令 |

### 经济（需要 Vault）

| 方法 | 别名 | 说明 |
|------|------|------|
| `.balance()` | `.余额()` | 获取余额 |
| `.pay(amount)` | `.扣款(amount)` | 扣款 |
| `.earn(amount)` | `.收入(amount)` | 存款 |

### 冷却

| 方法 | 别名 | 说明 |
|------|------|------|
| `.cooldown(key, ms)` | `.冷却(key, ms)` | 检查冷却（true=冷却中，false=可用并自动设置） |
| `.cooldownRemaining(key)` | | 获取冷却剩余毫秒 |

### PlaceholderAPI

| 方法 | 说明 |
|------|------|
| `.papi(text)` | 解析文本中的 PAPI 占位符 |

```javascript
// 属性缩写
val loc = player.loc()
val item = player.hand()
val health = player.hp()
if (player.perm("vip.fly")) player.fly(true)

// 状态控制
player.heal()
player.effect("SPEED", 10, 2)
player.sound("ENTITY_PLAYER_LEVELUP")
player.fly(true)
player.speed(0.3)

// 命令执行
player.cmd("spawn")
player.sudocmd("give " + player.getName() + " diamond 1")

// PAPI
player.msg(player.papi("你的等级: %player_level%"))
```

## ItemStack 扩展

### 基本属性

| 方法 | 别名 | 说明 |
|------|------|------|
| `.name()` | `.名称()` | 获取物品显示名称 |
| `.lore()` | `.描述()` | 获取物品 Lore 列表 |
| `.type()` | `.类型()` | 获取材质名称（如 `"DIAMOND_SWORD"`） |
| `.amount()` | `.数量()` | 获取物品数量 |

### NBT 操作

| 方法 | 别名 | 说明 |
|------|------|------|
| `.nbt()` | | 获取完整 NBT（返回 ItemTag 对象） |
| `.getNbt(key)` | `.获取NBT(key)` | 获取指定 NBT 值（支持深路径 `"a.b.c"`） |
| `.setNbt(key, value)` | `.设置NBT(key, value)` | 设置 NBT 值并返回新 ItemStack |
| `.hasNbt(key)` | `.有NBT(key)` | 检查 NBT 是否存在 |
| `.removeNbt(key)` | `.移除NBT(key)` | 移除 NBT 值并返回新 ItemStack |
| `.nbtJson()` | | 获取 NBT 的 JSON 字符串 |
| `.toJson()` | `.转JSON()` | 物品完整 JSON（含材质、数量、NBT） |

全局函数：

| 函数 | 说明 |
|------|------|
| `itemFromJson(json)` | 从 JSON 字符串还原 ItemStack |

```javascript
val item = player.hand()

// 基本属性
log(item.name())       // "&b传说之剑"
log(item.type())       // "DIAMOND_SWORD"
log(item.amount())     // 1

// 读取 NBT
val level = item.getNbt("rpg.level")
if (item.hasNbt("rpg.owner")) {
    log("owner: " + item.getNbt("rpg.owner"))
}

// 写入 NBT（返回新 ItemStack）
val newItem = item.setNbt("rpg.level", 10)
                   .setNbt("rpg.owner", player.getName())
player.inv().setItemInMainHand(newItem)

// 移除 NBT
val cleaned = item.removeNbt("rpg.tempData")

// 深路径
item.setNbt("custom.stats.damage", 50)
val dmg = item.getNbt("custom.stats.damage")

// JSON 序列化/反序列化
val json = item.toJson()
log(json)
val restored = itemFromJson(json)

// NBT JSON（仅 NBT 部分）
log(item.nbtJson())
```

## Number 扩展

| 方法 | 说明 |
|------|------|
| `.formatShort()` | 短格式，如 `1234567` → `"123.5万"` |
| `.formatGrouped()` | 千位分隔，如 `1234567` → `"1,234,567"` |
| `.formatPercent()` | 百分比，如 `0.756` → `"75.6%"` |
| `.toRoman()` | 罗马数字，如 `5` → `"V"` |

## Location 扩展

| 方法 | 说明 |
|------|------|
| `.inCuboid(cuboid)` | 坐标是否在立方体区域内 |
| `.inSphere(sphere)` | 坐标是否在球形区域内 |

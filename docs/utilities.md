# 内置工具库

## 冷却管理

```javascript
// 全局静态冷却（一行搞定：检查+自动设置）
if (cooldown(player, "fireball", 5000)) {
    val remain = cooldownRemaining(player, "fireball")
    player.msg("&c冷却中! 剩余 " + formatDuration(remain))
    return
}
player.msg("&6发射火球!")

// 重置冷却
cooldownReset(player, "fireball")

// 扩展函数写法
if (player.cooldown("skill", 3000)) return

// 实例化方式
val cd = Cooldown(5000)
if (cd.check(player.getUniqueId())) return
cd.set(player.getUniqueId())
```

| 函数 | 说明 |
|------|------|
| `cooldown(player, key, durationMs)` | 检查冷却，冷却中返回 true，否则返回 false 并自动设置 |
| `cooldownRemaining(player, key)` | 获取冷却剩余毫秒数 |
| `cooldownReset(player, key)` | 重置指定冷却 |

## 时间解析

```javascript
val dur = parseDuration("1d2h30m10s")
log(dur.toFormatted())     // "1天2小时30分钟10秒"
log(dur.toMillis())        // 95410000

formatDuration(95410000)   // "1天2小时30分钟10秒"
Duration.parse("2h30m")
Duration.ofMillis(3600000)
```

| 函数 | 说明 |
|------|------|
| `parseDuration(str)` | 解析时间字符串 `"1d2h30m10s"` |
| `formatDuration(millis)` | 毫秒格式化为中文时间 |

Duration 对象方法：`.toMillis()` `.toSeconds()` `.toMinutes()` `.toHours()` `.toFormatted()` `.days` `.hours` `.minutes` `.seconds`

## 随机工具

```javascript
val num = randomInt(1, 100)
val d = randomDouble(0.0, 1.0)
val item = randomEle(["a", "b", "c"])
val code = randomString(8)

val wr = weightRandom(#{"普通": 70, "稀有": 25, "史诗": 5})
val result = wr.next()
```

| 函数 | 说明 |
|------|------|
| `randomInt(min, max)` | `[min, max]` 随机整数 |
| `randomDouble(min, max)` | `[min, max)` 随机浮点 |
| `randomEle(list)` | 随机选择列表元素 |
| `randomString(length)` | 随机字母数字字符串 |
| `weightRandom(map)` | 权重随机器，`.next()` 获取结果 |

## 数字格式化

```javascript
formatShort(1234567)       // "123.5万"
formatGrouped(1234567)     // "1,234,567"
formatPercent(0.756)       // "75.6%"
toRoman(4)                 // "IV"

// 扩展函数
1234567.formatShort()
0.75.formatPercent()
5.toRoman()
```

| 函数 | 说明 |
|------|------|
| `formatShort(number)` | 短格式 `1234567` → `"123.5万"` |
| `formatGrouped(number)` | 千位分隔 `1234567` → `"1,234,567"` |
| `formatPercent(number)` | 百分比 `0.756` → `"75.6%"` |
| `toRoman(number)` | 罗马数字 `5` → `"V"` |

## 区域检测

```javascript
val region = cuboid(loc1, loc2)
region.contains(player.getLocation())
region.volume()
region.center()

val circle = sphere(center, 10.0)
circle.contains(player.getLocation())

player.getLocation().inCuboid(region)
player.getLocation().inSphere(circle)
```

| 函数 | 说明 |
|------|------|
| `cuboid(loc1, loc2)` | 创建立方体区域 |
| `sphere(center, radius)` | 创建球形区域 |

Cuboid 方法：`.contains()` `.volume()` `.center()` `.expand()` `.shift()` `.forEachBlock()` `.serialize()` / `Cuboid.deserialize()`

## 物品匹配

```javascript
matchItem(item, "name:&b传说之剑")
matchItem(item, "name:&b传说之剑,lore:攻击力,material:DIAMOND_SWORD")
matchItem(item, "name:/传说.*/")    // 正则
matchItem(item, "name:*传说*")      // 模糊
```

| 函数 | 说明 |
|------|------|
| `matchItem(item, expression)` | 物品匹配，支持 `name:`/`lore:`/`material:`/`nbt:` 组合 |

## 倒计时

```javascript
val cd = Countdown.start(10, { builder ->
    builder.onTick { sec -> broadcast("&e倒计时: " + sec + "s") }
    builder.onFinish { broadcast("&a开始!") }
    builder.onCancel { broadcast("&c已取消") }
})

cd.isRunning    // 是否运行中
cd.remaining    // 剩余秒数
cd.cancel()     // 取消
```

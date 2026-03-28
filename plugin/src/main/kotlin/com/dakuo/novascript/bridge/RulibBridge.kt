package com.dakuo.novascript.bridge

import com.dakuo.rulib.common.Cuboid
import com.dakuo.rulib.common.Sphere
import com.dakuo.rulib.common.item.matcher.ItemTextMatcher
import com.dakuo.rulib.common.lang.*
import com.novalang.runtime.Nova
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID

/**
 * Rulib 工具库桥接层。
 * 将 Rulib 的实用工具注册为脚本全局函数和扩展函数。
 */
object RulibBridge {

    private val itemMatcher = ItemTextMatcher()

    /**
     * 注册到 NovaRuntime.shared()，所有脚本自动可见。
     */
    fun injectGlobal() {
        val rt = com.novalang.runtime.NovaRuntime.shared()

        // ── 冷却 ─────────────────────────────────
        // cooldown(player, "skill_fire", 5000) → true=冷却中 false=可用(已自动设置)
        rt.register("cooldown",
            com.novalang.runtime.Function3<Any?, Any?, Any?, Any?> { player, key, duration ->
                val uuid = (player as Player).uniqueId
                Cooldown.check(uuid, key.toString(), (duration as Number).toLong())
            }, null, null,
            "cooldown(player, key, durationMs) — 检查冷却，冷却中返回true，否则返回false并自动设置冷却")

        // cooldownRemaining(player, "skill_fire") → 剩余毫秒
        rt.register("cooldownRemaining",
            com.novalang.runtime.Function2<Any?, Any?, Any?> { player, key ->
                Cooldown.remaining((player as Player).uniqueId, key.toString())
            }, null, null,
            "cooldownRemaining(player, key) — 获取冷却剩余毫秒数")

        // cooldownReset(player, "skill_fire")
        rt.register("cooldownReset",
            com.novalang.runtime.Function2<Any?, Any?, Any?> { player, key ->
                Cooldown.reset((player as Player).uniqueId, key.toString()); null
            }, null, null,
            "cooldownReset(player, key) — 重置指定冷却")

        // ── 时间解析 ──────────────────────────────
        // parseDuration("1d2h30m") → Duration 对象
        rt.register("parseDuration",
            com.novalang.runtime.Function1<Any?, Any?> { input -> Duration.parse(input.toString()) },
            null, null,
            "parseDuration(str) — 解析时间字符串如 '1d2h30m10s'，返回 Duration 对象")

        // formatDuration(95410000) → "1天2小时30分钟10秒"
        rt.register("formatDuration",
            com.novalang.runtime.Function1<Any?, Any?> { millis -> (millis as Number).toLong().formatDuration() },
            null, null,
            "formatDuration(millis) — 将毫秒格式化为中文时间如 '1天2小时30分钟'")

        // ── 随机 ──────────────────────────────────
        // randomInt(1, 100)
        rt.register("randomInt",
            com.novalang.runtime.Function2<Any?, Any?, Any?> { min, max ->
                RandomUtil.randomInt((min as Number).toInt(), (max as Number).toInt() + 1)
            }, null, null,
            "randomInt(min, max) — 生成 [min, max] 范围内的随机整数")

        // randomDouble(0.0, 1.0)
        rt.register("randomDouble",
            com.novalang.runtime.Function2<Any?, Any?, Any?> { min, max ->
                RandomUtil.randomDouble((min as Number).toDouble(), (max as Number).toDouble())
            }, null, null,
            "randomDouble(min, max) — 生成 [min, max) 范围内的随机浮点数")

        // randomEle([...]) → 随机元素
        rt.register("randomEle",
            com.novalang.runtime.Function1<Any?, Any?> { list ->
                @Suppress("UNCHECKED_CAST")
                RandomUtil.randomEle(list as List<Any?>)
            }, null, null,
            "randomEle(list) — 从列表中随机选择一个元素")

        // randomString(8)
        rt.register("randomString",
            com.novalang.runtime.Function1<Any?, Any?> { length ->
                RandomUtil.randomString((length as Number).toInt())
            }, null, null,
            "randomString(length) — 生成指定长度的随机字母数字字符串")

        // weightRandom(#{...}) → WeightRandom
        rt.register("weightRandom",
            com.novalang.runtime.Function1<Any?, Any?> { map ->
                @Suppress("UNCHECKED_CAST")
                val m = map as Map<Any?, Number>
                val wr = WeightRandom<Any?>()
                m.forEach { (k, v) -> wr.add(k, v.toDouble()) }
                wr
            }, null, null,
            "weightRandom(#{item: weight, ...}) — 创建权重随机器，调用 .next() 获取结果")

        // ── 数字格式化 ────────────────────────────
        // formatShort(1234567) → "123.5万"
        rt.register("formatShort",
            com.novalang.runtime.Function1<Any?, Any?> { value ->
                NumberFormat.formatShort((value as Number).toDouble())
            }, null, null,
            "formatShort(number) — 短格式化数字，如 1234567 → '123.5万'")

        // formatGrouped(1234567) → "1,234,567"
        rt.register("formatGrouped",
            com.novalang.runtime.Function1<Any?, Any?> { value ->
                NumberFormat.formatGrouped((value as Number).toDouble())
            }, null, null,
            "formatGrouped(number) — 千位分隔格式，如 1234567 → '1,234,567'")

        // formatPercent(0.756) → "75.6%"
        rt.register("formatPercent",
            com.novalang.runtime.Function1<Any?, Any?> { value ->
                NumberFormat.formatPercent((value as Number).toDouble())
            }, null, null,
            "formatPercent(number) — 百分比格式，如 0.756 → '75.6%'")

        // toRoman(5) → "V"
        rt.register("toRoman",
            com.novalang.runtime.Function1<Any?, Any?> { value ->
                NumberFormat.toRoman((value as Number).toInt())
            }, null, null,
            "toRoman(number) — 罗马数字，如 5 → 'V'")

        // ── 区域 ──────────────────────────────────
        // cuboid(loc1, loc2) → Cuboid
        rt.register("cuboid",
            com.novalang.runtime.Function2<Any?, Any?, Any?> { loc1, loc2 ->
                Cuboid(loc1 as Location, loc2 as Location)
            }, null, null,
            "cuboid(loc1, loc2) — 创建立方体区域")

        // sphere(center, radius) → Sphere
        rt.register("sphere",
            com.novalang.runtime.Function2<Any?, Any?, Any?> { center, radius ->
                Sphere(center as Location, (radius as Number).toDouble())
            }, null, null,
            "sphere(center, radius) — 创建球形区域")

        // ── 物品匹配 ─────────────────────────────
        // matchItem(item, "name:&b传说之剑,lore:攻击力") → boolean
        rt.register("matchItem",
            com.novalang.runtime.Function2<Any?, Any?, Any?> { item, expression ->
                itemMatcher.matches(item as ItemStack, expression.toString())
            }, null, null,
            "matchItem(item, expression) — 物品匹配，支持 name:/lore:/material:/nbt: 条件")

        // ── 倒计时 ────────────────────────────────
        // 注入 Countdown 类供脚本直接使用
        rt.set("Countdown", Countdown::class.java, null, null,
            "倒计时类，使用 Countdown.start(seconds) { ... } 启动")

        // 注入 Cooldown 类供高级用法
        rt.set("Cooldown", Cooldown::class.java, null, null,
            "冷却管理类，可实例化: val cd = Cooldown(5000)")

        // 注入 Duration 类
        rt.set("Duration", Duration::class.java, null, null,
            "时间段类，Duration.parse('1d2h30m') / Duration.ofMillis(ms)")

        // ── Player 扩展 ──────────────────────────
        // player.cooldown("skill", 5000) → true=冷却中
        rt.registerExt(Player::class.java, "cooldown",
            com.novalang.runtime.Function3<Any?, Any?, Any?, Any?> { p, key, duration ->
                Cooldown.check((p as Player).uniqueId, key.toString(), (duration as Number).toLong())
            })
        rt.registerExt(Player::class.java, "冷却",
            com.novalang.runtime.Function3<Any?, Any?, Any?, Any?> { p, key, duration ->
                Cooldown.check((p as Player).uniqueId, key.toString(), (duration as Number).toLong())
            })

        // player.cooldownRemaining("skill") → 毫秒
        rt.registerExt(Player::class.java, "cooldownRemaining",
            com.novalang.runtime.Function2<Any?, Any?, Any?> { p, key ->
                Cooldown.remaining((p as Player).uniqueId, key.toString())
            })

        // ── Number 扩展 ─────────────────────────
        // 123456.formatShort() → "12.3万"
        rt.registerExt(Number::class.java, "formatShort",
            com.novalang.runtime.Function1<Any?, Any?> { n -> NumberFormat.formatShort((n as Number).toDouble()) })
        rt.registerExt(Number::class.java, "formatGrouped",
            com.novalang.runtime.Function1<Any?, Any?> { n -> NumberFormat.formatGrouped((n as Number).toDouble()) })
        rt.registerExt(Number::class.java, "formatPercent",
            com.novalang.runtime.Function1<Any?, Any?> { n -> NumberFormat.formatPercent((n as Number).toDouble()) })
        rt.registerExt(Number::class.java, "toRoman",
            com.novalang.runtime.Function1<Any?, Any?> { n -> NumberFormat.toRoman((n as Number).toInt()) })

        // ── Location 扩展 ───────────────────────
        // loc.inCuboid(cuboid) → boolean
        rt.registerExt(Location::class.java, "inCuboid",
            com.novalang.runtime.Function2<Any?, Any?, Any?> { loc, region ->
                (region as Cuboid).contains(loc as Location)
            })
        rt.registerExt(Location::class.java, "inSphere",
            com.novalang.runtime.Function2<Any?, Any?, Any?> { loc, region ->
                (region as Sphere).contains(loc as Location)
            })
    }
}

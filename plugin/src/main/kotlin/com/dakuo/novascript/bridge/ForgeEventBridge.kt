package com.dakuo.novascript.bridge

import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.util.function.Consumer

/**
 * Forge/NeoForge EventBus 通用桥接器。
 *
 * 提供两层能力：
 *   1. 底层 API —— 供其他插件（如 VxCore）在 Kotlin/Java 中使用
 *   2. 脚本 API —— 由 ScriptApi 注入 forge.on()，供 .nova 脚本使用
 *
 * 脚本用法：
 *   forge.on("com.pixelmonmod.pixelmon.api.events.CaptureEvent$SuccessfulCapture", fun(event) {
 *       var pokemon = event.getPokemon()
 *       broadcast(pokemon.getSpecies().getName() + " 被捕获了!")
 *   })
 *
 * 脚本卸载时所有 Forge 监听器自动停用。
 */
object ForgeEventBridge {

    private var eventBus: Any? = null
    private var addListenerMethod: java.lang.reflect.Method? = null
    private var priorityNormal: Any? = null
    private var busType: String = "unknown"

    val isAvailable get() = eventBus != null

    @Awake(LifeCycle.ENABLE)
    fun init() {
        if (eventBus != null) return
        eventBus = tryField("net.minecraftforge.common.MinecraftForge", "EVENT_BUS")
            ?: tryField("net.neoforged.neoforge.common.NeoForge", "EVENT_BUS")
        if (eventBus == null) return

        busType = if (eventBus!!.javaClass.name.contains("neoforge")) "NeoForge" else "Forge"

        try {
            val priorityClass = tryClass("net.minecraftforge.eventbus.api.EventPriority")
                ?: tryClass("net.neoforged.bus.api.EventPriority")
                ?: return
            priorityNormal = priorityClass.getField("NORMAL").get(null)
            addListenerMethod = eventBus!!.javaClass.getMethod(
                "addListener", priorityClass, Boolean::class.javaPrimitiveType,
                Class::class.java, Consumer::class.java
            )
        } catch (e: Exception) {
            warning("[ForgeEventBridge] Failed to resolve addListener: ${e.message}")
            eventBus = null
            return
        }

        info("[ForgeEventBridge] $busType EventBus detected")
    }

    fun getBusType(): String = busType

    // ---- 事件注册 ----

    /**
     * 注册 Forge 事件监听器，返回可停用的 handle。
     *
     * @param eventClassName 完整类名（内部类用 $）
     * @param handler 事件回调
     * @return ListenerHandle（用于停用），注册失败返回 null
     */
    fun listen(eventClassName: String, handler: (Any) -> Unit): ListenerHandle? {
        val bus = eventBus ?: return null
        return try {
            val eventClass = Class.forName(eventClassName)
            val handle = ListenerHandle()
            addListenerMethod!!.invoke(bus, priorityNormal, false, eventClass,
                Consumer<Any> { event ->
                    if (!handle.active) return@Consumer
                    try { handler(event) }
                    catch (e: Exception) { warning("[ForgeEventBridge] ${eventClass.simpleName}: ${e.message}") }
                })
            handle
        } catch (e: Exception) {
            warning("[ForgeEventBridge] Failed to listen $eventClassName: ${e.message}")
            null
        }
    }

    // ---- MethodHandle 工具 ----

    private val lookup = MethodHandles.publicLookup()

    /** 解析无参方法为 MethodHandle */
    fun method(className: String, methodName: String): MethodHandle? {
        return try {
            lookup.unreflect(Class.forName(className).getMethod(methodName))
        } catch (e: Exception) {
            warning("[ForgeEventBridge] Cannot resolve $className.$methodName(): ${e.message}")
            null
        }
    }

    /** 解析 public 字段为 getter MethodHandle */
    fun field(className: String, fieldName: String): MethodHandle? {
        return try {
            lookup.unreflectGetter(Class.forName(className).getField(fieldName))
        } catch (e: Exception) {
            warning("[ForgeEventBridge] Cannot resolve $className.$fieldName: ${e.message}")
            null
        }
    }

    /** 类是否可用 */
    fun classExists(name: String): Boolean {
        return try { Class.forName(name); true } catch (_: ClassNotFoundException) { false }
    }

    // ---- 内部 ----

    private fun tryField(cls: String, field: String): Any? {
        return try { Class.forName(cls).getField(field).get(null) } catch (_: Exception) { null }
    }

    private fun tryClass(name: String): Class<*>? {
        return try { Class.forName(name) } catch (_: Exception) { null }
    }

    /**
     * 监听器句柄。脚本卸载时调用 deactivate() 使回调变为空操作。
     * （Forge EventBus 不支持 removeListener，用停用标志替代。）
     */
    class ListenerHandle {
        @Volatile
        var active = true
            private set

        fun deactivate() {
            active = false
        }
    }
}

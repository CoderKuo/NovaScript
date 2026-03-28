package com.dakuo.novascript.bridge

import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning
import java.lang.reflect.Proxy

/**
 * Fabric API 事件桥接器。
 *
 * Fabric 事件模型：每个事件是独立的静态字段 Event<T>，
 * T 是该事件专属的回调接口（参数数量/类型各不相同）。
 *
 * 本桥接器通过 java.lang.reflect.Proxy 动态实现任意回调接口，
 * 将所有参数打包后传给脚本回调。
 *
 * 脚本用法：
 *   fabric.on("net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents",
 *             "JOIN", fun(handler, sender, server) {
 *       log("Player joined!")
 *   })
 *
 *   fabric.on("net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents",
 *             "ALLOW_DAMAGE", fun(entity, source, amount) {
 *       log(entity.getName().getString() + " took " + amount + " damage")
 *       return true  // 允许伤害
 *   })
 */
object FabricEventBridge {

    private var fabricDetected = false

    val isAvailable get() = fabricDetected

    @Awake(LifeCycle.ENABLE)
    fun init() {
        fabricDetected = try {
            Class.forName("net.fabricmc.fabric.api.event.Event")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
        if (fabricDetected) {
            info("[FabricEventBridge] Fabric API detected")
        }
    }

    /**
     * 注册 Fabric 事件监听器。
     *
     * @param className 持有事件字段的类全名（如 "net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents"）
     * @param fieldName 事件静态字段名（如 "JOIN"）
     * @param handler 脚本回调，接收事件回调接口的所有参数
     * @return ListenerHandle，脚本卸载时停用
     */
    fun listen(className: String, fieldName: String, handler: (Array<Any?>) -> Any?): ListenerHandle? {
        if (!fabricDetected) return null
        return try {
            val cls = Class.forName(className)
            val eventHolder = cls.getField(fieldName).get(null)
                ?: throw IllegalStateException("Event field $className.$fieldName is null")

            // 找到 register(T) 方法，T 是回调接口
            val registerMethod = eventHolder.javaClass.methods.firstOrNull {
                it.name == "register" && it.parameterCount == 1
            } ?: throw IllegalStateException("No register(T) method found on ${eventHolder.javaClass.name}")

            val callbackInterface = registerMethod.parameterTypes[0]
            val handle = ListenerHandle()

            // 动态代理实现回调接口
            val proxy = Proxy.newProxyInstance(
                callbackInterface.classLoader,
                arrayOf(callbackInterface)
            ) { _, method, args ->
                // 跳过 Object 方法（toString/hashCode/equals）
                if (method.declaringClass == Object::class.java) {
                    return@newProxyInstance when (method.name) {
                        "toString" -> "NovaScript FabricEventProxy[$className.$fieldName]"
                        "hashCode" -> System.identityHashCode(handle)
                        "equals" -> false
                        else -> null
                    }
                }
                if (!handle.active) {
                    return@newProxyInstance defaultReturnValue(method.returnType)
                }
                try {
                    handler(args ?: emptyArray())
                } catch (e: Exception) {
                    warning("[FabricEventBridge] $className.$fieldName: ${e.message}")
                    defaultReturnValue(method.returnType)
                }
            }

            registerMethod.invoke(eventHolder, proxy)
            handle
        } catch (e: Exception) {
            warning("[FabricEventBridge] Failed to listen $className.$fieldName: ${e.message}")
            null
        }
    }

    /** 类是否可用 */
    fun classExists(name: String): Boolean {
        return try { Class.forName(name); true } catch (_: ClassNotFoundException) { false }
    }

    /**
     * 回调接口方法可能有返回值（如 ALLOW_DAMAGE 返回 boolean）。
     * 监听器停用后需要返回安全默认值。
     */
    private fun defaultReturnValue(type: Class<*>): Any? {
        return when (type) {
            Boolean::class.javaPrimitiveType, java.lang.Boolean::class.java -> true
            Int::class.javaPrimitiveType, java.lang.Integer::class.java -> 0
            Float::class.javaPrimitiveType, java.lang.Float::class.java -> 0f
            Double::class.javaPrimitiveType, java.lang.Double::class.java -> 0.0
            Long::class.javaPrimitiveType, java.lang.Long::class.java -> 0L
            Void.TYPE -> null
            else -> null
        }
    }

    /** 监听器句柄，脚本卸载时停用 */
    class ListenerHandle {
        @Volatile
        var active = true
            private set

        fun deactivate() {
            active = false
        }
    }
}

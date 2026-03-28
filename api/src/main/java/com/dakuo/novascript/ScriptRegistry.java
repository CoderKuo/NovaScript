package com.dakuo.novascript;

/**
 * 命名脚本注册表，用于批量编译、存储和调用预编译的 NovaLang 脚本。
 * <p>
 * 比 {@link NovaCompiled} 更高层 — 命名管理 + 自动清理。
 * 比 {@link NovaScriptAPI#register} 更轻 — 不走完整脚本生命周期。
 * <p>
 * 典型用途：扩展模块适配第三方插件。
 *
 * <pre>{@code
 * ScriptRegistry registry = NovaScriptAPI.createRegistry("mythicmobs");
 *
 * // 预编译并命名保存
 * registry.compile("fire_condition", "player.getHealth() > 10");
 * registry.compile("reward", """
 *     player.give(buildItem("DIAMOND") { b -> b.setAmount(5) })
 *     player.msg("&a获得钻石!")
 * """);
 *
 * // 调用（传入运行时变量）
 * boolean canFire = (Boolean) registry.call("fire_condition", "player", player);
 * registry.call("reward", "player", player);
 *
 * // 模块卸载时自动清理，或手动：
 * registry.close();
 * }</pre>
 */
public interface ScriptRegistry {

    /**
     * 预编译代码并以指定名称保存（字节码模式）。
     *
     * @param name 脚本名称（注册表内唯一）
     * @param code NovaLang 代码
     * @return this（支持链式调用）
     */
    ScriptRegistry compile(String name, String code);

    /**
     * 按名称调用已编译的脚本。
     *
     * @param name       脚本名称
     * @param kvBindings 运行时变量，key-value 交替：{@code "player", player, "amount", 100}
     * @return 脚本返回值
     */
    Object call(String name, Object... kvBindings);

    /**
     * 检查指定名称的脚本是否已编译。
     */
    boolean has(String name);

    /**
     * 移除指定名称的脚本。
     */
    void remove(String name);

    /**
     * 获取所有已注册的脚本名称。
     */
    java.util.List<String> names();

    /**
     * 释放全部资源（取消事件监听、命令、定时任务等）。
     * 模块卸载时由 NovaScript 自动调用。
     */
    void close();
}

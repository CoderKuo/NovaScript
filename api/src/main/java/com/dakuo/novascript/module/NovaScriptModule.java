package com.dakuo.novascript.module;

import com.dakuo.novascript.NovaScriptAPI;
import com.dakuo.novascript.ScriptRegistry;
import com.dakuo.novascript.ScriptSetup;

/**
 * NovaScript 扩展模块接口。
 * <p>
 * 实现此接口并添加 {@link NovaModule} 注解，打包为 jar 放入
 * {@code plugins/NovaScript/modules/} 目录即可自动加载。
 * <p>
 * {@link #onEnable} 中可以:
 * <ul>
 *   <li>通过 {@code globalSetup} 注入全局函数/变量（所有脚本可见）</li>
 *   <li>通过 {@code registry} 预编译并管理私有脚本</li>
 *   <li>通过 {@code api} 注册脚本、函数库等</li>
 * </ul>
 *
 * 示例:
 * <pre>{@code
 * @NovaModule(name = "MythicAdapter", version = "1.0.0")
 * public class MythicAdapter implements NovaScriptModule {
 *     @Override
 *     public void onEnable(NovaScriptAPI api, ScriptSetup globalSetup, ScriptRegistry registry) {
 *         // 注入全局函数
 *         globalSetup.defineFunction("isBoss", (ScriptHandler1) mob ->
 *             ((Entity) mob).hasMetadata("boss"));
 *
 *         // 预编译脚本（按名调用）
 *         registry.compile("fire_check", "player.getHealth() > 10");
 *         registry.compile("reward", "player.give(buildItem(\"DIAMOND\") { b -> b.setAmount(5) })");
 *
 *         // 目标插件触发时调用
 *         MythicMobs.registerCondition("nova_fire", (caster) ->
 *             (Boolean) registry.call("fire_check", "player", caster));
 *     }
 * }
 * }</pre>
 */
public interface NovaScriptModule {

    /**
     * 模块加载时调用。
     *
     * @param api         NovaScript 公共 API，可注册脚本、函数库等
     * @param globalSetup 全局函数/变量注入器，注入的内容所有脚本自动可见
     * @param registry    脚本注册表，预编译并管理模块私有的脚本，模块卸载时自动清理
     */
    void onEnable(NovaScriptAPI api, ScriptSetup globalSetup, ScriptRegistry registry);

    /**
     * 模块卸载时调用（可选）。
     * 默认空实现，有需要清理的资源时覆盖。
     * registry 会由 NovaScript 自动清理，无需手动 close。
     */
    default void onDisable() {}
}

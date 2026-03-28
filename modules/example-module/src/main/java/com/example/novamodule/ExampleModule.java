package com.example.novamodule;

import com.dakuo.novascript.*;
import com.dakuo.novascript.module.*;

/**
 * NovaScript 示例扩展模块。
 * 展示如何注入全局函数和使用 ScriptRegistry 管理预编译脚本。
 */
@NovaModule(name = "ExampleModule", version = "1.0.0", description = "示例扩展模块")
public class ExampleModule implements NovaScriptModule {

    @Override
    public void onEnable(NovaScriptAPI api, ScriptSetup globalSetup, ScriptRegistry registry) {
        // 注入全局函数（所有脚本可用）
        globalSetup.defineFunction("hello", name -> "Hello, " + name + "!");
        globalSetup.set("EXAMPLE_VERSION", "1.0.0");



        // 预编译脚本（按名调用）
        registry.compile("greeting", "\"Welcome, \" + name + \"!\"");
    }

    @Override
    public void onDisable() {
        // registry 会由 NovaScript 自动清理
    }
}

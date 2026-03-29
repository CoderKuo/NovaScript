# 扩展模块开发

将自定义扩展 jar 放入 `plugins/NovaScript/modules/` 目录，使用独立 ClassLoader 加载。

## 开发扩展模块

```java
package com.example;

import com.dakuo.novascript.*;
import com.dakuo.novascript.module.*;

@NovaModule(name = "MyModule", version = "1.0.0", description = "自定义扩展")
public class MyModule implements NovaScriptModule {

    @Override
    public void onEnable(NovaScriptAPI api, ScriptSetup globalSetup, ScriptRegistry registry) {
        // 注入全局函数（所有脚本可用）
        globalSetup.defineFunction("hello", (ScriptHandler1) name -> "Hello, " + name + "!");
        globalSetup.set("MY_CONSTANT", 42);

        // 预编译私有脚本
        registry.compile("check", "player.hp() > 10");

        // 目标插件触发时调用
        SomePlugin.onEvent(player -> {
            if ((Boolean) registry.call("check", "player", player)) {
                registry.call("reward", "player", player);
            }
        });
    }

    @Override
    public void onDisable() {
        // registry 由 NovaScript 自动清理
    }
}
```

## 构建配置

```kotlin
// build.gradle.kts
plugins {
    java
}

repositories {
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("com.github.CoderKuo.NovaScript:api:v1.0.2")
    // compileOnly 目标插件 API...
}
```

打包为 jar 放入 `modules/`，重启即可。`/nova modules` 查看已加载模块。

## onEnable 参数

| 参数 | 说明 |
|------|------|
| `api` | NovaScriptAPI 实例，可注册脚本、函数库等 |
| `globalSetup` | 全局函数/变量注入器，注入的内容所有脚本自动可见 |
| `registry` | 脚本注册表，预编译并管理模块私有脚本，模块卸载时自动清理 |

## ClassLoader 隔离

- 每个模块一个独立 `URLClassLoader`
- parent = NovaScript 的 ClassLoader → 可访问 NovaScriptAPI + Bukkit API + 其他已加载插件
- 模块间互相隔离
- 卸载时 ClassLoader 自动关闭
- 对服务端已有的库（如其他插件 API）使用 `compileOnly`，不要 `implementation`

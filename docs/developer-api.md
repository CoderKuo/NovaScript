# 开发者 API

第三方 Bukkit 插件和扩展模块通过 `NovaScriptAPI` 单例集成 NovaScript。

## 添加依赖

```kotlin
// Gradle
compileOnly("com.dakuo.novascript:novascript-api:1.0.0-SNAPSHOT")

// 或本地文件
compileOnly(files("libs/novascript-api.jar"))
```

## 注册脚本

```kotlin
// 从文件注册
NovaScriptAPI.register(name, "rewards", File(dataFolder, "rewards.nova")) { setup ->
    setup.set("plugin", this)
    setup.defineFunction("getVipLevel") { player -> getVipLevel(player as Player) }
}

// 从代码注册
NovaScriptAPI.register(name, "rules", codeString, dataFolder)

// 指定运行模式
NovaScriptAPI.register(name, "debug", file, mode = RunMode.INTERPRETED)
```

## 调用脚本函数

```kotlin
val result = NovaScriptAPI.callFunction(name, "rewards", "calculateReward", player, 100)

// 通过命名空间调用（跨插件）
val result = NovaScriptAPI.call("otherPlugin:scriptName", "funcName", arg1, arg2)
```

## 全局函数库

```kotlin
NovaScriptAPI.defineLibrary(name, "rewards") { lib ->
    lib.set("VIP_BONUS", 1.5)
    lib.defineFunction("calculate") { player, amount ->
        calculateReward(player as Player, (amount as Number).toDouble())
    }
}
// 脚本中: rewards.calculate(player, 100)
```

## 编译 API

```kotlin
val compiled = NovaScriptAPI.compileToBytecode("fun greet(name) = \"Hello, \" + name")
compiled.run()
val msg = compiled.call("greet", "World")
compiled.close()
```

## 脚本注册表（ScriptRegistry）

批量编译、管理和调用预编译脚本。

```kotlin
val registry = NovaScriptAPI.createRegistry("myFeature")
registry.compile("check", "player.hp() > 10")
registry.compile("reward", "player.give(buildItem(\"DIAMOND\") { b -> b.setAmount(5) })")

val canDo = registry.call("check", "player", player) as Boolean
registry.call("reward", "player", player)

registry.close() // 释放资源
```

## 完整 API 列表

| 方法 | 说明 |
|------|------|
| `register(pluginName, name, file, ...)` | 从文件注册脚本 |
| `register(pluginName, name, code, ...)` | 从代码注册脚本 |
| `unregister(pluginName, name)` | 卸载脚本 |
| `unregisterAll(pluginName)` | 卸载所有脚本 |
| `reload(pluginName, name)` | 重载脚本 |
| `callFunction(pluginName, scriptName, functionName, ...args)` | 调用脚本函数 |
| `call(namespace, functionName, ...args)` | 通过命名空间调用 |
| `eval(code)` | 一次性执行代码片段 |
| `compile(code, [setup])` | 预编译 AST 模式 |
| `compileToBytecode(code, [setup])` | 预编译字节码模式 |
| `createRegistry(name)` | 创建脚本注册表 |
| `hasFunction(pluginName, name, functionName)` | 检查函数是否存在 |
| `isCallback(value)` | 检查值是否可调用 |
| `invokeCallback(pluginName, scriptName, callback, ...args)` | 调用回调 |
| `isLoaded(pluginName, name)` | 检查脚本是否加载 |
| `getScripts(pluginName)` | 获取脚本名列表 |
| `defineLibrary(pluginName, name, setup)` | 注册全局函数库 |
| `removeLibrary(pluginName, name)` | 注销函数库 |
| `getLibraries(pluginName)` | 获取函数库名列表 |

插件卸载时自动清理所有脚本和函数库。

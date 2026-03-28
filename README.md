<p align="center">
  <img src="docs/nova-banner.svg" alt="NovaLang" width="600" />
</p>

# NovaScript

基于 [NovaLang](https://github.com/CoderKuo/NovaLang) 脚本语言的 Minecraft 服务端脚本插件。用简洁的脚本快速开发服务器功能，同时提供开发者 API 供第三方插件集成。

## 特性

- **NovaLang 脚本** — 现代语法，支持 lambda、when 表达式、管道操作符、字符串插值等
- **字节码编译** — 脚本预编译为 JVM 字节码执行，接近原生性能
- **热重载** — 运行时加载/卸载/重载脚本，无需重启服务器
- **完整 API** — 事件监听、命令注册、GUI 菜单、经济系统、数据库等
- **扩展函数** — `player.msg("&a你好")`、`player.hp()`、`"&6文本".color()` 等链式调用
- **配置式功能** — 占位符、事件、定时任务、命令、物品交互、方块交互均可通过 YAML 配置
- **开发者 API** — 第三方插件可注册脚本、注入自定义函数、定义全局函数库
- **模块扩展** — modules/ 目录加载扩展 jar，独立 ClassLoader 隔离
- **内置工具库** — 冷却、随机、时间解析、数字格式化、区域检测、物品匹配等

## 安装

1. 将 `NovaScript.jar` 放入服务器的 `plugins/` 目录
2. 启动服务器，插件自动生成配置和示例脚本
3. 在 `plugins/NovaScript/scripts/` 下编写 `.nova` 脚本

## 快速开始

```javascript
on("PlayerJoinEvent") { event ->
    val player = event.getPlayer()
    broadcast("&a欢迎 &e${player.getName()} &a加入服务器!")
    player.title("&6欢迎回来", "&e祝你游戏愉快")
}
```

保存为 `scripts/hello.nova`，执行 `/nova load hello` 即可生效。

## 文档索引

| 文档 | 内容 |
|------|------|
| [脚本 API 参考](docs/script-api.md) | 事件监听、命令注册、工具函数、物品构建、GUI 菜单、配置文件、数据库、PlaceholderAPI、全局常量 |
| [扩展函数参考](docs/extensions.md) | String/Player/ItemStack/Number/Location 扩展函数（含中文别名） |
| [内置工具库](docs/utilities.md) | 冷却管理、时间解析、随机工具、数字格式化、区域检测、物品匹配、倒计时 |
| [配置式功能](docs/config-features.md) | 占位符、事件、定时任务、命令、物品脚本、方块脚本（YAML 配置） |
| [开发者 API](docs/developer-api.md) | NovaScriptAPI 完整接口、脚本注册、函数调用、编译 API、ScriptRegistry |
| [扩展模块开发](docs/modules.md) | @NovaModule 注解、NovaScriptModule 接口、ScriptRegistry、ClassLoader 隔离 |

## 管理命令

所有命令需要 `novascript.admin` 权限。

| 命令 | 说明 |
|------|------|
| `/nova load <脚本名>` | 加载指定脚本 |
| `/nova unload <脚本名>` | 卸载指定脚本 |
| `/nova reload [脚本名]` | 重载脚本，不指定则重载全部 |
| `/nova list` | 列出所有已加载的脚本 |
| `/nova eval <代码>` | 直接执行代码片段 |
| `/nova eval <脚本名> <代码>` | 在指定脚本的上下文中执行调试代码 |
| `/nova papi` | 重载配置式占位符 |
| `/nova events` | 重载配置式事件 |
| `/nova tasks` | 重载配置式定时任务 |
| `/nova commands` | 重载配置式命令 |
| `/nova items` | 重载配置式物品脚本 |
| `/nova blocks` | 方块脚本管理（list/reload/look/here/at/bind/unbind） |
| `/nova modules` | 列出已加载的扩展模块 |

## 目录结构

```
plugins/NovaScript/
├── placeholders.yml              # 配置式占位符
├── events.yml                    # 配置式事件
├── tasks.yml                     # 配置式定时任务
├── scripts/                      # NovaLang 脚本
│   ├── welcome.nova
│   └── rpg/
│       └── main.nova
├── commands/                     # 配置式命令
├── items/                        # 配置式物品脚本
├── blocks/                       # 配置式方块脚本
└── modules/                      # 扩展模块（jar）
```

## 构建

```bash
# 构建所有模块
./gradlew build

# 发布 API 到 mavenLocal
./gradlew :api:publishToMavenLocal
```

项目结构：`api/`（公共 API）+ `plugin/`（主插件）+ `modules/`（适配模块）

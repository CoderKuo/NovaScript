package com.dakuo.novascript

/**
 * 编译后的 NovaLang 脚本，可多次执行、调用函数、读写变量。
 * 第三方插件无需直接依赖 nova-runtime。
 *
 * 用法：
 * ```kotlin
 * val compiled = NovaScriptAPI.compile("fun add(a, b) = a + b")
 * compiled.run()
 * val result = compiled.call("add", 1, 2)  // 3
 * ```
 */
interface NovaCompiled {
    /** 释放资源（取消事件监听、命令、定时任务等） */
    fun close()
    /** 执行编译后的代码，返回最后一个表达式的值 */
    fun run(): Any?
    /** 先绑定变量，再执行。kvBindings 格式: "name1", value1, "name2", value2, ... */
    fun run(vararg kvBindings: Any?): Any?
    /** 调用脚本中定义的函数 */
    fun call(funcName: String, vararg args: Any?): Any?
    /** 检查函数是否存在 */
    fun hasFunction(funcName: String): Boolean
    /** 设置变量（支持链式调用） */
    fun set(name: String, value: Any?): NovaCompiled
    /** 获取变量值 */
    fun get(name: String): Any?
    /** 获取所有绑定变量 */
    fun getBindings(): Map<String, Any?>
}

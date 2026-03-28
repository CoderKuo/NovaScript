package com.dakuo.novascript

/**
 * 脚本运行模式。
 */
enum class RunMode {
    /** 编译为 JVM 字节码执行（最高性能，默认） */
    BYTECODE,
    /** 预编译 AST + 解释执行（解析一次，可多次运行） */
    COMPILED,
    /** 直接解释执行（最简单，适合调试） */
    INTERPRETED
}

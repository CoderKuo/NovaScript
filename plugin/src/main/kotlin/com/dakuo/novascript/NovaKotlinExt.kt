@file:Suppress("NOTHING_TO_INLINE")

package com.dakuo.novascript

import com.novalang.runtime.Nova
import com.novalang.runtime.Function0 as NvFunc0
import com.novalang.runtime.Function1 as NvFunc1
import com.novalang.runtime.Function2 as NvFunc2
import com.novalang.runtime.Function3 as NvFunc3
import com.novalang.runtime.Function4 as NvFunc4
import com.novalang.runtime.Function5 as NvFunc5

// ════════════════════════════════════════════════════
//  Nova Kotlin 语法糖 — defineFunction / registerExtension
//  消除 FunctionN<Any, Any> 显式类型声明
// ════════════════════════════════════════════════════

// ── defineFunction（类型安全，不指定泛型时默认 Any） ──

inline fun Nova.define(name: String, crossinline fn0: () -> Any?): Nova =
    defineFunction(name, NvFunc0 { fn0() })

inline fun <reified A> Nova.define(name: String, crossinline fn: (A) -> Any?): Nova =
    defineFunction(name, NvFunc1 { a -> fn(a as A) })

inline fun <reified A, reified B> Nova.define(name: String, crossinline fn: (A, B) -> Any?): Nova =
    defineFunction(name, NvFunc2 { a, b -> fn(a as A, b as B) })

inline fun <reified A, reified B, reified C> Nova.define(name: String, crossinline fn: (A, B, C) -> Any?): Nova =
    defineFunction(name, NvFunc3 { a, b, c -> fn(a as A, b as B, c as C) })

inline fun <reified A, reified B, reified C, reified D> Nova.define(name: String, crossinline fn: (A, B, C, D) -> Any?): Nova =
    defineFunction(name, NvFunc4 { a, b, c, d -> fn(a as A, b as B, c as C, d as D) })

inline fun <reified A, reified B, reified C, reified D, reified E> Nova.define(name: String, crossinline fn: (A, B, C, D, E) -> Any?): Nova =
    defineFunction(name, NvFunc5 { a, b, c, d, e -> fn(a as A, b as B, c as C, d as D, e as E) })

// ── registerExtension（类型安全泛型版） ──────────

inline fun <reified R> Nova.ext(type: Class<*>, name: String, crossinline fn: (R) -> Any?): Nova =
    registerExtension(type, name, NvFunc1 { a -> fn(a as R) })

inline fun <reified R, reified A> Nova.ext(type: Class<*>, name: String, crossinline fn: (R, A) -> Any?): Nova =
    registerExtension(type, name, NvFunc2 { a, b -> fn(a as R, b as A) })

inline fun <reified R, reified A, reified B> Nova.ext(type: Class<*>, name: String, crossinline fn: (R, A, B) -> Any?): Nova =
    registerExtension(type, name, NvFunc3 { a, b, c -> fn(a as R, b as A, c as B) })

inline fun <reified R, reified A, reified B, reified C> Nova.ext(type: Class<*>, name: String, crossinline fn: (R, A, B, C) -> Any?): Nova =
    registerExtension(type, name, NvFunc4 { a, b, c, d -> fn(a as R, b as A, c as B, d as C) })

// ── registerExtension（类型安全 + 带别名） ───────

inline fun <reified R> Nova.ext(type: Class<*>, vararg names: String, crossinline fn: (R) -> Any?): Nova {
    val f = NvFunc1 { a: Any -> fn(a as R) }
    names.forEach { registerExtension(type, it, f) }
    return this
}

inline fun <reified R, reified A> Nova.ext(type: Class<*>, vararg names: String, crossinline fn: (R, A) -> Any?): Nova {
    val f = NvFunc2 { a: Any, b: Any -> fn(a as R, b as A) }
    names.forEach { registerExtension(type, it, f) }
    return this
}

inline fun <reified R, reified A, reified B> Nova.ext(type: Class<*>, vararg names: String, crossinline fn: (R, A, B) -> Any?): Nova {
    val f = NvFunc3 { a: Any, b: Any, c: Any -> fn(a as R, b as A, c as B) }
    names.forEach { registerExtension(type, it, f) }
    return this
}

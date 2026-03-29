package com.dakuo.novascript

import taboolib.common.platform.function.info
import java.net.HttpURLConnection
import java.net.URL

/**
 * 异步更新检查器。
 * 从 GitHub raw 文件读取最新版本号，支持多源 fallback。
 */
object UpdateChecker {

    private const val REPO = "CoderKuo/NovaScript"
    private const val FILE = "gradle.properties"
    private const val BRANCH = "main"

    private const val RAW_URL = "https://raw.githubusercontent.com/$REPO/$BRANCH/$FILE"

    /** 加速代理前缀，直连失败时依次尝试 */
    private val PROXIES = listOf(
        "",                          // 直连
        "https://ghfast.top/",
        "https://gh.xxooo.cf/",
        "https://gh-proxy.org/"
    )

    private val SOURCES = PROXIES.map { it + RAW_URL }

    fun checkAsync(currentVersion: String) {
        Thread({
            try {
                val latest = fetchLatestVersion() ?: return@Thread
                if (latest != currentVersion && isNewer(latest, currentVersion)) {
                    info("")
                    info("§8╔══════════════════════════════════════════╗")
                    info("§8║  §e⬆ NovaScript §a有新版本可用!              §8║")
                    info("§8║                                          §8║")
                    info("§8║  §7当前版本: §f$currentVersion                        §8║")
                    info("§8║  §7最新版本: §b$latest                        §8║")
                    info("§8║                                          §8║")
                    info("§8║  §7下载: §fhttps://github.com/$REPO §8║")
                    info("§8╚══════════════════════════════════════════╝")
                    info("")
                } else if (latest == currentVersion) {
                    info("[NovaScript] 当前已是最新版本 (v$currentVersion)")
                }
            } catch (_: Exception) {
                // 静默失败，不影响插件运行
            }
        }, "NovaScript-UpdateChecker").apply { isDaemon = true }.start()
    }

    private fun fetchLatestVersion(): String? {
        for (url in SOURCES) {
            try {
                val content = httpGet(url, 5000) ?: continue
                val version = content.lines()
                    .firstOrNull { it.startsWith("version=") }
                    ?.substringAfter("version=")
                    ?.trim()
                if (!version.isNullOrBlank()) return version
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }

    private fun httpGet(url: String, timeoutMs: Int): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.setRequestProperty("User-Agent", "NovaScript-UpdateChecker")
        return try {
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().readText()
            } else null
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 比较语义化版本号：a > b 返回 true
     */
    private fun isNewer(a: String, b: String): Boolean {
        val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
        val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
        val len = maxOf(pa.size, pb.size)
        for (i in 0 until len) {
            val va = pa.getOrElse(i) { 0 }
            val vb = pb.getOrElse(i) { 0 }
            if (va > vb) return true
            if (va < vb) return false
        }
        return false
    }
}

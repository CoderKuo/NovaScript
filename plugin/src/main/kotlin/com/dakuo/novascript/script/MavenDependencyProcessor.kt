package com.dakuo.novascript.script

import com.novalang.runtime.Nova
import taboolib.common.PrimitiveIO
import taboolib.common.env.DependencyScope
import taboolib.common.env.legacy.Artifact
import taboolib.common.env.legacy.Dependency
import taboolib.common.env.legacy.DependencyDownloader
import taboolib.common.env.legacy.Repository
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.util.concurrent.ConcurrentHashMap

/**
 * 脚本 Maven 依赖处理器。
 *
 * 解析脚本中的 @file:DependsOn / @file:Repository 注解，
 * 通过 TabooLib 的 DependencyDownloader 下载 Maven 依赖，
 * 创建独立 URLClassLoader 实现类隔离（不污染 plugin classloader）。
 *
 * 用法：
 * ```nova
 * @file:Repository("https://maven.aliyun.com/repository/central")
 * @file:DependsOn("com.google.code.gson:gson:2.10.1")
 * @file:DependsOn("org.apache.commons:commons-lang3:3.14.0")
 * @file:Jar("libs/custom-utils.jar")
 *
 * val gson = javaClass("com.google.gson.Gson")
 * ```
 */
object MavenDependencyProcessor {

    /** 已解析的依赖 ClassLoader 缓存：坐标集合签名 → ClassLoader */
    private val classLoaderCache = ConcurrentHashMap<String, URLClassLoader>()

    private val baseDir = File(taboolib.common.PrimitiveSettings.FILE_LIBS)

    private const val DEFAULT_REPO = "https://maven.aliyun.com/repository/central"

    /**
     * 预处理脚本源码：提取 @file:DependsOn/@file:Repository 注解，
     * 下载依赖并创建独立 ClassLoader。
     *
     * @return 脚本专属 ClassLoader，无依赖时返回 null
     */
    fun resolve(source: String): ClassLoader? {
        val annotations = Nova.extractFileAnnotations(source)
        if (annotations.isEmpty()) return null

        val repositories = mutableListOf<String>()
        val dependencies = mutableListOf<String>()
        val localJars = mutableListOf<String>()

        for (ann in annotations) {
            when (ann.name) {
                "Repository" -> {
                    val url = ann.args["value"]?.toString()
                        ?: ann.args["url"]?.toString()
                    if (url != null) repositories.add(url)
                }
                "DependsOn" -> {
                    val coord = ann.args["value"]?.toString()
                    if (coord != null) dependencies.add(coord)
                }
                "Jar" -> {
                    val path = ann.args["value"]?.toString()
                    if (path != null) localJars.add(path)
                }
            }
        }

        if (dependencies.isEmpty() && localJars.isEmpty()) return null

        if (repositories.isEmpty()) {
            repositories.add(DEFAULT_REPO)
        }

        // 缓存 key：排序后的坐标 + 仓库 + 本地 JAR 拼接
        val cacheKey = dependencies.sorted().joinToString("|") +
                "@@" + repositories.sorted().joinToString("|") +
                "@@" + localJars.sorted().joinToString("|")
        classLoaderCache[cacheKey]?.let { return it }

        // 下载所有依赖，收集 JAR 文件路径
        val jarFiles = mutableListOf<File>()

        for (coord in dependencies) {
            try {
                val artifact = Artifact(coord)
                val downloader = DependencyDownloader(baseDir, emptyList())
                for (repo in repositories) {
                    downloader.addRepository(Repository(repo))
                }
                downloader.isIgnoreOptional = true
                downloader.isTransitive = true
                downloader.dependencyScopes = listOf(DependencyScope.RUNTIME, DependencyScope.COMPILE)

                // 下载 POM + 传递依赖
                val pomPath = "${artifact.groupId.replace('.', '/')}/${artifact.artifactId}/${artifact.version}/${artifact.artifactId}-${artifact.version}.pom"
                val pomFile = File(baseDir, pomPath)
                val pomHashFile = File("${pomFile.path}.sha1")

                if (PrimitiveIO.validation(pomFile, pomHashFile)) {
                    downloader.loadDependencyFromInputStream(pomFile.toPath().toUri().toURL().openStream())
                } else {
                    info("[NovaScript] 正在下载依赖: $coord")
                    for (repo in repositories) {
                        try {
                            downloader.loadDependencyFromInputStream(URL("$repo/$pomPath").openStream())
                            break
                        } catch (_: Exception) {
                            // 尝试下一个仓库
                        }
                    }
                }

                // 加载主依赖
                val dep = Dependency(artifact.groupId, artifact.artifactId, artifact.version, DependencyScope.RUNTIME)
                dep.type = artifact.extension
                val allDeps = downloader.loadDependency(downloader.repositories, dep)

                // 收集所有 JAR 文件
                for (d in allDeps) {
                    val jar = d.findFile(baseDir, "jar")
                    if (jar.exists()) jarFiles.add(jar)
                }

                // 主依赖 JAR
                val mainJar = dep.findFile(baseDir, "jar")
                if (mainJar.exists() && mainJar !in jarFiles) {
                    jarFiles.add(mainJar)
                }

                info("[NovaScript] 依赖加载完成: $coord (${allDeps.size + 1} 个 JAR)")
            } catch (e: Throwable) {
                warning("[NovaScript] 依赖下载失败: $coord - ${e.message}")
            }
        }

        // 本地 JAR 文件
        for (path in localJars) {
            val jar = File(path)
            if (jar.exists()) {
                jarFiles.add(jar)
            } else {
                warning("[NovaScript] 本地 JAR 不存在: $path")
            }
        }

        if (jarFiles.isEmpty()) return null

        // 创建独立 URLClassLoader（parent = 当前插件 ClassLoader）
        val urls = jarFiles.map { it.toURI().toURL() }.toTypedArray()
        val classLoader = URLClassLoader(urls, MavenDependencyProcessor::class.java.classLoader)
        classLoaderCache[cacheKey] = classLoader
        return classLoader
    }
}

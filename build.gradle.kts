plugins {
    // AGP 9 内置 Kotlin 支持，无需再挂 org.jetbrains.kotlin.android
    id("com.android.application") version "9.3.2" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
}

// ── 离线环境兜底（默认关闭；离线构建加 -Papp.offline=true 启用）──────────────
// 部分 androidx 传递依赖只有「更高版本」（如
// androidx.core:core 只有 1.18.0，但 Compose 1.12.0 解析要求 1.16.0）。
// 离线时把缺失的依赖重定向到缓存中已存在的版本。
//  联网时绝不能启用：无条件跳到「缓存最高版」会把本该解析到新版的模块
// 压成缓存里的远古版本（如 androidx.compose.ui:ui 1.12.0 → 1.0.1），
// 新旧两套同名类同时进编译类路径，按包名互相遮蔽，编译随机翻车。
val offlineFallback = (findProperty("app.offline") as String?) == "true"

fun versionRank(s: String): Long {
    val parts = s.split('.', '-').mapNotNull { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }
    var rank = 0L
    for (p in parts) rank = rank * 1000 + p.coerceIn(0, 999)
    return rank
}

val gradleCache = File(
    System.getenv("GRADLE_USER_HOME")?.let { "$it/caches/modules-2" }
        ?: "${System.getProperty("user.home")}/.gradle/caches/modules-2"
)

subprojects {
    configurations.all {
        resolutionStrategy.eachDependency {
            if (!offlineFallback) return@eachDependency
            val modDir = File(gradleCache, "files-2.1/${requested.group}/${requested.name}")
            if (modDir.isDirectory) {
                // 只兜底「请求的版本缓存里没有」的情况，不碰已能命中的
                val cached = modDir.listFiles()
                    ?.filter { it.isDirectory }
                    ?.map { it.name }
                    ?: emptyList()
                if (requested.version !in cached) {
                    cached.maxByOrNull { versionRank(it) }?.let { useVersion(it) }
                }
            }
        }
    }

    // 离线环境兜底：library 模块的「注解提取」与「lint vital 分析」等任务需要
    // com.android.tools.lint:lint-gradle（AGP 9.3.2 → 32.3.2），离线时需预先解析，
    // 离线无法解析。联网时必须保留 lint，否则质量门槛长期失效。
    if (offlineFallback) {
        tasks.configureEach {
            val n = name
            val off =
                n.startsWith("lint") ||
                    n.startsWith("lintVital") ||
                    n == "extractReleaseAnnotations" ||
                    n == "extractDebugAnnotations" ||
                    n == "syncReleaseLibJars" ||
                    n == "syncDebugLibJars" ||
                    n.endsWith("ComposeMapping") ||
                    n.endsWith("ComposeMappingErrors")
            if (off) enabled = false
        }
    }
}

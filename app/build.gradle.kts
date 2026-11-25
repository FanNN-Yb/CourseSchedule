import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ── release 签名：密钥与口令只在 local.properties / 环境变量里，源码不留任何口令 ──
// 历史做法是复用 ~/.android/debug.keystore + 口令 "android"：那是 Android SDK 自动生成的
// 「人人相同」的公共密钥，任何人都能打同签名的包冒充升级包、覆盖安装并读走用户课表。
// local.properties 已被 .gitignore 忽略；四项缺任意一项 → release 不签名（仍可构建，
// 只是产出 app-release-unsigned.apk，方便别人裸 clone 编译）。
//   本地生成密钥（只做一次，之后务必备份，丢了就再也无法给老用户升级）：
//     keytool -genkeypair -v -keystore "$HOME/courseschedule-release.jks" \
//       -alias courseschedule -keyalg RSA -keysize 4096 -validity 10000
//   然后写进 local.properties（路径可绝对，相对路径以仓库根目录为基准）：
//     RELEASE_STORE_FILE=courseschedule-release.jks
//     RELEASE_STORE_PASSWORD=***
//     RELEASE_KEY_ALIAS=courseschedule
//     RELEASE_KEY_PASSWORD=***
val keyProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun secret(key: String): String? =
    keyProps.getProperty(key)?.takeIf { it.isNotBlank() }
        ?: System.getenv(key)?.takeIf { it.isNotBlank() }

val releaseStoreFile = secret("RELEASE_STORE_FILE")
val releaseStorePassword = secret("RELEASE_STORE_PASSWORD")
val releaseKeyAlias = secret("RELEASE_KEY_ALIAS")
val releaseKeyPassword = secret("RELEASE_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword,
).all { it != null }

android {
    namespace = "com.example.schedule"
    compileSdk = 37

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.example.schedule"
        minSdk = 33
        targetSdk = 37
        versionCode = 2
        versionName = "1.0.1"
    }

    buildTypes {
        // debug 也开 R8 纯缩减（不开混淆/优化）：Compose 没用到的 API 不再打进包里，
        // 42MB → 预计 10MB 上下；堆栈带行号，崩溃日志照常可读
        debug {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                "proguard-debug.pro",
            )
        }
        // release：缩减 + 混淆（保留行号）。第三方 JNI/反射库整体 keep，只混淆本 App 实现
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            // 不再把本机构建的 git revision 写进 APK（META-INF/version-control-info.textproto）
            vcsInfo.include = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

if (!hasReleaseSigning) {
    logger.warn(
        "release 未配置签名：local.properties / 环境变量里缺 RELEASE_STORE_FILE、" +
            "RELEASE_STORE_PASSWORD、RELEASE_KEY_ALIAS、RELEASE_KEY_PASSWORD 之一，" +
            "产物将为 app-release-unsigned.apk",
    )
}

dependencies {
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("org.jetbrains.compose.ui:ui:1.12.0")
    implementation("org.jetbrains.compose.foundation:foundation:1.12.0")
    implementation("org.jetbrains.compose.material3:material3-android:1.12.0-alpha03")

    testImplementation("junit:junit:4.13.2")

    // 液态玻璃 + 橡皮筋依赖
    // iOS 27 玻璃渲染引擎：Glasmio（androidx Compose 1.12.1 / material3 与现有依赖兼容）
    // glass = 材质/背景采样引擎；ui = 原生组件（按钮/开关/底部导航栏/菜单/弹窗/列表）
    implementation("io.github.neoruaa.glasmio:glasmio-glass:0.1.0")
    implementation("io.github.neoruaa.glasmio:glasmio-ui:0.1.0")
    implementation("io.github.neoruaa.glasmio:glasmio-scaffold:0.1.0")
    implementation("io.github.kyant0:shapes:1.2.1")
    implementation("io.github.kyant0:capsule:2.1.3")
    implementation("com.github.FanNN-Yb:Rubber-Band:v1")
}

// com.google.guava:listenablefuture 的 9999.0-empty-to-avoid-conflict-with-guava
// 是个空 jar（仅 META-INF）。版本冲突解析会选中它，导致
// androidx.concurrent.futures.AbstractResolvableFuture 的接口类缺失，
// profileinstaller 后台写基准配置时抛 NoClassDefFoundError 闪退。
// 强制使用含真实类的 1.0。本规则注册晚于根构建的重定向规则，最终生效。
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "com.google.guava" && requested.name == "listenablefuture") {
            useVersion("1.0")
        }
    }
}

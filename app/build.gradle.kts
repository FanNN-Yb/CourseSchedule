plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.schedule"
    compileSdk = 37

    // release 复用 debug 签名：个人分发用，且能直接覆盖安装已装的 debug 版（数据保留）
    signingConfigs {
        create("release") {
            storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
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
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
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

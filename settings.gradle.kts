pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "CourseSchedule"
include(":app")

// 直接消费 Glasmio 源码（iOS 27 液态玻璃渲染引擎），不依赖其未发布的 Maven 制品
includeBuild("../Glasmio") {
    dependencySubstitution {
        substitute(module("io.github.neoruaa.glasmio:glasmio-glass")).using(project(":glasmio-glass"))
        substitute(module("io.github.neoruaa.glasmio:glasmio-ui")).using(project(":glasmio-ui"))
        substitute(module("io.github.neoruaa.glasmio:glasmio-scaffold")).using(project(":glasmio-scaffold"))
        substitute(module("io.github.neoruaa.glasmio:glasmio-core")).using(project(":glasmio-core"))
        substitute(module("io.github.neoruaa.glasmio:glasmio-icons")).using(project(":glasmio-icons"))
    }
}

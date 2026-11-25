# ── release 混淆规则 ────────────────────────────────────────────────
# 只做「缩减 + 混淆」，显式关掉优化：AGP 已禁止 proguard-android.txt，且保持
# 优化关闭能让行为与之前完全一致（混淆本身已经把逆向成本从 0 抬起来）。
-dontoptimize
# 保留源文件名 + 行号：混淆后崩溃堆栈仍能看出是哪一行（类名会是 a/b/c）。
# 发布时请把 build/outputs/mapping/release/mapping.txt 一起归档，才能还原类名。
-keepattributes SourceFile,LineNumberTable

# 第三方库多为 JNI / 反射入口（glasmio 带 C++ 原生渲染，native 按名字回调 Java 方法），
# 一旦被重命名就会在真机上崩。整体 keep，只混淆 com.example.schedule 自己的实现。
-keep class io.github.neoruaa.** { *; }
-keep class io.github.kyant0.** { *; }
-keep class io.github.fannn_yb.** { *; }

# 备注：教务网页解析用的 jsoup 已移除（现为格式文本导入），如新增反射依赖再在此追加 keep 规则

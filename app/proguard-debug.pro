# ── debug 专用规则（生效顺序在 proguard-rules.pro 之后）──────────────
# debug 包要能直接看懂崩溃堆栈（类名 + 行号都在），所以不混淆、不优化，
# 只保留 R8 的缩减效果。
-dontobfuscate
-dontoptimize
-keepattributes SourceFile,LineNumberTable

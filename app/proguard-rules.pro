# 只做「代码缩减」，关闭混淆和优化 —— 保证崩溃堆栈直接可读（类名/行号都在）
-dontobfuscate
# 暂时关闭优化（AGP 已禁止 proguard-android.txt，这里显式关）保持行为最稳
-dontoptimize
-keepattributes SourceFile,LineNumberTable

# 备注：教务网页解析用的 jsoup 已移除（现为格式文本导入），如新增反射依赖再在此追加 keep 规则

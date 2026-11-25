# 课程表 (CourseSchedule)

一个简单的 Android 课程表应用：按日期/时间显示对应课程与所在周次，课程与学期设置均可调整。

## 功能

- **日期 → 第几周**：设置学期开始上课时间（第一周的第一天），自动计算任意日期属于第几周。
- **当前的周数校准**：直接设定"今天处于第几周"，自动反推开始上课时间，方便对齐单双周。
- **周视图课表**：按周切换（上一周 / 本周 / 下一周），网格显示每天各节次的课程；可设置本学期总周数（限制翻页范围与编辑器周次上限）。
- **显示选项**：是否显示周末列；是否显示非本周课程（开启后单双周课程均可见，半透明展示，点击仍可编辑）。
- **每周起始日**：周一或周日作为一周起点，课表列顺序与周次划分随之调整。
- **课程管理**：新增 / 编辑 / 删除课程，可设置课程名、教室、教师、星期、起止节次、上课周次（支持全选、清空、单周、双周快捷选择）。
- **课表节数设置**：调整每天节数（1–12 节），减少时截断、增加时用默认时间补足。
- **课表时间设置**：逐节调整起止时间（24 小时制 HH:mm，自动补零校验）。
- **外观**：跟随系统 / 浅色 / 深色。
- **本地持久化**：所有数据保存在 SharedPreferences，无需网络。
- **多课表 / 导入导出**：格式导入、分享码、JSON 备份、ICS 日历。

## 构建

模块 `app`，应用 ID `com.example.schedule`，compileSdk 37 / minSdk 33，Jetpack Compose (Material3，经 JetBrains Compose Multiplatform 依赖引入)。

**玻璃引擎：[Glasmio](https://github.com/NEORUAA/Glasmio)**（iOS 27 液态玻璃：材质、底部导航栏、菜单、弹窗组件）。通过 `settings.gradle.kts` 的 `includeBuild("../Glasmio")` 直接消费**相邻目录的源码**；单独 clone 本仓库需另行准备该库，或改为远程依赖。glasmio-glass 带 NDK/C++ 原生模块，构建需 NDK + CMake。App 独有的滚轮选择器等容器面板经由 `ui/glass/Ios27Glass.kt` 适配层调用其 `navigationGlassBackground` 材质；SF 符号图标经 `ui/glass/AppGlassIcons.kt` 映射到 App 自带矢量。

项目未附带 Gradle Wrapper，需 **Gradle ≥ 9.5.0**（AGP 9.3.2 要求）：

```bash
gradle :app:assembleDebug     # 调试版
gradle :app:assembleRelease   # 发布版
```

首次构建需联网解析插件与依赖；产物位于 `app/build/outputs/apk/<buildType>/`。

**发布签名**：release 不再复用 debug 密钥（那是人人相同的公共密钥，别人能打同签名包冒充升级）。
先在 `local.properties`（已 gitignore）里配置，或用同名环境变量：

```
RELEASE_STORE_FILE=courseschedule-release.jks   # 相对路径以仓库根目录为基准
RELEASE_STORE_PASSWORD=***
RELEASE_KEY_ALIAS=courseschedule
RELEASE_KEY_PASSWORD=***
```

四项缺任意一项时 `assembleRelease` 产出 `app-release-unsigned.apk`（不影响构建）。
换签名后**老用户无法覆盖安装**（签名不一致），发版说明里要写清"先导出备份 → 卸载 → 重装"。
release 开启混淆（第三方 JNI/反射库整体 keep、保留行号），请归档 `mapping.txt`
（`app/build/outputs/mapping/release/mapping.txt`）用于还原崩溃堆栈。

## 数据与导入说明

- **格式导入 / 备份导入**：会作为**新课表**追加；格式导入会沿用当前学期配置（开学日、作息、周起始日等）。
- **分享码**：携带课表自身的学期配置。注意它只是 **deflate + Base64 编码，没有加密** ——
  拿到分享码的人都能解出课程名 / 教室 / 教师名，别贴到公开群或公开仓库。
- **分享码深链**（`schedule://import?data=…`）：点开会把码预填进导入弹窗，**必须用户点「导入」才写入**，
  网页 / 其它 App 无法静默往课表里塞数据。
- **不参与系统备份**：`allowBackup="false"`，课表不会进云备份、也拉不出 `adb backup`；换机请用导出备份 JSON。
- 解析失败的原始数据会备份到 SharedPreferences key `tables_corrupt_backup`（截断保存，最多 256K 字符），
  不会被静默覆盖为可恢复的正常表数据（需自行导出该 prefs 恢复）。


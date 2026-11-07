package com.example.schedule

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

/**
 * 一张课表（= 一个学期）：独立的学期配置 + 课程列表。
 * 多张课表即「多学期 / 历史课表」：当前使用的一张可编辑，其余归档随时切回。
 */
data class ScheduleTable(
    val id: String,
    val name: String,
    val config: SemesterConfig = SemesterConfig(defaultStartDate(), defaultPeriods()),
    val courses: List<Course> = emptyList(),
    val archived: Boolean = false,
)

/**
 * 本地持久化（SharedPreferences + JSONObject，无需额外依赖）。
 *
 * 数据模型：多张 [ScheduleTable]，[activeTableId] 指向当前使用的课表。
 * 对外暴露的 [config] / [courses] 永远是当前课表的 —— 既有页面代码零改动。
 * 旧版本（单课表 config/courses 两个 key）首次 load 时自动迁移。
 */
class Store private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences("schedule", Context.MODE_PRIVATE)

    var tables: List<ScheduleTable> by mutableStateOf(listOf(ScheduleTable(newTableId(), "我的课表")))
        private set

    var activeTableId: String by mutableStateOf("")
        private set

    /** 主题模式（themeAuto=false 时生效）：0=浅色, 1=深色 */
    var themeMode: Int by mutableStateOf(0)
        private set

    /** 自动主题：跟随系统深浅色 */
    var themeAuto: Boolean by mutableStateOf(true)
        private set

    /** 自定义主题色开关 */
    var useCustomTheme: Boolean by mutableStateOf(false)
        private set

    /** 自定义主题色下标（ThemePresetColors） */
    var accentColorIndex: Int by mutableStateOf(0)
        private set

    // ── 当前课表的视图（可观察：读的是 tables 状态，改动自动重组）─────────────

    val activeTable: ScheduleTable
        get() = tables.firstOrNull { it.id == activeTableId } ?: tables.first()

    val config: SemesterConfig get() = activeTable.config

    val courses: List<Course> get() = activeTable.courses

    fun load() {
        // 主题迁移：旧版 themeMode 0=跟随系统 1=浅色 2=深色 → themeAuto + themeMode(0浅/1深)
        val legacyThemeMode = prefs.getInt(KEY_THEME_MODE, -1)
        themeAuto = prefs.getBoolean(KEY_THEME_AUTO, legacyThemeMode <= 0)
        themeMode = prefs.getInt(KEY_THEME_LIGHT_DARK, if (legacyThemeMode == 2) 1 else 0)
        useCustomTheme = prefs.getBoolean(KEY_USE_CUSTOM_THEME, false)
        accentColorIndex = prefs.getInt(KEY_ACCENT_INDEX, 0).coerceIn(0, ThemePresetColors.lastIndex)
        val raw = prefs.getString(KEY_TABLES, null)
        if (raw != null) {
            val parsed = parseTables(raw)
            if (parsed == null) {
                // 解析失败：先把原始 JSON 备份到独立 key 再用空课表启动。
                // 否则随后的任何一次 persist 都会把（可能可恢复的）真数据覆盖掉
                prefs.edit().putString(KEY_TABLES_CORRUPT, raw).apply()
                tables = listOf(ScheduleTable(newTableId(), "我的课表"))
                activeTableId = tables.first().id
            } else {
                tables = parsed
                activeTableId = prefs.getString(KEY_ACTIVE_TABLE, null)
                    ?.takeIf { id -> tables.any { it.id == id } }
                    ?: tables.first().id
            }
        } else {
            // 旧版本迁移：单课表 config/courses → 一张课表；全新安装 = 一张空课表
            val migrated = ScheduleTable(
                id = newTableId(),
                name = "我的课表",
                config = parseConfig(prefs.getString(KEY_CONFIG, null)),
                courses = parseCourses(prefs.getString(KEY_COURSES, null))
                    ?: mutableListOf(),
            )
            tables = listOf(migrated)
            activeTableId = migrated.id
            persist()
        }
    }

    // ── 持久化：防抖 + 后台序列化 ─────────────────────────────────
    // 连续开关 / 快速编辑时 mutate 会高频触发 persist：
    // - 主线程只做「引用快照」（tables 是 copy-on-write 的不可变列表，随手引用即一致视图）
    // - JSON 序列化 + 写盘放到单后台线程；150ms 内的连续改动合并成一次写
    private val persistHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val persistExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val persistRunnable = Runnable {
        val snapshot = tables to activeTableId
        persistExecutor.execute {
            prefs.edit()
                .putString(KEY_TABLES, tablesToJson(snapshot.first))
                .putString(KEY_ACTIVE_TABLE, snapshot.second)
                .apply()
        }
    }

    private fun persist() {
        persistHandler.removeCallbacks(persistRunnable)
        persistHandler.postDelayed(persistRunnable, 150)
    }

    private fun mutateActive(transform: (ScheduleTable) -> ScheduleTable) {
        tables = tables.map { if (it.id == activeTableId) transform(it) else it }
        persist()
    }

    // ── 课程（作用域 = 当前课表）────────────────────────────────────

    fun upsert(course: Course) = mutateActive { t ->
        val list = t.courses.toMutableList()
        val idx = list.indexOfFirst { it.id == course.id }
        if (idx >= 0) list[idx] = course else list.add(course)
        t.copy(courses = list)
    }

    fun delete(id: Long) = mutateActive { t ->
        t.copy(courses = t.courses.filterNot { it.id == id })
    }

    fun nextId(): Long = (courses.maxOfOrNull { it.id } ?: 0L) + 1

    // ── 学期配置（作用域 = 当前课表）────────────────────────────────

    fun updateConfig(newConfig: SemesterConfig) = mutateActive { t ->
        val count = newConfig.periodCount
        // 节数缩小 → 引用超界节次的课程钳到新范围内，避免卡片画到网格底线以下悬空
        val clampedCourses = if (t.courses.any { it.endPeriod > count }) {
            t.courses.map { c ->
                if (c.endPeriod <= count) c
                else c.copy(
                    startPeriod = c.startPeriod.coerceIn(1, count),
                    endPeriod = count,
                )
            }
        } else {
            t.courses
        }
        t.copy(config = newConfig, courses = clampedCourses)
    }

    fun updateThemeMode(lightDark: Int) {
        themeMode = lightDark.coerceIn(0, 1)
        persistTheme()
    }

    fun updateThemeAuto(auto: Boolean) {
        themeAuto = auto
        persistTheme()
    }

    fun updateUseCustomTheme(use: Boolean) {
        useCustomTheme = use
        persistTheme()
    }

    fun updateAccentColorIndex(index: Int) {
        accentColorIndex = index.coerceIn(0, ThemePresetColors.lastIndex)
        persistTheme()
    }

    /** 实际深色判定：自动 → 跟随系统；否则按 themeMode（0=浅 1=深）。 */
    fun isDarkTheme(systemDark: Boolean): Boolean = if (themeAuto) systemDark else themeMode == 1

    /** 当前主题色：未开启自定义时用默认蓝。 */
    fun accentColor(): Color =
        if (useCustomTheme) ThemePresetColors[accentColorIndex].color else UnifiedBlue

    private fun persistTheme() {
        prefs.edit()
            .putInt(KEY_THEME_LIGHT_DARK, themeMode)
            .putBoolean(KEY_THEME_AUTO, themeAuto)
            .putBoolean(KEY_USE_CUSTOM_THEME, useCustomTheme)
            .putInt(KEY_ACCENT_INDEX, accentColorIndex)
            .apply()
    }

    // ── 多课表管理 ──────────────────────────────────────────────────

    fun switchTable(id: String) {
        if (tables.any { it.id == id }) {
            activeTableId = id
            persist()
        }
    }

    /** 新建空白课表并切换过去。 */
    fun addTable(name: String) {
        val t = ScheduleTable(newTableId(), name.ifBlank { "新课表" })
        tables = tables + t
        activeTableId = t.id
        persist()
    }

    fun renameTable(id: String, name: String) {
        if (name.isBlank()) return
        tables = tables.map { if (it.id == id) it.copy(name = name.trim()) else it }
        persist()
    }

    fun setArchived(id: String, archived: Boolean) {
        tables = tables.map { if (it.id == id) it.copy(archived = archived) else it }
        persist()
    }

    /** 删除课表（至少保留一张）；删的是当前课表时自动切到第一张。 */
    fun deleteTable(id: String) {
        if (tables.size <= 1) return
        tables = tables.filterNot { it.id == id }
        if (activeTableId == id) activeTableId = tables.first().id
        persist()
    }

    /** 导入一张课表（分享码 / 备份恢复用）：分配新 id 并追加，可选立即启用。 */
    fun importTable(table: ScheduleTable, makeActive: Boolean): ScheduleTable {
        val imported = table.copy(id = newTableId())
        tables = tables + imported
        if (makeActive) activeTableId = imported.id
        persist()
        return imported
    }

    /** 备份导入：每张课表作为新课表追加（重新分配 id，防重名冲突），不覆盖现有数据；返回导入数量。 */
    fun importBackup(json: String): Int {
        val imported = parseBackupJson(json)
        require(imported.isNotEmpty()) { "备份文件解析失败：请确认选的是本 App 导出的备份文件" }
        tables = tables + imported.map { it.copy(id = newTableId()) }
        // 备份里带外观设置（见 exportAllJson）时一并应用
        runCatching {
            val o = JSONObject(json)
            if (o.has("themeMode")) {
                themeAuto = o.optBoolean("themeAuto", true)
                themeMode = o.optInt("themeMode", 0).coerceIn(0, 1)
                useCustomTheme = o.optBoolean("useCustomTheme", false)
                accentColorIndex = o.optInt("accentColorIndex", 0).coerceIn(0, ThemePresetColors.lastIndex)
                persistTheme()
            }
        }
        persist()
        return imported.size
    }

    /** 导出全部课表（备份 JSON，含外观设置）。 */
    fun exportAllJson(): String = JSONObject().apply {
        put("version", 2)
        put("app", "course-schedule")
        put("themeMode", themeMode)
        put("themeAuto", themeAuto)
        put("useCustomTheme", useCustomTheme)
        put("accentColorIndex", accentColorIndex)
        put("tables", tablesToJson(tables))
    }.toString()

    // ── JSON 编解码 ────────────────────────────────────────────────

    private fun tablesToJson(list: List<ScheduleTable>): String {
        val arr = JSONArray()
        list.forEach { t ->
            arr.put(JSONObject().apply {
                put("id", t.id)
                put("name", t.name)
                put("archived", t.archived)
                put("config", configToJson(t.config))
                put("courses", coursesToJson(t.courses))
            })
        }
        return arr.toString()
    }

    /** 解析失败返回 null（区别于「合法但为空」→ 兜底默认课表），调用方据此保护原数据。 */
    private fun parseTables(raw: String): List<ScheduleTable>? = try {
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            ScheduleTable(
                id = o.optString("id").ifBlank { newTableId() },
                name = o.optString("name").ifBlank { "未命名课表" },
                archived = o.optBoolean("archived", false),
                //  存的是「JSON 字符串」不是对象/数组（见 tablesToJson）：
                // 用 optJSONObject / optJSONArray 取会拿到 null → 课程与学期配置被读成空！
                config = parseConfig(o.optString("config")),
                // courses 字符串整体损坏 → 向上传播 null，触发整层 corrupt 备份
                courses = parseCourses(o.optString("courses")) ?: return null,
            )
        }.ifEmpty { listOf(ScheduleTable(newTableId(), "我的课表")) }
    } catch (e: Exception) {
        null
    }

    private fun parseBackupJson(json: String): List<ScheduleTable> = try {
        val o = JSONObject(json)
        // tablesToJson）。用 getJSONArray 取必抛异常 → 永远解析成空列表 → 导入必失败
        parseTables(o.optString("tables")) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    /** 分享码载荷：一张课表的紧凑 JSON（不带 id，导入时重新分配）。 */
    fun tableSharePayload(table: ScheduleTable): String = JSONObject().apply {
        put("name", table.name)
        put("config", configToJson(table.config))
        put("courses", coursesToJson(table.courses))
    }.toString()

    fun parseTablePayload(json: String): ScheduleTable? = try {
        val o = JSONObject(json)
        ScheduleTable(
            id = newTableId(),
            name = o.optString("name").ifBlank { "导入的课表" },
            // 同上：tableSharePayload 存的也是字符串，必须按字符串读
            config = parseConfig(o.optString("config")),
            courses = parseCourses(o.optString("courses")) ?: mutableListOf(),
        )
    } catch (e: Exception) {
        null
    }

    private fun configToJson(c: SemesterConfig): String {
        val arr = JSONArray()
        c.periods.forEach { p ->
            arr.put(JSONObject().apply {
                put("period", p.period)
                put("start", p.start)
                put("end", p.end)
            })
        }
        return JSONObject().apply {
            put("startDate", c.startDate.toString())
            put("periods", arr)
            put("totalWeeks", c.totalWeeks)
            put("showWeekend", c.showWeekend)
            put("showOtherWeekCourses", c.showOtherWeekCourses)
            put("weekStartDay", c.weekStartDay)
        }.toString()
    }

    private fun parseConfig(raw: String?): SemesterConfig {
        if (raw.isNullOrBlank()) return SemesterConfig(defaultStartDate(), defaultPeriods())
        return try {
            val o = JSONObject(raw)
            val start = LocalDate.parse(o.getString("startDate"))
            val arr = o.getJSONArray("periods")
            val periods = (0 until arr.length()).map { i ->
                val p = arr.getJSONObject(i)
                PeriodTime(p.getInt("period"), p.getString("start"), p.getString("end"))
            }
            // 周起始日只允许周一/周日；startDate 对齐到周起始日，避免旧数据错周
            val weekStartDay = if (o.optInt("weekStartDay", 1) == 7) 7 else 1
            SemesterConfig(
                startOfWeek(start, weekStartDay),
                periods.ifEmpty { defaultPeriods() },
                totalWeeks = o.optInt("totalWeeks", 20).coerceIn(1, 30),
                showWeekend = o.optBoolean("showWeekend", true),
                showOtherWeekCourses = o.optBoolean("showOtherWeekCourses", true),
                weekStartDay = weekStartDay,
            )
        } catch (e: Exception) {
            SemesterConfig(defaultStartDate(), defaultPeriods())
        }
    }

    private fun coursesToJson(list: List<Course>): String {
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("room", c.room)
                put("teacher", c.teacher)
                put("day", c.day)
                put("start", c.startPeriod)
                put("end", c.endPeriod)
                put("weeks", JSONArray(c.weeks.sorted()))
                if (c.colorIndex >= 0) put("color", c.colorIndex)
            })
        }
        return arr.toString()
    }

    /**
     * 解析课程数组。单项损坏只丢那一门；JSON 本身不是合法数组返回 **null**
     * （调用方据此走 corrupt 备份路径，防止真数据被空列表覆盖）。同时做范围消毒：
     * day 钳到 1..7、节次须 >=1，脏数据产出不了非法 ICS。
     */
    private fun parseCourses(raw: String?): MutableList<Course>? {
        if (raw.isNullOrBlank()) return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                // 单项解析失败只丢那一门，不让整张课表的课程静默消失
                runCatching {
                    val o = arr.getJSONObject(i)
                    val weeksArr = o.getJSONArray("weeks")
                    // 起止节次强制 start <= end，脏数据/分享码倒挂也不会画出负高度卡片
                    val s = o.getInt("start").coerceAtLeast(1)
                    val e = o.getInt("end").coerceAtLeast(1)
                    Course(
                        id = o.getLong("id"),
                        name = o.getString("name"),
                        room = o.optString("room"),
                        teacher = o.optString("teacher"),
                        day = o.getInt("day").coerceIn(1, 7),
                        startPeriod = minOf(s, e),
                        endPeriod = maxOf(s, e),
                        weeks = (0 until weeksArr.length()).map { weeksArr.getInt(it) }
                            .filter { it in 1..30 }.toSet(),
                        colorIndex = o.optInt("color", -1),
                    )
                }.getOrNull()
            }.toMutableList()
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        // 进程内共享单例：二级页面（主题 / 节数 / 时间 / 多课表）是独立 Activity，
        // 必须与主界面共用同一份状态（mutableStateOf 跨 Activity 天然同步）
        @Volatile
        private var shared: Store? = null

        fun shared(context: Context): Store = shared ?: synchronized(this) {
            shared ?: Store(context.applicationContext).apply { load() }.also { shared = it }
        }

        private const val KEY_TABLES = "tables"
        /** 解析失败时的原始 JSON 备份（防止 persist 覆盖真数据后无法找回）。 */
        private const val KEY_TABLES_CORRUPT = "tables_corrupt_backup"
        private const val KEY_ACTIVE_TABLE = "activeTableId"
        // 旧版本 key（仅迁移时读取）
        private const val KEY_CONFIG = "config"
        private const val KEY_COURSES = "courses"
        private const val KEY_THEME_MODE = "themeMode"
        private const val KEY_THEME_LIGHT_DARK = "themeLightDark"
        private const val KEY_THEME_AUTO = "themeAuto"
        private const val KEY_USE_CUSTOM_THEME = "useCustomTheme"
        private const val KEY_ACCENT_INDEX = "accentColorIndex"

        fun newTableId(): String = UUID.randomUUID().toString()
    }
}

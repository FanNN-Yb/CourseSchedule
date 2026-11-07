package com.example.schedule

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.zIndex
import com.example.schedule.R
import com.kyant.capsule.ContinuousRoundedRectangle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberBackdrop
import io.github.neoruaa.glasmio.glass.trackBackdropPosition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import com.example.schedule.ui.glass.GlassDateWheelPicker
import com.example.schedule.ui.glass.GlassPeriodCountPicker
import com.example.schedule.ui.glass.GlassInputSheet
import com.example.schedule.ui.glass.GlassOptionPicker
import com.example.schedule.ui.glass.GlassWheelPicker
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.core.IosTypography
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.layerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberCombinedBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import com.example.schedule.ui.glass.DemoPinnedPage
import io.github.neoruaa.glasmio.ui.GlasmioListRow
import io.github.neoruaa.glasmio.ui.GlasmioGroupedList
import io.github.neoruaa.glasmio.ui.GlasmioSwitch
import com.example.schedule.ui.components.SwipeAction
import com.example.schedule.ui.components.SwipeableRow
import com.example.schedule.ui.components.rememberSwipeableListState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * 设置页（iOS 风格列表）：左侧标题 + 灰色说明，右侧当前值/箭头或开关。
 * 深色模式下为黑底白字，浅色模式自动适配。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    store: Store,
    showBack: Boolean = true,
    onBack: () -> Unit = {},
    onOpenPeriodTime: () -> Unit = {},
    onOpenTables: () -> Unit = {},
    onOpenTheme: () -> Unit = {},
    onOpenThanks: () -> Unit = {},
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var showStartDate by remember { mutableStateOf(false) }
    var showPeriodCount by remember { mutableStateOf(false) }
    var showCurrentWeek by remember { mutableStateOf(false) }
    var showTotalWeeks by remember { mutableStateOf(false) }
    var showWeekStart by remember { mutableStateOf(false) }
    var showShareCode by remember { mutableStateOf(false) }
    var showImportCode by remember { mutableStateOf(false) }
    var showImportFormat by remember { mutableStateOf(false) }

    //  不要在这里读 store.config / version++：
    // Store 是 mutableStateOf 驱动的，谁读谁重组 —— 在最外层读会让
    // 每次开关切换都整页重组（连续切换时明显卡顿）。配置只在
    // 真正用到它的 item / 弹窗作用域里就近读取。

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    // 打开外部链接（致谢里的开源项目主页）；无浏览器时兜底提示
    fun openUrl(url: String) {
        runCatching {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        }.onFailure { toast("没有找到可以打开链接的应用") }
    }

    // ── 文件导入导出（SAF）────────────────────────────────────────
    val exportBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(store.exportAllJson().toByteArray(Charsets.UTF_8))
                } ?: error("无法写入文件")
            }.onSuccess { toast("备份已导出（共 ${store.tables.size} 张课表）") }
                .onFailure { toast("导出失败：${it.message}") }
        }
    }
    val importBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { ins ->
                    ins.readBytes().toString(Charsets.UTF_8)
                } ?: error("无法读取文件")
            }.mapCatching { json -> store.importBackup(json) }
                .onSuccess { toast("已导入 $it 张课表") }
                .onFailure { toast("导入失败：${it.message}") }
        }
    }
    val exportIcs = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/calendar")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val ics = ImportExport.buildIcs(store.activeTable)
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(ics.toByteArray(Charsets.UTF_8))
                } ?: error("无法写入文件")
            }.onSuccess { toast("日历文件已导出") }
                .onFailure { toast("导出失败：${it.message}") }
        }
    }

    val colors = MaterialTheme.colorScheme
    val isLight = colors.background.luminance() > 0.5f
    val cardBg = if (isLight) Color.White else Color(0xFF1C1C1E)
    val cardBorder = if (isLight) Color.Black.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.06f)

    val density = LocalDensity.current
    val listState = rememberLazyListState()

    //  玻璃采样分层铁律（页面内玻璃只能采不含自己的层，否则渲染树循环闪退）：
    //    flatBackdrop  = 铺满全窗的纯色层 → 开关滑块的镜头（位移采样不能出界、
    //                    不能含开关自己；纯色在深色模式的折射观感与 iOS 一致）
    //    pageLayer     = 页面内容层（含列表，不含弹窗）→ 弹窗玻璃采样它
    val flatBackdrop = rememberLayerBackdrop()
    val pageLayer = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize()) {
        //  layerBackdrop 必须写在 background 【前面】：它录的是「自己之后的绘制」。
        //    写反了（先 background 再 layerBackdrop）录到的是空层 ——
        //    开关按下时滑块的镜头采到空层 → 玻璃里是黑色图案 + 放大锯齿。
        Box(Modifier.fillMaxSize().layerBackdrop(flatBackdrop).background(GlasmioTheme.colors.groupedBackground))
        //  trackBackdropPosition 必须紧跟 layerBackdrop：弹窗在独立窗口里，
        // CrossWindowBackdrop 要靠它登记的窗口坐标才能采样到页面内容；
        Box(Modifier.fillMaxSize().layerBackdrop(pageLayer).trackBackdropPosition(pageLayer)) {
        DemoPinnedPage(
            title = "设置",
            subtitle = null,
            backgroundColor = GlasmioTheme.colors.groupedBackground,
            bottomPadding = 140.dp,
        ) {
            // ── 基本设置 ──
            item(key = "sec_basic_title") {
                Text(
                    "基本设置",
                    style = IosTypography.headline,
                    color = GlasmioTheme.colors.content,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            item(key = "sec_basic") {
                // 就近读取：只有这个 item 订阅配置变化，开关切换不整页重组
                val config = store.config
                val todayWeek = weekOf(LocalDate.now(), config.startDate).coerceAtLeast(1)
                GlasmioGroupedList {
                    GlasmioListRow(
                        "开始上课时间",
                        detail = config.startDate.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")),
                        onClick = { showStartDate = true },
                    )
                    GlasmioListRow(
                        "当前的周数",
                        detail = "$todayWeek",
                        onClick = { showCurrentWeek = true },
                    )
                    GlasmioListRow(
                        "本学期总周数",
                        detail = "${config.totalWeeks}",
                        onClick = { showTotalWeeks = true },
                    )
                    GlasmioListRow(
                        "是否显示周末",
                        trailing = {
                            GlasmioSwitch(
                                checked = config.showWeekend,
                                onCheckedChange = {
                                    store.updateConfig(store.config.copy(showWeekend = it))
                                },
                                backdrop = flatBackdrop,
                            )
                        },
                    )
                    GlasmioListRow(
                        "是否显示非本周课程",
                        trailing = {
                            GlasmioSwitch(
                                checked = config.showOtherWeekCourses,
                                onCheckedChange = {
                                    store.updateConfig(store.config.copy(showOtherWeekCourses = it))
                                },
                                backdrop = flatBackdrop,
                            )
                        },
                    )
                    GlasmioListRow("课表节数设置", onClick = { showPeriodCount = true })
                    GlasmioListRow("课表时间设置", onClick = { onOpenPeriodTime() })
                    GlasmioListRow(
                        "设置每周起始日",
                        detail = if (config.weekStartDay == 1) "周一" else "周日",
                        onClick = { showWeekStart = true },
                    )
                    GlasmioListRow(
                        "主题",
                        onClick = { onOpenTheme() },
                    )
                }
            }

            // ── 课表与数据 ──
            item(key = "sec_data_title") {
                Text(
                    "课表与数据",
                    style = IosTypography.headline,
                    color = GlasmioTheme.colors.content,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            item(key = "sec_data") {
                GlasmioGroupedList {
                    GlasmioListRow(
                        "多课表管理",
                        detail = "${store.tables.size} 张",
                        onClick = { onOpenTables() },
                    )
                    GlasmioListRow(
                        "导出备份（JSON）",
                        onClick = { exportBackup.launch("课程表备份_${LocalDate.now()}.json") },
                    )
                    GlasmioListRow(
                        "导入备份（JSON）",
                        onClick = { importBackup.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    )
                    GlasmioListRow(
                        "导出日历（ICS）",
                        onClick = { exportIcs.launch("${store.activeTable.name}.ics") },
                    )
                    GlasmioListRow(
                        "分享课表（分享码）",
                        onClick = { showShareCode = true },
                    )
                    GlasmioListRow(
                        "粘贴分享码导入",
                        onClick = { showImportCode = true },
                    )
                    GlasmioListRow(
                        "通过格式导入",
                        onClick = { showImportFormat = true },
                    )
                }
            }

            // ── 关于 ──
            item(key = "sec_about_title") {
                Text(
                    "关于",
                    style = IosTypography.headline,
                    color = GlasmioTheme.colors.content,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            item(key = "sec_about") {
                GlasmioGroupedList {
                    GlasmioListRow("版本", subtitle = "课程表 CourseSchedule", detail = BuildConfig.VERSION_NAME)
                    GlasmioListRow("作者", subtitle = "设计与开发", detail = "FanNN-Yb", onClick = { openUrl("https://github.com/FanNN-Yb") })
                    GlasmioListRow("致谢", subtitle = "用到的开源项目与参考", onClick = { onOpenThanks() })
                }
            }
        }
        } // ← pageLayer Box 闭合（弹窗在它外面采样，避免自引用循环）

    // ── 液态玻璃选择器：全部走 FlutterGlassMenu 引擎，不用任何原生 Material 弹窗 ──
    val pickerOpen =
        showPeriodCount || showStartDate || showCurrentWeek || showTotalWeeks || showWeekStart
    if (pickerOpen) {
        // 消费型透明遮罩：展开期间挡住列表，点空白处收起
        Box(
            Modifier
                .fillMaxSize()
                .zIndex(0.5f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    showStartDate = false
                    showPeriodCount = false
                    showCurrentWeek = false
                    showTotalWeeks = false
                    showWeekStart = false
                }
        )
    }

    // 配置类选择器：只在展开时才读配置（平时开关切换不会触发这里重组）
    if (pickerOpen) {
        val config = store.config
        val todayWeek = weekOf(LocalDate.now(), config.startDate).coerceAtLeast(1)

    // 课表节数：上午 / 下午 / 晚上 三轮齿轮（初始分段按现有节次时间推导）
    if (showPeriodCount) {
        val hours = config.periods.map { it.start.substringBefore(':').toIntOrNull() ?: 20 }
        val initM = if (hours.isEmpty()) 4 else hours.count { it < 12 }.coerceIn(1, 10)
        val initA = if (hours.isEmpty()) 4 else hours.count { it in 12..17 }.coerceIn(1, 10)
        val initE = if (hours.isEmpty()) 2 else hours.count { it >= 18 }.coerceIn(1, 10)
        GlassPeriodCountPicker(
            expanded = showPeriodCount,
            onExpandedChange = { if (!it) showPeriodCount = false },
            title = "课表节数设置",
            morning = initM,
            afternoon = initA,
            evening = initE,
            backdrop = pageLayer,
            modifier = Modifier.zIndex(4f),
            onConfirm = { m, a, e ->
                store.updateConfig(
                    store.config.copy(periods = buildPeriods(store.config.periods, m + a + e))
                )
            },
        )
    }

    // 开始上课时间：年 / 月 / 日 三轮齿轮
    GlassDateWheelPicker(
        expanded = showStartDate,
        onExpandedChange = { if (!it) showStartDate = false },
        title = "开始上课时间",
        date = config.startDate,
        backdrop = pageLayer,
        modifier = Modifier.zIndex(4f),
        onConfirm = { picked ->
            // 对齐到每周起始日，保证周次按整周推算
            store.updateConfig(store.config.copy(startDate = startOfWeek(picked, store.config.weekStartDay)))
        },
    )

    // 当前的周数
    GlassWheelPicker(
        expanded = showCurrentWeek,
        onExpandedChange = { if (!it) showCurrentWeek = false },
        title = "当前的周数",
        value = todayWeek.toFloat(),
        range = (1..30).map { it.toFloat() },
        backdrop = pageLayer,
        modifier = Modifier.zIndex(4f),
        onConfirm = { n ->
            val start = startOfWeek(LocalDate.now(), store.config.weekStartDay)
                .minusDays((n.toInt() - 1) * 7L)
            store.updateConfig(store.config.copy(startDate = start))
        },
    )

    // 本学期总周数
    GlassWheelPicker(
        expanded = showTotalWeeks,
        onExpandedChange = { if (!it) showTotalWeeks = false },
        title = "本学期总周数",
        value = config.totalWeeks.toFloat(),
        range = (1..30).map { it.toFloat() },
        backdrop = pageLayer,
        modifier = Modifier.zIndex(4f),
        onConfirm = { n ->
            store.updateConfig(store.config.copy(totalWeeks = n.toInt()))
        },
    )

    // 每周起始日
    GlassOptionPicker(
        expanded = showWeekStart,
        onExpandedChange = { if (!it) showWeekStart = false },
        title = "设置每周起始日",
        options = listOf(1 to "周一", 7 to "周日"),
        current = config.weekStartDay,
        backdrop = pageLayer,
        modifier = Modifier.zIndex(4f),
        onSelect = { day ->
            // 平移开学日期（移到最近的同义周起始日），周次编号变化最小
            val diff = (day - store.config.weekStartDay + 7) % 7
            val shift = if (diff <= 3) diff.toLong() else (diff - 7).toLong()
            store.updateConfig(
                store.config.copy(
                    weekStartDay = day,
                    startDate = store.config.startDate.plusDays(shift),
                )
            )
        },
    )
    }

    // 常驻组合 + visible：保留 GlasmioAlertDialog 的退场动画
    val shareCode = remember(showShareCode) {
        ImportExport.encodeShareCode(store.tableSharePayload(store.activeTable))
    }
    run {
        GlassInputSheet(
            visible = showShareCode,
            title = "分享课表",
            hint = "把这串分享码发给同学，对方在「设置 → 粘贴分享码导入」即可一键导入这张课表（含课程与学期设置）。",
            text = shareCode,
            onTextChange = {},
            readOnly = true,
            minLines = 3,
            confirmLabel = "分享…",
            extraLabel = "复制",
            onExtra = {
                clipboard.setText(AnnotatedString(shareCode))
                toast("分享码已复制")
            },
            onConfirm = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "我的课表分享码：\n" + shareCode +
                            "\n（在「课程表」App：设置 → 粘贴分享码导入）",
                    )
                }
                context.startActivity(Intent.createChooser(intent, "分享课表"))
            },
            onDismiss = { showShareCode = false },
            backdrop = pageLayer,
            modifier = Modifier.zIndex(5f),
        )
    }

    var importCodeText by remember { mutableStateOf("") }
    LaunchedEffect(showImportCode) { if (showImportCode) importCodeText = "" }
    run {
        GlassInputSheet(
            visible = showImportCode,
            title = "粘贴分享码导入",
            hint = "支持完整链接（schedule://import?data=…）或裸分享码，将作为一张新课表加入。",
            text = importCodeText,
            onTextChange = { importCodeText = it },
            minLines = 3,
            confirmLabel = "导入",
            onConfirm = {
                val payload = ImportExport.extractSharePayload(importCodeText)
                val table = payload?.let { store.parseTablePayload(it) }
                if (table == null) {
                    toast("分享码无效，请检查是否完整")
                } else {
                    val imported = store.importTable(table, makeActive = true)
                    toast("已导入「" + imported.name + "」（" + imported.courses.size + " 门课程）")
                    showImportCode = false
                }
            },
            onDismiss = { showImportCode = false },
            backdrop = pageLayer,
            modifier = Modifier.zIndex(5f),
        )
    }

    var importFormatText by remember { mutableStateOf("") }
    LaunchedEffect(showImportFormat) { if (showImportFormat) importFormatText = "" }
    run {
        GlassInputSheet(
            visible = showImportFormat,
            title = "通过格式导入",
            hint = "每行一门课：课程名 教室 老师 星期 节次 周次\n" +
                "用空格或逗号分隔，教室 / 老师可省略，顺序不限。\n" +
                "例：高等数学 教学楼101 王老师 周一 1-2节 1-16周单\n" +
                "可把教务课表发给 AI，让它转成这个格式再粘贴。",
            text = importFormatText,
            onTextChange = { importFormatText = it },
            minLines = 5,
            confirmLabel = "解析并导入",
            extraLabel = "复制格式",
            onExtra = {
                clipboard.setText(AnnotatedString(ImportExport.courseFormatPrompt))
                toast("格式说明已复制，发给 AI 后把结果粘贴到这里")
            },
            onConfirm = {
                val courses = runCatching { ImportExport.parseCoursesText(importFormatText) }
                    .getOrElse { emptyList() }
                if (courses.isEmpty()) {
                    toast("没能解析出课程，请按格式填写：课程名 教室 老师 星期 节次 周次")
                } else {
                    val name = "格式导入 " +
                        LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日"))
                    // 复用当前学期配置，避免导入后开学日/作息被重置成默认值
                    val imported = store.importTable(
                        ScheduleTable(
                            Store.newTableId(),
                            name,
                            config = store.config,
                            courses = courses,
                        ),
                        makeActive = true,
                    )
                    toast("解析到 " + imported.courses.size + " 门课程，已导入为新课表")
                    showImportFormat = false
                }
            },
            onDismiss = { showImportFormat = false },
            backdrop = pageLayer,
            modifier = Modifier.zIndex(5f),
        )
    }
    }
}

/** 设置行锚点：右上角窗口坐标 + 尺寸（px），供液态玻璃选择器定位。 */
// ── 列表行 ──────────────────────────────────────────────────────

@Composable
internal fun SectionHeader(text: String) {
    Text(
        text,
        style = IosTypography.headline,
        color = GlasmioTheme.colors.content,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun SettingsCard(
    cardBg: Color,
    borderColor: Color,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(ContinuousRoundedRectangle(22.dp))
            .background(cardBg)
            .border(0.5.dp, borderColor, ContinuousRoundedRectangle(22.dp))
    ) {
        content()
    }
}

/** 行底部的分割线：左右各留 16dp，0.5dp 细线。 */
private fun Modifier.rowDivider(dividerColor: Color): Modifier = this.drawBehind {
    drawLine(
        color = dividerColor,
        start = Offset(16.dp.toPx(), size.height),
        end = Offset(size.width - 16.dp.toPx(), size.height),
        strokeWidth = 0.5.dp.toPx(),
    )
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    value: String? = null,
    showDivider: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val isLight = colors.background.luminance() > 0.5f
    val dividerColor = if (isLight) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.08f)
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (showDivider) Modifier.rowDivider(dividerColor) else Modifier)
            .clickable(
                // 无涟漪：点按下即响应，不要灰色水波纹
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.onSurface, fontSize = 17.sp)
            Text(
                subtitle,
                color = colors.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (value != null) {
            Box(
                Modifier
                    .padding(start = 8.dp)
            ) {
                Text(value, color = colors.onSurfaceVariant, fontSize = 15.sp)
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = if (isLight) Color(0xFF1D1D1F) else Color.Unspecified,
            modifier = Modifier
                .padding(start = 6.dp)
                .size(width = 8.dp, height = 12.dp),
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    backdrop: Backdrop,
    showDivider: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val isLight = colors.background.luminance() > 0.5f
    val dividerColor = if (isLight) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.08f)
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (showDivider) Modifier.rowDivider(dividerColor) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.onSurface, fontSize = 17.sp)
            Text(
                subtitle,
                color = colors.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        GlasmioSwitch(
            checked = checked,
            onCheckedChange = onChange,
            backdrop = backdrop,
        )
    }
}

// ── 设置流程（供底部 Tab 使用）─────────────────────────────────

/**
 * 设置流程：根设置页直接渲染；二级页面（主题 / 节数 / 时间 / 多课表）
 */
@Composable
fun SettingsFlow(store: Store) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current

    Box(Modifier.fillMaxSize().background(colors.background)) {
        SettingsScreen(
            store = store,
            showBack = false,
            onOpenPeriodTime = {
                context.startActivity(android.content.Intent(context, PeriodTimeActivity::class.java))
            },
            onOpenTables = {
                context.startActivity(android.content.Intent(context, TablesActivity::class.java))
            },
            onOpenTheme = {
                context.startActivity(android.content.Intent(context, ThemeActivity::class.java))
            },
            onOpenThanks = {
                context.startActivity(android.content.Intent(context, ThanksActivity::class.java))
            },
        )
    }
}

// ── 多课表管理页 ────────────────────────────────────────────────

/**
 * 多课表（多学期 / 历史课表）管理：使用中的一张 + 其他课表 + 已归档。
 * 点卡片切换；每张卡支持重命名 / 归档 / 删除（至少保留一张）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TablesScreen(store: Store, onBack: () -> Unit) {
    val context = LocalContext.current
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    var showNew by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ScheduleTable?>(null) }
    var deleteTarget by remember { mutableStateOf<ScheduleTable?>(null) }

    // 弹窗玻璃采样层：录滚动内容（弹窗在它外面，无自引用）
    val pageLayer = rememberLayerBackdrop()
    val current = store.activeTable
    val others = store.tables.filter { it.id != current.id && !it.archived }
    val archived = store.tables.filter { it.id != current.id && it.archived }

    val colors = MaterialTheme.colorScheme
    val isLight = colors.background.luminance() > 0.5f
    val cardBg = if (isLight) Color.White else Color(0xFF1C1C1E)
    val cardBorder = if (isLight) Color(0xFFE5E5E5) else Color(0xFF3A3A3C)
    val swipeableListState = rememberSwipeableListState()

    // 有归档时中间加一枚归档，否则功能会随卡片内按钮一起去掉
    fun actionsFor(onArchive: (() -> Unit)?): List<SwipeAction> = buildList {
        add(SwipeAction(0, Color(0xFF0A84FF), R.drawable.ic_pencil, "重命名"))
        if (onArchive != null) {
            add(SwipeAction(1, Color(0xFF8E8E93), R.drawable.ic_gear_fill, "归档"))
        }
        // 至少保留一张课表：只剩一张时不再提供删除入口（Store 里也是静默拒绝）
        if (store.tables.size > 1) {
            add(
                SwipeAction(
                    index = if (onArchive != null) 2 else 1,
                    color = Color(0xFFFF3B30),
                    iconRes = R.drawable.ic_trash,
                    contentDescription = "删除",
                )
            )
        }
    }

    // 弹窗玻璃采样层：录页面内容（弹窗在录制层外，无自引用循环）
    Box(Modifier.fillMaxSize().layerBackdrop(pageLayer).trackBackdropPosition(pageLayer)) {
    SubPageChrome(
        title = "多课表管理",
        onBack = onBack,
        // 右上角圆形加号 = 新建课表（与顶部返回键同一套玻璃按钮）
        confirm = { showNew = true },
        confirmIcon = R.drawable.ic_plus,
        confirmContentDescription = "新建课表",
    ) {
        // SubPageChrome 内容列已带左右 20dp，不再叠加（否则卡片比设置页窄）
        Column {
            Spacer(Modifier.height(8.dp))
            SectionHeader("使用中")
            SwipeableRow(
                key = current.id,
                listState = swipeableListState,
                actions = actionsFor(null),
                onAction = { idx ->
                    when (idx) {
                        0 -> renameTarget = current
                        else -> deleteTarget = current
                    }
                },
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                TableCard(
                    table = current,
                    isActive = true,
                    onSwitch = {},
                    cardBg = cardBg,
                    cardBorder = cardBorder,
                )
            }

            if (others.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                SectionHeader("其他课表")
                others.forEach { t ->
                    SwipeableRow(
                        key = t.id,
                        listState = swipeableListState,
                        actions = actionsFor({ store.setArchived(t.id, true) }),
                        onAction = { idx ->
                            when (idx) {
                                0 -> renameTarget = t
                                1 -> store.setArchived(t.id, true)
                                else -> deleteTarget = t
                            }
                        },
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        TableCard(
                            table = t,
                            isActive = false,
                            onSwitch = {
                                store.switchTable(t.id)
                                toast("已切换到「${t.name}」")
                            },
                            cardBg = cardBg,
                            cardBorder = cardBorder,
                        )
                    }
                }
            }

            if (archived.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                SectionHeader("已归档（往期学期）")
                archived.forEach { t ->
                    SwipeableRow(
                        key = t.id,
                        listState = swipeableListState,
                        actions = actionsFor({ store.setArchived(t.id, false) }),
                        onAction = { idx ->
                            when (idx) {
                                0 -> renameTarget = t
                                1 -> store.setArchived(t.id, false)
                                else -> deleteTarget = t
                            }
                        },
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        TableCard(
                            table = t,
                            isActive = false,
                            onSwitch = {
                                store.switchTable(t.id)
                                toast("已切换到「${t.name}」")
                            },
                            cardBg = cardBg,
                            cardBorder = cardBorder,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    }

    // 常驻组合 + visible：保留 GlasmioAlertDialog 的退场动画；
    // 退场期间用 last*Target 继续显示目标课表信息
    var newTableName by remember { mutableStateOf("") }
    LaunchedEffect(showNew) { if (showNew) newTableName = "" }
    GlassInputSheet(
        visible = showNew,
        title = "新建课表",
        hint = "给这张课表起个名字，例如 2025-2026-2。",
        text = newTableName,
        onTextChange = { newTableName = it },
        minLines = 1,
        confirmLabel = "创建",
        onConfirm = {
            store.addTable(newTableName)
            showNew = false
        },
        onDismiss = { showNew = false },
        backdrop = pageLayer,
    )

    var lastRenameTarget by remember { mutableStateOf<ScheduleTable?>(null) }
    SideEffect { if (renameTarget != null) lastRenameTarget = renameTarget }
    var renameName by remember { mutableStateOf("") }
    LaunchedEffect(renameTarget?.id) {
        val t = renameTarget
        if (t != null) renameName = t.name
    }
    val renameShown = renameTarget ?: lastRenameTarget
    GlassInputSheet(
        visible = renameTarget != null,
        title = "重命名课表",
        hint = "修改后课表列表与分享码都会用新名字。",
        text = renameName,
        onTextChange = { renameName = it },
        minLines = 1,
        confirmLabel = "保存",
        onConfirm = {
            renameShown?.let { store.renameTable(it.id, renameName) }
            renameTarget = null
        },
        onDismiss = { renameTarget = null },
        backdrop = pageLayer,
    )

    var lastDeleteTarget by remember { mutableStateOf<ScheduleTable?>(null) }
    SideEffect { if (deleteTarget != null) lastDeleteTarget = deleteTarget }
    val deleteShown = deleteTarget ?: lastDeleteTarget
    GlassInputSheet(
        visible = deleteTarget != null,
        title = "删除课表",
        hint = "确定删除「" + (deleteShown?.name ?: "") + "」吗？其中 " +
            (deleteShown?.courses?.size ?: 0) + " 门课程将一并删除，且无法恢复。",
        text = "",
        onTextChange = {},
        showField = false,
        confirmLabel = "删除",
        destructive = true,
        onConfirm = {
            deleteShown?.let { store.deleteTable(it.id) }
            deleteTarget = null
        },
        onDismiss = { deleteTarget = null },
        backdrop = pageLayer,
    )
}

/**
 * （22dp 连续圆角 + 白/深灰底 + 16dp 内边距；深色才描边；
 *  标题 17sp/W600 + 右侧次要信息 13sp；副行 13sp 用 16dp 间距）。
 * 操作不放在卡内，由外层 [SwipeableRow] 左滑露出。
 */
@Composable
private fun TableCard(
    table: ScheduleTable,
    isActive: Boolean,
    onSwitch: () -> Unit,
    cardBg: Color,
    cardBorder: Color,
) {
    val colors = MaterialTheme.colorScheme
    val isLight = colors.background.luminance() > 0.5f
    val shape = ContinuousRoundedRectangle(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cardBg)
            .then(if (isLight) Modifier else Modifier.border(0.25.dp, cardBorder, shape))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSwitch,
            )
            .padding(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                table.name,
                fontSize = 17.sp,
                fontWeight = FontWeight.W600,
                color = colors.onSurface,
                letterSpacing = (-0.374).sp,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (isActive) {
                Text(
                    "使用中",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.W400,
                    color = colors.primary,
                    letterSpacing = (-0.224).sp,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "${table.courses.size} 门课程",
                fontSize = 13.sp,
                color = colors.onSurfaceVariant,
            )
            Text(
                "${table.config.totalWeeks} 周",
                fontSize = 13.sp,
                color = colors.onSurfaceVariant,
            )
            Text(
                table.config.startDate.format(DateTimeFormatter.ofPattern("yyyy/M/d")) + " 开课",
                fontSize = 13.sp,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/** 致谢页：设置 → 关于 → 致谢。列出用到的开源项目与参考。 */
@Composable
fun ThanksScreen(onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    fun openUrl(url: String) {
        runCatching {
            context.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
            )
        }.onFailure { toast(context, "没有找到可以打开链接的应用") }
    }

    SubPageChrome(
        title = "致谢",
        onBack = onBack,
    ) { _ ->
        // SubPageChrome 的内容列已带左右 20dp 内边距，这里不再叠加 —— 否则卡片比设置页窄
        Column {
            Spacer(Modifier.height(8.dp))
            SectionHeader("开源项目")
            Spacer(Modifier.height(8.dp))
            GlasmioGroupedList {
                GlasmioListRow(
                    "Glasmio",
                    subtitle = "iOS 风格液态玻璃组件库（弹窗/菜单/选择器/按压动效）· NEORUAA（Apache-2.0）",
                    detail = "GitHub",
                    onClick = { openUrl("https://github.com/NEORUAA/Glasmio") },
                )
                GlasmioListRow(
                    "Backdrop / Shapes / Capsule",
                    subtitle = "液态玻璃效果与形状引擎 · Kyant0（Apache-2.0）",
                    detail = "GitHub",
                    onClick = { openUrl("https://github.com/Kyant0/AndroidLiquidGlass") },
                )
                GlasmioListRow(
                    "Rubber-Band",
                    subtitle = "橡皮筋回弹滚动 · FanNN-Yb",
                    detail = "GitHub",
                    onClick = { openUrl("https://github.com/FanNN-Yb/Rubber-Band") },
                )
                GlasmioListRow(
                    "Compose Multiplatform",
                    subtitle = "跨平台声明式 UI 框架 · JetBrains（Apache-2.0）",
                    detail = "GitHub",
                    onClick = { openUrl("https://github.com/JetBrains/compose-multiplatform") },
                )
                GlasmioListRow(
                    "Jetpack Compose / AndroidX",
                    subtitle = "Android 现代化 UI 工具集（Apache-2.0）",
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "感谢以上开源项目的作者们，\n本应用建立在它们的肩膀上。",
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/** Toast 小助手（致谢页等无 toast 函数的页面复用）。 */
private fun toast(context: android.content.Context, msg: String) {
    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
}

package com.example.schedule

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.unit.sp
import com.example.schedule.R
import androidx.compose.ui.zIndex
import com.example.schedule.ui.glass.rememberRubberBandState
import com.example.schedule.ui.glass.DemoPinnedPage
import com.example.schedule.ui.glass.RubberBandContainer
import com.example.schedule.ui.glass.topProgressiveBlurScrim
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.layerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import io.github.neoruaa.glasmio.glass.GlasmioMenuAnchorState
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.glass.GlassEmphasis
import io.github.neoruaa.glasmio.glass.glasmioMenuAnchor
import io.github.neoruaa.glasmio.glass.rememberGlasmioMenuAnchorState
import io.github.neoruaa.glasmio.icons.SfSymbol
import io.github.neoruaa.glasmio.ui.NavigationBarSurfaceHeight
import io.github.neoruaa.glasmio.ui.GlasmioIconButton
import io.github.neoruaa.glasmio.core.IosTypography
import io.github.neoruaa.glasmio.ui.GlasmioMenu
import io.github.neoruaa.glasmio.ui.GlasmioMenuItem
import io.github.neoruaa.glasmio.ui.GlasmioNavigationBar
import io.github.neoruaa.glasmio.ui.GlasmioTabItem
import io.github.neoruaa.glasmio.glass.render.effects.blur
import io.github.neoruaa.glasmio.glass.render.effects.lens
import io.github.neoruaa.glasmio.glass.render.effects.vibrancy
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

private val WeekdayNames = listOf("一", "二", "三", "四", "五", "六", "日")
private val TabLabels = listOf("课程表", "今日", "设置")

/**
 * 「今日」页：顶部日期标题 + 今天课程的玻璃卡片列表。
 * [bottomBar] 由外层提供（底部液态玻璃 Tabs），在根 Box 上居底渲染。
 */
@Composable
fun TodayScreen(store: Store) {
    val colors = MaterialTheme.colorScheme
    val config = store.config
    // 15 秒一跳的时钟：驱动「正在上 / 即将上 / 已结束」流转 + 跨天自动更新日期
    var nowMinute by remember { mutableStateOf(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            nowMinute = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
        }
    }
    val today = nowMinute.toLocalDate()
    val now = nowMinute.toLocalTime()
    val todayWeek = weekOf(today, config.startDate)
    // 以课程列表本身做 key：编辑已有课程（数量不变）也会刷新今日列表
    val todaysCourses = remember(store.courses, store.config, today) {
        store.courses
            .filter { it.day == today.dayOfWeek.value && todayWeek in it.weeks }
            .sortedBy { it.startPeriod }
    }

    // 待办式过滤：已结束的课程直接从列表去掉；进行中 = 正在上课；其余 = 即将上课
    val entries: List<Pair<Course, TodayStatus>> = remember(todaysCourses, nowMinute) {
        todaysCourses.mapNotNull { course ->
            val startT = parseHm(config.periodTimeOf(course.startPeriod)?.start)
            val endT = parseHm(config.periodTimeOf(course.endPeriod)?.end)
            when {
                // 没配时间信息无法判定结束 → 常驻显示为「即将上课」
                startT == null -> course to TodayStatus.Upcoming
                endT != null && now.isAfter(endT) -> null
                !now.isBefore(startT) -> course to TodayStatus.Ongoing
                else -> course to TodayStatus.Upcoming
            }
        }
    }
    val finishedCount = todaysCourses.size - entries.size

    DemoPinnedPage(
        title = "今日",
        subtitle = "${today.monthValue}月${today.dayOfMonth}日 星期${WeekdayNames[today.dayOfWeek.value - 1]}",
        backgroundColor = GlasmioTheme.colors.groupedBackground,
        bottomPadding = 140.dp,
    ) {
        // 今日状态行（大标题下的小字说明）：有课时才显示 ——
        // 没课/已结束的信息由中间的空状态承载，避免重复
        if (todaysCourses.isNotEmpty() && entries.isNotEmpty()) {
            item(key = "status") {
                Text(
                    "剩 ${entries.size} 门课程" +
                        (if (finishedCount > 0) "（已上 $finishedCount 门）" else ""),
                    style = IosTypography.subheadline,
                    color = GlasmioTheme.colors.secondaryContent,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        if (todaysCourses.isEmpty()) {
            item(key = "empty_none") {
                TodayEmptyCard(done = false, finishedCount = 0)
            }
        } else if (entries.isEmpty()) {
            item(key = "empty_done") {
                TodayEmptyCard(done = true, finishedCount = todaysCourses.size)
            }
        } else {
            items(entries.size, key = { "card_${entries[it].first.id}" }) { idx ->
                val (course, status) = entries[idx]
                TodayCard(
                    course = course,
                    config = config,
                    status = status,
                    now = now,
                )
            }
        }
    }
}

/** 今日课程状态：Ongoing = 正在上课；Upcoming = 即将上课（已结束的直接不显示）。 */
private enum class TodayStatus { Ongoing, Upcoming }

/** "HH:mm" → [LocalTime]，解析失败返回 null。 */
private fun parseHm(s: String?): LocalTime? {
    val parts = s?.split(":") ?: return null
    val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
    return if (h in 0..23 && m in 0..59) LocalTime.of(h, m) else null
}

/**
 * 今日页空状态：不做卡片，直接贴在页面背景上（与大标题同层）。
 * done=false = 今天没课（灰玻璃徽章 + 日历图标）；done=true = 今日已结束（主题色徽章 + 白✓）。
 */
@Composable
private fun TodayEmptyCard(done: Boolean, finishedCount: Int) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (done) "今日课程已结束" else "今天没有课程",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = GlasmioTheme.colors.content,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (done) {
                if (finishedCount > 0) {
                    "今天的 $finishedCount 门课都上完啦，好好休息"
                } else {
                    "好好休息，做点喜欢的事吧"
                }
            } else {
                "日历上今天一片空白，好好享受吧"
            },
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = GlasmioTheme.colors.secondaryContent,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TodayCard(
    course: Course,
    config: SemesterConfig,
    status: TodayStatus,
    now: LocalTime,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val isLight = colors.background.luminance() > 0.5f
    val cardBg = if (isLight) Color.White else Color(0xFF1C1C1E)
    val cardBorder = if (isLight) Color(0xFFE5E5E5) else Color(0xFF3A3A3C)
    val shape = ContinuousRoundedRectangle(22.dp)
    val tint = courseColorOf(course)
    val st = config.periodTimeOf(course.startPeriod)?.start ?: ""
    val en = config.periodTimeOf(course.endPeriod)?.end ?: ""
    // 即将上课且 1 小时内开始 → 标题右侧显示倒计时
    val minutesUntil = if (status == TodayStatus.Upcoming) {
        parseHm(config.periodTimeOf(course.startPeriod)?.start)
            ?.let { Duration.between(now, it).toMinutes() }
            ?.takeIf { it in 1..59 }
    } else {
        null
    }

    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cardBg)
            .then(if (isLight) Modifier else Modifier.border(0.25.dp, cardBorder, shape))
            .padding(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 课程色点（对应课表格子的颜色）
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(tint)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    course.name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.W600,
                    color = colors.onSurface,
                    letterSpacing = (-0.374).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (status == TodayStatus.Ongoing) {
                Text(
                    "上课中",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.W400,
                    color = colors.primary,
                    letterSpacing = (-0.224).sp,
                )
            } else if (minutesUntil != null) {
                Text(
                    "${minutesUntil}分钟后",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.W400,
                    color = colors.onSurfaceVariant,
                    letterSpacing = (-0.224).sp,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        // 单行 + 省略号：卡片高度恒定，不随教室 / 老师名字长短换行
        Text(
            buildString {
                append(
                    if (course.startPeriod == course.endPeriod) "第${course.startPeriod}节"
                    else "第${course.startPeriod}-${course.endPeriod}节"
                )
                if (st.isNotBlank()) append(" · $st–$en")
                if (course.room.isNotBlank()) append(" · @${course.room}")
                if (course.teacher.isNotBlank()) append(" · ${course.teacher}")
            },
            fontSize = 13.sp,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 底部栏整体排版：Glasmio 原生玻璃导航栏 + 圆形「＋」玻璃按钮。
 *
 * 「＋」按钮同时是导入菜单的变形锚点：按钮上直接挂 glasmioMenuAnchor，
 * 菜单展开时按钮的玻璃原样融进菜单（portal 采集按钮像素，按钮自动隐藏），
 * 收起时按原位融回；菜单位置 = 按钮的真实窗口边界。
 * GlasmioMenu 的 anchor 槽位只放 1dp 占位（弹层组合要求锚点尺寸非零），
 * 整条导航栏不能进锚点 —— 否则变形动画会把整条栏当成融球源（严重变形错位）。
 */
@Composable
fun BottomBarRow(
    selectedTabIndex: () -> Int,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop,
    onFormatImport: () -> Unit,
    onCodeImport: () -> Unit,
    onBackupImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 不使用 Glasmio 的 compact 压缩模式：常量 0 进度 + 空回调
    val compactProgress = remember { mutableStateOf(0f) }
    var importMenuExpanded by remember { mutableStateOf(false) }
    val menuAnchorState = rememberGlasmioMenuAnchorState()
    BackHandler(enabled = importMenuExpanded) { importMenuExpanded = false }


    Box(modifier) {
    GlasmioMenu(
        //  菜单最终位置的右下角要盖住「＋」按钮原位置：外层 Box 用 align(BottomEnd)
        // + 与按钮一致的边距/尺寸精确叠在按钮上，弹层锚定它 → 右下角自然重合
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 40.dp, bottom = 24.dp)
            .size(NavigationBarSurfaceHeight),
        menuAnchorState = menuAnchorState,
        expanded = importMenuExpanded,
        onExpandedChange = { importMenuExpanded = it },
        itemCount = 3,
        backdrop = backdrop,
        anchor = { Box(Modifier.size(1.dp)) },
    ) { menuBackdrop, close ->
        // 单行菜单项（iOS 上下文菜单风格）：44dp 等高、图标与标题对齐
        GlasmioMenuItem(
            "通过格式导入", { onFormatImport(); close() },
            systemName = "app.import.format",
            backdrop = menuBackdrop,
        )
        GlasmioMenuItem(
            "分享码导入", { onCodeImport(); close() },
            systemName = "link",
            backdrop = menuBackdrop,
        )
        GlasmioMenuItem(
            "备份导入", { onBackupImport(); close() },
            systemName = "app.import.backup",
            backdrop = menuBackdrop,
        )
    }
    Row(
        Modifier
            .padding(start = 40.dp, end = 40.dp, top = 12.dp, bottom = 24.dp)
    ) {
        GlasmioNavigationBar(
            items = remember(TabLabels) {
                TabLabels.mapIndexed { index, label ->
                    GlasmioTabItem(
                        index,
                        label,
                        // 0=课程表（房子）、1=今日（日历）、2=设置（齿轮）
                        when (index) {
                            0 -> SfSymbol.HouseFilled
                            1 -> SfSymbol.Calendar
                            else -> SfSymbol.GearshapeFilled
                        },
                    )
                }
            },
            selectedKey = selectedTabIndex(),
            onSelected = onSelect,
            onExpand = {},
            compactProgress = compactProgress,
            compactSize = 48.dp,
            backdrop = backdrop,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(16.dp))
        GlasmioIconButton(
            onClick = { importMenuExpanded = true },
            backdrop = backdrop,
            size = NavigationBarSurfaceHeight,
            modifier = Modifier.glasmioMenuAnchor(menuAnchorState),
            // 主题色染色玻璃：Prominent 保留玻璃采样，navigationSurfaceColor
            // 把材质 tint 换成当前主题色（defaultGlassColors(dark, accentColor)）。
            emphasis = GlassEmphasis.Prominent,
            navigationSurfaceColor = GlasmioTheme.colors.accent,
        ) {
            PlusIcon(Color.White)
        }
    }
    }
}

/** 加号图标（圆头线条）。 */
@Composable
private fun PlusIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val w = 2.25.dp.toPx()
        val midX = size.width / 2f
        val midY = size.height / 2f
        val half = size.minDimension * 0.28f
        drawLine(tint, Offset(midX - half, midY), Offset(midX + half, midY), w, StrokeCap.Round)
        drawLine(tint, Offset(midX, midY - half), Offset(midX, midY + half), w, StrokeCap.Round)
    }
}

package com.example.schedule.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FloatAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.example.schedule.R
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.core.LocalGroupedListBackgroundAlpha
import io.github.neoruaa.glasmio.glass.GlassEmphasis
import io.github.neoruaa.glasmio.glass.GlassSurfaceStyle
import io.github.neoruaa.glasmio.glass.LocalGlassBackdrop
import io.github.neoruaa.glasmio.glass.rememberCrossWindowBackdrop
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import io.github.neoruaa.glasmio.ui.GlasmioAlertButton
import io.github.neoruaa.glasmio.ui.GlasmioAlertButtonRole
import io.github.neoruaa.glasmio.ui.GlasmioGroupedList
import io.github.neoruaa.glasmio.ui.GlasmioIconButton
import io.github.neoruaa.glasmio.ui.GlasmioTopBar
import io.github.neoruaa.glasmio.ui.GlasmioAlertDialog
import io.github.neoruaa.glasmio.ui.GlasmioAlertButtonLayout
import io.github.neoruaa.glasmio.ui.GlasmioAlertButtonSpec
import io.github.neoruaa.glasmio.ui.GlasmioAlertFieldGroup
import io.github.neoruaa.glasmio.ui.GlasmioAlertTextField
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import com.kyant.capsule.ContinuousRoundedRectangle
import com.kyant.shapes.RoundedCornerStyle
import com.kyant.shapes.RoundedRectangle
import io.github.fannn_yb.rubberband.overscroll.rememberOffsetOverscrollFactory
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * 该值与 38dp 同量级 —— 直接使用 38dp 四面统一，与系统观感一致。
 */
private val SheetPanelShape = ContinuousRoundedRectangle(
    topStart = 38.dp,
    topEnd = 38.dp,
    bottomStart = 38.dp,
    bottomEnd = 38.dp,
)

/**
 * Core Animation spring，mass 3 / stiffness 1000 / damping 500 → 临界阻尼、固定 500ms，
 * 角频率 ω = sqrt(1000/3)。库里 `show()` 和 `hide()` 都用这一条，所以进退场完全对称。
 * 位移曲线：value = 1 + (d + (v0 + ωd)·t)·e^(−ωt)，d = 起点 − 终点。
 */
private object SheetPresentationMotion : FloatAnimationSpec {
    private const val DurationNanos = 500L * 1_000_000L
    private val omega = sqrt(1000.0 / 3.0)

    override fun getValueFromNanos(
        playTimeNanos: Long,
        initialValue: Float,
        targetValue: Float,
        initialVelocity: Float,
    ): Float {
        if (playTimeNanos <= 0L) return initialValue
        if (playTimeNanos >= DurationNanos) return targetValue
        val t = playTimeNanos / 1_000_000_000.0
        val d = initialValue.toDouble() - targetValue
        val c = initialVelocity + omega * d
        return (targetValue + (d + c * t) * exp(-omega * t)).toFloat()
    }

    override fun getVelocityFromNanos(
        playTimeNanos: Long,
        initialValue: Float,
        targetValue: Float,
        initialVelocity: Float,
    ): Float {
        if (playTimeNanos <= 0L) return initialVelocity
        if (playTimeNanos >= DurationNanos) return 0f
        val t = playTimeNanos / 1_000_000_000.0
        val d = initialValue.toDouble() - targetValue
        val c = initialVelocity + omega * d
        return ((initialVelocity - omega * c * t) * exp(-omega * t)).toFloat()
    }

    override fun getDurationNanos(initialValue: Float, targetValue: Float, initialVelocity: Float): Long =
        DurationNanos
}

/**
 * 底部 Action Sheet 式玻璃面板（设置页选择器统一容器）：
 */
@Composable
private fun GlassWheelSheet(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    title: String,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    onConfirm: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    var closing by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var panelTravelPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(expanded) {
        if (expanded) {
            closing = false
            progress.animateTo(1f, SheetPresentationMotion)
        } else if (!closing && progress.value > 0f) {
            //  expanded 被外部直接置 false（系统返回键 → onDismissRequest 等）时，
            // 必须在这里补播收起动画：否则面板停在屏幕上（p > 0），
            // 而 close() 因 !expanded 直接 return —— 所有按钮点击全部失效，面板卡死
            closing = true
            progress.animateTo(0f, SheetPresentationMotion)
        }
    }
    fun close() {
        if (closing || !expanded) return
        closing = true
        scope.launch {
            progress.animateTo(0f, SheetPresentationMotion)
            onExpandedChange(false)
        }
    }
    val p = progress.value
    // 完全关闭（含退出动画播完）后彻底移出组合：透明遮罩层若残留，
    // 面板区域的无操作 clickable 会吃掉触摸 → 设置页「卡死」
    if (!expanded && p == 0f) return

    //  深浅色只认 App 主题：GlasmioSheetSurface 在 Dialog 窗口里采样不到页面背景，
    // 会稳定判成深色 —— 面板就永远是黑玻璃，跟随主题必须用 ios27Glass + 显式 isDark
    val isDarkPanel = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val panelSurface = rememberIos27Surface(Ios27Glass.Large)
    //  Dialog 在独立窗口里，LayerBackdrop 的 localPositionOf 跨不了 compose owner，
    // 直接传 pageLayer 会「采样不到任何东西」→ 玻璃退化成一块纯色。
    // 库自己的 sheet 也必须套这层（IosOverlays.kt: rememberCrossWindowBackdrop）
    val dialogBackdrop = rememberCrossWindowBackdrop(backdrop)
    // sheet 自己的像素材质层：顶栏那两个玻璃钮是浮在 sheet 上的，
    // 该采的是这块「sheet 面」，不是页面背景（空 backdrop 会采到空纹理 → 按钮渲染成黑的）
    val sheetBackdrop = rememberLayerBackdrop()
    // 即浅色下卡片比白玻璃暗一档、深色下亮一档；分隔线用 iOS 原生 sheet 色
    val cellTint = if (isDarkPanel) Color.White else Color(0xFF3C3C43)
    val cellAlpha = 0.06f
    val sheetSeparator =
        if (isDarkPanel) Color(0xFF545458).copy(alpha = 0.5f)
        else Color(0xFF3C3C43).copy(alpha = 0.12f)
    val panelColors = GlasmioTheme.colors.copy(
        elevatedBackground = cellTint,
        separator = sheetSeparator,
    )

    Dialog(
        onDismissRequest = { onExpandedChange(false) },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { dialogWindow?.setDimAmount(0f) }

        //  Dialog 是独立窗口，拿不到调用点的 LocalGlassBackdrop；
        // 而 GlasmioTopBar 的 actions 默认 Merged → GlasmioToolbarActionGroup
        // 读 LocalGlassBackdrop 时没有宿主就抛
        // "Glass controls must be hosted by GlasmioBackdropHost or GlasmioBackdropProvider"
        // → 二级页面（独立 Activity，SubPageTheme 里没有 provider）点开选择器直接崩。
        // 这里补一层：默认给 sheet 自己的面（顶栏胶囊就浮在它上面）
        CompositionLocalProvider(LocalGlassBackdrop provides sheetBackdrop) {

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.2f * p))
                .clickable(
                    enabled = !closing,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { close() },
            contentAlignment = Alignment.BottomCenter,
        ) {
            // 到窗口/屏幕底部的真实距离：容器高 − 面板顶边 y。
            //  不能用面板自身高度——面板外面还有 8dp 下边距（可能还有窗口 inset），
            //    只位移「高度」的话底部会留出那一小截（入场/出场都能看见）
            val containerHeightPx = constraints.maxHeight.toFloat()
            Column(
                Modifier
                    .fillMaxWidth()
                    //  固定下边距，不要 navigationBarsPadding()：
                    //    系统手势条会把面板整体再顶上去，底部就会空出一大截
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 8.dp)
                    .onGloballyPositioned { coords ->
                        val d = (containerHeightPx - coords.positionInParent().y)
                            .coerceAtLeast(0f)
                        if (abs(d - panelTravelPx) > 0.5f) panelTravelPx = d
                    }
                    .graphicsLayer {
                        translationY = (1f - p) * panelTravelPx
                        // 首帧还没量到位置：先藏起来，避免面板闪一下
                        alpha = if (panelTravelPx > 0f) 1f else 0f
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { }
                    .ios27Glass(
                        surface = panelSurface,
                        backdrop = dialogBackdrop,
                        shape = { SheetPanelShape },
                        // Glasmio 校准的玻璃容器色（浅 0.64 / 深 0.54 透明度）——
                        // 旧的 glassOverlay@0.85 会把玻璃盖成实色卡片
                        containerColor = panelColors.container,
                        isDark = isDarkPanel,
                        // 把面板自己的像素材质导出，顶栏按钮采样它
                        exportedBackdrop = sheetBackdrop,
                    ),
            ) {
                Text(
                    title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = GlasmioTheme.colors.content,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                )
                CompositionLocalProvider(
                    LocalGroupedListBackgroundAlpha provides cellAlpha,
                    LocalGlassColors provides panelColors,
                ) {
                    GlasmioGroupedList(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(top = 12.dp),
                    ) {
                        Column(Modifier.fillMaxWidth()) { content() }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GlasmioAlertButton(
                        "取消",
                        onClick = { close() },
                        modifier = Modifier.weight(1f),
                        role = GlasmioAlertButtonRole.Cancel,
                        enabled = !closing,
                    )
                    GlasmioAlertButton(
                        "确定",
                        onClick = {
                            // 收起动画期间不再接受确认（与遮罩的 enabled=!closing 同语义）
                            if (!closing) {
                                onConfirm()
                                close()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !closing,
                    )
                }
            }
        }
        } // CompositionLocalProvider(LocalGlassBackdrop provides sheetBackdrop)
    }
}

/**
 * 单轮齿轮选择器（底部面板）：当前的周数 / 本学期总周数。
 */
@Composable
fun GlassWheelPicker(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    title: String,
    value: Float,
    range: List<Float>,
    backdrop: Backdrop,
    onConfirm: (Float) -> Unit,
    modifier: Modifier = Modifier,
    unit: String = "周",
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    var pending by remember(value, expanded) { mutableFloatStateOf(value) }
    GlassWheelSheet(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        title = title,
        backdrop = backdrop,
        modifier = modifier,
        onConfirm = { onConfirm(pending) },
    ) {
        // 轮子独享 overscroll 工厂，不与设置页列表共用（RubberBand 工厂是单例）
        val wheelFactory = rememberOffsetOverscrollFactory(Orientation.Vertical)
        CompositionLocalProvider(LocalOverscrollFactory provides wheelFactory) {
            NumberPicker(
                value = pending,
                onValueChange = { pending = it },
                range = range,
                label = unit,
                isLight = isLight,
                backdropParam = backdrop,
            )
        }
    }
}

/**
 * 三轮日期选择器（底部面板）：年 / 月 / 日，天数随年月联动（2 月按闰年算）。
 */
@Composable
fun GlassDateWheelPicker(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    title: String,
    date: LocalDate,
    backdrop: Backdrop,
    onConfirm: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    var year by remember(date, expanded) { mutableIntStateOf(date.year) }
    var month by remember(date, expanded) { mutableIntStateOf(date.monthValue) }
    var day by remember(date, expanded) { mutableIntStateOf(date.dayOfMonth) }

    //  年份范围绝不能跟着 year 变：滚动更新 year → 列表以新年份重建 →
    // NumberPicker 检测到 index 变化又强制 scrollToItem，与手指打架 → 数字跳变。
    // 锚定在「打开面板时的年份」，固定 ±2 年窗口（共 5 项），滚动期间列表保持稳定
    val baseYear = remember(date, expanded) { date.year }
    val years = remember(baseYear) { ((baseYear - 2)..(baseYear + 2)).map { it.toFloat() } }
    val months = remember { (1..12).map { it.toFloat() } }
    val daysInMonth = remember(year, month) {
        YearMonth.of(year, month).lengthOfMonth()
    }
    val days = remember(daysInMonth) { (1..daysInMonth).map { it.toFloat() } }
    val safeDay = day.coerceIn(1, daysInMonth)

    GlassWheelSheet(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        title = title,
        backdrop = backdrop,
        modifier = modifier,
        onConfirm = { onConfirm(LocalDate.of(year, month, safeDay)) },
    ) {
        //  三个轮子各自独享一份 overscroll 工厂（RubberBand 工厂是单例，不能共用）
        val yearFactory = rememberOffsetOverscrollFactory(Orientation.Vertical)
        val monthFactory = rememberOffsetOverscrollFactory(Orientation.Vertical)
        val dayFactory = rememberOffsetOverscrollFactory(Orientation.Vertical)

        Row(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .weight(1.1f)
                    // 8dp 水平内边距 → 三个轮子的灰色遮罩之间留出 16dp 间距
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("年", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CompositionLocalProvider(LocalOverscrollFactory provides yearFactory) {
                    NumberPicker(
                        value = year.toFloat(),
                        onValueChange = { year = it.toInt() },
                        range = years,
                        label = "年",
                        isLight = isLight,
                        backdropParam = backdrop,
                    )
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("月", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CompositionLocalProvider(LocalOverscrollFactory provides monthFactory) {
                    NumberPicker(
                        value = month.toFloat(),
                        onValueChange = { month = it.toInt().coerceIn(1, 12) },
                        range = months,
                        label = "月",
                        isLight = isLight,
                        backdropParam = backdrop,
                    )
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("日", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CompositionLocalProvider(LocalOverscrollFactory provides dayFactory) {
                    NumberPicker(
                        value = safeDay.toFloat(),
                        onValueChange = { day = it.toInt().coerceIn(1, daysInMonth) },
                        range = days,
                        label = "日",
                        isLight = isLight,
                        backdropParam = backdrop,
                    )
                }
            }
        }
    }
}

/**
 * 选项齿轮选择器（底部面板）：选项以文字标签上轮（如 周一/周日、跟随系统/浅色/深色），
 * 滚动选中后按 ✓ 提交。
 */
@Composable
fun GlassOptionPicker(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    title: String,
    options: List<Pair<Int, String>>,
    current: Int,
    backdrop: Backdrop,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    var pending by remember(current, expanded) { mutableIntStateOf(current) }
    GlassWheelSheet(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        title = title,
        backdrop = backdrop,
        modifier = modifier,
        onConfirm = { onSelect(pending) },
    ) {
        // 轮子独享 overscroll 工厂，不与设置页列表共用（RubberBand 工厂是单例）
        val wheelFactory = rememberOffsetOverscrollFactory(Orientation.Vertical)
        CompositionLocalProvider(LocalOverscrollFactory provides wheelFactory) {
            NumberPicker(
                value = pending.toFloat(),
                onValueChange = { pending = it.toInt() },
                range = options.map { it.first.toFloat() },
                label = "",
                isLight = isLight,
                backdropParam = backdrop,
                labels = options.map { it.second },
            )
        }
    }
}

/**
 * 三轮节数选择器（底部面板）：上午 / 下午 / 晚上，每个 1-10，总节数 = 三段之和。
 */
@Composable
fun GlassPeriodCountPicker(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    title: String,
    morning: Int,
    afternoon: Int,
    evening: Int,
    backdrop: Backdrop,
    onConfirm: (morning: Int, afternoon: Int, evening: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    var m by remember(expanded) { mutableIntStateOf(morning) }
    var a by remember(expanded) { mutableIntStateOf(afternoon) }
    var e by remember(expanded) { mutableIntStateOf(evening) }
    val range = remember { (1..10).map { it.toFloat() } }

    GlassWheelSheet(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        title = title,
        backdrop = backdrop,
        modifier = modifier,
        onConfirm = { onConfirm(m, a, e) },
    ) {
        //  三个轮子各自独享一份 overscroll 工厂（同日期选择器）
        val mf = rememberOffsetOverscrollFactory(Orientation.Vertical)
        val af = rememberOffsetOverscrollFactory(Orientation.Vertical)
        val ef = rememberOffsetOverscrollFactory(Orientation.Vertical)
        Row(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .weight(1.1f)
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("上午", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CompositionLocalProvider(LocalOverscrollFactory provides mf) {
                    NumberPicker(
                        value = m.toFloat(),
                        onValueChange = { m = it.toInt().coerceIn(1, 10) },
                        range = range,
                        label = "上午",
                        isLight = isLight,
                        backdropParam = backdrop,
                    )
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("下午", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CompositionLocalProvider(LocalOverscrollFactory provides af) {
                    NumberPicker(
                        value = a.toFloat(),
                        onValueChange = { a = it.toInt().coerceIn(1, 10) },
                        range = range,
                        label = "下午",
                        isLight = isLight,
                        backdropParam = backdrop,
                    )
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("晚上", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CompositionLocalProvider(LocalOverscrollFactory provides ef) {
                    NumberPicker(
                        value = e.toFloat(),
                        onValueChange = { e = it.toInt().coerceIn(1, 10) },
                        range = range,
                        label = "晚上",
                        isLight = isLight,
                        backdropParam = backdrop,
                    )
                }
            }
        }
    }
}

/**
 * 时间范围选择器（底部面板）：开始 / 结束 两个齿轮。
 * 选项 = 06:00–23:00 每 5 分钟一档；结束联动不早于开始。
 */
@Composable
fun GlassTimeRangePicker(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    title: String,
    startTime: String,
    endTime: String,
    backdrop: Backdrop,
    onConfirm: (start: String, end: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val times = remember {
        buildList {
            var h = 6; var m = 0
            while (h < 23 || (h == 23 && m == 0)) {
                add("%02d:%02d".format(h, m))
                m += 5
                if (m == 60) { m = 0; h++ }
            }
        }
    }

    fun indexOf(time: String): Int {
        val parts = time.split(":")
        val total = ((parts.getOrNull(0)?.toIntOrNull() ?: 6) * 60) +
            (parts.getOrNull(1)?.toIntOrNull() ?: 0)
        val clamped = total.coerceIn(6 * 60, 23 * 60)
        return ((clamped - 6 * 60) / 5).coerceIn(0, times.lastIndex)
    }

    var sIdx by remember(expanded, startTime) { mutableIntStateOf(indexOf(startTime)) }
    var eIdx by remember(expanded, endTime) { mutableIntStateOf(indexOf(endTime)) }

    GlassWheelSheet(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        title = title,
        backdrop = backdrop,
        modifier = modifier,
        onConfirm = { onConfirm(times[sIdx], times[maxOf(eIdx, sIdx)]) },
    ) {
        //  两个轮子各自独享一份 overscroll 工厂（同日期选择器）
        val sf = rememberOffsetOverscrollFactory(Orientation.Vertical)
        val ef = rememberOffsetOverscrollFactory(Orientation.Vertical)
        Row(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("开始", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CompositionLocalProvider(LocalOverscrollFactory provides sf) {
                    NumberPicker(
                        value = sIdx.toFloat(),
                        onValueChange = {
                            sIdx = it.toInt().coerceIn(0, times.lastIndex)
                            if (eIdx < sIdx) eIdx = sIdx
                        },
                        range = (0..times.lastIndex).map { it.toFloat() },
                        label = "开始",
                        isLight = isLight,
                        backdropParam = backdrop,
                        labels = times,
                    )
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("结束", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CompositionLocalProvider(LocalOverscrollFactory provides ef) {
                    NumberPicker(
                        value = maxOf(eIdx, sIdx).toFloat(),
                        onValueChange = { eIdx = it.toInt().coerceIn(sIdx, times.lastIndex) },
                        range = (0..times.lastIndex).map { it.toFloat() },
                        label = "结束",
                        isLight = isLight,
                        backdropParam = backdrop,
                        labels = times,
                    )
                }
            }
        }
    }
}

/**
 * 居中 300dp 玻璃卡 + 压暗 + 缩放淡入，iOS 式并排/纵排按钮；
 * showField=false 时为纯确认 Alert，带字段时为 Input Alert。
 * 参数契约与旧版一致（8 处调用点零改动）。
 */
@Composable
fun GlassInputSheet(
    visible: Boolean = true,
    title: String,
    hint: String,
    text: String,
    onTextChange: (String) -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    // false = 纯提示/确认面板（删除确认等），不显示输入框
    showField: Boolean = true,
    minLines: Int = 3,
    extraLabel: String? = null,
    onExtra: (() -> Unit)? = null,
    // true = 「确定」按钮用错误色（删除等破坏性操作）
    destructive: Boolean = false,
) {
    // 内部持有 TextFieldValue（保光标位置）；text 仅在外部被重置时跟随
    var field by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    var syncedText by remember { mutableStateOf(text) }
    if (text != syncedText) {
        syncedText = text
        field = TextFieldValue(text, TextRange(text.length))
    }

    GlasmioAlertDialog(
        visible = visible,
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = title,
        message = hint,
        backdrop = backdrop,
        buttonLayout = if (extraLabel != null) {
            GlasmioAlertButtonLayout.Stacked
        } else {
            GlasmioAlertButtonLayout.SideBySide
        },
        buttons = buildList {
            add(GlasmioAlertButtonSpec("取消", onDismiss, role = GlasmioAlertButtonRole.Cancel))
            if (extraLabel != null) {
                add(GlasmioAlertButtonSpec(extraLabel, { onExtra?.invoke() }))
            }
            add(
                GlasmioAlertButtonSpec(
                    confirmLabel,
                    onConfirm,
                    role = if (destructive) {
                        GlasmioAlertButtonRole.Destructive
                    } else {
                        GlasmioAlertButtonRole.Default
                    },
                ),
            )
        },
    ) {
        if (showField) {
            GlasmioAlertFieldGroup {
                GlasmioAlertTextField(
                    value = field,
                    onValueChange = {
                        if (!readOnly) {
                            field = it
                            syncedText = it.text
                            onTextChange(it.text)
                        }
                    },
                    // 多行时撑高字段区（库默认固定 52dp；heightIn 与内部 height 取大者）
                    modifier = if (minLines > 1) {
                        Modifier.heightIn(min = (52 * minLines).dp)
                    } else {
                        Modifier
                    },
                    singleLine = minLines <= 1,
                )
            }
        }
    }
}

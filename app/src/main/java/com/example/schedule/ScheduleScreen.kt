package com.example.schedule

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.schedule.ui.glass.GlassCapsuleButton
import com.example.schedule.ui.glass.ios27Glass
import com.example.schedule.ui.glass.Ios27Glass
import com.example.schedule.ui.glass.NumberPickerInlineContent
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.ui.GlasmioMenu
import com.example.schedule.ui.glass.rememberIos27Surface
import com.example.schedule.ui.glass.rememberRubberBandState
import com.example.schedule.ui.glass.RubberBandContainer
import com.example.schedule.ui.glass.topProgressiveBlurScrim
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.layerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import io.github.neoruaa.glasmio.glass.render.drawBackdrop
import io.github.neoruaa.glasmio.glass.render.effects.blur
import io.github.neoruaa.glasmio.glass.render.effects.lens
import io.github.neoruaa.glasmio.glass.render.effects.vibrancy
import io.github.neoruaa.glasmio.ui.GlasmioAlertDialog
import io.github.neoruaa.glasmio.ui.GlasmioAlertButtonRole
import io.github.neoruaa.glasmio.ui.GlasmioAlertButtonSpec
import io.github.neoruaa.glasmio.ui.GlasmioAlertFieldGroup
import io.github.neoruaa.glasmio.core.IosTypography
import io.github.neoruaa.glasmio.glass.InteractiveHighlight
import io.github.neoruaa.glasmio.glass.applyGlassDragScale
import com.kyant.capsule.ContinuousRoundedRectangle
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedCornerStyle
import com.kyant.shapes.RoundedRectangle
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DayNames = listOf("一", "二", "三", "四", "五", "六", "日")
private val RangeFormatter = DateTimeFormatter.ofPattern("M/d")

private const val LabelColWidth = 30
private const val RowHeight = 60

/** 顶部两段玻璃栏（日期行 + 星期玻璃块行）总高，含上下留白与间隙。 */
private const val TopBarsHeightDp = 128

/** 顶部模糊遮罩高度：底缘刚好到日期玻璃块的底部（statusBar + 116dp）。 */
private const val TopScrimHeightDp = 116

/** iOS 风格缓动（与页面切换一致）。 */
private val iosEaseOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

@Composable
fun ScheduleScreen(store: Store, onOpenEditor: (Course?, Int, Int) -> Unit = { _, _, _ -> }) {
    // 空格子两段式新建：第一下出现灰色「＋」卡片标记位置，再点同一格才进入编辑。
    // 编辑/新增卡片本体由根级（AppRoot）承载，盖住底部导航栏
    var ghostCell by remember { mutableStateOf<Pair<Int, Int>?>(null) } // day to startPeriod
    var ghostPage by remember { mutableIntStateOf(0) }

    // 课程详情弹窗：点课程卡片 → 居中玻璃卡片看详情，不直接进编辑器；
    // 同一天、时间段重叠的多门课（不同周次「同一位置」）左右滑动切换
    var detailGroup by remember { mutableStateOf<List<Course>?>(null) }
    var detailStartIndex by remember { mutableIntStateOf(0) }
    var detailWeek by remember { mutableIntStateOf(1) }

    // 「今天」做成状态：每 30 秒检查一次，App 开着过零点也会自动切到新的一天
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            LocalDate.now().takeIf { it != today }?.let { today = it }
        }
    }

    // 选中的日期（默认今天）；点击顶部日期玻璃块切换；非今日时出现“回到今日”
    var selectedDate by remember { mutableStateOf(today) }

    // 周次选择：Glasmio 原生菜单（锚点 = 「第N周」胶囊，液态变形由库内置）+ 3D 轮子选择器
    val showWeekPicker = remember { mutableStateOf(false) }
    var pendingWeek by remember { mutableFloatStateOf(1f) }

    //  GlasmioMenu 的弹层高度不测内容（= 20dp + 44dp × itemCount），
    // 内容 = 顶部按钮行(18+44) + 间距16 + 轮子高度 + 底部留白16
    val wheelRows = if (LocalConfiguration.current.screenHeightDp < 480) 5 else 7
    val wheelHalf = wheelRows / 2
    val itemH = 40f
    val wheelExtent = (wheelHalf + 1) * itemH
    val quarter = Math.toRadians(90.0).toFloat()
    val wheelArc = sin(wheelHalf * itemH / wheelExtent * quarter) * (wheelExtent / quarter)
    val weekMenuHeight = (18f + 44f + 16f + itemH + 2f * wheelArc + 16f).dp

    val config = store.config
    val colors = MaterialTheme.colorScheme
    val lineColor = colors.outlineVariant
    val thisWeek = weekOf(today, config.startDate).coerceAtLeast(1)
    val totalWeeks = maxOf(
        thisWeek,
        store.courses.maxOfOrNull { c -> c.weeks.maxOrNull() ?: 1 } ?: 1,
        config.totalWeeks,
    )

    // 课表列：从每周起始日开始；关闭“显示周末”时去掉周六周日
    val columns = weekDayOrder(config.weekStartDay).let { order ->
        if (config.showWeekend) order else order.filter { it <= 5 }
    }

    // ── 顶部遮罩尺寸──
    val density = LocalDensity.current
    val statusBarHeightPx = WindowInsets.statusBars.getTop(density).toFloat()
    val topBarsHeightPx = with(density) { TopBarsHeightDp.dp.toPx() }
    val maskBottomPx = statusBarHeightPx + topBarsHeightPx
    val maskHeightDp = with(density) { maskBottomPx.toDp() }
    val maskVisualHeightDp = with(density) { (statusBarHeightPx + TopScrimHeightDp.dp.toPx()).toDp() }

    // 橡皮筋状态 + 玻璃专用 backdrop 包装：
    // 拉伸/回弹期间 offset 逐帧变化 → 强制玻璃逐帧重新采样背景，不再冻结。
    val rubberBandState = rememberRubberBandState()
    val contentBackdrop = rememberLayerBackdrop()
    val glassOnDraw = remember(rubberBandState) {
        val block: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit = { drawBackdrop ->
            rubberBandState.offset
            drawBackdrop()
        }
        block
    }
    val glassBackdrop = rememberBackdrop(contentBackdrop, glassOnDraw)
    // 顶栏（日期文字 + 星期玻璃块）单独录一层：周次选择弹窗的玻璃要采样它。
    // 顶栏因玻璃铁律不能进主录制层（星期玻璃块自己采样 glassBackdrop 会自引用），
    // 但把它「画好的结果」录进另一层没有环 —— 弹窗玻璃采样两层合成即可

    // ── 可预测式翻页：课程内容跟手拖动、松手吸附整周 ──
    var pagerCount by remember { mutableIntStateOf(totalWeeks) }
    SideEffect { pagerCount = totalWeeks }
    // 默认定位到「今日所在周」（thisWeek 从 1 计数，pager 从 0 计数）
    val pagerState = rememberPagerState(
        initialPage = (thisWeek - 1).coerceIn(0, (totalWeeks - 1).coerceAtLeast(0)),
        pageCount = { pagerCount },
    )
    // 翻周后提示卡片自动消失
    LaunchedEffect(pagerState.currentPage) { ghostCell = null }
    val scope = rememberCoroutineScope()
    val currentWeekState = pagerState.currentPage + 1
    val weekFirstDay = firstDayOfWeek(currentWeekState, config.startDate)

    Box(modifier = Modifier.fillMaxSize()) {
        // ── 内容层（录制进 backdrop，供顶部遮罩与玻璃组件采样）──
        //  玻璃铁律：顶部玻璃栏 / 遮罩必须在录制层【外】，否则自引用环 → RenderThread 栈溢出
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(contentBackdrop)
                .background(colors.background)
        ) {
            // 整页橡皮筋：顶部下拉 / 底部上拉出现 iOS 式弹性拉伸
            RubberBandContainer(state = rubberBandState, modifier = Modifier.fillMaxSize()) {
                // 课表窗口：左右留边、四周裁圆角。
                //  横向翻页完全交给下面的 HorizontalPager：这里不要挂
                // detectHorizontalDragGestures —— 两套横拖手势会互相抢占，
                // 松手还叠加一次 animateScrollToPage，连续滑动明显掉帧。
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp)
                        .clip(ContinuousRoundedRectangle(20.dp))
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        // 让出顶部玻璃栏区域（滚动 / 拉伸时课程从其底下穿过）
                        Spacer(Modifier.height(maskHeightDp))

                        Row(Modifier.fillMaxWidth()) {
                            // ── 左侧节次列：固定不动，翻页时只有课程内容滑动 ──
                            Column(Modifier.width(LabelColWidth.dp)) {
                                config.periods.forEach { pt ->
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(RowHeight.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                "${pt.period}",
                                                fontSize = 16.sp,
                                                lineHeight = 18.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.onSurface,
                                            )
                                            Text(
                                                pt.start,
                                                fontSize = 8.5.sp,
                                                lineHeight = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.onSurfaceVariant,
                                            )
                                            Text(
                                                pt.end,
                                                fontSize = 8.5.sp,
                                                lineHeight = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }

                            // ── 右侧课程区：横向 Pager，内容跟手、松手吸附整周 ──
                            // 高度必须显式给定（节数×行高）：matchParentSize 在可滚动
                            // 容器里测出 0 高，Pager 和分隔线会整体不可见
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height((config.periods.size * RowHeight).dp)
                            ) {
                                // 静态虚线分隔（画在 Pager 下层，不随翻页移动）：
                                // 顶部一道、每两节之间一道、最底部一道
                                Column(Modifier.matchParentSize()) {
                                    // 虚线效果只建一次（每帧新建会有 GC 抖动）
                                    val dashEffect = remember(density) {
                                        with(density) {
                                            PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
                                        }
                                    }
                                    val dashStroke = with(density) { 1.dp.toPx() }
                                    config.periods.forEachIndexed { rowIdx, _ ->
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .height(RowHeight.dp)
                                                .drawBehind {
                                                    if (rowIdx == 0) {
                                                        drawLine(
                                                            color = lineColor,
                                                            start = Offset(0f, 0f),
                                                            end = Offset(size.width, 0f),
                                                            strokeWidth = dashStroke,
                                                            pathEffect = dashEffect,
                                                        )
                                                    }
                                                    drawLine(
                                                        color = lineColor,
                                                        start = Offset(0f, size.height),
                                                        end = Offset(size.width, size.height),
                                                        strokeWidth = dashStroke,
                                                        pathEffect = dashEffect,
                                                    )
                                                }
                                        )
                                    }
                                }

                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier.matchParentSize(),
                                ) { page ->
                                    val pageWeek = page + 1
                                    // 页面课程：非本周的先画（垫底），本周的后画（盖在上层）
                                    // 直接以课程列表本身做 key：编辑已有课程（数量不变）也会
                                    // 换来新的列表实例 → 网格内容随之刷新
                                    val pageCourses = remember(page, store.courses, store.config) {
                                        buildList {
                                            val inWeek = store.courses
                                                .filter { pageWeek in it.weeks }
                                                .sortedBy { it.id }
                                            if (config.showOtherWeekCourses) {
                                                store.courses.filter { pageWeek !in it.weeks }
                                                    .sortedBy { it.id }
                                                    //  与本周课程同一天且时间重叠的非本周课不再垫底渲染：
                                                    // 反正整张都被压在下面看不见，叠着只会出缺口三角、看着杂乱
                                                    .filter { nc ->
                                                        inWeek.none { c ->
                                                            c.day == nc.day &&
                                                                c.startPeriod <= nc.endPeriod &&
                                                                nc.startPeriod <= c.endPeriod
                                                        }
                                                    }
                                                    .forEach { add(it to false) }
                                            }
                                            inWeek.forEach { add(it to true) }
                                        }
                                    }
                                    BoxWithConstraints(Modifier.fillMaxSize()) {
                                        val colWidth = maxWidth / columns.size

                                        // 空格子点击层：第一下放置灰色「＋」提示卡片，再点同一格进入新建
                                        Column(Modifier.fillMaxSize()) {
                                            config.periods.forEach { pt ->
                                                Row(Modifier.fillMaxWidth().height(RowHeight.dp)) {
                                                    columns.forEach { day ->
                                                        Box(
                                                            Modifier
                                                                .weight(1f)
                                                                .height(RowHeight.dp)
                                                                .clickable(
                                                                    // 空格子不要灰色涟漪反馈
                                                                    interactionSource = remember { MutableInteractionSource() },
                                                                    indication = null,
                                                                ) {
                                                                    if (ghostCell?.first == day &&
                                                                        ghostCell?.second == pt.period &&
                                                                        ghostPage == page
                                                                    ) {
                                                                        onOpenEditor(null, day, pt.period)
                                                                        ghostCell = null
                                                                    } else {
                                                                        ghostCell = day to pt.period
                                                                        ghostPage = page
                                                                    }
                                                                }
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // 灰色「＋」提示卡片：标记空格子两段式新建的位置
                                        ghostCell?.takeIf { ghostPage == page }?.let { (gDay, gPeriod) ->
                                            val gCol = columns.indexOf(gDay)
                                            if (gCol >= 0) {
                                                GhostCellCard(
                                                    onClick = {
                                                        onOpenEditor(null, gDay, gPeriod)
                                                        ghostCell = null
                                                    },
                                                    modifier = Modifier
                                                        .offset(
                                                            x = colWidth * gCol + 1.dp,
                                                            y = ((gPeriod - 1) * RowHeight).dp + 1.dp,
                                                        )
                                                        .width(colWidth - 2.dp)
                                                        .height(RowHeight.dp - 2.dp),
                                                )
                                            }
                                        }

                                        // 课程卡片：一门课一块，渲染顺序：非本周垫底、本周居顶，
                                        // 同层按 id；与本周课时间重叠的非本周课已在 pageCourses 过滤掉
                                        pageCourses.forEachIndexed { i, (course, inWeek) ->
                                            val colIdx = columns.indexOf(course.day)
                                            if (colIdx >= 0) {
                                                CourseBlock(
                                                    course = course,
                                                    inWeek = inWeek,
                                                    onClick = {
                                                        ghostCell = null
                                                        // 同一天、时间段与所点课程重叠的全部课程
                                                        //（含不同周次「同一位置」的课），左右滑动切换；
                                                        // 本周有的排前面，其余按 id
                                                        val group = store.courses
                                                            .filter {
                                                                it.day == course.day &&
                                                                    it.startPeriod <= course.endPeriod &&
                                                                    it.endPeriod >= course.startPeriod
                                                            }
                                                            .sortedWith(
                                                                compareByDescending<Course> { pageWeek in it.weeks }
                                                                    .thenBy { it.id }
                                                            )
                                                        detailWeek = pageWeek
                                                        detailStartIndex =
                                                            group.indexOfFirst { it.id == course.id }
                                                                .coerceAtLeast(0)
                                                        detailGroup = group
                                                    },
                                                    modifier = Modifier
                                                        .offset(
                                                            x = colWidth * colIdx + 1.dp,
                                                            y = ((course.startPeriod - 1) * RowHeight).dp + 1.dp,
                                                        )
                                                        .width(colWidth - 2.dp)
                                                        .height(((course.endPeriod - course.startPeriod + 1) * RowHeight).dp - 2.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 底部留白：Tabs + 橡皮筋回弹空间
                        Spacer(Modifier.height(140.dp))
                    }
                }
            }
        }

        // ── 顶部渐进式模糊遮罩（渐进式模糊）——录制层【外】──
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(maskVisualHeightDp)
                .topProgressiveBlurScrim(
                    backdrop = glassBackdrop,
                    tint = colors.background,
                    opacity = { 1f }
                )
        )

        // ── 顶部玻璃栏：左上角所选日期（翻滚动画）+ 最右周次选择器 ──
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 日期 + “今日”：数字变化为上下翻滚（旧数字上移模糊消失，新数字自下而上出现；
                // 方向按日期先后），整段带模糊
                Box(
                    Modifier
                        .weight(1f)
                        .height(48.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    var shownDate by remember { mutableStateOf(today) }
                    val rollProgress = remember { Animatable(0f) }
                    var rollDir by remember { mutableIntStateOf(1) }

                    LaunchedEffect(selectedDate) {
                        if (selectedDate != shownDate) {
                            rollDir = if (selectedDate > shownDate) 1 else -1
                            rollProgress.snapTo(0f)
                            rollProgress.animateTo(1f, tween(250, easing = iosEaseOut))
                            shownDate = selectedDate
                            rollProgress.snapTo(0f)
                        }
                    }

                    val rolling = shownDate != selectedDate
                    // 「回到今日」胶囊显隐动画：
                    // 280ms 弹大到 1.18 → 向内聚缩到 0 → 模糊消失；出现为反向弹入
                    val backAnim by animateFloatAsState(
                        targetValue = if (selectedDate != today || currentWeekState != thisWeek) 0f else 1f,
                        animationSpec = tween(280, easing = FastOutSlowInEasing),
                        label = "backBtnDisappear",
                    )
                    val bp = backAnim
                    // 胶囊出现时给日期文字让位（66dp 胶囊 + 8dp 间距），随动画同步收放 ——
                    // 窄屏 / 大字体下日期文字再长也不会压到胶囊底下
                    val dateEndPad = with(density) { (74f * (1f - bp)).dp }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(end = dateEndPad),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (rolling) {
                            val p = rollProgress.value
                            val later = rollDir == 1
                            val textHeightPx = with(density) { 36.dp.toPx() }
                            // 模糊值按 2.5dp 步进量化：避免逐帧新建 Modifier.blur 的
                            // RenderEffect / 硬件层（翻滚 250ms 内最多 4 次重建）
                            val blurOut = ((p * 4).toInt() * 2.5f).dp
                            val blurIn = (((1f - p) * 4).toInt() * 2.5f).dp
                            DateText(
                                date = shownDate,
                                showToday = shownDate == today,
                                translationYPx = (if (later) -1f else 1f) * p * textHeightPx,
                                alpha = 1f - p,
                                blurDp = blurOut,
                            )
                            DateText(
                                date = selectedDate,
                                showToday = selectedDate == today,
                                translationYPx = (if (later) 1f else -1f) * (1f - p) * textHeightPx,
                                alpha = p,
                                blurDp = blurIn,
                            )
                        } else {
                            DateText(
                                date = shownDate,
                                showToday = shownDate == today,
                                translationYPx = 0f,
                                alpha = 1f,
                                blurDp = 0.dp,
                            )
                        }
                    }

                    // “回到今日”胶囊显隐动画
                    if (selectedDate != today || currentWeekState != thisWeek || bp < 0.999f) {
                        Box(
                            Modifier
                                .align(Alignment.CenterEnd)
                                .size(width = 66.dp, height = 42.dp)
                                .graphicsLayer {
                                    val scale = if (bp <= 0.25f) {
                                        1f + 0.18f * (bp / 0.25f)
                                    } else {
                                        1.18f * (1f - (bp - 0.25f) / 0.75f)
                                    }
                                    scaleX = scale
                                    scaleY = scale
                                    alpha = if (bp < 0.5f) 1f else (1f - (bp - 0.5f) / 0.5f)
                                    // 模糊半径按 4px 步进量化：连续变化的 renderEffect
                                    // 会逐帧分配 BlurEffect + 重建硬件层，非常拖帧
                                    val blur = if (bp < 0.1f) 0f
                                    else (16f * ((bp - 0.1f) / 0.6f).coerceIn(0f, 1f))
                                    val qBlur = (blur / 4f).toInt() * 4f
                                    renderEffect = if (qBlur > 0.5f)
                                        BlurEffect(qBlur, qBlur, TileMode.Decal) else null
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            GlassCapsuleButton(
                                onClick = {
                                    selectedDate = today
                                    val w = weekOf(today, config.startDate).coerceAtLeast(1)
                                    scope.launch { pagerState.animateScrollToPage(w - 1) }
                                },
                                backdrop = glassBackdrop,
                                emphasized = true,
                            ) {
                                Text(
                                    "今日",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                GlasmioMenu(
                    expanded = showWeekPicker.value,
                    onExpandedChange = { showWeekPicker.value = it },
                    itemCount = 1,
                    //  面板与内容统一采样 glassBackdrop（页面整层）：菜单弹窗是独立窗口，
                    // 跨窗口采样组合层 + 内嵌玻璃再采样菜单导出层，会构成 HWUI 渲染树
                    // 循环（prepareTree 递归栈溢出闪退），必须用单一普通层
                    backdrop = glassBackdrop,
                    // 收窄的面板：固定 280dp 宽，锚定「第N周」胶囊
                    menuWidth = 280.dp,
                    // 锚点在屏幕右上角：强制向下展开，否则向上弹会被屏幕顶裁掉
                    forceBelowAnchor = true,
                    heightOverride = weekMenuHeight,
                    anchor = { open ->
                        WeekPickerCapsule(
                            week = currentWeekState,
                            backdrop = glassBackdrop,
                            onExpand = open,
                        )
                    },
                ) { _, close ->
                    NumberPickerInlineContent(
                        value = currentWeekState.toFloat(),
                        onValueChange = { pendingWeek = it },
                        onCancel = { close() },
                        onConfirm = {
                            close()
                            val w = pendingWeek.toInt().coerceIn(1, totalWeeks)
                            scope.launch { pagerState.animateScrollToPage(w - 1) }
                        },
                        range = (1..totalWeeks).map { it.toFloat() },
                        label = "选择周次",
                        isLight = colors.background.luminance() > 0.5f,
                        // 头部按钮采样页面层（不在该层内，无循环）
                        backdrop = glassBackdrop,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // 表头：每天一颗窄高的圆角玻璃块（上“一”下“7”），可点击选中（蓝色描边）
            HeaderDayChips(
                backdrop = glassBackdrop,
                columns = columns,
                weekStartDay = config.weekStartDay,
                weekFirstDay = weekFirstDay,
                today = today,
                selectedDate = selectedDate,
                onSelectDay = { selectedDate = it },
            )
        }

        var lastDetailGroup by remember { mutableStateOf<List<Course>>(emptyList()) }
        SideEffect { detailGroup?.let { lastDetailGroup = it } }
        CourseDetailPopup(
            courses = detailGroup ?: lastDetailGroup,
            visible = detailGroup != null,
            initialIndex = detailStartIndex,
            week = detailWeek,
            config = config,
            backdrop = glassBackdrop,
            onDismiss = { detailGroup = null },
            onEdit = { c ->
                detailGroup = null
                onOpenEditor(c, 0, 0)
            },
        )
    }
}

/**
 * 表头：每天一颗窄高的圆角玻璃块（上“一”下“7”），列对齐课表。
 * 位于录制层之外 —— 真玻璃采样。
 * 今日的块 = 始终蓝色染色玻璃 + 白字；点击其他块 = 蓝色描边（选中）+ 黑字不变。
 */
@Composable
private fun HeaderDayChips(
    backdrop: Backdrop,
    columns: List<Int>,
    weekStartDay: Int,
    weekFirstDay: LocalDate,
    today: LocalDate,
    selectedDate: LocalDate,
    onSelectDay: (LocalDate) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    // Glasmio 主题色：isDark 驱动材质明暗，content 为玻璃上的正文色
    val glassColors = GlasmioTheme.colors
    val isDarkChip = glassColors.isDark
    val chipSurface = rememberIos27Surface(Ios27Glass.Small)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(LabelColWidth.dp))
        columns.forEach { day ->
            val date = weekFirstDay.plusDays(dayOffsetInWeek(day, weekStartDay).toLong())
            val isToday = date == today
            val isSelected = date == selectedDate
            //    弹簧参数照抄 GlassSurface 小按钮档（按下 response .262 / damping .625）
            val chipScope = rememberCoroutineScope()
            // 默认弹簧与 GlassSurface 小按钮一致（pressHighlightSpring）
            val press = remember(chipScope) { InteractiveHighlight(chipScope) }
            Box(
                Modifier
                    .weight(1f)
                    .height(52.dp)
                    .padding(horizontal = 4.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelectDay(date) }
                    .then(press.gestureModifier)
                    .graphicsLayer {
                        applyGlassDragScale(press.pressProgress, Offset.Zero, nativeButtonLift = true)
                    }
            ) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .ios27Glass(
                            surface = chipSurface,
                            backdrop = backdrop,
                            shape = { ContinuousRoundedRectangle(18.dp) },
                            // Glasmio 校准的玻璃容器色（与原生组件一致）
                            containerColor = GlasmioTheme.colors.container,
                            isDark = isDarkChip,
                            backgroundOverlay = {
                                // 今日 = 材质之上叠主色；其他 = 选中时画蓝色描边
                                if (isToday) {
                                    drawRect(colors.primary.copy(alpha = 0.78f))
                                } else if (isSelected) {
                                    // 沿玻璃真实形状（平滑圆角）内缩均匀描边，盖过白色高光
                                    val stroke = 2.dp.toPx()
                                    val inset = 1.dp.toPx()
                                    val inner = ContinuousRoundedRectangle(18.dp).createOutline(
                                        Size(size.width - 2 * inset, size.height - 2 * inset),
                                        layoutDirection, this
                                    )
                                    val selPath = outlinePath(inner).apply {
                                        translate(Offset(inset, inset))
                                    }
                                    drawPath(selPath, color = colors.primary, style = Stroke(width = stroke))
                                }
                            },
                        ),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            DayNames[day - 1],
                            fontSize = 11.sp,
                            lineHeight = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isToday) Color.White else glassColors.content.copy(alpha = 0.70f),
                        )
                        Text(
                            "${date.dayOfMonth}",
                            fontSize = 18.sp,
                            lineHeight = 21.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isToday) Color.White else colors.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/** 周次选择器：玻璃胶囊按钮（“第N周”），作为 GlasmioMenu 的锚点。 */
@Composable
private fun WeekPickerCapsule(
    week: Int,
    backdrop: Backdrop,
    onExpand: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    GlassCapsuleButton(
        onClick = onExpand,
        backdrop = backdrop,
        horizontalPadding = 10.dp,
    ) {
        // 固定最小宽度按两位周数预留：个位 ↔ 十位切换时胶囊尺寸不变（文字居中）
        Box(
            Modifier.widthIn(min = 46.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "第${week}周",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                maxLines = 1,
            )
        }
    }
}

/** 把 Shape 的 Outline 转成 Path（供 drawPath 描边 / 填充）。 */
private fun outlinePath(outline: androidx.compose.ui.graphics.Outline): Path = when (outline) {
    is androidx.compose.ui.graphics.Outline.Generic -> outline.path
    is androidx.compose.ui.graphics.Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
    is androidx.compose.ui.graphics.Outline.Rectangle -> Path().apply { addRect(outline.rect) }
}

/**
 * 课程卡片绘制缓存：Path / Brush 按尺寸构建一次。
 * GC 抖动是滑动掉帧的主要 CPU 来源之一。卡片尺寸基本不变，命中率高。
 */
private class CourseBlockCache {
    var cachedSize: Size = Size.Zero
    var cachedDark: Boolean = false
    lateinit var path: Path
    lateinit var hiPath: Path
    lateinit var topBrush: Brush
    lateinit var hiBrush: Brush
    var hiStroke: Float = 0f

    fun ensure(
        sz: Size,
        shape: ContinuousRoundedRectangle,
        layoutDir: LayoutDirection,
        density: Density,
        dark: Boolean,
    ) {
        if (cachedSize == sz && cachedDark == dark) return
        cachedSize = sz
        cachedDark = dark
        path = outlinePath(shape.createOutline(sz, layoutDir, density))
        hiStroke = 1f * density.density
        hiPath = outlinePath(
            shape.createOutline(Size(sz.width - hiStroke, sz.height - hiStroke), layoutDir, density)
        )
        topBrush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.16f),
            0.35f to Color.Transparent,
        )
        val (hiTop, hiMid, hiBottom) =
            if (dark) Triple(0.55f, 0.14f, 0.22f) else Triple(0.85f, 0.28f, 0.38f)
        hiBrush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = hiTop),
            0.5f to Color.White.copy(alpha = hiMid),
            1f to Color.White.copy(alpha = hiBottom),
        )
    }
}

/**
 * 课程卡片：一门课一块（可跨多节），不透明填充，整块统一：
 */
@Composable
private fun CourseBlock(
    course: Course,
    inWeek: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    val fill = if (inWeek) {
        courseColorOf(course)
    } else {
        // 非本周：浅色模式浅灰卡片 / 深色模式深灰卡片
        if (dark) Color(0xFF2C2C2E) else Color(0xFFE9E9EB)
    }
    val shape = ContinuousRoundedRectangle(16.dp)
    // 本周卡片一律白字（iOS 日历风格，黄色不再例外用黑字）；
    // 非本周卡片灰字：浅色模式深灰字 / 深色模式浅灰字
    val contentColor = if (inWeek) {
        Color.White
    } else {
        if (dark) Color(0xFFAEAEB2) else Color(0xFF636366)
    }
    val renderCache = remember { CourseBlockCache() }

    Box(
        modifier
            .drawBehind {
                renderCache.ensure(size, shape, layoutDirection, this, dark)
                // 1. 不透明填充
                drawPath(renderCache.path, color = fill)
                // 2/3. 本周才有玻璃高光渐变 + 高光描边；非本周纯灰底，避免晃眼
                if (inWeek) {
                    drawPath(renderCache.path, brush = renderCache.topBrush)
                    // 沿边缘一圈、上亮下暗的渐变（模拟玻璃组件的
                    // Highlight.Default 内描边），内缩半线宽保证画在卡片边界内
                    translate(renderCache.hiStroke / 2f, renderCache.hiStroke / 2f) {
                        drawPath(
                            renderCache.hiPath,
                            brush = renderCache.hiBrush,
                            style = Stroke(renderCache.hiStroke),
                        )
                    }
                }
            }
            .clickable(
                // 去掉安卓原版灰色涟漪，点击反馈交给卡片自身的按压效果
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 3.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (!inWeek) {
                Text(
                    "非本周",
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor.copy(alpha = 0.95f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                course.name,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            if (course.room.isNotBlank()) {
                Text(
                    "@${course.room}",
                    fontSize = 8.sp,
                    lineHeight = 10.sp,
                    color = contentColor.copy(alpha = 0.88f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
            if (course.teacher.isNotBlank()) {
                Text(
                    course.teacher,
                    fontSize = 8.sp,
                    lineHeight = 10.sp,
                    color = contentColor.copy(alpha = 0.80f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 空格子两段式新建的提示卡片：灰色圆角块 + 居中「＋」，
 * 出现时带一个快速缩放淡入（150ms），点击进入新建课程。
 */
@Composable
private fun GhostCellCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    val fill = if (dark) Color(0xFF58585E) else Color(0xFFC7C7CB)
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, tween(150, easing = FastOutSlowInEasing))
    }
    Box(
        modifier
            .graphicsLayer {
                val p = appear.value
                scaleX = 0.7f + 0.3f * p
                scaleY = 0.7f + 0.3f * p
                alpha = p
            }
            .drawBehind {
                val shape = ContinuousRoundedRectangle(16.dp)
                val path = outlinePath(shape.createOutline(size, layoutDirection, this))
                drawPath(path, fill.copy(alpha = 0.92f))
                drawPath(path, Color.White.copy(alpha = 0.30f), style = Stroke(0.5.dp.toPx()))
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(22.dp)) {
            val w = 2.5.dp.toPx()
            val mid = size.minDimension / 2f
            val half = size.minDimension * 0.28f
            drawLine(Color.White, Offset(mid - half, mid), Offset(mid + half, mid), w, StrokeCap.Round)
            drawLine(Color.White, Offset(mid, mid - half), Offset(mid, mid + half), w, StrokeCap.Round)
        }
    }
}

/** 顶部日期文本：可带“今日”后缀（蓝色），支持位移 / 透明度 / 模糊的翻滚渲染。 */
@Composable
private fun DateText(
    date: LocalDate,
    showToday: Boolean,
    translationYPx: Float,
    alpha: Float,
    blurDp: Dp,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.graphicsLayer {
            translationY = translationYPx
            this.alpha = alpha
        },
    ) {
        Text(
            "${date.monthValue}月${date.dayOfMonth}日",
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // 空间不足时日期先截断，保住「今日」字样；窄屏 / 大字体不再顶到右侧按钮
            modifier = Modifier
                .weight(1f, fill = false)
                .then(if (blurDp > 0.dp) Modifier.blur(blurDp) else Modifier),
        )
        if (showToday) {
            Text(
                " 今日",
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = colors.primary,
                maxLines = 1,
                modifier = if (blurDp > 0.dp) Modifier.blur(blurDp) else Modifier,
            )
        }
    }
}

/**
 * 同时段多门课（同一天、时间重叠、不同周次）时左右滑动切换，底部圆点指示；
 * 「编辑课程」进入编辑器，点遮罩 / 关闭收起。
 */
@Composable
private fun CourseDetailPopup(
    courses: List<Course>,
    visible: Boolean,
    initialIndex: Int,
    week: Int,
    config: SemesterConfig,
    backdrop: Backdrop,
    onDismiss: () -> Unit,
    onEdit: (Course) -> Unit,
) {
    if (courses.isEmpty()) return
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, courses.lastIndex),
        pageCount = { courses.size },
    )
    // 每次打开都复位到点中的那门课（常驻组合 + visible，退场动画期间保持最后一页）
    LaunchedEffect(visible, initialIndex, courses) {
        if (visible) pagerState.scrollToPage(initialIndex.coerceIn(0, courses.lastIndex))
    }

    GlasmioAlertDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = "",
        backdrop = backdrop,
        titleContent = {
            val current = courses[pagerState.currentPage.coerceIn(0, courses.lastIndex)]
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(courseColorOf(current))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    current.name,
                    style = IosTypography.headline,
                    color = GlasmioTheme.colors.content,
                    textAlign = TextAlign.Center,
                )
            }
        },
        // 排版微调：标题→「第X周有课」收紧 24→12dp，翻页点→按钮放宽 0→12dp，
        // 一减一加，弹窗总高度不变。
        titleBottomPadding = 12.dp,
        contentButtonsSpacing = 12.dp,
        buttons = listOf(
            GlasmioAlertButtonSpec("关闭", onDismiss, role = GlasmioAlertButtonRole.Cancel),
            GlasmioAlertButtonSpec(
                "编辑",
                { onEdit(courses[pagerState.currentPage.coerceIn(0, courses.lastIndex)]) },
            ),
        ),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            // 每页的 GlasmioAlertFieldGroup 是满宽的，默认 pageSpacing = 0 时
            // 翻页中间态两张浅灰卡片会边贴边糊成一块。留 12dp 间隙（与弹窗内
            // 其它间距一致），静止时单页布局不变。
            pageSpacing = 12.dp,
        ) { pageIdx ->
            val course = courses[pageIdx]
            val inWeek = week in course.weeks
            val st = config.periodTimeOf(course.startPeriod)?.start
            val en = config.periodTimeOf(course.endPeriod)?.end
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (inWeek) "第${week}周有课" else "第${week}周无课",
                    fontSize = 12.sp,
                    color = if (inWeek) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        GlasmioTheme.colors.tertiaryContent
                    },
                )
                Spacer(Modifier.height(12.dp))
                GlasmioAlertFieldGroup {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        CourseDetailRow(
                            "时间",
                            "周${"一二三四五六日"[course.day - 1]} 第${course.startPeriod}-${course.endPeriod}节" +
                                if (st != null && en != null) " · $st-$en" else "",
                        )
                        CourseDetailRow("教室", course.room.ifBlank { "—" })
                        CourseDetailRow("老师", course.teacher.ifBlank { "—" })
                        CourseDetailRow("周次", weeksSummary(course.weeks))
                    }
                }
            }
        }
        if (courses.size > 1) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(courses.size) { i ->
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (pagerState.currentPage == i) 7.dp else 5.dp)
                            .clip(CircleShape)
                            .background(
                                if (pagerState.currentPage == i) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    GlasmioTheme.colors.tertiaryContent
                                }
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseDetailRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = GlasmioTheme.colors.secondaryContent,
            modifier = Modifier.width(44.dp),
        )
        Text(value, fontSize = 15.sp, color = GlasmioTheme.colors.content, modifier = Modifier.weight(1f))
    }
}

/** 周次集合 → "1-16周（单）" 式摘要。 */
private fun weeksSummary(weeks: Set<Int>): String {
    val sorted = weeks.sorted()
    if (sorted.isEmpty()) return "—"
    val sb = StringBuilder()
    var i = 0
    while (i < sorted.size) {
        var j = i
        while (j + 1 < sorted.size && sorted[j + 1] == sorted[j] + 1) j++
        if (sb.isNotEmpty()) sb.append("、")
        if (i == j) sb.append(sorted[i]) else sb.append("${sorted[i]}-${sorted[j]}")
        i = j + 1
    }
    sb.append("周")
    if (sorted.all { it % 2 == 1 }) sb.append("（单）")
    else if (sorted.all { it % 2 == 0 }) sb.append("（双）")
    return sb.toString()
}

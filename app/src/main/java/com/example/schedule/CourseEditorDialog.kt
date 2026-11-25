package com.example.schedule

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.schedule.ui.glass.GlassInputSheet
import io.github.fannn_yb.rubberband.overscroll.rememberOffsetOverscrollFactory
import com.example.schedule.ui.glass.NumberPicker
import io.github.neoruaa.glasmio.icons.SfIcon
import io.github.neoruaa.glasmio.core.GlassAppearance
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.core.LocalGlassAppearance
import io.github.neoruaa.glasmio.glass.CrossWindowBackdrop
import io.github.neoruaa.glasmio.glass.GlassEmphasis
import io.github.neoruaa.glasmio.glass.GlassSurfaceStyle
import io.github.neoruaa.glasmio.glass.LocalBlurBackdrop
import io.github.neoruaa.glasmio.ui.GlasmioBottomSheet
import io.github.neoruaa.glasmio.ui.GlasmioButton
import io.github.neoruaa.glasmio.ui.GlasmioIconButton
import io.github.neoruaa.glasmio.ui.GlasmioMenu
import io.github.neoruaa.glasmio.ui.GlasmioSheetToolbarButton
import io.github.neoruaa.glasmio.ui.GlasmioSheetTopToolbar
import io.github.neoruaa.glasmio.ui.LocalGlasmioSheetContentPadding
import io.github.neoruaa.glasmio.ui.rememberGlasmioSheetState
import io.github.neoruaa.glasmio.glass.trackBackdropPosition
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.LayerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.layerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import com.kyant.capsule.ContinuousRoundedRectangle
import com.kyant.shapes.Capsule
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.sin

private val EditorFieldRadius = ContinuousRoundedRectangle(28.dp)

/**
 * 课程新增/编辑：Glasmio 原生 iOS Sheet（[GlasmioBottomSheet]）。
 *
 * 窗口、遮罩、grabber、玻璃材质、下滑回弹、返回键、IME 避让、顶部工具栏的
 * 渐进模糊都由 Sheet 负责，这里只提供内容：
 * 胶囊输入框（课程名/教室/老师）→ 上课时间 → 上课周数（单周/双周/全选 + 周次格子）
 * → 颜色 → 删除课程胶囊按钮。
 *
 * 关闭动画结束后统一回调 [onDone]：保存/删除/关闭在 hide() 返回后回调；
 * 下滑/返回键/点遮罩由 Sheet 的 onDismissRequest 回调。两条路用同一个
 * finished 标记收敛，保证只触发一次（与旧卡片的行为一致）。
 *
 * [backdrop] 供 Sheet 的玻璃材质采样页面内容；为 null 时用宿主提供的默认值。
 * [existing] 为 null 表示新增。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseEditorDialog(
    store: Store,
    existing: Course?,
    presetDay: Int,
    presetPeriod: Int,
    backdrop: Backdrop? = null,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val config = store.config
    val todayWeek = weekOf(LocalDate.now(), config.startDate).coerceAtLeast(1)
    //  与存储侧对齐：parseCourses 只保留 1..30 周。开学日期设得很久远时
    //    todayWeek 会超 30，默认全选会把 >30 周写进去，下次加载被静默截断
    val maxWeek = maxOf(config.totalWeeks, todayWeek, existing?.weeks?.maxOrNull() ?: 1)
        .coerceAtMost(30)

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var room by remember { mutableStateOf(existing?.room ?: "") }
    var teacher by remember { mutableStateOf(existing?.teacher ?: "") }
    var day by remember {
        mutableStateOf(existing?.day ?: presetDay.takeIf { it in 1..7 } ?: 1)
    }
    var startPeriod by remember {
        mutableStateOf(existing?.startPeriod ?: presetPeriod.takeIf { it >= 1 } ?: 1)
    }
    var endPeriod by remember {
        mutableStateOf(existing?.endPeriod ?: presetPeriod.takeIf { it >= 1 } ?: 1)
    }
    // 删除课程二次确认（删除按钮 → 确认弹窗 → 真正删除）
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // 编辑时选上卡片当前的颜色：colorIndex = -1 表示「按 id 自动轮换」，
    // 卡片上显示的是 courseColor(id) 算出来的颜色，这里要把对应下标也算出来选上
    val palette = coursePaletteColors()
    // 周次/颜色网格也会重新分行。配置不变就直接复用（打字、点色块时不再重复分配）。
    val periodRange = remember(config.periodCount) {
        (1..config.periodCount).map { it.toFloat() }
    }
    val allWeeks = remember(maxWeek) { (1..maxWeek).toList() }
    val singleWeeks = remember(maxWeek) { (1..maxWeek step 2).toList() }
    val doubleWeeks = remember(maxWeek) { (2..maxWeek step 2).toList() }
    val weekRows = remember(allWeeks) { allWeeks.chunked(6) }
    val paletteRows = remember(palette) { palette.indices.toList().chunked(6) }
    // 编辑时选上卡片当前的颜色：colorIndex = -1 表示「按 id 自动轮换」，
    // 卡片上显示的是 courseColor(id) 算出来的颜色，这里要把对应下标也算出来选上
    var colorIndex by remember {
        mutableStateOf(
            existing?.let { c ->
                if (c.colorIndex in palette.indices) c.colorIndex
                else ((c.id % palette.size).toInt().let { if (it < 0) -it else it })
            } ?: -1
        )
    }
    val weeks = remember {
        val init = existing?.weeks?.toMutableSet() ?: defaultWeekSet(store)
        androidx.compose.runtime.mutableStateListOf<Int>().also { it.addAll(init) }
    }
    var error by remember { mutableStateOf<String?>(null) }

    // ── 选择节数弹窗（Glasmio 原生菜单，锚点 = 「第X节」数字胶囊；双轮齿轮选择器）──
    var showPeriodPicker by remember { mutableStateOf(false) }
    var tmpStart by remember { mutableIntStateOf(1) }
    var tmpEnd by remember { mutableIntStateOf(1) }
    //  整个编辑弹窗统一「降低透明度」：这个 sheet 视觉上本来就看不出玻璃，
    //    但库每帧都在跑背景重放 + 模糊 + 折射 + 材质 shader —— 白花钱。
    //    GlassAppearance.reduceTransparency = true 让 `usesBackdrop = false`：
    //    所有玻璃完全不采样背景、不建 RenderEffect / 折射层，改成画不透明 tint。
    //    观感几乎不变（本来就是一块实色卡片），但把每帧最贵的一整套管线砍掉。
    //    菜单的形变动画走 morph 通道，不受影响；菜单 / 删除确认里的同名 provider
    //    是历史遗留的重复设置，留着无害。
    val solidAppearance = remember {
        GlassAppearance(liquidGlassEnabled = true, reduceTransparency = true)
    }

    // 编辑表单的录制层：菜单、菜单里的玻璃按钮、删除确认弹窗都采样它
    // （弹层本身画在这层的兄弟位置，所以不会采到自己）
    val panelLayer = rememberLayerBackdrop()

    LaunchedEffect(showPeriodPicker) {
        if (showPeriodPicker) {
            tmpStart = startPeriod
            tmpEnd = endPeriod
        }
    }

    //  GlasmioMenu 的弹层高度不测内容（= 20dp + 44dp × itemCount），
    // 自定义滚轮内容必须 heightOverride 显式给出：
    // 内容 = 顶部按钮行(18+44) + 间距8 + 从/至标签26 + 轮子高度 + 底部留白16
    val wheelRows = if (LocalConfiguration.current.screenHeightDp < 480) 5 else 7
    val wheelHalf = wheelRows / 2
    val wheelItemH = 40f
    val wheelExtent = (wheelHalf + 1) * wheelItemH
    val quarter = Math.toRadians(90.0).toFloat()
    val wheelArc = sin(wheelHalf * wheelItemH / wheelExtent * quarter) * (wheelExtent / quarter)
    val periodMenuHeight = (18f + 44f + 8f + 26f + wheelItemH + 2f * wheelArc + 16f).dp

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // ── Sheet 状态：skipPartiallyExpanded = 只有大档位，表单直接铺满 ──────────
    val sheetState = rememberGlasmioSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    //    重放整屏主页面（课程表网格 + 玻璃底栏 + 橡皮筋层），再叠模糊/折射。
    //    主页面在弹窗打开期间是静止的，这里自己构造一个**带栅格化缓存**的
    //    CrossWindowBackdrop：不传 sourceFrame，缓存只在尺寸变化时录制一次，
    //    之后每帧只 drawLayer 一张 1/2 分辨率纹理。玻璃里的内容还是主页面，观感不变。
    val cachedPageLayer = rememberGraphicsLayer()
    val fallbackBackdrop = LocalBlurBackdrop.current
    val sheetBackdrop = remember(backdrop, fallbackBackdrop, cachedPageLayer) {
        when (backdrop) {
            is LayerBackdrop -> CrossWindowBackdrop(backdrop, cachedPageLayer, renderScale = 0.5f)
            else -> backdrop ?: fallbackBackdrop
        }
    }

    val scrollState = rememberScrollState()
    //    **半透明**的，底下那份没模糊的原内容就透出来 = 重影。
    //    设置页 DemoPinnedPage 传给 GlasmioPageBody 的是 `topBarBlurProgress = 1f`
    //    常驻（注释「顶部渐进模糊常驻，不跟滚动联动」）——「渐进」指的是 shader 在
    //    垂直方向由强到弱的模糊，不是随滚动淡入。照抄：固定 1f，顶栏 α 恒为 1，
    //    完全盖住下面的原内容，重影消失；也不再订阅滚动位置 → 滚动 0 次重组。

    // 只回调一次：下滑 / 返回键 / 点遮罩走 onDismissRequest，保存/删除/关闭走
    // dismissAnimated 里 hide() 结束后的这一句，两条路都收敛到这里。
    var finished by remember { mutableStateOf(false) }
    fun finish() {
        if (!finished) {
            finished = true
            onDone()
        }
    }

    fun dismissAnimated() {
        focusManager.clearFocus()
        keyboardController?.hide()
        scope.launch {
            sheetState.hide()
            finish()
        }
    }

    fun confirm() {
        when {
            name.isBlank() -> error = "请填写课程名称"
            weeks.isEmpty() -> error = "请至少选择一个上课周次"
            else -> {
                store.upsert(
                    Course(
                        id = existing?.id ?: store.nextId(),
                        name = name.trim(),
                        room = room.trim(),
                        teacher = teacher.trim(),
                        day = day,
                        // 起止节次兜底交换：任何路径进来都不允许存出「第5节到第3节」的倒挂课
                        startPeriod = minOf(startPeriod, endPeriod),
                        endPeriod = maxOf(startPeriod, endPeriod),
                        weeks = weeks.toSet(),
                        colorIndex = colorIndex,
                    )
                )
                dismissAnimated()
            }
        }
    }

    fun deleteCourse() {
        existing?.let {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            store.delete(it.id)
            dismissAnimated()
        }
    }

    // 返回键：Sheet 自己处理收起；只有齿轮选择器开着时先关选择器
    BackHandler(enabled = showPeriodPicker) { showPeriodPicker = false }

    val title = if (existing == null) "添加课程" else "编辑课程"

    CompositionLocalProvider(LocalGlassAppearance provides solidAppearance) {
        GlasmioBottomSheet(
            onDismissRequest = { finish() },
            sheetState = sheetState,
            backdrop = sheetBackdrop,
            title = title,
            topBarBlurProgress = 1f,
            progressiveTopBarBlur = true,
            //  必须保持默认 true：IosSheets.kt 的 onPostScroll 里，上滑回弹靠
            //    `!prefersScrollingExpandsWhenScrolledToEdge` 这个分支之外的
            //    把它设成 false，结果下拉后反手上滑直接 return Offset.Zero →
            //    手势被内容的橡皮筋吃掉、sheet 卡在半开。菜单那边已用
            //    LocalOverscrollFactory provides null 单独隔离，不需要这个开关。
            // sheet 主体仍是 reduceTransparency（不透明、无材质 shader），
            forceChromeBlur = true,
            topBar = {
                GlasmioSheetTopToolbar(
                    title = title,
                    navigation = {
                        GlasmioSheetToolbarButton(onClick = { dismissAnimated() }) {
                            Image(
                                painter = painterResource(R.drawable.ic_xmark),
                                contentDescription = "关闭",
                                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                    actions = {
                        GlasmioIconButton(
                            onClick = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                confirm()
                            },
                            enabled = name.isNotBlank(),
                            emphasis = if (name.isNotBlank()) GlassEmphasis.Prominent else GlassEmphasis.Regular,
                            style = GlassSurfaceStyle.Navigation,
                            size = 42.dp,
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_checkmark),
                                contentDescription = "确认",
                                colorFilter = ColorFilter.tint(
                                    if (name.isNotBlank()) Color.White
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    },
                )
            },
        ) {
            // 顶部工具栏（grabber 20dp + 工具栏高度）与底部安全区由 Sheet 通过这个
            // PaddingValues 给出；内容自己加 8 / 16dp 呼吸位。
            val sheetPadding = LocalGlasmioSheetContentPadding.current
            val isDarkSheet = MaterialTheme.colorScheme.background.luminance() < 0.5f
            Box(Modifier.fillMaxWidth().weight(1f)) {
                //    3 输入框）**额外录进一张全屏 GraphicsLayer**。滚动位移、光标闪烁、
                //    每敲一个字都会让这层重录一遍 —— 等于整屏多渲染一次，却没有人在看：
                //    「选择节数」菜单现在用 reduceTransparency（不采样），
                //    唯一还引用它的「删除确认」弹窗也是 reduceTransparency 且平时不显示。
                //    → 只在删除确认真正打开时才挂录制，平时零开销；功能一点没删。
                val recordPanel = showDeleteConfirm && existing != null
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (recordPanel) {
                                Modifier.layerBackdrop(panelLayer).trackBackdropPosition(panelLayer)
                            } else Modifier
                        ),
                ) {
                // 和其他 Tab / 子页面一样的橡皮拉伸：每个滚动容器独立一份 factory
                // （RubberBand 的工厂是单例，共用会让位移只画在一个列表上）
                //  sheet 正在被拖拽/半开时关掉内容橡皮：否则反手上滑会被内容
                //    的 overscroll 抢走（视觉上是页面弹性动画），sheet 收不到
                //    `dragBy` 就停在半开状态。松手 endDrag 后橡皮自动恢复。
                val contentOverscroll = rememberOffsetOverscrollFactory(Orientation.Vertical)
                CompositionLocalProvider(
                    LocalOverscrollFactory provides if (sheetState.isDragging) null else contentOverscroll
                ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(
                            start = 20.dp,
                            end = 20.dp,
                            top = sheetPadding.calculateTopPadding() + 8.dp,
                            bottom = sheetPadding.calculateBottomPadding() + 16.dp,
                        ),
                ) {
                    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                    //  深色下 sheet 自己就是 #1C1C1E@54% 的玻璃（压在黑色页面上 ≈ #0F0F10），
                    // 卡片再用 #1C1C1E 就跟背景同色、整块糊在一起。
                    val fieldBg = if (isDark) Color(0xFF2C2C2E) else Color.White
                    val gridUnselected = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE9E9EE)
                    val separatorColor =
                        if (isDark) Color(0xFF545458).copy(alpha = 0.5f)
                        else Color(0xFF3C3C43).copy(alpha = 0.12f)

                    // ── 卡片一：课程名 / 教室 / 老师 合到同一张卡里（行间细分割线）──
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(EditorFieldRadius)
                            .background(fieldBg),
                    ) {
                        CapsuleField(
                            value = name,
                            onValueChange = { name = it; error = null },
                            label = "课程名",
                            hint = if (name.isBlank()) "必填" else "",
                            hintColor = MaterialTheme.colorScheme.error,
                            imeAction = ImeAction.Next,
                        )
                        FieldDivider(separatorColor)
                        CapsuleField(
                            value = room,
                            onValueChange = { room = it },
                            label = "教室",
                            hint = if (room.isBlank()) "非必填" else "",
                            hintColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            imeAction = ImeAction.Next,
                        )
                        FieldDivider(separatorColor)
                        CapsuleField(
                            value = teacher,
                            onValueChange = { teacher = it },
                            label = "老师",
                            hint = if (teacher.isBlank()) "非必填" else "",
                            hintColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            imeAction = ImeAction.Done,
                            onDone = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            },
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(EditorFieldRadius)
                            .background(fieldBg)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "上课时间",
                            fontSize = 17.sp,
                            color = if (isDark) Color.White else Color(0xFF1D1D1F),
                            modifier = Modifier.weight(1f),
                        )
                        // 外面就是 GlasmioMenu 的锚点：点它 → 菜单从这块胶囊「长出来」
                        //  Popup 的子组合会继承 CompositionLocal。表单的
                        //    LocalOverscrollFactory 是共享位移状态的橡皮筋工厂，
                        //    菜单里两个 NumberPicker 的 LazyColumn 一旦继承到它，
                        //    轮子上滑就会驱动那份共享位移 → 整张表单跟着上滑。
                        //    这里给菜单（含它的 Popup）切断继承，菜单滚动不再碰 sheet。
                        CompositionLocalProvider(
                            LocalGlassAppearance provides solidAppearance,
                            LocalOverscrollFactory provides null,
                        ) {
                            GlasmioMenu(
                                expanded = showPeriodPicker,
                                onExpandedChange = { showPeriodPicker = it },
                                itemCount = 1,
                                // 采样源用不到（reduceTransparency 不采样），给 sheet 的即可
                                backdrop = sheetBackdrop,
                                menuWidth = 300.dp,
                                heightOverride = periodMenuHeight,
                                anchor = { open ->
                                    Row(
                                        Modifier
                                            .height(44.dp)
                                            .clickable(
                                                interactionSource = null,
                                                indication = null,
                                                role = Role.Button,
                                            ) { open() },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    ) {
                                        Text(
                                            if (startPeriod == endPeriod) "第 $startPeriod 节"
                                            else "第 $startPeriod-$endPeriod 节",
                                            fontSize = 17.sp,
                                            color = GlasmioTheme.colors.accent,
                                        )
                                        SfIcon(
                                            "chevron.up.chevron.down",
                                            contentDescription = null,
                                            size = 18.dp,
                                            fontSize = 18.sp,
                                            tint = GlasmioTheme.colors.accent,
                                            modifier = Modifier.size(width = 14.dp, height = 18.dp),
                                            fontFeatureSettings = "'ss15' 1",
                                        )
                                    }
                                },
                            ) { cardBackdrop, close ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    GlasmioIconButton(
                                        onClick = { close() },
                                        backdrop = cardBackdrop,
                                        size = 42.dp,
                                    ) {
                                        Image(
                                            painter = painterResource(R.drawable.ic_xmark),
                                            contentDescription = "取消",
                                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    Text(
                                        "选择节数",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    GlasmioIconButton(
                                        onClick = {
                                            // 两个轮子独立选，确认时归一化保证起始 ≤ 结束
                                            startPeriod = minOf(tmpStart, tmpEnd)
                                            endPeriod = maxOf(tmpStart, tmpEnd)
                                            close()
                                        },
                                        backdrop = cardBackdrop,
                                        emphasis = GlassEmphasis.Prominent,
                                        size = 42.dp,
                                    ) {
                                        Image(
                                            painter = painterResource(R.drawable.ic_checkmark),
                                            contentDescription = "确定",
                                            colorFilter = ColorFilter.tint(Color.White),
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                // 左右各一个齿轮：从 / 至。各给 8dp 内边距 → 两轮之间留 16dp，不贴在一起
                                Row(Modifier.fillMaxWidth()) {
                                    Column(
                                        Modifier.weight(1f).padding(horizontal = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(
                                            "从",
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        NumberPicker(
                                            value = tmpStart.toFloat(),
                                            onValueChange = { tmpStart = it.toInt().coerceIn(1, config.periodCount) },
                                            range = periodRange,
                                            label = "从",
                                            isLight = !isDark,
                                        )
                                    }
                                    Column(
                                        Modifier.weight(1f).padding(horizontal = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(
                                            "至",
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        NumberPicker(
                                            value = tmpEnd.toFloat(),
                                            onValueChange = { tmpEnd = it.toInt().coerceIn(1, config.periodCount) },
                                            range = periodRange,
                                            label = "至",
                                            isLight = !isDark,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // ── 上课周数：单周 / 双周 / 全选 ──
                    val selectedWeeks = weeks.toList().sorted()
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "上课周数",
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        WeekModeRadio(
                            label = "单周",
                            selected = selectedWeeks == singleWeeks,
                            onClick = {
                                // 已选中 → 再点一次取消勾选（清空）
                                if (selectedWeeks == singleWeeks) {
                                    weeks.clear()
                                } else {
                                    weeks.clear()
                                    singleWeeks.forEach { weeks.add(it) }
                                }
                            },
                        )
                        Spacer(Modifier.width(12.dp))
                        WeekModeRadio(
                            label = "双周",
                            selected = selectedWeeks == doubleWeeks,
                            onClick = {
                                if (selectedWeeks == doubleWeeks) {
                                    weeks.clear()
                                } else {
                                    weeks.clear()
                                    doubleWeeks.forEach { weeks.add(it) }
                                }
                            },
                        )
                        Spacer(Modifier.width(12.dp))
                        WeekModeRadio(
                            label = "全选",
                            selected = selectedWeeks == allWeeks,
                            onClick = {
                                if (selectedWeeks == allWeeks) {
                                    weeks.clear()
                                } else {
                                    weeks.clear()
                                    allWeeks.forEach { weeks.add(it) }
                                }
                            },
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    weekRows.forEach { rowWeeks ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            rowWeeks.forEach { w ->
                                val selected = w in weeks
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .clip(Capsule())
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary else gridUnselected
                                        )
                                        .clickable {
                                            if (selected) weeks.remove(w) else weeks.add(w)
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "$w",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            repeat(6 - rowWeeks.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // ── 颜色 ──
                    Text("颜色", fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                    paletteRows.forEach { rowIdxs ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            rowIdxs.forEach { realIndex ->
                                val color = palette[realIndex]
                                val selected = colorIndex == realIndex
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .then(
                                                if (selected) Modifier.border(
                                                    3.dp,
                                                    if (!isDark) Color(0xFFF2F2F7)
                                                    else MaterialTheme.colorScheme.onSurface,
                                                    CircleShape,
                                                ) else Modifier
                                            )
                                            .clickable { colorIndex = realIndex },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (selected) {
                                            Image(
                                                painter = painterResource(R.drawable.ic_checkmark),
                                                contentDescription = null,
                                                colorFilter = ColorFilter.tint(Color.White),
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    }
                                }
                            }
                            repeat(6 - rowIdxs.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }

                    error?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }

                    Spacer(Modifier.height(16.dp))

                    // ── 底部：删除课程（仅编辑时）──
                    // 红色染色玻璃：走库的 Prominent 材质（destructive → prominentContainer = 红色），
                    // 文字固定白（红底对比色，与 iOS 破坏性按钮一致）
                    // 底部安全区与呼吸位已由内容 padding 的 bottom 提供，这里不再重复加
                    if (existing != null) {
                        //  不能采 sheetBackdrop 之外的「表单层」：那层录的就是上面这个 Column，
                        //    而按钮自己也在里面 → 图层采样到自己的子节点 = 渲染树自引用，
                        //    libhwui 在 RenderNode::prepareTreeImpl 里无限递归直接 SIGSEGV 闪退。
                        // 这里给按钮单独垫一块「sheet 底色」当采样源（纯色，与 sheet 面几乎无差）
                        val deleteSample = rememberLayerBackdrop()
                        Box(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            Box(
                                Modifier
                                    .matchParentSize()
                                    // layerBackdrop 必须写在 background 前面：
                                    // 它录的是「自己之后的绘制」，写反了录到的是空层
                                    .layerBackdrop(deleteSample)
                                    .background(if (isDark) Color(0xFF141416) else Color(0xFFF6F6F9)),
                            )
                            GlasmioButton(
                                onClick = { showDeleteConfirm = true },
                                modifier = Modifier.fillMaxWidth(),
                                backdrop = deleteSample,
                                emphasis = GlassEmphasis.Prominent,
                                destructive = true,
                            ) {
                                Text("删除课程", fontSize = 16.sp, color = Color.White)
                            }
                        }
                    }
                }
                }

                }

                // ── 删除课程二次确认（Alert，常驻组合保留退场动画）──
                //  编辑器本身是 Dialog 窗口，这里再叠一层 Dialog —— 跨两层窗口
                //    采样不到 panelLayer，玻璃必花。与「选择节数」菜单同一处理：
                //    库的「降低透明度」开关让渲染器完全不采样背景 → 不透明实心玻璃。
                CompositionLocalProvider(LocalGlassAppearance provides solidAppearance) {
                GlassInputSheet(
                    visible = showDeleteConfirm && existing != null,
                    title = "删除课程",
                        hint = "确定删除「" + (existing?.name ?: "") + "」吗？删除后无法恢复。",
                        text = "",
                        onTextChange = {},
                        showField = false,
                        confirmLabel = "删除",
                        destructive = true,
                        onConfirm = {
                            showDeleteConfirm = false
                            deleteCourse()
                        },
                        onDismiss = { showDeleteConfirm = false },
                        // 采样表单自己的录制层（不能用 backdrop 参数——那是主页面玻璃）
                        backdrop = panelLayer,
                )
                }
            }
        }
    }
}

/** 组卡片里的行间细分割线：从左侧 16dp 起画（与标签左边对齐），右侧不留白。 */
@Composable
private fun FieldDivider(color: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(0.5.dp)
            .background(color),
    )
}

/**
 * 胶囊输入框：左侧标签常驻白色，输入文字右对齐；
 * 空态时右侧显示提示文字（必填 / 非必填），开始输入后提示消失。
 */
@Composable
private fun CapsuleField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    hint: String,
    hintColor: Color,
    imeAction: ImeAction,
    onDone: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // 卡片底 / 圆角由外层卡片统一提供，这里只负责一行（这样三行能合进同一张卡）
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontSize = 17.sp,
            // 深色模式白色常驻；浅色模式底是白的，退回深色字保证可读
            color = if (isDark) Color.White else Color(0xFF1D1D1F),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
            ),
            keyboardOptions = KeyboardOptions(imeAction = imeAction),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                keyboardController?.hide()
                onDone()
            }),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    if (value.isEmpty() && hint.isNotEmpty()) {
                        Text(hint, fontSize = 15.sp, color = hintColor)
                    }
                    inner()
                }
            },
        )
    }
}

/** 上课周数快捷模式（单周/双周/全选）的单选行。 */
@Composable
private fun WeekModeRadio(label: String, selected: Boolean, onClick: () -> Unit) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    Row(
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .then(
                    if (selected) {
                        Modifier.background(MaterialTheme.colorScheme.primary)
                    } else {
                        Modifier.border(
                            2.dp,
                            if (isLight) Color(0xFF8E8E93) else Color.White.copy(alpha = 0.5f),
                            RoundedCornerShape(12.dp),
                        )
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                CheckmarkIcon(color = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun defaultWeekSet(store: Store): Set<Int> {
    val todayWeek = weekOf(LocalDate.now(), store.config.startDate).coerceAtLeast(1)
    return (1..maxOf(store.config.totalWeeks, todayWeek)).toSet()
}

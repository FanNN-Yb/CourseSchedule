package com.example.schedule

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import io.github.neoruaa.glasmio.glass.GlasmioBackdropProvider
import io.github.neoruaa.glasmio.glass.render.backdrops.layerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import io.github.neoruaa.glasmio.glass.render.drawBackdrop
import io.github.neoruaa.glasmio.glass.trackBackdropPosition
import io.github.fannn_yb.rubberband.overscroll.rememberOffsetOverscrollFactory
import kotlinx.coroutines.launch
import androidx.compose.runtime.derivedStateOf

class MainActivity : ComponentActivity() {
    /** 待导入的分享码载荷（深链 schedule://import?data=…），由 AppRoot 消费。 */
    private var pendingImport = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        //  仅首建时消费导入深链：旋转/分屏等重建会带着原始 intent 重走 onCreate，
        //    不设防会把同一张课表再导入一遍（每次重建多一张重复课表）。
        //    onNewIntent（应用在前台时点链接）不受影响。
        if (savedInstanceState == null) handleImportIntent(intent)
        setContent { AppRoot(pendingImport.value) { pendingImport.value = null } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleImportIntent(intent)
    }

    private fun handleImportIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "schedule" && data.host == "import") {
            pendingImport.value = data.getQueryParameter("data")
        }
    }
}


/** iOS 风格页面滑动缓动。 */
private val iosEaseOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

@Composable
fun AppRoot(
    pendingImport: String? = null,
    onImportConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val store = remember { Store.shared(context) }

    // 分享码深链导入：把链接里的课表作为新课表加入并启用
    LaunchedEffect(pendingImport) {
        if (pendingImport != null) {
            val payload = ImportExport.extractSharePayload(pendingImport)
            val table = payload?.let { store.parseTablePayload(it) }
            if (table == null) {
                Toast.makeText(context, "分享码无效，请检查是否完整", Toast.LENGTH_LONG).show()
            } else {
                val imported = store.importTable(table, makeActive = true)
                Toast.makeText(
                    context,
                    "已导入「${imported.name}」（${imported.courses.size} 门课程）",
                    Toast.LENGTH_LONG,
                ).show()
            }
            onImportConsumed()
        }
    }

    //  不要在 ON_RESUME 重读偏好重放状态：Store 是全进程单例，所有改动都写在这份
    // 内存状态里，持久化是 150ms 防抖异步写盘。从 SAF 选完文件回来时 ON_RESUME 先于
    // 防抖落盘，load() 会把 prefs 里的旧数据盖回内存 —— 刚导入的课表就被冲掉了。

    // 主题：自动=跟随系统；否则浅色/深色；主题色可自定义
    val dark = store.isDarkTheme(isSystemInDarkTheme())

    AppTheme(dark = dark, accentColor = store.accentColor()) {
        // 状态栏/导航栏图标明暗跟随应用主题（而非系统主题）
        val view = LocalView.current
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(activity.window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }

        val colors = MaterialTheme.colorScheme
        val scope = rememberCoroutineScope()

        // 底部 Tabs：课程表 / 今日 / 设置；「＋」按钮 = 导入菜单锚点（GlasmioMenu，
        // 在 BottomBarRow 内与按钮同处组合；这里只负责菜单项触发的弹窗）
        // rememberSaveable：旋转/分屏重建后停留在原 Tab（其余页面内滚动状态本就可恢复）
        val selectedTab = rememberSaveable { mutableIntStateOf(0) }
        val selectedIndexGetter = remember { { selectedTab.intValue } }

        var showImportCode by remember { mutableStateOf(false) }
        var showImportFormat by remember { mutableStateOf(false) }
        val importBackupLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { ins ->
                        ins.readBytes().toString(Charsets.UTF_8)
                    } ?: error("无法读取文件")
                }.mapCatching { json -> store.importBackup(json) }
                    .onSuccess { Toast.makeText(context, "已导入 $it 张课表", Toast.LENGTH_SHORT).show() }
                    .onFailure {
                        Toast.makeText(context, "导入失败：${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        // 记录已访问过的 tab，首次访问后常驻内存不销毁（保留各页状态）
        val visitedTabs = remember { mutableStateOf(setOf(0)) }
        LaunchedEffect(selectedTab.intValue) {
            visitedTabs.value = visitedTabs.value + selectedTab.intValue
        }

        // 编辑/新增课程卡片：状态提升到根级，在底部 Tabs 之上渲染（盖住导航栏）
        // （课程表页点空白格触发；底栏「＋」不再直接新建课程）
        var editorRequest by remember { mutableStateOf<Pair<Course?, Pair<Int, Int>>?>(null) }

        // 返回键：非课程表页时先回到课程表
        BackHandler(enabled = selectedTab.intValue != 0) { selectedTab.intValue = 0 }

        val contentBackdrop = rememberLayerBackdrop()
        val glassBackdrop = rememberBackdrop(contentBackdrop, onDraw = { drawBackdrop -> drawBackdrop() })

        // 给所有 Glasmio 原生组件提供默认 backdrop（LocalGlassBackdrop / LocalBlurBackdrop）
        GlasmioBackdropProvider(backdrop = glassBackdrop) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val screenW: Dp = maxWidth
            val slideSpec = tween<Dp>(250, easing = iosEaseOut)

            // 页面层（整体录制进 backdrop，供底部玻璃 Tabs 采样）
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(contentBackdrop)
                    .trackBackdropPosition(contentBackdrop)
                    .background(colors.background)
            ) {
                // ── 页面 0：课程表（最左）──
                if (0 in visitedTabs.value) {
                    val isActive = selectedTab.intValue == 0
                    val offsetX by animateDpAsState(
                        if (isActive) 0.dp else if (selectedTab.intValue > 0) -screenW else screenW,
                        slideSpec, label = "slide0"
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .zIndex(if (isActive) 1f else 0f)
                            .graphicsLayer { translationX = offsetX.toPx() }
                            .background(colors.background)
                    ) {
                        // 每页独立提供 overscroll 工厂：RubberBand 的工厂是单例，
                        CompositionLocalProvider(
                            LocalOverscrollFactory provides rememberOffsetOverscrollFactory(Orientation.Vertical)
                        ) {
                            ScheduleScreen(
                                store,
                                onOpenEditor = { existing, day, period ->
                                    editorRequest = existing to (day to period)
                                },
                            )
                        }
                    }
                }

                // ── 页面 1：今日 ──
                if (1 in visitedTabs.value) {
                    val isActive = selectedTab.intValue == 1
                    val offsetX by animateDpAsState(
                        if (isActive) 0.dp else if (selectedTab.intValue > 1) -screenW else screenW,
                        slideSpec, label = "slide1"
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .zIndex(if (isActive) 1f else 0f)
                            .graphicsLayer { translationX = offsetX.toPx() }
                            .background(colors.background)
                    ) {
                        CompositionLocalProvider(
                            LocalOverscrollFactory provides rememberOffsetOverscrollFactory(Orientation.Vertical)
                        ) {
                            TodayScreen(store)
                        }
                    }
                }

                // ── 页面 2：设置 ──
                if (2 in visitedTabs.value) {
                    val isActive = selectedTab.intValue == 2
                    val offsetX by animateDpAsState(
                        if (isActive) 0.dp else screenW,
                        slideSpec, label = "slide2"
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .zIndex(if (isActive) 1f else 0f)
                            .graphicsLayer { translationX = offsetX.toPx() }
                            .background(colors.background)
                    ) {
                        CompositionLocalProvider(
                            LocalOverscrollFactory provides rememberOffsetOverscrollFactory(Orientation.Vertical)
                        ) {
                            SettingsFlow(store)
                        }
                    }
                }
            }

        // ── 底部液态玻璃 Tabs（在录制层外采样页面内容）──
        BottomBarRow(
            selectedTabIndex = selectedIndexGetter,
            onSelect = { selectedTab.intValue = it },
            backdrop = glassBackdrop,
            onFormatImport = { showImportFormat = true },
            onCodeImport = { showImportCode = true },
            onBackupImport = {
                importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

            // ── 编辑/新增课程卡片：渲染在 Tabs 之后 → 盖住底部导航栏 ──
            editorRequest?.let { req ->
                CourseEditorDialog(
                    store = store,
                    existing = req.first,
                    presetDay = req.second.first,
                    presetPeriod = req.second.second,
                    //  传原始 LayerBackdrop，让弹窗内部自己构造「带缓存的跨窗口采样」。
                    //    传包装过的 glassBackdrop 会退化成每帧重放整屏主页面。
                    backdrop = contentBackdrop,
                    onDone = { editorRequest = null },
                    onDismiss = { editorRequest = null },
                )
            }

            // ── 导入弹窗（菜单项触发；GlasmioMenu 的弹层在独立窗口，
            //    收起动画与弹窗打开互不遮挡）──
            ImportCodeSheet(
                store,
                glassBackdrop,
                visible = showImportCode,
                onDismiss = { showImportCode = false },
            )
            ImportFormatSheet(
                store,
                glassBackdrop,
                visible = showImportFormat,
                onDismiss = { showImportFormat = false },
            )
        }
        }
    }
}

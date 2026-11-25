package com.example.schedule

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.schedule.ui.glass.GlassTimeRangePicker
import com.example.schedule.ui.glass.topProgressiveBlurScrim
import io.github.neoruaa.glasmio.glass.GlassEmphasis
import io.github.neoruaa.glasmio.ui.GlasmioIconButton
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.LayerBackdrop
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.glass.LocalGlassBackdrop
import com.example.schedule.ui.glass.RubberBandContainer
import com.example.schedule.ui.glass.rememberRubberBandState
import io.github.neoruaa.glasmio.scaffold.GlasmioPageBody
import io.github.neoruaa.glasmio.glass.render.backdrops.layerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberCombinedBackdrop
import io.github.neoruaa.glasmio.ui.GlasmioGroupedList
import io.github.neoruaa.glasmio.ui.GlasmioListRow
import io.github.neoruaa.glasmio.ui.GlasmioTopBar
import io.github.neoruaa.glasmio.icons.SfIcon
import io.github.neoruaa.glasmio.icons.SfSymbol
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop

/**
 * GlasmioPageBody（顶栏渐进边缘模糊）+ GlasmioTopBar 常显折叠标题
 * （返回键在 navigation 槽、确认键在 actions 槽 —— 按压反馈由玻璃按钮内置）。
 *
 *  采样分层：顶栏玻璃采样 pageBackdrop（只录滚动内容，不含顶栏/弹窗自身）；
 *    content(pickerBackdrop) 把它交给调用方供弹窗玻璃采样 —— 弹窗在页面层外，
 *    无自引用环。
 */
@Composable
fun SubPageChrome(
    title: String,
    onBack: () -> Unit,
    confirm: (() -> Unit)? = null,
    confirmIcon: Int = R.drawable.ic_checkmark,
    confirmContentDescription: String = "完成",
    pageBackdrop: LayerBackdrop = rememberLayerBackdrop(),
    content: @Composable ColumnScope.(pickerBackdrop: Backdrop) -> Unit,
) {
    val scrollState = rememberScrollState()
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    //  顶部渐进模糊常驻（不跟滚动联动）：模糊带 = 状态栏 + 62dp 顶栏 + 34dp 余量，
    //    内容顶部从模糊带下沿之下开始（+12dp 间距）—— 静止时卡片不被模糊，
    val contentPadding = PaddingValues(
        start = 16.dp,
        top = statusBar + 108.dp,
        end = 16.dp,
        bottom = 24.dp,
    )

    Box(Modifier.fillMaxSize().background(GlasmioTheme.colors.groupedBackground)) {
        CompositionLocalProvider(LocalGlassBackdrop provides pageBackdrop) {
            GlasmioPageBody(
                contentPadding = contentPadding,
                backgroundColor = GlasmioTheme.colors.groupedBackground,
                topBarBlurProgress = 1f,
                useOverscrollHost = false,
                backdrop = pageBackdrop,
            ) { padding ->
                RubberBandContainer(state = rememberRubberBandState(), modifier = Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(padding)
                            .padding(horizontal = 4.dp),
                        content = { content(pageBackdrop) },
                    )
                }
            }
            GlasmioTopBar(
                title = title,
                modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                collapseProgress = 1f,
                navigation = {
                    GlasmioIconButton(onClick = onBack) {
                        SfIcon(SfSymbol.ChevronBack, "返回", mirrored = true, size = 18.dp)
                    }
                },
                actions = {
                    if (confirm != null) {
                        //  确认钮 = 主题色染色玻璃（Prominent：prominentContainer =
                        //    AppTheme 传入的强调色，默认 iOS 蓝，随用户主题色变化），
                        //    图标白色、由调用方传入（多课表 = 加号，时间设置 = 对勾）
                        GlasmioIconButton(
                            onClick = confirm,
                            emphasis = GlassEmphasis.Prominent,
                        ) {
                            Image(
                                painter = painterResource(confirmIcon),
                                contentDescription = confirmContentDescription,
                                colorFilter = ColorFilter.tint(Color.White),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun SubCard(
    cardBg: Color,
    cardBorder: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(cardBg)
            .border(0.5.dp, cardBorder, RoundedCornerShape(22.dp)),
        content = content,
    )
}

/** 课表时间设置：按上午 / 下午 / 晚上分组的节次时间列表，点击行弹出齿轮选择器。 */
@Composable
fun PeriodTimeScreen(store: Store, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val isLight = colors.background.luminance() > 0.5f
    val cardBg = if (isLight) Color.White else Color(0xFF1C1C1E)
    val cardBorder = if (isLight) Color.Black.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.06f)
    val textPrimary = if (isLight) Color(0xFF1D1D1F) else Color.White
    val textSecondary = if (isLight) Color(0x99000000) else Color(0x99FFFFFF)
    val chevronTint = if (isLight) Color(0xFF1D1D1F) else Color.Unspecified

    val config = store.config
    // 编辑中的节次号（null = 弹窗关闭）
    var editPeriod by remember { mutableStateOf<Int?>(null) }
    // 弹窗玻璃采样层：录页面内容（弹窗在录制层外，无自引用循环）
    val pageLayer = rememberLayerBackdrop()

    // 按开始时间分段：上午(<12) / 下午(12-17) / 晚上(≥18)，段内按节次号排序
    val groups = remember(config.periods) {
        config.periods
            .sortedBy { it.period }
            .groupBy { pt ->
                val h = pt.start.substringBefore(':').toIntOrNull() ?: 12
                when {
                    h < 12 -> "上午"
                    h < 18 -> "下午"
                    else -> "晚上"
                }
            }
    }

    SubPageChrome(
        title = "课表时间设置",
        onBack = onBack,
        pageBackdrop = pageLayer,
        confirm = onBack,
    ) { pickerBackdrop ->
        groups.forEach { (label, periods) ->
            SectionHeader(label)
            Spacer(Modifier.height(8.dp))
            GlasmioGroupedList {
                periods.forEach { pt ->
                    GlasmioListRow(
                        periodName(pt.period),
                        detail = "${pt.start} - ${pt.end}",
                        onClick = { editPeriod = pt.period },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        // 底部留白：底部 Tabs 高度
        Spacer(Modifier.height(130.dp))
    }

    // 时间范围齿轮弹窗（确认即应用，无需保存按钮）
    val editing = editPeriod?.let { p -> config.periods.find { it.period == p } }
    GlassTimeRangePicker(
        expanded = editing != null,
        onExpandedChange = { if (!it) editPeriod = null },
        title = editing?.let { periodName(it.period) } ?: "",
        startTime = editing?.start ?: "08:00",
        endTime = editing?.end ?: "08:45",
        backdrop = pageLayer,
        modifier = Modifier.zIndex(80f),
        onConfirm = { start, end ->
            val p = editing!!.period
            store.updateConfig(
                store.config.copy(
                    periods = store.config.periods.map {
                        if (it.period == p) it.copy(start = start, end = end) else it
                    },
                ),
            )
        },
    )
}

private val CnPeriodNums = listOf("一", "二", "三", "四", "五", "六", "七", "八", "九", "十", "十一", "十二")

/** 节次中文名：第X节（1-12 用中文数字，超出用阿拉伯数字）。 */
private fun periodName(n: Int): String =
    "第" + CnPeriodNums.getOrElse(n - 1) { n.toString() } + "节"

private val TimeRegex = Regex("(\\d{1,2}):(\\d{2})")

/** 规范化 HH:mm；非法返回 null。 */
private fun normalizeTime(s: String): String? {
    val m = TimeRegex.matchEntire(s.trim()) ?: return null
    val h = m.groupValues[1].toInt()
    val min = m.groupValues[2].toInt()
    if (h !in 0..23 || min !in 0..59) return null
    return "%02d:%02d".format(h, min)
}

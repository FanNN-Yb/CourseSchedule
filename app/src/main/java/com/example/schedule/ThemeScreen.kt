package com.example.schedule

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.neoruaa.glasmio.ui.GlasmioIconButton
import io.github.neoruaa.glasmio.ui.GlasmioGroupedList
import io.github.neoruaa.glasmio.ui.GlasmioSwitch
import io.github.neoruaa.glasmio.glass.render.Backdrop
import androidx.compose.runtime.CompositionLocalProvider
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.glass.LocalGlassBackdrop
import com.example.schedule.ui.glass.RubberBandContainer
import com.example.schedule.ui.glass.rememberRubberBandState
import io.github.neoruaa.glasmio.scaffold.GlasmioPageBody
import io.github.neoruaa.glasmio.ui.GlasmioTopBar
import io.github.neoruaa.glasmio.glass.render.backdrops.layerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop

/**
 * 浅色 / 深色预览卡片 + 自动开关 + 自定义主题色 + 颜色选择。
 * 自动 = 跟随系统深浅色；自定义主题色 = 全 App 强调色（底部 Tab 选中 / 主色按钮等）。
 */
@Composable
fun ThemeScreen(
    store: Store,
    onBack: () -> Unit = {},
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val bg = if (isLight) Color(0xFFF2F2F7) else Color(0xFF000000)
    val textPrimary = if (isLight) Color(0xFF1D1D1F) else Color.White
    val textSecondary = if (isLight) Color(0x99000000) else Color(0x99FFFFFF)
    val cardBg = if (isLight) Color.White else Color(0xFF1C1C1E)
    val cardBorder = if (isLight) Color.Black.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.06f)
    val dividerColor = if (isLight) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.08f)

    // pageBackdrop：录纯色背景 → 开关 / 返回按钮的玻璃采样它
    // contentLayer：录滚动内容 → 顶部渐进模糊采样它（遮罩必须在录制层外）
    val backdrop = rememberLayerBackdrop()
    val contentLayer = rememberLayerBackdrop()
    val density = LocalDensity.current

    var viewportHeightPx by remember { mutableStateOf(0) }
    var contentHeightPx by remember { mutableStateOf(0) }

    val themeMode = store.themeMode
    val themeAuto = store.themeAuto
    val useCustomTheme = store.useCustomTheme
    val selectedColorIndex = store.accentColorIndex
    val accent = store.accentColor()

    // 遮罩高度
    val statusBarHeightPx = WindowInsets.statusBars.getTop(density).toFloat()
    // 遮罩尺寸与设置页完全一致：状态栏 + 68dp，视觉高度 1.3×
    val maskBottomPx = statusBarHeightPx + with(density) { 68.dp.toPx() }
    val maskHeightDp = with(density) { maskBottomPx.toDp() }
    val maskVisualHeightDp = with(density) { (maskBottomPx * 1.3f).toDp() }

    val contentPaddingVertical = (maskVisualHeightDp + 8.dp) + 165.dp
    val viewportContentArea = with(density) { viewportHeightPx.toDp() } - contentPaddingVertical
    val contentHeight = with(density) { contentHeightPx.toDp() }
    val spacerHeight = if (viewportHeightPx > 0 && contentHeightPx > 0) {
        (viewportContentArea - contentHeight + 1.dp).coerceAtLeast(1.dp)
    } else {
        1.dp
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        val isLandscape = maxWidth > maxHeight
        val horizontalPadding = if (isLandscape) ((maxWidth - 360.dp) / 2f).coerceAtLeast(20.dp) else 20.dp

        // 背景层：录纯色背景 → 开关 / 返回按钮的玻璃采样它（避免自引用）
        //  layerBackdrop 必须在 background 【前面】：它录的是「自己之后的绘制」，
        //    写反了录到的是空层 → 开关按下时镜头里是黑色图案 + 锯齿。
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(bg))

        //    （topBarBlurProgress = 1f 恒定；内容顶部从模糊带下沿之下开始）
        GlasmioPageBody(
            contentPadding = PaddingValues(
                start = horizontalPadding,
                top = with(density) { statusBarHeightPx.toDp() } + 108.dp,
                end = horizontalPadding,
                bottom = 165.dp,
            ),
            backgroundColor = GlasmioTheme.colors.groupedBackground,
            topBarBlurProgress = 1f,
            //  橡皮筋独占过滚动：关掉内置 host，用 Rubber-Band 库（全 App 统一）
            useOverscrollHost = false,
            backdrop = contentLayer,
        ) { padding ->
        // 不做状态栏避让：contentPadding 已含状态栏高度，
        // 内容滚动时从状态栏/顶栏底下穿过，由渐进模糊自然盖住（同设置页）
        RubberBandContainer(state = rememberRubberBandState(), modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { it -> viewportHeightPx = it.size.height },
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.onGloballyPositioned { it -> contentHeightPx = it.size.height },
                ) {
                    // 预览卡片：浅色 / 深色 + 自动开关
                    GlasmioGroupedList {
                        ThemePreviewSection(
                            selectedMode = themeMode,
                            isAuto = themeAuto,
                            onSelectMode = { if (!themeAuto) store.updateThemeMode(it) },
                            onAutoToggle = { store.updateThemeAuto(it) },
                            isLight = isLight,
                            dividerColor = dividerColor,
                            accent = accent,
                            backdrop = backdrop,
                        )
                    }

                    // 说明
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "自动模式将跟随系统深浅色设置切换主题。",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W400,
                        color = textSecondary.copy(alpha = 0.5f),
                        letterSpacing = (-0.12).sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )

                    // 自定义主题色开关
                    Spacer(Modifier.height(24.dp))
                    GlasmioGroupedList {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "自定义主题色",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.W400,
                                color = textPrimary,
                                letterSpacing = (-0.374).sp,
                            )
                            GlasmioSwitch(
                                checked = useCustomTheme,
                                onCheckedChange = { store.updateUseCustomTheme(it) },
                                backdrop = backdrop,
                            )
                        }
                    }

                    // 颜色选择卡片（未开启自定义时压暗）
                    Spacer(Modifier.height(8.dp))
                    Box(modifier = Modifier.alpha(if (useCustomTheme) 1f else 0.4f)) {
                        GlasmioGroupedList {
                            Column(Modifier.padding(16.dp)) {
                                // 标题行：颜色 + 当前色名
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "颜色",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.W400,
                                        color = textPrimary,
                                        letterSpacing = (-0.374).sp,
                                    )
                                    Text(
                                        text = ThemePresetColors[selectedColorIndex].name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.W400,
                                        color = textSecondary,
                                        letterSpacing = (-0.2).sp,
                                    )
                                }

                                Spacer(Modifier.height(16.dp))

                                // 颜色圆点行
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    ThemePresetColors.forEachIndexed { index, themeColor ->
                                        val isSelected = index == selectedColorIndex
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(themeColor.color)
                                                .then(
                                                    if (isSelected) {
                                                        Modifier.border(
                                                            3.dp,
                                                            if (isLight) Color(0xFFF2F2F7) else textPrimary,
                                                            CircleShape,
                                                        )
                                                    } else {
                                                        Modifier
                                                    }
                                                )
                                                .clickable(enabled = useCustomTheme) {
                                                    store.updateAccentColorIndex(index)
                                                },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (isSelected) {
                                                CheckmarkIcon(
                                                    color = Color.White,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // 动态补位 Spacer：填满屏幕让滚动范围 > 0，触发橡皮筋
                Spacer(Modifier.height(spacerHeight))
            }
            }
        }
        }


        CompositionLocalProvider(LocalGlassBackdrop provides contentLayer) {
            GlasmioTopBar(
                title = "主题",
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .zIndex(60f),
                collapseProgress = 1f,
                navigation = {
                    GlasmioIconButton(
                        onClick = onBack,
                        backdrop = contentLayer,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_chevron_left),
                            contentDescription = "返回",
                            modifier = Modifier.size(width = 12.dp, height = 17.dp),
                            colorFilter = ColorFilter.tint(if (isLight) Color.Black else Color.White),
                        )
                    }
                },
            )
        }
    }
}


@Composable
private fun ThemePreviewSection(
    selectedMode: Int,
    isAuto: Boolean,
    onSelectMode: (Int) -> Unit,
    onAutoToggle: (Boolean) -> Unit,
    isLight: Boolean,
    dividerColor: Color,
    accent: Color,
    backdrop: Backdrop,
) {
    val textPrimary = if (isLight) Color(0xFF1D1D1F) else Color.White

    Column(Modifier.fillMaxWidth()) {
        // ===== 浅色 & 深色预览卡片 =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ThemePreviewCard(
                modifier = Modifier.weight(1f),
                isDark = false,
                isSelected = selectedMode == 0,
                enabled = !isAuto,
                label = "浅色",
                isLight = isLight,
                accent = accent,
                onSelect = { onSelectMode(0) },
            )
            ThemePreviewCard(
                modifier = Modifier.weight(1f),
                isDark = true,
                isSelected = selectedMode == 1,
                enabled = !isAuto,
                label = "深色",
                isLight = isLight,
                accent = accent,
                onSelect = { onSelectMode(1) },
            )
        }

        // ===== 分割线 =====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawLine(
                        color = dividerColor,
                        start = Offset(16.dp.toPx(), 0f),
                        end = Offset(size.width - 16.dp.toPx(), 0f),
                        strokeWidth = 0.5.dp.toPx(),
                    )
                }
                .height(1.dp),
        )

        // ===== 自动开关行 =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onAutoToggle(!isAuto) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "自动",
                fontSize = 17.sp,
                fontWeight = FontWeight.W400,
                color = textPrimary,
                letterSpacing = (-0.374).sp,
            )
            GlasmioSwitch(
                checked = isAuto,
                onCheckedChange = { onAutoToggle(it) },
                backdrop = backdrop,
            )
        }
    }
}

@Composable
private fun ThemePreviewCard(
    modifier: Modifier = Modifier,
    isDark: Boolean,
    isSelected: Boolean,
    enabled: Boolean,
    label: String,
    isLight: Boolean,
    accent: Color,
    onSelect: () -> Unit,
) {
    val cardBg = if (isDark) Color.Black else Color(0xFFF2F2F7)
    val surfaceColor = if (isDark) Color(0xFF2C2C2E) else Color.White
    val blockColor = if (isDark) Color(0xFF3A3A3C) else Color(0xFFC7C7CC)

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // ===== 手机预览图 =====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(cardBg)
                .clickable(enabled = enabled, onClick = onSelect),
            contentAlignment = Alignment.Center,
        ) {
            // 模拟手机界面
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 顶部状态栏模拟
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Box(
                        modifier = Modifier
                            .width(30.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(blockColor.copy(alpha = 0.8f)),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .width(10.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(blockColor.copy(alpha = 0.8f)),
                        )
                        Box(
                            modifier = Modifier
                                .width(14.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(blockColor.copy(alpha = 0.8f)),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                // 内容块
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(blockColor.copy(alpha = 0.8f)),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(blockColor.copy(alpha = 0.7f)),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(surfaceColor),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(surfaceColor),
                )
                Spacer(Modifier.weight(1f))
                // 底部导航栏模拟
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(accent),
                    )
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(blockColor.copy(alpha = 0.8f)),
                    )
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(blockColor.copy(alpha = 0.8f)),
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ===== 标签 =====
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.W400,
            color = if (isLight) Color(0xFF1D1D1F) else Color.White,
        )

        Spacer(Modifier.height(8.dp))

        // ===== 选择圆圈 =====
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .then(
                    if (isSelected) {
                        Modifier.background(accent)
                    } else {
                        Modifier.border(
                            2.dp,
                            if (isLight) Color(0xFF8E8E93) else Color.White.copy(alpha = 0.5f),
                            RoundedCornerShape(12.dp),
                        )
                    }
                )
                .clickable(enabled = enabled, onClick = onSelect),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                CheckmarkIcon(color = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun ThemeCard(
    cardBg: Color,
    cardBorder: Color,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(cardBg)
            .border(0.5.dp, cardBorder, RoundedCornerShape(22.dp)),
    ) {
        content()
    }
}

@Composable
internal fun CheckmarkIcon(
    color: Color = Color.White,
    modifier: Modifier = Modifier,
) {
    val path = remember { Path() }
    var lastWidth by remember { mutableStateOf(-1f) }
    var lastHeight by remember { mutableStateOf(-1f) }

    Canvas(modifier = modifier) {
        if (size.width != lastWidth || size.height != lastHeight) {
            lastWidth = size.width
            lastHeight = size.height
            path.reset()
            val sx = size.width / 17.6199f
            val sy = size.height / 17.4086f
            path.apply {
                moveTo(6.60448f * sx, 17.4086f * sy)
                cubicTo(7.11229f * sx, 17.4086f * sy, 7.51287f * sx, 17.1967f * sy, 7.79119f * sx, 16.7721f * sy)
                lineTo(16.9705f * sx, 2.46229f * sy)
                cubicTo(17.1762f * sx, 2.14042f * sy, 17.2586f * sx, 1.87109f * sy, 17.2586f * sx, 1.60605f * sy)
                cubicTo(17.2586f * sx, 0.933592f * sy, 16.7857f * sx, 0.469534f * sy, 16.1062f * sx, 0.469534f * sy)
                cubicTo(15.6242f * sx, 0.469534f * sy, 15.3443f * sx, 0.631642f * sy, 15.052f * sx, 1.09062f * sy)
                lineTo(6.56542f * sx, 14.5731f * sy)
                lineTo(2.19217f * sx, 8.95f * sy)
                cubicTo(1.9076f * sx, 8.57305f * sy, 1.60897f * sx, 8.41524f * sy, 1.1871f * sx, 8.41524f * sy)
                cubicTo(0.489839f * sx, 8.41524f * sy, 0f, 8.90332f * sy, 0f, 9.57754f * sy)
                cubicTo(0f, 9.86718f * sy, 0.108203f * sx, 10.1598f * sy, 0.351755f * sx, 10.4557f * sy)
                lineTo(5.41133f * sx, 16.7859f * sy)
                cubicTo(5.74824f * sx, 17.2072f * sy, 6.11249f * sx, 17.4086f * sy, 6.60448f * sx, 17.4086f * sy)
                close()
            }
        }
        drawPath(path = path, color = color)
    }
}

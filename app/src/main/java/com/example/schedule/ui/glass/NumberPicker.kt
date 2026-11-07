package com.example.schedule.ui.glass

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.res.painterResource
import com.example.schedule.R
import com.kyant.capsule.ContinuousRoundedRectangle
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.sp
import io.github.fannn_yb.rubberband.overscroll.rememberOffsetOverscrollFactory
import io.github.neoruaa.glasmio.glass.GlassEmphasis
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.ui.GlasmioIconButton
import android.util.Log
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.sin

/**
 * iOS 风格的 3D 轮子数字选择器
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NumberPicker(
    value: Float,
    onValueChange: (Float) -> Unit,
    range: List<Float>,
    label: String,
    isLight: Boolean,
    modifier: Modifier = Modifier,
    backdropParam: io.github.neoruaa.glasmio.glass.render.Backdrop? = null,
    // 文字标签（与 range 一一对应）：传了就用标签显示（如 周一/周日、跟随系统/浅色/深色），
    // 不传则按数字格式化
    labels: List<String>? = null,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    var hasEmittedInitialSelection by remember { mutableStateOf(false) }

    val textPrimary = if (isLight) Color(0xFF1D1D1F) else Color.White
    val textSecondary = Color(0xFF999999)

    val items = remember(range, labels) {
        labels ?: range.map { v ->
            if (v == v.toInt().toFloat()) {
                v.toInt().toString()
            } else {
                String.format("%.2f", v).trimEnd('0').trimEnd('.')
            }
        }
    }

    val currentIndex = remember(value, range) {
        range.indexOf(value).coerceAtLeast(0)
    }

    // 矮屏（横屏手机等）减少可见行数，避免弹窗总高超出屏幕
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val visibleCount = if (screenHeightDp < 480) 5 else 7
    val halfCount = visibleCount / 2
    val itemHeight = 40.dp
    val itemHeightPx = with(density) { itemHeight.toPx() }

    val maxRotationDeg = 90f
    val maxAngleRad = Math.toRadians(maxRotationDeg.toDouble()).toFloat()
    val geometricHalfCount = halfCount + 1
    val wheelExtentPx = (geometricHalfCount * itemHeightPx).coerceAtLeast(1f)
    val radiusPx = wheelExtentPx / maxAngleRad

    fun distanceToAngleRad(distanceToCenter: Float): Float {
        val normalized = (distanceToCenter / wheelExtentPx).coerceIn(-1f, 1f)
        return normalized * maxAngleRad
    }

    fun mapToWheelY(distanceToCenter: Float): Float {
        val angle = distanceToAngleRad(distanceToCenter)
        return sin(angle) * radiusPx
    }

    val visibleHalfLinearPx = halfCount * itemHeightPx
    val visibleHalfArcPx = mapToWheelY(visibleHalfLinearPx).absoluteValue
    val wheelContainerHeight = with(density) {
        (itemHeightPx + (visibleHalfArcPx * 2f)).toDp()
    }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex)
    // 每个轮子独享一份原厂橡皮拉伸工厂，不再依赖调用点各自包 provider
    val wheelFactory = rememberOffsetOverscrollFactory(Orientation.Vertical)
    // 这个工厂只产出一个共享 effect，直接拿同一个实例来手动触发 fling 回弹
    val wheelEffect = remember(wheelFactory) { wheelFactory.createOverscrollEffect() }
    val pulseScope = rememberCoroutineScope()
    val snapFling = rememberSnapFlingBehavior(lazyListState = listState)
    //  拖拽过边界的拉伸走 applyToScroll（UserInput 分支），一直是好的；
    //    惯性 fling 到边界却硬停 —— 前两轮去改 performFling 的返回值都没命中，
    //    因为那条链的语义由 scrollable / effect 内部决定，靠猜不可靠。
    //    这里绕开它：snap 吸附完成后若已在边界，直接调库自己的 applyToFling
    //    给一个速度脉冲（performFling 传 Zero → unconsumed 就是脉冲本身），
    //    由 effect 用自己的 animationSpec 做 snapTo + animateTo(0f) 拉伸回弹，
    //    和拖拽拉伸同一套动画；吸附仍由 snapFling 决定。
    val flingBehavior = remember(snapFling, listState, wheelEffect, pulseScope) {
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                val consumed = with(snapFling) { performFling(initialVelocity) }
                //    v > 0 的那次 fling 到的是 forward 边界 (fwd=false back=true)，
                //    v < 0 到的是 backward 边界 (fwd=true back=false)。
                //    snapFling.performFling 返回剩余速度
                //    （全消费），所以 scrollable 的 onPostFling available=0，
                //    库自己的 applyToFling 走 else 分支什么都不做 = 硬停。
                val atEdge = when {
                    initialVelocity > 0f -> !listState.canScrollForward
                    initialVelocity < 0f -> !listState.canScrollBackward
                    else -> false
                }
                Log.d("WHEEL_FLING", "v=$initialVelocity consumed=$consumed fwd=${listState.canScrollForward} back=${listState.canScrollBackward} edge=$atEdge")
                if (atEdge) {
                    pulseScope.launch {
                        // 新版库的 applyToFling 只 snapTo「一帧由速度推出的位移」(≈pulse*0.016)，
                        // 小到看不见；拖拽那条路是 applyToScroll 直接累加 delta，效果好。
                        // 所以这里复用同一条路：先灌一段 UserInput 过滚动 delta，等一帧让
                        // snapTo 落地，再用 applyToFling(Zero) 触发库自己的 animateTo(0f) 回弹。
                        // forward 边界的过滚动是内容继续上移 → delta.y 为负。
                        // 线性映射 + 上下限，慢甩轻轻弹、快甩明显拉长。
                        // 之后还要过库自己的 computeOffset 阻尼曲线，不会线性拉到失控。
                        val dir = if (initialVelocity > 0f) -1f else 1f
                        val magnitude = (initialVelocity.absoluteValue * 0.010f).coerceIn(12f, 150f)
                        wheelEffect.applyToScroll(
                            delta = Offset(0f, dir * magnitude),
                            source = NestedScrollSource.UserInput,
                            performScroll = { Offset.Zero },
                        )
                        withFrameNanos { }
                        wheelEffect.applyToFling(Velocity.Zero) { Velocity.Zero }
                    }
                }
                return consumed
            }
        }
    }

    val centeredIndex by remember(listState, items) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            if (layoutInfo.visibleItemsInfo.isEmpty()) return@derivedStateOf currentIndex
            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
            layoutInfo.visibleItemsInfo
                .minByOrNull { visible ->
                    val itemCenter = visible.offset + visible.size / 2f
                    mapToWheelY(itemCenter - viewportCenter).absoluteValue
                }
                ?.index
                ?.let { index -> index.coerceIn(0, items.lastIndex) }
                ?: currentIndex
        }
    }

    LaunchedEffect(centeredIndex) {
        if (hasEmittedInitialSelection) {
            onValueChange(range[centeredIndex])
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        } else {
            hasEmittedInitialSelection = true
        }
    }

    LaunchedEffect(currentIndex, items.size) {
        if (items.isEmpty()) return@LaunchedEffect
        val targetIndex = currentIndex.coerceIn(0, items.lastIndex)
        if (targetIndex != centeredIndex && !listState.isScrollInProgress) {
            listState.animateScrollToItem(targetIndex)
        }
    }

    val cardShape = ContinuousRoundedRectangle(20.dp)
    // 压在玻璃面板上会发闷、跟玻璃不搭。选中态改由文字明暗（textPrimary/textSecondary）表达。
    val indicatorColor = Color.Transparent

    Box(
        modifier = modifier
            .height(wheelContainerHeight)
            .clipToBounds()
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                        .drawWithContent {
                            drawContent()
                            val outline = cardShape.createOutline(size, layoutDirection, this)
                            drawOutline(outline, indicatorColor)
                            // 填充也走同一条 outline，保证是平滑圆角（drawRoundRect 只能画圆弧角）
                            drawOutline(outline, indicatorColor.copy(alpha = 0.15f), style = Fill)
                        }
        )

        // 统一在轮子内部提供，手感与其它页面一致；即使外层被设成 null
        // （编辑弹窗的菜单为隔离 sheet 而这么做）这里也能覆盖回来。
        CompositionLocalProvider(LocalOverscrollFactory provides wheelFactory) {
            LazyColumn(
                state = listState,
                flingBehavior = flingBehavior,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.requiredHeight((visibleCount * itemHeight.value).dp),
                contentPadding = PaddingValues(vertical = (halfCount * itemHeight.value).dp)
            ) {
                items(items.size) { index ->
                    Box(
                        modifier = Modifier
                            .height(itemHeight)
                            .fillMaxWidth()
                            .graphicsLayer {
                                val layoutInfo = listState.layoutInfo
                                val visibleInfo = layoutInfo.visibleItemsInfo.find { it.index == index }

                                if (visibleInfo != null) {
                                    val itemCenterY = visibleInfo.offset + (visibleInfo.size / 2f)
                                    val viewportCenterY = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
                                    val distanceToCenter = itemCenterY - viewportCenterY

                                    val angleRad = distanceToAngleRad(distanceToCenter)
                                    val wheelY = mapToWheelY(distanceToCenter)
                                    val falloff = (angleRad / maxAngleRad).absoluteValue

                                    translationY = wheelY - distanceToCenter
                                    rotationX = -Math.toDegrees(angleRad.toDouble()).toFloat()

                                    scaleX = 1f - (falloff * 0.15f)
                                    scaleY = 1f - (falloff * 0.15f)
                                    alpha = 1f - falloff
                                    cameraDistance = 4f * density.density
                                } else {
                                    alpha = 0f
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val isSelected = centeredIndex == index
                        Text(
                            text = items[index],
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Normal,
                            color = if (isSelected) textPrimary else textSecondary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Picker 内部内容（无玻璃外壳），供 FlutterGlassMenu 弹窗内容层使用。
 */
@Composable
fun NumberPickerInlineContent(
    value: Float,
    onValueChange: (Float) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    range: List<Float>,
    label: String,
    isLight: Boolean,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val textSecondary = if (isLight) Color(0xFF1D1D1F) else Color.White
    var pendingValue by remember(value) { mutableFloatStateOf(value) }

    val buttonSize = 44.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp),  // 卡片左右边距 = 圆角44dp - 按钮半宽22dp
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 头部：SpaceBetween 模式下按钮自然位于左右两端
        // 22dp 的 Column padding 使按钮中心正好在 44dp(=圆角半径)处，与圆角同心
        // padding(top=18dp) 将按钮垂直下移使其竖直中心对齐圆角圆心（44dp - 4dp - 22dp = 18dp）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 关闭按钮 — 左上角
            GlasmioIconButton(
                onClick = onCancel,
                backdrop = backdrop,
                size = buttonSize,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_xmark),
                    contentDescription = "取消",
                    modifier = Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(textSecondary)
                )
            }

            // 标题 — 居中
            Text(
                text = label,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isLight) Color(0xFF1D1D1F) else Color.White
            )

            // 确定按钮 — 右上角
            GlasmioIconButton(
                onClick = { onValueChange(pendingValue); onConfirm() },
                backdrop = backdrop,
                emphasis = GlassEmphasis.Prominent,
                size = buttonSize,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_checkmark),
                    contentDescription = "确定",
                    modifier = Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(Color.White)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        NumberPicker(
            value = pendingValue,
            onValueChange = { pendingValue = it },
            range = range,
            label = label,
            isLight = isLight
        )
    }
}

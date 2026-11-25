package com.example.schedule.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * [index] 越小越靠左；最右一枚最先露出（典型：[0]=重命名、[1]=删除）。
 */
@Immutable
data class SwipeAction(
    val index: Int,
    val color: Color,
    val iconRes: Int,
    val contentDescription: String,
)

/** 同一列表内同时只允许一张卡片处于展开态。 */
@Stable
class SwipeableListState {
    var currentOpenKey: Any? by mutableStateOf(null)
        private set

    fun setOpen(key: Any) {
        currentOpenKey = key
    }

    fun close() {
        currentOpenKey = null
    }
}

@Composable
fun rememberSwipeableListState(): SwipeableListState = remember { SwipeableListState() }

private enum class SwipeState { IDLE, REVEALED }

private const val SwipeRubberBandConstant = 0.55f

private fun rubberBandSwipeOffset(
    currentOffset: Float,
    delta: Float,
    revealOffsetPx: Float,
    viewportWidthPx: Float,
): Float {
    val revealOffset = revealOffsetPx.coerceAtLeast(0f)
    val nextOffset = (currentOffset + delta).coerceAtMost(0f)
    if (nextOffset >= -revealOffset || viewportWidthPx <= 0f) return nextOffset

    val overscroll = abs(nextOffset) - revealOffset
    val previousOverscroll = (abs(currentOffset) - revealOffset).coerceAtLeast(0f)
    val dimension = viewportWidthPx.coerceAtLeast(revealOffset)
    val resistance = dimension / (dimension + SwipeRubberBandConstant * previousOverscroll)
    val resistedOverscroll = previousOverscroll + (overscroll - previousOverscroll) * resistance
    return -revealOffset - resistedOverscroll.coerceAtLeast(0f)
}

/**
 * 弹性滑动 → 过半/快滑展开 → 点按钮执行并收起；同一列表只展开一张。
 */
@Composable
fun SwipeableRow(
    modifier: Modifier = Modifier,
    key: Any,
    listState: SwipeableListState,
    actions: List<SwipeAction>,
    onAction: (Int) -> Unit,
    content: @Composable () -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    var swipeState by remember(key) { mutableStateOf(SwipeState.IDLE) }
    val coroutineScope = rememberCoroutineScope()

    val actionButtonWidth = 60.dp
    val gapPx = with(androidx.compose.ui.platform.LocalDensity.current) { 6.dp.toPx() }
    val actionButtonWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        actionButtonWidth.toPx()
    }
    val totalActionsWidthPx = actionButtonWidthPx * actions.size + gapPx * 2
    val snapThresholdPx = -totalActionsWidthPx / 2
    val velocityThreshold = with(androidx.compose.ui.platform.LocalDensity.current) {
        500.dp.toPx()
    }

    var viewportWidthPx by remember { mutableStateOf(0f) }

    val flingOffset = remember(key) { Animatable(0f) }
    val shouldIntercept = listState.currentOpenKey != null && listState.currentOpenKey == key
    var shouldComposeActions by remember(key) { mutableStateOf(false) }

    val revealedThresholds = remember(actions, actionButtonWidthPx, gapPx) {
        actions.indices.map { index ->
            val trueIndex = actions.size - index - 1
            gapPx + actionButtonWidthPx * trueIndex + actionButtonWidthPx / 2
        }
    }
    val revealedCount by remember(key, revealedThresholds) {
        derivedStateOf { revealedThresholds.count { abs(flingOffset.value) >= it } }
    }

    fun executeAction(action: SwipeAction) {
        coroutineScope.launch {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        //  数据变更（onAction）必须放在收起完成之后：
        // 归档/切换会让本行立即离开组合 → 旧协程被取消 → close() 永远不执行
        // → currentOpenKey 残留 → 新位置的卡片第一次点击被拦截层吞掉
        coroutineScope.launch {
            swipeState = SwipeState.IDLE
            flingOffset.animateTo(0f)
            shouldComposeActions = false
            listState.close()
            onAction(action.index)
        }
    }

    LaunchedEffect(listState.currentOpenKey) {
        if (listState.currentOpenKey != key) {
            coroutineScope.launch {
                swipeState = SwipeState.IDLE
                flingOffset.animateTo(0f)
                shouldComposeActions = false
            }
        }
    }

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { viewportWidthPx = it.width.toFloat() },
    ) {
        if (shouldComposeActions) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(with(androidx.compose.ui.platform.LocalDensity.current) {
                        totalActionsWidthPx.toDp()
                    })
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = {},
                    )
                    .padding(end = 6.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions.forEachIndexed { index, action ->
                    Box(
                        modifier = Modifier
                            .width(actionButtonWidth)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        val trueIndex = actions.size - index - 1
                        val isVisible = trueIndex < revealedCount

                        val scaleAnim = remember { Animatable(0.6f) }
                        val alphaAnim = remember { Animatable(0f) }
                        LaunchedEffect(isVisible) {
                            if (isVisible) {
                                launch {
                                    alphaAnim.animateTo(1f, tween(300, easing = LinearOutSlowInEasing))
                                }
                                scaleAnim.animateTo(1f, tween(300, easing = LinearOutSlowInEasing))
                            } else {
                                launch {
                                    alphaAnim.animateTo(0f, tween(100, easing = LinearOutSlowInEasing))
                                }
                                scaleAnim.animateTo(0.6f, tween(200, easing = LinearOutSlowInEasing))
                            }
                        }

                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = scaleAnim.value
                                    scaleY = scaleAnim.value
                                    alpha = alphaAnim.value
                                }
                                .size(48.dp)
                                .background(action.color, CircleShape)
                                .clickable {
                                    coroutineScope.launch { executeAction(action) }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(action.iconRes),
                                contentDescription = action.contentDescription,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = flingOffset.value }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        coroutineScope.launch {
                            val newOffset = rubberBandSwipeOffset(
                                currentOffset = flingOffset.value,
                                delta = delta,
                                revealOffsetPx = totalActionsWidthPx,
                                viewportWidthPx = viewportWidthPx,
                            )
                            flingOffset.snapTo(newOffset)
                            swipeState =
                                if (newOffset < snapThresholdPx) SwipeState.REVEALED
                                else SwipeState.IDLE
                        }
                    },
                    onDragStarted = {
                        coroutineScope.launch { shouldComposeActions = true }
                        listState.setOpen(key)
                    },
                    onDragStopped = { velocity ->
                        coroutineScope.launch {
                            val currentOffset = flingOffset.value
                            val isFastSwipe = velocity < -velocityThreshold
                            val shouldReveal =
                                (currentOffset < snapThresholdPx ||
                                        (isFastSwipe && currentOffset < 0)) && velocity <= 0

                            when {
                                shouldReveal -> {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    swipeState = SwipeState.REVEALED
                                    flingOffset.animateTo(
                                        targetValue = -totalActionsWidthPx,
                                        animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f),
                                        initialVelocity = velocity,
                                    )
                                }
                                else -> {
                                    if (listState.currentOpenKey == key) listState.close()
                                    swipeState = SwipeState.IDLE
                                    val finalVelocity =
                                        if (currentOffset == 0f && velocity > 0) 0f else velocity
                                    flingOffset.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f),
                                        initialVelocity = finalVelocity,
                                    )
                                    shouldComposeActions = false
                                }
                            }
                        }
                    },
                ),
        ) {
            content()

            if (shouldIntercept) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { listState.close() },
                        ),
                )
            }
        }
    }
}

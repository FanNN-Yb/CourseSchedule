package com.example.schedule.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 边界过滚动的自建弹性位移（旧橡皮筋手感的内建版，单链路）：
 *
 * - 消费列表边界外的过滚动（onPostScroll 全量收下），让外层的
 *   GlasmioOverscrollHost 不再叠加拉伸 —— 单一责任、不打架；
 * - 位移用布局级 offset 呈现（非 graphicsLayer）：每帧触发子树
 *   onGloballyPositioned，液态玻璃的背景采样逐帧跟上拉伸（玻璃不冻结）；
 * - [BounceOverscrollState.offset] 为快照状态，供模糊/折叠进度联动读取。
 *
 * 符号约定：offset 正 = 下拉（内容向下），负 = 上拉（内容向上滑进顶栏）。
 */
@Stable
class BounceOverscrollState internal constructor(
    internal val scope: CoroutineScope,
    private val maxPx: Float,
) {
    private val anim = Animatable(0f)

    /** 当前过滚动位移（px，正 = 下拉，负 = 上拉），静止时为 0。 */
    val offset: Float get() = anim.value

    /** 拖动增量（过滚动剩余量，px）。 */
    suspend fun dragBy(delta: Float) {
        anim.snapTo((anim.value + delta).coerceIn(-maxPx, maxPx))
    }

    /** 松手后弹回原位。 */
    suspend fun settle() {
        if (anim.value != 0f) {
            anim.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 380f))
        }
    }
}

/** 上拉/下拉的最大弹性位移：140dp。 */
@Composable
fun rememberBounceOverscrollState(): BounceOverscrollState {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val maxPx = remember(density) { with(density) { 140.dp.toPx() } }
    return remember(maxPx) { BounceOverscrollState(scope, maxPx) }
}

/**
 * 给可滚动内容挂弹性过滚动：边界外的过滚动由本修饰符消费并以布局位移呈现。
 */
fun Modifier.bounceOverscroll(state: BounceOverscrollState): Modifier = this
    .nestedScroll(
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (available.y != 0f) {
                    state.scope.launch { state.dragBy(available.y) }
                }
                // 全量消费 → GlasmioOverscrollHost 不再叠加拉伸（单链路）
                return available
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                state.settle()
                return available
            }
        },
    )
    .offset {
        // 布局级位移（非 graphicsLayer）：玻璃背景采样逐帧跟上拉伸
        IntOffset(0, state.offset.roundToInt())
    }

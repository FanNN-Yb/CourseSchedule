package com.example.schedule.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import io.github.fannn_yb.rubberband.overscroll.OffsetOverscrollEffect
import io.github.fannn_yb.rubberband.overscroll.util.ProgressConverter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * 应用内版橡皮筋容器（逻辑来自 Rubber-Band 库的同名组件，仅一处关键改动）。
 *
 * 关键改动：拉伸位移用 [Modifier.offset]（布局放置层）实现，
 * 而不是库版本的 graphicsLayer（纯图层平移）。
 *
 * 原因：液态玻璃的背景采样依赖 onGloballyPositioned 感知内容位置。
 * graphicsLayer 平移不触发任何布局/位置回调，过度拉伸时玻璃组件
 * 既不会重绘、采样坐标也停在拉伸前 —— 表现为玻璃画面冻结在最后一帧、
 * 不跟随拉伸，拉伸结束后残留旧画面直到下一次真实滚动。
 * offset 平移会让内容子树的位置逐帧变化，玻璃组件随之逐帧重采样，完全跟手。
 *
 * 其余逻辑（嵌套滚动接管、非线性阻尼、惯性撞墙回弹、屏蔽列表自带 overscroll）
 * 与库版本保持一致。
 */
/**
 * 橡皮筋状态：可提升到页面级，供容器外的玻璃组件读取过冲量。
 *
 * [offset] 是快照状态（Animatable 支持）：拉伸/回弹期间逐帧变化。
 * 玻璃组件在绘制阶段读取它（例如通过 rememberBackdrop 包装 backdrop），
 * 即可被逐帧失效并重新采样背景 —— 这是修复"拉伸时玻璃冻结"的通知通路。
 */
@Stable
class RubberBandState internal constructor(
    animationScope: CoroutineScope,
    animationSpec: AnimationSpec<Float>
) {
    internal val connection = RubberBandConnection(animationScope, animationSpec)

    /** 当前原始过冲量（px，+= 下拉），静止时为 0 */
    val offset: Float get() = connection.offset
}

@Composable
fun rememberRubberBandState(
    animationSpec: AnimationSpec<Float> = OffsetOverscrollEffect.DefaultAnimationSpec
): RubberBandState {
    val animationScope = rememberCoroutineScope()
    return remember(animationScope, animationSpec) {
        RubberBandState(animationScope, animationSpec)
    }
}

@Composable
fun RubberBandContainer(
    state: RubberBandState = rememberRubberBandState(),
    modifier: Modifier = Modifier,
    maxFraction: Float = 0.65f,
    content: @Composable () -> Unit
) {
    var containerHeight by remember { mutableStateOf(0) }
    Box(
        modifier = modifier
            .nestedScroll(state.connection)
            .onSizeChanged { containerHeight = it.height }
            .offset {
                // 必须在 lambda 内读取 offset，才会被快照系统追踪、随回弹动画逐帧重新放置。
                // offset 是放置层平移：每帧都会触发子树的 onGloballyPositioned，
                // 液态玻璃的背景采样因此能逐帧跟上拉伸（graphicsLayer 平移则会让玻璃冻结）。
                val raw = state.offset
                if (raw == 0f || containerHeight <= 0) {
                    IntOffset.Zero
                } else {
                    val maxDistance = containerHeight.toFloat().coerceAtLeast(1f)
                    IntOffset(
                        0,
                        (ProgressConverter.convert(raw / maxDistance, maxFraction) * maxDistance)
                            .roundToInt()
                    )
                }
            }
    ) {
        CompositionLocalProvider(LocalOverscrollFactory provides NoOpOverscrollFactory()) {
            content()
        }
    }
}

/** 空边界效果：列表自带的 overscroll 全部变成直通，视觉上不做任何事。 */
private object NoOpOverscrollEffect : OverscrollEffect {
    override val isInProgress: Boolean = false
    override val node: DelegatableNode = object : Modifier.Node() {}

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset
    ): Offset = performScroll(delta)

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity
    ) {
        performFling(velocity)
    }
}

// foundation 1.12 起，OverscrollFactory 接口显式要求实现 equals/hashCode（data class 即可满足）
private data class NoOpOverscrollFactory(val id: Int = 0) : OverscrollFactory {
    override fun createOverscrollEffect(): OverscrollEffect = NoOpOverscrollEffect
}

internal class RubberBandConnection(
    private val animationScope: CoroutineScope,
    private val animationSpec: AnimationSpec<Float>
) : NestedScrollConnection {

    /** 原始累计过冲量（px，+= 下拉）。显示时经 ProgressConverter 衰减。 */
    private val overscrollAnimation = Animatable(0f, 0.5f)

    val offset: Float get() = overscrollAnimation.value

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        val delta = available.y
        val current = overscrollAnimation.value
        if (current == 0f || delta == 0f || delta.sign == current.sign) return Offset.Zero
        // 反向回拉：先消化自身过冲（穿越零点则归零，避免"先拉回一段才动"）
        val newValue = current + delta
        return if (newValue.sign != current.sign) {
            animationScope.launch { overscrollAnimation.snapTo(0f) }
            Offset(0f, -current) // 剩余量继续流向列表/post-scroll
        } else {
            animationScope.launch { overscrollAnimation.snapTo(newValue) }
            Offset(0f, delta)
        }
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset {
        // 只接管手指拖拽的增量；惯性帧（SideEffect）一概放行，防止持续叠加导致漂移
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        val delta = available.y
        if (delta == 0f) return Offset.Zero
        animationScope.launch { overscrollAnimation.snapTo(overscrollAnimation.value + delta) }
        return Offset(0f, delta)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (overscrollAnimation.value != 0f) {
            // 已拉出过冲：带着松手速度顺势冲一点再弹簧归位，并消费全部速度
            overscrollAnimation.animateTo(0f, animationSpec, available.y)
            return available
        }
        return Velocity.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        if (available.y != 0f) {
            // 列表惯性撞墙后剩下的速度：从这里注入弹簧，产生 iOS 式撞墙回弹
            overscrollAnimation.animateTo(0f, animationSpec, available.y)
            return Velocity.Zero
        }
        return available
    }
}

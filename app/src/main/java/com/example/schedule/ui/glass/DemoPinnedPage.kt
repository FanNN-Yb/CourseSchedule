package com.example.schedule.ui.glass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.derivedStateOf
import kotlin.math.max
import com.example.schedule.ui.glass.RubberBandContainer
import com.example.schedule.ui.glass.rememberRubberBandState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.schedule.ui.glass.BounceOverscrollState
import com.example.schedule.ui.glass.bounceOverscroll
import com.example.schedule.ui.glass.rememberBounceOverscrollState
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.core.IosTypography
import io.github.neoruaa.glasmio.glass.LocalGlassBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.LayerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import io.github.neoruaa.glasmio.scaffold.GlasmioPageBody
import io.github.neoruaa.glasmio.scaffold.rememberGlasmioHeaderCollapse
import io.github.neoruaa.glasmio.scaffold.rememberGlasmioHeaderScrollEdge
import io.github.neoruaa.glasmio.ui.GlasmioTopBar

/**
 * GlasmioPageBody（顶栏渐进边缘模糊）+ 折叠大标题 + GlasmioTopBar + 自管 LazyColumn。
 *
 * 与 GlasmioPinnedListPage 的差异：内容包在 [RubberBandContainer] 里 ——
 * 内容不足一屏时上拉，整个列表以布局级位移滑进顶部模糊带（模糊进度由橡皮筋
 * 位移驱动），复现旧版「仅靠橡皮筋拉伸就有顶部模糊」的手感。
 *
 *  采样分层铁律：顶栏玻璃采样内部 pageBackdrop（只录列表，不含顶栏自身）。
 *    调用方还需在更外层自备「纯色层」（给开关滑块镜头）与「页面内容层」（给弹窗），
 *    任何玻璃都不得采样包含自己的层 —— 否则渲染树循环闪退。
 *
 * @param flatBackdrop 调用方的纯色层：页面内容里的开关滑块镜头采样它
 *                     （铺满全窗、不含开关；传进来只为约定，内容 lambda 自行闭包引用）
 */
@Composable
fun DemoPinnedPage(
    title: String,
    subtitle: String?,
    backgroundColor: Color,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    val scrollCollapse = rememberGlasmioHeaderCollapse(listState)
    val scrollEdge = rememberGlasmioHeaderScrollEdge(listState)
    //  橡皮筋位移驱动模糊/折叠：仅上拉（offset 负 = 内容滑进顶栏）触发，
    //    下拉（内容远离顶栏）不触发（RubberBandState 注释：+= 下拉）
    val rubberBandState = rememberRubberBandState()
    val density = LocalDensity.current
    val rubberEdge by remember(rubberBandState, density) {
        derivedStateOf {
            val threshold = with(density) { 140.dp.toPx() }
            ((-rubberBandState.offset) / threshold).coerceIn(0f, 1f)
        }
    }
    val edgeProgress = max(scrollEdge, rubberEdge)
    val collapseProgress = max(scrollCollapse, rubberEdge)

    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    //  内容顶部从模糊带下沿（statusBar + 62dp 顶栏 + 34dp = statusBar + 96dp）
    //    之下开始（+12dp 间距）—— 静止时大标题不被常驻模糊盖住
    val contentPadding = PaddingValues(
        start = 16.dp,
        top = statusBar + 72.dp,
        end = 16.dp,
        bottom = bottomPadding + 24.dp,
    )
    val pageBackdrop = rememberLayerBackdrop()

    Box(modifier.fillMaxSize()) {
        GlasmioPageBody(
            contentPadding = contentPadding,
            backgroundColor = backgroundColor,
            //  顶部渐进模糊常驻（不跟滚动联动）
            topBarBlurProgress = 1f,
            useOverscrollHost = false,
            backdrop = pageBackdrop,
        ) { padding ->
            //  橡皮筋拉伸：布局级位移让玻璃背景逐帧重采样（内容上拉滑进顶栏时
            //    模糊/折叠联动）；过滚动由它全量消费，PageBody 的 host 不会叠加拉伸
            //  橡皮筋独占过滚动：GlasmioPageBody 以 useOverscrollHost = false 关掉
            //    内置 host（否则 host 的拉伸与橡皮筋叠加，复位后同幅度二次弹跳）
            RubberBandContainer(state = rubberBandState, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "large_title") {
                    PinnedLargeTitle(
                        title = title,
                        subtitle = subtitle,
                        collapseProgress = collapseProgress,
                    )
                }
                content()
            }
            }
            }
        }
        // 顶栏玻璃采样 pageBackdrop（只录列表内容，不含顶栏自身 —— 无自引用）
        CompositionLocalProvider(LocalGlassBackdrop provides pageBackdrop) {
            GlasmioTopBar(
                title = title,
                subtitle = subtitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                collapseProgress = collapseProgress,
            )
        }
    }

@Composable
private fun PinnedLargeTitle(
    title: String,
    subtitle: String?,
    collapseProgress: Float,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = 1f - collapseProgress
                val scale = 1f - 0.04f * collapseProgress
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .blur(6.dp * collapseProgress)
            .offset(y = (-10).dp)
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Text(
            title,
            style = IosTypography.largeTitle,
            fontWeight = FontWeight.Bold,
            color = GlasmioTheme.colors.content,
        )
        subtitle?.let {
            Text(
                it,
                style = IosTypography.subheadline,
                color = GlasmioTheme.colors.secondaryContent,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

package com.example.schedule.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.unit.Dp
import io.github.neoruaa.glasmio.glass.NavigationGlassMaterial
import io.github.neoruaa.glasmio.glass.NavigationInnerShadowRenderer
import io.github.neoruaa.glasmio.glass.navigationGlassBackground
import io.github.neoruaa.glasmio.glass.rememberNavigationInnerShadows
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.LayerBackdrop

/**
 * 直接套用 Glasmio 自己的玻璃实现 —— `Modifier.navigationGlassBackground`。
 *
 * 这是 Glasmio 导航栏 / GlassSurface 内部用的同一条路径：
 * `navigationMaterial = true` + `innerShadows != null` + 不传 `surfaceOverlay`
 * 时，库会走 `nativeNavigationGlassFilter(...)` + `NativeGlassCompletion`，
 * 也就是完整的 iOS 27 原生材质：采样模糊、饱和度提升、face 填充、外圈暗边、
 * 顶部高光、内阴影全在同一次 AGSL 合成里出，和示例 App 里的玻璃一模一样。
 *
 * 不再手动拼 drawRect / 填充栈，只把组件原有的 backdrop、形状、
 * 尺寸、按压动画原样喂进去。
 */
object Ios27Glass {
    /** 小件：表头星期块、周次胶囊、图标按钮、底部导航栏、设置页按钮 */
    val Small: NavigationGlassMaterial = NavigationGlassMaterial.RegularSmall

    /** 中等面板：齿轮选择器 / 液态菜单面板 */
    val Medium: NavigationGlassMaterial = NavigationGlassMaterial.RegularMedium

    /** 大面板：弹窗、课程详情卡 */
    val Large: NavigationGlassMaterial = NavigationGlassMaterial.RegularLarge

    /** 几乎纯透：底部导航栏的选中胶囊 */
    val Clear: NavigationGlassMaterial = NavigationGlassMaterial.Clear
}

/** 一个玻璃表面需要的两样东西：材质档位 + 内阴影/边缘渲染器。 */
class Ios27Surface internal constructor(
    val material: NavigationGlassMaterial,
    val innerShadows: NavigationInnerShadowRenderer?,
)

@Composable
fun rememberIos27Surface(material: NavigationGlassMaterial): Ios27Surface {
    val innerShadows = rememberNavigationInnerShadows(enabled = true)
    return androidx.compose.runtime.remember(material, innerShadows) {
        Ios27Surface(material, innerShadows)
    }
}

/**
 *
 * @param layerBlock 组件原有的按压 / 拖拽形变，原样透传
 * @param backgroundOverlay 材质之上、内容之下的自绘层（品牌色染色、选中描边等）
 */
fun Modifier.ios27Glass(
    surface: Ios27Surface,
    backdrop: Backdrop,
    shape: () -> Shape,
    containerColor: Color,
    isDark: Boolean,
    /**
     * 显式模糊半径。不传 = 用 `navigationGlassGeometryProfile` 的 5dp。
     *
     *  注意库里的采样缩放：`GlassSurface` 的 `samplingScale` 会把背景按
     * `navigationGlassCaptureScale(最小边)` = `(50/x).coerceIn(.25f,.5f)` 的比例**降采样**，
     * 这个比例同时乘到模糊核上 —— 即传 5dp 在 300dp 的卡片上实际是 20dp 的模糊。
     * 想「玻璃后面还看得出是什么」就必须显式传更小的值。
     */
    blurRadius: Dp? = null,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    highlightAlpha: Float = 1f,
    shadowAlpha: Float = 1f,
    /**
     * 材质染色层。默认 null = 完全不染色，和示例 App 里的玻璃一致（烟熏半透 + 亮边）。
     */
    materialTint: Color? = null,
    backgroundOverlay: (DrawScope.() -> Unit)? = null,
    /**
     * 导出这层玻璃自己的像素材质，给「浮在玻璃上面」的子按钮采样用。
     * 传空 backdrop 反而会因为采样到空纹理而渲染成黑色。
     */
    exportedBackdrop: LayerBackdrop? = null,
): Modifier = this.navigationGlassBackground(
    backdrop = backdrop,
    shape = shape,
    containerColor = containerColor,
    navigationMaterial = true,
    blurRadius = blurRadius ?: androidx.compose.ui.unit.Dp.Unspecified,
    innerShadows = surface.innerShadows,
    isDark = isDark,
    material = surface.material,
    materialTint = materialTint,
    layerBlock = layerBlock,
    highlightAlphaMultiplier = highlightAlpha,
    shadowAlpha = shadowAlpha,
    backgroundOverlay = backgroundOverlay,
    exportedBackdrop = exportedBackdrop,
)

package com.example.schedule.ui.glass

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.schedule.ui.glass.LiquidGlassConfig
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.drawPlainBackdrop
import io.github.neoruaa.glasmio.glass.render.effects.blur
import io.github.neoruaa.glasmio.glass.render.effects.runtimeShaderEffect
import io.github.neoruaa.glasmio.glass.render.isRuntimeShaderSupported

/**
 * 顶部渐进式模糊遮罩的 AGSL 着色器。
 * 仅增加 opacity 乘数（随滚动渐隐联动）与可调的 fadeStart 参数。
 */
private const val TOP_SCRIM_SHADER = """
uniform shader content;

uniform float2 size;
uniform float fadeStart;
uniform float opacity;
uniform float fromBottom;
layout(color) uniform half4 tint;
uniform float tintIntensity;

half4 main(float2 coord) {
    float m0 = smoothstep(size.y, size.y * fadeStart, coord.y);
    // fromBottom=1 时翻转方向：遮罩贴底（底缘不透明、向上渐隐），用于底部搜索栏区域
    float dir = mix(m0, 1.0 - m0, fromBottom);
    // pow < 1 抬高过渡带中段的不透明度：透出的清晰原文变少，双影减轻
    float mask = pow(dir, 0.7) * opacity;
    float maskT = pow(dir, 0.7) * opacity;
    return mix(content.eval(coord) * mask, tint * maskT, tintIntensity);
}
"""

/**
 * 顶部渐进式模糊遮罩 —— 与库示例（ProgressiveBlurContent）完全同构：
 * drawPlainBackdrop 采样背景 → blur(radius) → AGSL AlphaMask（顶部不透明、向下渐隐）+ 主题色染色。
 *
 * - [opacity]：遮罩整体透明度（快照状态读取器，如随滚动渐隐的 titleHidden），
 *   在 effects 阶段读取，变化时只重建渲染效果链，不触发整页重组。
 * - API < 33（无 AGSL）时退化为纯色渐变遮罩。
 *
 * 注意：本组件采样 [backdrop]，必须放置在 layerBackdrop 录制节点【外】
 * （同玻璃铁律 2，避免背景层自引用环）。
 */
@Composable
fun Modifier.topProgressiveBlurScrim(
    backdrop: Backdrop,
    tint: Color,
    opacity: () -> Float,
    fromBottom: Boolean = false,
    blurRadiusDp: Float = LiquidGlassConfig.topScrimBlurDp,
    tintIntensity: Float = LiquidGlassConfig.topScrimTintIntensity,
    fadeStartFraction: Float = LiquidGlassConfig.topScrimFadeStart
): Modifier {
    if (!isRuntimeShaderSupported()) {
        val o = opacity()
        return background(
            Brush.verticalGradient(
                *(if (fromBottom) listOf(
                    0f to Color.Transparent,
                    0.2f to tint.copy(alpha = 0.5f * o),
                    0.6f to tint.copy(alpha = 1f * o),
                    1f to tint.copy(alpha = 1f * o)
                ) else listOf(
                    0f to tint.copy(alpha = 1f * o),
                    0.4f to tint.copy(alpha = 1f * o),
                    0.8f to tint.copy(alpha = 0.5f * o),
                    1f to Color.Transparent
                )).toTypedArray()
            )
        )
    }
    return drawPlainBackdrop(
        backdrop = backdrop,
        shape = { RectangleShape },
        effects = {
            blur(blurRadiusDp.dp.toPx())
            runtimeShaderEffect(
                "TopProgressiveBlur",
                TOP_SCRIM_SHADER,
                "content"
            ) {
                setFloatUniform("size", size.width, size.height)
                setFloatUniform("fadeStart", fadeStartFraction)
                setFloatUniform("opacity", opacity())
                setFloatUniform("fromBottom", if (fromBottom) 1f else 0f)
                setColorUniform("tint", tint)
                setFloatUniform("tintIntensity", tintIntensity)
            }
        }
    )
}

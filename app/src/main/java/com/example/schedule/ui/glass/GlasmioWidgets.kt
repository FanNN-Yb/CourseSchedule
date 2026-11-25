package com.example.schedule.ui.glass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import io.github.neoruaa.glasmio.glass.GlassEmphasis
import io.github.neoruaa.glasmio.glass.GlassSurface
import io.github.neoruaa.glasmio.glass.LocalGlassBackdrop
import io.github.neoruaa.glasmio.glass.render.Backdrop

/**
 * App 级玻璃控件适配层：把旧自研控件的形状/尺寸习惯接到 Glasmio 原生材质上。
 * 材质、按压高光、液态形变全部来自 Glasmio（GlassSurface），不再自绘叠加层。
 */

/** 胶囊玻璃按钮（替代旧 LiquidButton）。[emphasized] = 强调色玻璃（iOS Prominent）。 */
@Composable
fun GlassCapsuleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalGlassBackdrop.current,
    emphasized: Boolean = false,
    enabled: Boolean = true,
    height: Dp = 42.dp,
    horizontalPadding: Dp = 16.dp,
    content: @Composable RowScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier.height(height),
        backdrop = backdrop,
        shape = Capsule(),
        emphasis = if (emphasized) GlassEmphasis.Prominent else GlassEmphasis.Regular,
        enabled = enabled,
        onClick = onClick,
    ) {
        Row(
            Modifier.padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}

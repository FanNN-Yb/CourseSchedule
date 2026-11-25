package com.example.schedule.ui.glass

/**
 * 液态玻璃组件全局参数（仅保留本工程实际引用的项）。
 *
 * 玻璃表面颜色/容器色统一走 Glasmio 主题（GlasmioTheme.colors / defaultGlassColors）。
 */
object LiquidGlassConfig {

    /** 顶部遮罩模糊半径 (dp) */
    const val topScrimBlurDp = 4f

    /** 顶部遮罩主题色染色强度（仅渐变兜底路径使用；Glasmio 材质不染色） */
    const val topScrimTintIntensity = 0.9f

    /** 顶部遮罩从起始侧到该比例处渐隐为透明（0~1） */
    const val topScrimFadeStart = 0.77f
}

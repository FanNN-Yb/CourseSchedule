package com.example.schedule

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.schedule.ui.glass.rememberAppGlassIconResolver
import com.example.schedule.ui.theme.SystemFont
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.core.defaultGlassColors
import io.github.neoruaa.glasmio.icons.GlasmioIconProvider

/**
 * iOS 26 设计规范色板。
 * 深色值取自规范 :root 变量（--bg/--surface/--card/--border/--text…）；
 * 浅色值为对应的 iOS 语义色浅色双值（规范第 04 章：深浅双值沿用，数值不变）。
 */

// 系统强调色（深 / 浅）
//  全 App 统一蓝色（唯一色源）：主色、所有蓝色按钮、课程色板蓝色、底部 Tab 选中色
//    共用这一个值。要换全 App 的蓝，只改这一行即可（不再分深浅两档）。
//    当前值：经典 iOS 蓝 #007AFF
val UnifiedBlue = Color(0xFF007AFF)

data class ThemeColor(val name: String, val color: Color)
val ThemePresetColors = listOf(
    ThemeColor("蓝色", Color(0xFF007AFF)),
    ThemeColor("橙色", Color(0xFFFF9500)),
    ThemeColor("绿色", Color(0xFF34C759)),
    ThemeColor("黄色", Color(0xFFFFCC00)),
    ThemeColor("红色", Color(0xFFFF3B30)),
)

/** 当前主题强调色：AppTheme 按自定义主题色注入，取色统一走这里。 */
val LocalAccentColor = staticCompositionLocalOf { UnifiedBlue }
private val BlueDark = UnifiedBlue;   private val BlueLight = UnifiedBlue
private val GreenDark = Color(0xFF30D158);  private val GreenLight = Color(0xFF34C759)
private val OrangeDark = Color(0xFFFF9F0A); private val OrangeLight = Color(0xFFFF9500)
private val PurpleDark = Color(0xFFBF5AF2); private val PurpleLight = Color(0xFFAF52DE)
private val PinkDark = Color(0xFFFF375F);   private val PinkLight = Color(0xFFFF2D55)
private val TealDark = Color(0xFF64D2FF);   private val TealLight = Color(0xFF32ADE6)
private val IndigoDark = Color(0xFF5E5CE6); private val IndigoLight = Color(0xFF5856D6)
private val RedDark = Color(0xFFFF453A);    private val RedLight = Color(0xFFFF3B30)
private val YellowDark = Color(0xFFFFD60A); private val YellowLight = Color(0xFFFFCC00)

/**
 * systemRed/Orange/Yellow/Green/Mint/Teal/Cyan/Blue/Indigo/Purple/Pink/Brown
 */
private val CourseColorsDark = listOf(
    Color(0xFFFF3B30), Color(0xFFFF9500), Color(0xFFFFCC00), Color(0xFF34C759),
    Color(0xFF00C7BE), Color(0xFF30B0C7), Color(0xFF32ADE6), Color(0xFF007AFF),
    Color(0xFF5856D6), Color(0xFFAF52DE), Color(0xFFFF2D55), Color(0xFFA2845E),
)

private val CourseColorsLight = CourseColorsDark

private val DarkScheme = darkColorScheme(
    primary = BlueDark,
    onPrimary = Color.White,
    primaryContainer = BlueDark,
    onPrimaryContainer = Color.White,
    secondary = TealDark,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF242428),
    onSecondaryContainer = TealDark,
    tertiary = PurpleDark,
    background = Color(0xFF0A0A0C),          // --bg
    onBackground = Color(0xFFF2F2F7),        // --text
    surface = Color(0xFF131316),             // --surface
    onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFF1C1C1F),      // --card
    onSurfaceVariant = Color(0x9EEBEBF5),    // --text2  rgba(235,235,245,.62)
    outline = Color(0x3DFFFFFF),
    outlineVariant = Color(0x24FFFFFF),      // --border2 rgba(255,255,255,.14) → 课表网格线
    error = RedDark,
    onError = Color.White,
)

private val LightScheme = lightColorScheme(
    primary = BlueLight,
    onPrimary = Color.White,
    primaryContainer = BlueLight,
    onPrimaryContainer = Color.White,
    secondary = TealLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5E5EA),
    onSecondaryContainer = BlueLight,
    tertiary = PurpleLight,
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF000000),
    surface = Color.White,
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFE9E9EE),
    onSurfaceVariant = Color(0x993C3C43),    // rgba(60,60,67,.6)
    outline = Color(0x4A3C3C43),             // iOS separator .29
    outlineVariant = Color(0x293C3C43),      // 课表网格线 ~.16
    error = RedLight,
    onError = Color.White,
)

/** 按当前明暗模式取课程格颜色。 */
@Composable
fun courseColor(id: Long): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val list = if (dark) CourseColorsDark else CourseColorsLight
    val idx = (id % list.size).toInt().let { if (it < 0) -it else it }
    return list[idx]
}

/** 课程颜色：用户在编辑器里指定的色板下标优先，-1 = 按课程 id 自动轮换。 */
@Composable
fun courseColorOf(course: Course): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val list = if (dark) CourseColorsDark else CourseColorsLight
    return if (course.colorIndex in list.indices) list[course.colorIndex] else courseColor(course.id)
}

/** 课程编辑器调色板（与自动轮换共用同一套下标）。 */
@Composable
fun coursePaletteColors(): List<Color> {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) CourseColorsDark else CourseColorsLight
}

@Composable
fun AppTheme(
    dark: Boolean = isSystemInDarkTheme(),
    accentColor: Color = UnifiedBlue,
    content: @Composable () -> Unit,
) {
    // 全局字体：优先厂商 UI 字体（MiSans），数字 / 日期观感更好
    val family = remember { SystemFont.family() }
    val scheme = (if (dark) DarkScheme else LightScheme).copy(
        primary = accentColor,
        primaryContainer = accentColor,
        onPrimary = Color.White,
        onPrimaryContainer = Color.White,
    )
    CompositionLocalProvider(LocalAccentColor provides accentColor) {
        // Glasmio 主题：在 App 自己的 Material 配色/字体之上提供玻璃组件的
        // 颜色（GlassColors）/尺寸/形状局部值；内部仍包 MaterialTheme。
        // 图标走 App 矢量映射（无 SF 字体时 Glasmio 组件的图标来源）
        GlasmioIconProvider(resolver = rememberAppGlassIconResolver()) {
            GlasmioTheme(
                isDark = dark,
                colorScheme = scheme,
                typography = appTypography(family),
                colors = defaultGlassColors(dark, accentColor),
                content = content,
            )
        }
    }
}

/** 把系统字体族挂到全部文字样式上。 */
private fun appTypography(family: androidx.compose.ui.text.font.FontFamily): Typography {
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family),
        headlineSmall = base.headlineSmall.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        titleSmall = base.titleSmall.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family),
        labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family),
    )
}

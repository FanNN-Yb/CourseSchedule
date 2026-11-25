package com.example.schedule.ui.theme

import android.graphics.fonts.Font as PlatformFont
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.io.File
import kotlin.math.abs

/**
 * 系统字体加载：优先用厂商 UI 字体（小米 HyperOS 的 MiSans，数字圆润好看），
 * 按候选表回退 Roboto / Noto。每个字重都校验文件的**真实字重**，
 * 防止"文件名像粗体、真身是 Regular"的伪档位混进来导致粗体失效
 * （针对系统字体的字形兼容与回退处理）。
 */
object SystemFont {
    private const val DIR = "/system/fonts"

    private val regular = listOf("MiSans-Regular.ttf", "Roboto-Regular.ttf", "NotoSans-Regular.ttf")
    private val medium = listOf("MiSans-Medium.ttf", "Roboto-Medium.ttf")
    private val semibold = listOf("MiSans-Semibold.ttf", "MiSans-Medium.ttf")
    private val bold = listOf("MiSans-Bold.ttf", "Roboto-Bold.ttf", "NotoSans-Bold.ttf")

    /** 按候选表找第一个真实字重匹配目标字重的字体文件；读不出字重时放行（fail-open）。 */
    private fun file(candidates: List<String>, targetWeight: Int): File? =
        candidates.firstNotNullOfOrNull { name ->
            File(DIR, name).takeIf { it.isFile }?.let { f ->
                try {
                    val real = PlatformFont.Builder(f).build().style.weight
                    if (abs(real - targetWeight) <= 100) f else null
                } catch (_: Exception) {
                    f
                }
            }
        }

    private val familyLazy: FontFamily by lazy {
        val fonts = buildList {
            file(regular, 400)?.let { add(Font(it, FontWeight.Normal)) }
            file(medium, 500)?.let { add(Font(it, FontWeight.Medium)) }
            file(semibold, 600)?.let { add(Font(it, FontWeight.SemiBold)) }
            file(bold, 700)?.let { add(Font(it, FontWeight.Bold)) }
        }
        if (fonts.isEmpty()) FontFamily.Default else FontFamily(fonts)
    }

    /** 系统 UI 字体族（进程内只找一次）。 */
    fun family(): FontFamily = familyLazy
}

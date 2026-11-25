package com.example.schedule.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.example.schedule.R
import io.github.neoruaa.glasmio.icons.GlasmioIconResolver

/**
 * 把 Glasmio 组件用到的 SF 符号名映射到 App 现有的矢量图标
 * （底部 Tabs、菜单行、弹窗按钮等；App 不带 SF 字体，走 resolver 兜底）。
 * 未命中的名字返回 null → Glasmio 内部回落 Material 图标。
 */
@Composable
fun rememberAppGlassIconResolver(): GlasmioIconResolver {
    val house = ImageVector.vectorResource(R.drawable.ic_house_fill)
    val calendar = ImageVector.vectorResource(R.drawable.ic_calendar_fill)
    val gear = ImageVector.vectorResource(R.drawable.ic_gear_fill)
    val plus = ImageVector.vectorResource(R.drawable.ic_plus)
    val xmark = ImageVector.vectorResource(R.drawable.ic_xmark)
    val checkmark = ImageVector.vectorResource(R.drawable.ic_checkmark)
    val trash = ImageVector.vectorResource(R.drawable.ic_trash)
    val pencil = ImageVector.vectorResource(R.drawable.ic_pencil)
    val chevronLeft = ImageVector.vectorResource(R.drawable.ic_chevron_left)
    val chevronRight = ImageVector.vectorResource(R.drawable.ic_chevron_right)
    // disclosure 箭头：22dp 视口自带留白（墨迹≈SF 17sp 字形），槽位渲染不发胀
    val chevronForward = ImageVector.vectorResource(R.drawable.ic_chevron_forward)
    // Pop-up Button 的上下双箭头：不映射会被 "chevron.up" 前缀命中 → 落成
    val chevronUpDown = ImageVector.vectorResource(R.drawable.ic_chevron_up_down)
    val link = ImageVector.vectorResource(R.drawable.ic_link)
    val importFormat = ImageVector.vectorResource(R.drawable.ic_import_format)
    val importBackup = ImageVector.vectorResource(R.drawable.ic_import_backup)
    return remember(house, calendar, gear, plus, xmark, checkmark, trash, pencil, chevronLeft, chevronRight, chevronForward, chevronUpDown, link, importFormat, importBackup) {
        val map = mapOf(
            "house.fill" to house,
            "calendar" to calendar,
            "gearshape.fill" to gear,
            "plus" to plus,
            "xmark" to xmark,
            "checkmark" to checkmark,
            "trash" to trash,
            "pencil" to pencil,
            "chevron.left" to chevronLeft,
            "chevron.right" to chevronRight,
            // GlasmioListRow 的 disclosure 箭头用 SF 名 "chevron.forward"；
            "chevron.forward" to chevronForward,
            "chevron.up.chevron.down" to chevronUpDown,
            "link" to link,
            // App 专属图标用自定义符号名，避免与 SF 语义冲突
            "app.import.format" to importFormat,
            "app.import.backup" to importBackup,
        )
        GlasmioIconResolver { name -> map[name] }
    }
}

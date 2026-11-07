package com.example.schedule

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.example.schedule.ui.glass.GlassInputSheet
import io.github.neoruaa.glasmio.glass.render.Backdrop
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.runtime.LaunchedEffect

// ═══════════════════════════════════════════════════════════════════════════════
//  右下角「＋」导入菜单已改用 Glasmio 原生 GlasmioMenu（锚点液态变形内置），
//  见 TodayScreen.kt 的 BottomBarRow。本文件只保留菜单项触发的两个导入弹窗。
// ═══════════════════════════════════════════════════════════════════════════════


@Composable
fun ImportCodeSheet(
    store: Store,
    backdrop: Backdrop,
    visible: Boolean = true,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    var text by remember { mutableStateOf("") }
    LaunchedEffect(visible) { if (visible) text = "" }
    GlassInputSheet(
        visible = visible,
        title = "粘贴分享码导入",
        hint = "支持完整链接（schedule://import?data=…）或裸分享码，将作为一张新课表加入。",
        text = text,
        onTextChange = { text = it },
        minLines = 3,
        confirmLabel = "导入",
        onConfirm = {
            val payload = ImportExport.extractSharePayload(text)
            val table = payload?.let { store.parseTablePayload(it) }
            if (table == null) {
                toast("分享码无效，请检查是否完整")
            } else {
                val imported = store.importTable(table, makeActive = true)
                toast("已导入「" + imported.name + "」（" + imported.courses.size + " 门课程）")
                onDismiss()
            }
        },
        onDismiss = onDismiss,
        backdrop = backdrop,
    )
}

@Composable
fun ImportFormatSheet(
    store: Store,
    backdrop: Backdrop,
    visible: Boolean = true,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    var text by remember { mutableStateOf("") }
    LaunchedEffect(visible) { if (visible) text = "" }
    GlassInputSheet(
        visible = visible,
        title = "通过格式导入",
        hint = "每行一门课：课程名 教室 老师 星期 节次 周次\n" +
            "用空格或逗号分隔，教室 / 老师可省略，顺序不限。\n" +
            "例：高等数学 教学楼101 王老师 周一 1-2节 1-16周单\n" +
            "可把教务课表发给 AI，让它转成这个格式再粘贴。",
        text = text,
        onTextChange = { text = it },
        minLines = 5,
        confirmLabel = "解析并导入",
        extraLabel = "复制格式",
        onExtra = {
            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager
            cm.setPrimaryClip(
                android.content.ClipData.newPlainText("课表格式", ImportExport.courseFormatPrompt)
            )
            toast("格式说明已复制，发给 AI 后把结果粘贴到这里")
        },
        onConfirm = {
            val courses = runCatching { ImportExport.parseCoursesText(text) }
                .getOrElse { emptyList() }
            if (courses.isEmpty()) {
                toast("没能解析出课程，请按格式填写：课程名 教室 老师 星期 节次 周次")
            } else {
                val name = "格式导入 " +
                    LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日"))
                // 复用当前学期配置，避免导入后开学日/作息被重置成默认值
                val imported = store.importTable(
                    ScheduleTable(
                        Store.newTableId(),
                        name,
                        config = store.config,
                        courses = courses,
                    ),
                    makeActive = true,
                )
                toast("解析到 " + imported.courses.size + " 门课程，已导入为新课表")
                onDismiss()
            }
        },
        onDismiss = onDismiss,
        backdrop = backdrop,
    )
}

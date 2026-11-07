package com.example.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 纯 JVM 逻辑单测：格式文本导入 / 分享码编解码 / 周次计算 / ICS 关键字段。
 * 注意：ImportExport 里引用 org.json 的方法（parseTablePayload 等）不会被这些
 * 用例触碰 —— JVM 懒解析，测试无需 Android stub。
 */
class ImportExportTest {

    // ── 格式文本导入 ──

    @Test
    fun `格式导入 - 空格分隔完整字段`() {
        val courses = ImportExport.parseCoursesText(
            "高等数学 教学楼101 王老师 周一 1-2节 1-16周单"
        )
        assertEquals(1, courses.size)
        val c = courses[0]
        assertEquals("高等数学", c.name)
        assertEquals("教学楼101", c.room)
        assertEquals("王老师", c.teacher)
        assertEquals(1, c.day)
        assertEquals(1, c.startPeriod)
        assertEquals(2, c.endPeriod)
        assertEquals((1..16 step 2).toSet(), c.weeks)
    }

    @Test
    fun `格式导入 - 逗号分隔与中文星期`() {
        val courses = ImportExport.parseCoursesText(
            "操作系统R, 行勉楼C413, 张晓波, 星期二, 3-4节, 1-16周"
        )
        assertEquals(1, courses.size)
        assertEquals(2, courses[0].day)
        assertEquals((1..16).toSet(), courses[0].weeks)
    }

    @Test
    fun `格式导入 - 双周与缺省周次`() {
        val even = ImportExport.parseCoursesText("大学英语 丹青楼205 周三 第5节 2-16周双")
        assertEquals((2..16 step 2).toSet(), even[0].weeks)

        val allWeeks = ImportExport.parseCoursesText("自习 图书馆 周五 9节")
        assertEquals((1..30).toSet(), allWeeks[0].weeks)
    }

    @Test
    fun `格式导入 - 多行与无法解析的行`() {
        val courses = ImportExport.parseCoursesText(
            """
            高等数学 教学楼101 王老师 周一 1-2节 1-16周
            这是一段没有格式的话
            # 注释行
            """.trimIndent()
        )
        assertEquals(1, courses.size)
    }

    @Test
    fun `格式导入 - 空输入返回空列表`() {
        assertTrue(ImportExport.parseCoursesText("").isEmpty())
        assertTrue(ImportExport.parseCoursesText("\n\n").isEmpty())
    }

    // ── 分享码 ──

    @Test
    fun `分享码 - 编解码往返`() {
        val payload = """{"name":"测试课表","courses":"[]"}"""
        val code = ImportExport.encodeShareCode(payload)
        assertTrue(code.startsWith(ImportExport.SHARE_URI_PREFIX))
        assertEquals(payload, ImportExport.extractSharePayload(code))
    }

    @Test
    fun `分享码 - 裸码与垃圾输入`() {
        val payload = """{"name":"x"}"""
        val bare = ImportExport.encodeShareCode(payload)
            .removePrefix(ImportExport.SHARE_URI_PREFIX)
        assertEquals(payload, ImportExport.extractSharePayload(bare))
        assertNull(ImportExport.extractSharePayload("not-a-valid-code!!!"))
        assertNull(ImportExport.extractSharePayload(""))
    }

    // ── 周次计算 ──

    @Test
    fun `weekOf - 开学当天是第1周`() {
        val start = LocalDate.of(2026, 9, 7) // 周一
        assertEquals(1, weekOf(start, start))
        assertEquals(2, weekOf(start.plusDays(7), start))
        assertEquals(4, weekOf(start.plusDays(25), start))
    }

    @Test
    fun `weekOf - 开学前返回0`() {
        val start = LocalDate.of(2026, 9, 7)
        assertEquals(0, weekOf(start.minusDays(1), start))
    }

    @Test
    fun `buildPeriods - 数量截断与补足`() {
        val base = defaultPeriods()
        assertEquals(10, buildPeriods(base, 10).size)
        val grown = buildPeriods(base, 12)
        assertEquals(12, grown.size)
        assertEquals(listOf(11, 12), grown.drop(10).map { it.period })
    }

    // ── 第三轮审查回归：反区间 / 裸数字歧义 / 括号写法 / 数字星期 ──

    @Test
    fun `格式导入 - 反区间周次整行跳过`() {
        // 曾静默兜底成全 30 周：一行笔误导入成满课
        val courses = ImportExport.parseCoursesText(
            "高等数学 教学楼101 王老师 周一 1-2节 16-1周"
        )
        assertTrue(courses.isEmpty())
    }

    @Test
    fun `格式导入 - 裸数字歧义以带标记字段优先`() {
        val courses = ImportExport.parseCoursesText(
            "高等数学 教学楼101 5 周一 3-4节 1-16周"
        )
        assertEquals(1, courses.size)
        assertEquals(1, courses[0].day)
        assertEquals(3, courses[0].startPeriod)
        assertEquals((1..16).toSet(), courses[0].weeks)
    }

    @Test
    fun `格式导入 - 数字星期与粘连写法`() {
        assertEquals(1, ImportExport.parseCoursesText("体育 体育馆 周1 7节")[0].day)
        assertEquals(4, ImportExport.parseCoursesText(
            "智能感知 远楼A202 周四1-2节 1-16周"
        )[0].day)
    }

    @Test
    fun `格式导入 - 括号单周写法`() {
        val c = ImportExport.parseCoursesText("数据结构 B203 周二 3-4节 1-16周(单)")
        assertEquals((1..16 step 2).toSet(), c[0].weeks)
    }

    @Test
    fun `分享码 - 超长输入拒绝`() {
        assertNull(ImportExport.extractSharePayload("A".repeat(600_000)))
    }

    // ── ICS 导出 ──

    @Test
    fun `ICS - 等差周次走 RRULE 且 BYDAY 正确`() {
        val cfg = SemesterConfig(LocalDate.of(2026, 9, 7), defaultPeriods(), totalWeeks = 20)
        val table = ScheduleTable(
            "t1", "测试课表", config = cfg,
            courses = listOf(Course(1, "高数", "", "王老师", 1, 1, 2, (1..16).toSet())),
        )
        val ics = ImportExport.buildIcs(table)
        assertTrue(ics.contains("RRULE:FREQ=WEEKLY;BYDAY=MO;INTERVAL=1;UNTIL=20261221T"))
    }

    @Test
    fun `ICS - 单双周 INTERVAL=2 与零散周次逐周展开`() {
        val cfg = SemesterConfig(LocalDate.of(2026, 9, 7), defaultPeriods(), totalWeeks = 20)
        val table = ScheduleTable(
            "t1", "测试课表", config = cfg,
            courses = listOf(
                Course(1, "单周课", "", "", 1, 1, 2, (1..16 step 2).toSet()),
                Course(2, "零散课", "", "", 4, 5, 6, setOf(1, 2, 5)),
            ),
        )
        val ics = ImportExport.buildIcs(table)
        assertTrue(ics.contains("INTERVAL=2"))
        assertTrue(ics.contains("零散课 第1周"))
        assertTrue(ics.contains("零散课 第2周"))
        assertTrue(ics.contains("零散课 第5周"))
        // 单周课 = 等差 → 走 RRULE，不逐周展开
        assertEquals(1, Regex("INTERVAL=2").findAll(ics).count())
    }

    @Test
    fun `ICS - DTSTAMP 为 UTC 与特殊字符转义`() {
        val cfg = SemesterConfig(LocalDate.of(2026, 9, 7), defaultPeriods(), totalWeeks = 20)
        val table = ScheduleTable(
            "t1", "测试", config = cfg,
            courses = listOf(Course(1, "高等,数学;测试", "", "", 1, 1, 1, setOf(1))),
        )
        val ics = ImportExport.buildIcs(table)
        val stamp = Regex("DTSTAMP:(\\d{8}T\\d{6}Z)").find(ics)
        assertTrue(stamp != null) // DTSTAMP 必须是带 Z 的 UTC
        assertTrue(ics.contains("SUMMARY:高等\\,数学\\;测试"))
    }

    @Test
    fun `ICS - 换行转义为字面量反斜杠n`() {
        val cfg = SemesterConfig(LocalDate.of(2026, 9, 7), defaultPeriods(), totalWeeks = 20)
        val table = ScheduleTable(
            "t1", "换行课表", config = cfg,
            courses = listOf(Course(1, "课名\n换行", "", "", 1, 1, 1, setOf(1))),
        )
        val ics = ImportExport.buildIcs(table)
        assertTrue(ics.contains("SUMMARY:课名\\n换行"))
        // 不能出现真正的换行打断 SUMMARY 行
        assertTrue(!Regex("SUMMARY:课名\n").containsMatchIn(ics))
    }

    @Test
    fun `格式导入 - 课程名优先取非教师非教室token`() {
        val courses = ImportExport.parseCoursesText(
            "高等数学 教学楼101 王老师 周一 1-2节 1-16周"
        )
        assertEquals("高等数学", courses[0].name)
        assertEquals("教学楼101", courses[0].room)
        assertEquals("王老师", courses[0].teacher)
    }

    @Test
    fun `startOfWeek - 对齐周起始日`() {
        // 周日作为起始日时，周一应落到前一个周日
        assertEquals(LocalDate.of(2026, 9, 6), startOfWeek(LocalDate.of(2026, 9, 7), 7))
        assertEquals(LocalDate.of(2026, 9, 7), startOfWeek(LocalDate.of(2026, 9, 7), 1))
    }

    @Test
    fun `startOfWeek - 跨年对齐`() {
        assertEquals(LocalDate.of(2025, 12, 29), startOfWeek(LocalDate.of(2026, 1, 1), 1))
        assertEquals(LocalDate.of(2026, 1, 4), startOfWeek(LocalDate.of(2026, 1, 4), 7))
    }
}
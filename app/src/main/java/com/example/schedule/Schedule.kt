package com.example.schedule

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 一门课：某星期几的第 startPeriod..endPeriod 节，指定在哪些周次上课。 */
data class Course(
    val id: Long,
    val name: String,
    val room: String,
    val teacher: String,
    val day: Int,            // 1=周一 … 7=周日
    val startPeriod: Int,    // 起始节次（从 1 开始）
    val endPeriod: Int,      // 结束节次
    val weeks: Set<Int>,     // 上课周次集合，如 {1,2,3,4,5}
    val colorIndex: Int = -1 // 用户指定颜色（CoursePalette 下标），-1 = 自动按 id 轮换
) {
    fun coversPeriod(period: Int) = period in startPeriod..endPeriod
    fun occursOn(date: LocalDate, semesterStart: LocalDate): Boolean {
        if (date.dayOfWeek.value != day) return false
        return weekOf(date, semesterStart) in weeks
    }
}

/** 一节的时间安排（如第 3 节 09:50-10:30）。 */
data class PeriodTime(val period: Int, val start: String, val end: String)

/** 学期设置。 */
data class SemesterConfig(
    val startDate: LocalDate,          // 第一周的第一天（按 weekStartDay 对齐）
    val periods: List<PeriodTime>,     // 节次时间表
    val totalWeeks: Int = 20,          // 本学期总周数
    val showWeekend: Boolean = true,   // 是否显示周六/周日列
    val showOtherWeekCourses: Boolean = true, // 是否显示非本周课程（半透明）
    val weekStartDay: Int = 1,         // 每周起始日：1=周一 … 7=周日
) {
    fun periodTimeOf(period: Int): PeriodTime? = periods.find { it.period == period }
    val periodCount: Int get() = periods.size
}

/**
 * 由日期算出第几周。
 * [startDate] 必须是第一周的第一天（按 weekStartDay 对齐）；返回 1 表示第 1 周。
 * 日期早于开学时返回 0（未开学）。
 */
fun weekOf(date: LocalDate, startDate: LocalDate): Int {
    val diff = ChronoUnit.DAYS.between(startDate, date)
    if (diff < 0) return 0
    return (diff / 7).toInt() + 1
}

/** 某周第一天的日期（startDate 本身即第一周的第一天）。 */
fun firstDayOfWeek(week: Int, startDate: LocalDate): LocalDate =
    startDate.plusDays((week - 1) * 7L)

/** [date] 所在周的第一天（按 weekStartDay）。 */
fun startOfWeek(date: LocalDate, weekStartDay: Int): LocalDate {
    val shift = (date.dayOfWeek.value - weekStartDay + 7) % 7
    return date.minusDays(shift.toLong())
}

/** 课表列顺序：从每周起始日开始依次 7 天的 day 值（1=周一 … 7=周日）。 */
fun weekDayOrder(weekStartDay: Int): List<Int> =
    (0 until 7).map { ((weekStartDay - 1 + it) % 7) + 1 }

/** day（1=周一 … 7=周日）在一周内相对起始日的偏移天数。 */
fun dayOffsetInWeek(day: Int, weekStartDay: Int): Int = (day - weekStartDay + 7) % 7

/** 按目标节数整理节次时间表：超出部分截断，不足部分用默认时间补足。 */
fun buildPeriods(current: List<PeriodTime>, count: Int): List<PeriodTime> {
    val defaults = defaultPeriods() + listOf(
        PeriodTime(11, "21:00", "21:45"),
        PeriodTime(12, "21:55", "22:40"),
    )
    return (1..count).map { p ->
        current.find { it.period == p }
            ?: defaults.find { it.period == p }
            ?: PeriodTime(p, "20:00", "20:45")
    }
}

fun defaultPeriods(): List<PeriodTime> = listOf(
    PeriodTime(1, "08:00", "08:45"),
    PeriodTime(2, "08:55", "09:40"),
    PeriodTime(3, "10:00", "10:45"),
    PeriodTime(4, "10:55", "11:40"),
    PeriodTime(5, "14:00", "14:45"),
    PeriodTime(6, "14:55", "15:40"),
    PeriodTime(7, "16:00", "16:45"),
    PeriodTime(8, "17:00", "17:45"),
    PeriodTime(9, "19:00", "19:45"),
    PeriodTime(10, "19:55", "20:40"),
)

/** 默认学期开始日：今天所在周的周一。 */
fun defaultStartDate(today: LocalDate = LocalDate.now()): LocalDate = startOfWeek(today, 1)

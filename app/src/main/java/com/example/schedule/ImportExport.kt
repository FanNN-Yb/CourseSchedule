package com.example.schedule

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.InflaterInputStream

/**
 * 导入导出引擎：
 *  · [encodeShareCode] / [decodeShareCode] —— 分享码：压缩 + URL-safe Base64，
 *    形如 schedule://import?data=xxx（也接受裸码粘贴）
 *  · [buildIcs] —— 导出 .ics 日历：等差周次（全周/单双周）用 RRULE 表达，
 *    任意零散周次逐周展开成独立事件，任何日历 App 可导入
 *  · [parseCoursesText] —— 解析按固定格式粘贴的课表文本（每行一门课，见函数上方注释）
 */
object ImportExport {

    /** 「通过格式导入」的格式说明 + AI 指令，一键复制后直接发给 AI 整理课表。 */
    val courseFormatPrompt: String =
        "请把我下面的课表信息转换成以下格式，每行一门课，只输出转换结果：\n" +
            "课程名 教室 老师 星期 节次 周次\n" +
            "· 字段用空格分隔，顺序不限；教室、老师不知道就省略\n" +
            "· 星期：周一～周日（也可以写 周一 或 数字 1-7）\n" +
            "· 节次：1-2节；单节写 3节\n" +
            "· 周次：1-16周；单周写 1-16周单，双周写 2-16周双；全周可省略\n" +
            "例：高等数学 教学楼101 王老师 周一 1-2节 1-16周单\n" +
            "\n" +
            "我的课表：\n"

    // ── 分享码 ──────────────────────────────────────────────────────

    const val SHARE_URI_PREFIX = "schedule://import?data="

    /** 把一张课表编成分享码（URI 形式，可直接粘贴给同学）。 */
    fun encodeShareCode(payload: String): String =
        SHARE_URI_PREFIX + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(deflate(payload.toByteArray(Charsets.UTF_8)))

    /**
     * 解析分享码：接受完整 URI（schedule://import?data=...）、裸 Base64 码，
     * 返回课表 JSON 载荷字符串；解析失败返回 null。
     */
    fun extractSharePayload(raw: String): String? {
        val code = raw.trim()
            .removePrefix(SHARE_URI_PREFIX)
            .removePrefix("schedule://import?data=")
            .trim()
        // 上下文里含完整分享链接（用户直接粘贴整段分享消息）→ 提取 data 参数
        val extracted = if (code.isEmpty() || code.contains(SHARE_URI_PREFIX)) {
            Regex("schedule://import\\?data=([A-Za-z0-9_\\-]+)").find(code.ifEmpty { raw })
                ?.groupValues?.get(1)
        } else {
            null
        }
        val finalCode = extracted ?: code
        if (finalCode.isEmpty()) return null
        // 防御性上限：一张课表的分享码远小于该值，超长输入直接拒绝（防构造超大链接）
        if (finalCode.length > 512_000) return null
        return try {
            val bytes = Base64.getUrlDecoder().decode(finalCode)
            String(inflate(bytes), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(data)
        deflater.finish()
        val buf = ByteArray(4096)
        val out = ByteArrayOutputStream()
        while (!deflater.finished()) {
            val n = deflater.deflate(buf)
            out.write(buf, 0, n)
        }
        deflater.end()
        return out.toByteArray()
    }

    private fun inflate(data: ByteArray): ByteArray {
        //  解压上限 2MB：512KB 压缩码理论可膨胀 ~400MB（deflate 极限压缩比），
        //    无上限的 readBytes 会被构造的分享码 OOM。超限直接判无效。
        val cap = 2_000_000
        InflaterInputStream(ByteArrayInputStream(data)).use { ins ->
            val out = java.io.ByteArrayOutputStream(minOf(cap, 64_000))
            val buf = ByteArray(8192)
            var total = 0
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                total += n
                if (total > cap) throw IllegalArgumentException("inflated payload too large")
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }

    // ── ICS 日历导出 ────────────────────────────────────────────────

    private val BYDAY = mapOf(
        1 to "MO", 2 to "TU", 3 to "WE", 4 to "TH", 5 to "FR", 6 to "SA", 7 to "SU",
    )

    /**
     * 把一张课表导出成 ICS 日历文本。
     * 周次是等差数列（如 1-16 全周、1-15 单周）→ 一个 VEVENT + RRULE；
     * 零散周次 → 逐周展开成独立 VEVENT，保证任何周次组合都能正确表达。
     */
    fun buildIcs(table: ScheduleTable): String {
        val cfg = table.config
        val sb = StringBuilder()
        sb.append("BEGIN:VCALENDAR\r\n")
        sb.append("VERSION:2.0\r\n")
        sb.append("PRODID:-//CourseSchedule//CN\r\n")
        sb.append("CALSCALE:GREGORIAN\r\n")
        sb.append("X-WR-CALNAME:").append(escape(table.name)).append("\r\n")

        table.courses.forEach { c ->
            val weeks = c.weeks.filter { it >= 1 }.sorted()
            if (weeks.isEmpty()) return@forEach
            if (c.day !in 1..7) return@forEach // 脏数据防 BYDAY=null
            val startTime = cfg.periodTimeOf(c.startPeriod)?.start ?: "08:00"
            val endTime = cfg.periodTimeOf(c.endPeriod)?.end ?: "08:45"
            val summary = buildString {
                append(c.name)
                if (c.room.isNotBlank()) append(" @").append(c.room)
            }
            val interval = detectWeekInterval(weeks)
            if (interval != null) {
                // 等差：RRULE 一条搞定（weeks.size == 1 时 detectWeekInterval 返回 null → 走逐周）
                val firstDate = courseDate(weeks.first(), c.day, cfg)
                val lastDate = courseDate(weeks.last(), c.day, cfg)
                sb.append("BEGIN:VEVENT\r\n")
                sb.append("UID:").append(c.id).append("-").append(table.id).append("@schedule.local\r\n")
                sb.append("DTSTART:").append(icsDateTime(firstDate, startTime)).append("\r\n")
                sb.append("DTEND:").append(icsDateTime(firstDate, endTime)).append("\r\n")
                sb.append("RRULE:FREQ=WEEKLY;BYDAY=").append(BYDAY[c.day])
                    .append(";INTERVAL=").append(interval)
                    .append(";UNTIL=").append(icsDateTime(lastDate, endTime))
                    .append("\r\n")
                appendEventDetails(sb, summary, c)
                sb.append("END:VEVENT\r\n")
            } else {
                weeks.forEach { w ->
                    val date = courseDate(w, c.day, cfg)
                    sb.append("BEGIN:VEVENT\r\n")
                    sb.append("UID:").append(c.id).append("-w").append(w).append("-")
                        .append(table.id).append("@schedule.local\r\n")
                    sb.append("DTSTART:").append(icsDateTime(date, startTime)).append("\r\n")
                    sb.append("DTEND:").append(icsDateTime(date, endTime)).append("\r\n")
                    appendEventDetails(sb, "$summary 第${w}周", c)
                    sb.append("END:VEVENT\r\n")
                }
            }
        }
        sb.append("END:VCALENDAR\r\n")
        return sb.toString()
    }

    private fun appendEventDetails(sb: StringBuilder, summary: String, c: Course) {
        sb.append("DTSTAMP:")
            .append(java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")))
            .append("\r\n")
        sb.append("SUMMARY:").append(escape(summary)).append("\r\n")
        if (c.teacher.isNotBlank()) {
            sb.append("DESCRIPTION:").append(escape("教师：${c.teacher}")).append("\r\n")
        }
    }

    /** 课程在某周某天的日期（按每周起始日对齐的学期开始日推算）。 */
    private fun courseDate(week: Int, day: Int, cfg: SemesterConfig): LocalDate =
        firstDayOfWeek(week, cfg.startDate).plusDays(dayOffsetInWeek(day, cfg.weekStartDay).toLong())

    /**
     * 周次若是公差 1/2 的等差数列（1..n / 单周 / 双周）返回公差；否则 null。
     * 单个周次也返回 null（直接展开成一个事件，不值得 RRULE）。
     */
    private fun detectWeekInterval(weeks: List<Int>): Int? {
        if (weeks.size < 2) return null
        val d = weeks[1] - weeks[0]
        if (d != 1 && d != 2) return null
        var expected = weeks[0]
        weeks.forEach { w ->
            if (w != expected) return null
            expected += d
        }
        return d
    }

    private fun icsDateTime(date: LocalDate, hhmm: String): String {
        val parts = hhmm.split(":")
        val h = parts.getOrNull(0)?.trim()?.padStart(2, '0') ?: "08"
        val m = parts.getOrNull(1)?.trim()?.padStart(2, '0') ?: "00"
        return date.format(DateTimeFormatter.BASIC_ISO_DATE) + "T" + h + m + "00"
    }

    private val escapeRegex = Regex("[\\\\,;]")
    /** ICS 文本转义：反斜杠/逗号/分号加反斜杠；换行写成字面量 `\n`。 */
    private fun escape(text: String): String =
        text.replace(escapeRegex) { "\\" + it.value }
            .replace("\r\n", "\\n")
            .replace("\r", "\\n")
            .replace("\n", "\\n")

    // ── 格式文本导入 ────────────────────────────────────────────────
    // 每行一门课，字段顺序不限，用空格 / 逗号 / 顿号分隔：
    //   课程名 教室 老师 星期 节次 周次
    // 例：高等数学 教学楼101 王老师 周一 1-2节 1-16周单
    // 教室、老师可省略；识别不了的行整行跳过，不产生半残课程。

    /** "周一" / "星期三" / "周日" / "周1" → 1..7（也吃 "周一1-2节" 这类粘连写法）。 */
    private val dayTokenRegex = Regex("(?:星期|周)\\s*([一二三四五六天日1-7])")

    /** "1-2节" / "第3节" / "第1-2节"。 */
    private val periodTokenRegex = Regex("第?\\s*(\\d{1,2})(?:\\s*[-–—~]\\s*第?\\s*(\\d{1,2}))?\\s*节")

    /** "1-16周" / "1-16周单" / "1-16周(单)" / "第3周"。 */
    private val weekTokenRegex = Regex("(\\d{1,2})(?:\\s*[-–—~]\\s*(\\d{1,2}))?\\s*周\\s*[（(]?(单|双)?[）)]?")

    /** 裸数字 / 区间："3"、"1-2"（歧义时：没星期当星期，没节次当节次，没周次当周次）。 */
    private val bareNumberTokenRegex = Regex("^(\\d{1,2})(?:\\s*[-–—~]\\s*(\\d{1,2}))?$")

    private val dayCharMap = mapOf('一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '天' to 7, '日' to 7)

    /** 解析格式化课表文本，格式见上方注释。 */
    fun parseCoursesText(text: String): List<Course> {
        val result = mutableListOf<Course>()
        var nextId = 1L

        text.lines().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) return@forEach
            val tokens = line.split(Regex("[\\s，,、；;]+")).filter { it.isNotEmpty() }

            var day: Int? = null
            var startPeriod: Int? = null
            var endPeriod: Int? = null
            var weeks: Set<Int>? = null
            var weekFailed = false // 周次 token 存在但解析失败（如反区间）→ 整行跳过
            val rest = mutableListOf<String>()
            val bareNumbers = mutableListOf<String>()

            fun takeDay(m: MatchResult) {
                if (day == null) {
                    val c = m.groupValues[1]
                    day = if (c[0] in '1'..'7') c[0] - '0' else dayCharMap[c[0]]
                }
            }

            // 第一遍：只处理带标记的字段（节次 / 周次 / 星期），歧义的裸数字留到第二遍
            tokens.forEach { t ->
                val dayM = dayTokenRegex.find(t)
                when {
                    periodTokenRegex.containsMatchIn(t) && t.contains('节') -> {
                        periodTokenRegex.find(t)?.let { m ->
                            val s = m.groupValues[1].toInt()
                            val e = (m.groupValues[2].ifEmpty { m.groupValues[1] }).toInt()
                            if (startPeriod == null && s in 1..30 && e in s..30) {
                                startPeriod = s
                                endPeriod = e
                            }
                        }
                        dayM?.let(::takeDay)
                    }
                    weekTokenRegex.containsMatchIn(t) && t.any { it.isDigit() } -> {
                        weekTokenRegex.find(t)?.let { m ->
                            if (weeks == null) {
                                val a = m.groupValues[1].toInt().coerceIn(1, 30)
                                val b = (m.groupValues[2].ifEmpty { m.groupValues[1] }).toInt().coerceIn(1, 30)
                                if (b < a) {
                                    weekFailed = true
                                } else {
                                    val step = if (m.groupValues[3].isNotEmpty()) 2 else 1
                                    // 单/双都归一化到正确奇偶（旧逻辑只处理「双」，「2-16周单」会错导入成双周）
                                    val start = when (m.groupValues[3]) {
                                        "单" -> if (a % 2 == 0) a + 1 else a
                                        "双" -> if (a % 2 == 1) a + 1 else a
                                        else -> a
                                    }.let { if (it > b) a else it }  // 单周区间被平移后为空 → 保留原范围
                                    weeks = (start..b step step).toSet().ifEmpty { null }
                                    if (weeks == null) weekFailed = true
                                }
                            }
                        }
                        dayM?.let(::takeDay)
                    }
                    dayM != null -> takeDay(dayM)
                    bareNumberTokenRegex.matches(t) -> bareNumbers += t
                    else -> rest += t
                }
            }

            // 第二遍：裸数字按「缺星期 → 缺节次 → 缺周次」的顺序补位
            bareNumbers.forEach { t ->
                val m = bareNumberTokenRegex.find(t)!!
                val s = m.groupValues[1].toInt()
                val e = (m.groupValues[2].ifEmpty { m.groupValues[1] }).toInt()
                if (s in 1..30 && e in s..30) {
                    when {
                        // 星期位必须是 1..7：越界的裸数字（如「数学 9 1-2节」）
                        // 会被存成 day=9 → 永不渲染、下次加载静默钳到周日
                        t.length == 1 && s in 1..7 && day == null -> day = s
                        startPeriod == null -> { startPeriod = s; endPeriod = e }
                        weeks == null -> weeks = (s..e).toSet()
                    }
                } else rest += t
            }

            // 课程名优先取「既不是教师也不是教室」的 token；都像地址/人名时退回第一个
            val name = rest.firstOrNull { !isTeacherName(it) && !isRoom(it) }
                ?: rest.firstOrNull()
            if (name.isNullOrBlank() || name.length > 40) return@forEach
            val d = day ?: return@forEach
            val sp = startPeriod ?: return@forEach
            val ep = (endPeriod ?: sp).coerceAtLeast(sp)
            // 反区间 / 空区间等周次解析失败 → 整行跳过（不再兜底成全学期满课）
            if (weekFailed) return@forEach
            val wk = weeks ?: (1..30).toSet()
            val teacher = rest.drop(1).firstOrNull { it != name && isTeacherName(it) } ?: ""
            val room = rest.drop(1).firstOrNull { it != name && it != teacher && isRoom(it) } ?: ""

            result += Course(
                id = nextId++,
                name = name,
                room = room,
                teacher = teacher,
                day = d,
                startPeriod = sp,
                endPeriod = ep,
                weeks = wk,
            )
        }
        return result
    }

    /** 教师名：2–4 个汉字（不含楼/室等课表地址用字）。 */
    private fun isTeacherName(s: String): Boolean =
        s.matches(Regex("[\\u4e00-\\u9fa5·]{2,4}")) && !s.containsAny("楼层室馆区号栋")

    /** 教室：含有数字且带楼/室/馆/区/栋/号字样，或字母数字混编（如 B203）。 */
    private fun isRoom(s: String): Boolean =
        s.containsAny("楼层室馆区栋号") || s.matches(Regex("[A-Za-z]{0,3}\\d{3,4}[室]?"))

    private fun String.containsAny(chars: String): Boolean = chars.any { it in this }
}

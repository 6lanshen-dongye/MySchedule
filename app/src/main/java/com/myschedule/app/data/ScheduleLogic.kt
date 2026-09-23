package com.myschedule.app.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** 课表核心计算:周次、日期、下一节课、GPA 等 */
object ScheduleLogic {

    private val df: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun parseDate(s: String): LocalDate? = try {
        if (s.isBlank()) null else LocalDate.parse(s, df)
    } catch (e: Exception) {
        null
    }

    fun formatDate(d: LocalDate): String = d.format(df)

    fun parseTime(s: String): LocalTime? = try {
        LocalTime.parse(s)
    } catch (e: Exception) {
        null
    }

    /** 学期第一周的周一(用户填的日期自动归整到所在周的周一) */
    fun semesterMonday(semester: Semester): LocalDate? {
        val d = parseDate(semester.startDate) ?: return null
        return d.minusDays((d.dayOfWeek.value - 1).toLong())
    }

    /** 今天是第几周(超出范围时收敛到边界) */
    fun currentWeek(semester: Semester, today: LocalDate): Int {
        val monday = semesterMonday(semester) ?: return 1
        if (today.isBefore(monday)) return 1
        val week = ChronoUnit.WEEKS.between(monday, today).toInt() + 1
        return week.coerceIn(1, semester.totalWeeks.coerceAtLeast(1))
    }

    /** 某一周里某一天对应的日期 */
    fun dateOfWeek(semester: Semester, week: Int, dayOfWeek: Int): LocalDate? {
        val monday = semesterMonday(semester) ?: return null
        return monday.plusWeeks((week - 1).toLong()).plusDays((dayOfWeek - 1).toLong())
    }

    /** 某天某周有哪些课(展开成 会话 列表,按节次排序) */
    fun sessionsOnDay(courses: List<Course>, dayOfWeek: Int, week: Int): List<Pair<Course, CourseSession>> {
        return courses.flatMap { c ->
            c.sessions.filter { it.dayOfWeek == dayOfWeek && week in it.weeks }
                .map { c to it }
        }.sortedBy { it.second.startPeriod }
    }

    /** 下一节课(或正在上的课)。返回 课程 + 会话 + 是否正在上课 */
    fun nextClass(data: AppData, now: LocalDateTime): NextClassInfo? {
        val today = now.toLocalDate()
        for (offset in 0..7L) {
            val date = today.plusDays(offset)
            val week = weekOfDate(data.semester, date) ?: continue
            if (week < 1 || week > data.semester.totalWeeks) continue
            val day = date.dayOfWeek.value
            val list = sessionsOnDay(data.courses, day, week)
            if (offset == 0L) {
                // 今天:先找正在上的,再找还没开始的
                for ((c, s) in list) {
                    val st = periodStart(data, s.startPeriod) ?: continue
                    val en = periodEnd(data, s.endPeriod) ?: continue
                    val startDt = LocalDateTime.of(today, st)
                    val endDt = LocalDateTime.of(today, en)
                    if (!now.isAfter(endDt)) {
                        val inClass = !now.isBefore(startDt)
                        return NextClassInfo(c, s, date, inClass)
                    }
                }
            } else {
                val first = list.firstOrNull() ?: continue
                return NextClassInfo(first.first, first.second, date, false)
            }
        }
        return null
    }

    data class NextClassInfo(
        val course: Course,
        val session: CourseSession,
        val date: LocalDate,
        val inClass: Boolean
    )

    /** 某日期属于第几周(不在学期范围内返回 null) */
    fun weekOfDate(semester: Semester, date: LocalDate): Int? {
        val monday = semesterMonday(semester) ?: return null
        if (date.isBefore(monday)) return null
        val week = ChronoUnit.WEEKS.between(monday, date).toInt() + 1
        return if (week in 1..semester.totalWeeks) week else null
    }

    fun periodStart(data: AppData, period: Int): LocalTime? =
        data.periods.getOrNull(period - 1)?.let { parseTime(it.start) }

    fun periodEnd(data: AppData, period: Int): LocalTime? =
        data.periods.getOrNull(period - 1)?.let { parseTime(it.end) }

    /** 周次列表转可读文本,如 "1-16周(单)" 或 "1-8周、10-16周" */
    fun weeksText(weeks: List<Int>): String {
        if (weeks.isEmpty()) return ""
        val sorted = weeks.distinct().sorted()
        val allOdd = sorted.all { it % 2 == 1 }
        val allEven = sorted.all { it % 2 == 0 }
        val full = (sorted.first()..sorted.last()).toList()
        if (sorted == full) {
            return if (sorted.size > 1) "${sorted.first()}-${sorted.last()}周" else "${sorted.first()}周"
        }
        if (sorted.size >= 4 && (allOdd || allEven)) {
            return "${sorted.first()}-${sorted.last()}周(${if (allOdd) "单周" else "双周"})"
        }
        // 压缩连续段
        val sb = StringBuilder()
        var runStart = sorted[0]
        var prev = sorted[0]
        fun flush(end: Int) {
            if (sb.isNotEmpty()) sb.append("、")
            sb.append(if (runStart == end) "${runStart}周" else "${runStart}-${end}周")
        }
        for (i in 1 until sorted.size) {
            val v = sorted[i]
            if (v == prev + 1) {
                prev = v
            } else {
                flush(prev)
                runStart = v
                prev = v
            }
        }
        flush(prev)
        return sb.toString()
    }

    /** 成绩转 4.0 制 GPA(常见中国高校分段) */
    fun gpaOf(score: Double): Double = when {
        score >= 90 -> 4.0
        score >= 85 -> 3.7
        score >= 82 -> 3.3
        score >= 78 -> 3.0
        score >= 75 -> 2.7
        score >= 72 -> 2.3
        score >= 68 -> 2.0
        score >= 64 -> 1.5
        score >= 60 -> 1.0
        else -> 0.0
    }

    fun weightedAverage(grades: List<GradeItem>): Double {
        val credits = grades.sumOf { it.credit }
        if (credits <= 0.0) return 0.0
        return grades.sumOf { it.score * it.credit } / credits
    }

    fun weightedGpa(grades: List<GradeItem>): Double {
        val credits = grades.sumOf { it.credit }
        if (credits <= 0.0) return 0.0
        return grades.sumOf { gpaOf(it.score) * it.credit } / credits
    }

    /** 两节课是否时间冲突(同一天、节次重叠、周次有交集) */
    fun conflicts(a: CourseSession, b: CourseSession): Boolean {
        if (a.dayOfWeek != b.dayOfWeek) return false
        if (maxOf(a.startPeriod, b.startPeriod) > minOf(a.endPeriod, b.endPeriod)) return false
        return a.weeks.any { it in b.weeks }
    }
}

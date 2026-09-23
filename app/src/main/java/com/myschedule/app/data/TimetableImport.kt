package com.myschedule.app.data

import kotlinx.serialization.Serializable

/** 从教务系统页面里抓到的一个课程块(即一次上课) */
@Serializable
data class ScrapedBlock(
    val day: Int = 0,          // 1=周一 ... 7=周日
    val start: Int = 0,        // 起始节次
    val end: Int = 0,          // 结束节次
    val name: String = "",
    val teacher: String = "",
    val weeks: String = "",    // 原始周次文本,如 "1-16周(单)"
    val location: String = ""
)

/** 抓取结果 */
@Serializable
data class ScrapeResult(
    val ok: Boolean = false,
    val msg: String = "",
    val blocks: List<ScrapedBlock> = emptyList(),
    /** 若页面里带了每节的作息时间(形如 "08:30-09:15"),下标 0 对应第 1 节 */
    val times: List<String> = emptyList(),
    /** 解析失败时的追踪信息,便于定位解析器看到了什么 */
    val trace: String = ""
)

/** 教务系统课表 → 本应用数据模型 */
object TimetableImport {

    /** "1-16周(单)" / "1-8,10-16周" / "第3周" → 具体周次列表 */
    fun parseWeeks(text: String, totalWeeks: Int): List<Int> {
        val t = text.replace(" ", "")
            .replace("，", ",")
            .replace("－", "-")
            .replace("—", "-")
            .replace("~", "-")
            .replace("至", "-")
        if (t.isBlank()) return (1..totalWeeks).toList()
        val odd = t.contains("单")
        val even = t.contains("双")
        val picked = sortedSetOf<Int>()
        Regex("(\\d{1,2})\\s*-\\s*(\\d{1,2})").findAll(t).forEach { m ->
            val a = m.groupValues[1].toIntOrNull()
            val b = m.groupValues[2].toIntOrNull()
            // 起始 > 结束 说明这不是一个周次区间(多半是把课程名末尾的数字吞进来了),跳过
            if (a != null && b != null && a <= b) {
                for (i in a..b) picked.add(i)
            }
        }
        // 去掉区间后剩下的是零散周次,如 "1,3,5周"
        val rest = t.replace(Regex("\\d{1,2}\\s*-\\s*\\d{1,2}"), " ")
        Regex("\\d{1,2}").findAll(rest).forEach { m ->
            m.value.toIntOrNull()?.let { picked.add(it) }
        }
        var weeks = picked.filter { it in 1..totalWeeks }
        if (odd != even) {
            weeks = weeks.filter { if (odd) it % 2 == 1 else it % 2 == 0 }
        }
        return weeks.ifEmpty { (1..totalWeeks).toList() }
    }

    /**
     * 把抓到的块合并成课程:同名 + 同老师 + 同地点算一门课,多个时间段挂在同一门下
     * (和超级课程表的显示方式一致,例如「电工电子技术2」周二、周四各一段)
     */
    fun toCourses(blocks: List<ScrapedBlock>, totalWeeks: Int, startColor: Int = 0): List<Course> {
        val groups = LinkedHashMap<String, MutableList<ScrapedBlock>>()
        blocks.forEach { b ->
            val name = b.name.trim()
            if (name.isBlank() || b.day !in 1..7 || b.start <= 0) return@forEach
            val key = listOf(name, b.teacher.trim(), b.location.trim()).joinToString("|")
            groups.getOrPut(key) { mutableListOf() }.add(b)
        }
        val result = mutableListOf<Course>()
        var idx = 0
        groups.values.forEach { list ->
            val first = list.first()
            val sessions = list.map { b ->
                CourseSession(
                    dayOfWeek = b.day,
                    startPeriod = b.start,
                    endPeriod = if (b.end >= b.start) b.end else b.start,
                    weeks = parseWeeks(b.weeks, totalWeeks)
                )
            }.distinctBy { listOf(it.dayOfWeek, it.startPeriod, it.endPeriod, it.weeks) }
                .sortedWith(compareBy({ it.dayOfWeek }, { it.startPeriod }))
            if (sessions.isNotEmpty()) {
                result.add(
                    Course(
                        id = newId(),
                        name = first.name.trim(),
                        teacher = first.teacher.trim(),
                        location = first.location.trim(),
                        colorIndex = (startColor + idx) % CoursePalette.size,
                        sessions = sessions
                    )
                )
                idx++
            }
        }
        return result
    }

    /** 把抓到的作息时间("08:30-09:15")套到现有作息表上;识别到太少就原样返回 */
    fun applyTimes(current: List<ClassPeriod>, times: List<String>): List<ClassPeriod> {
        val valid = times.count { it.contains(":") }
        if (valid < 4) return current
        val out = current.toMutableList()
        times.forEachIndexed { i, t ->
            val m = Regex("(\\d{1,2}:\\d{2})\\s*-\\s*(\\d{1,2}:\\d{2})").find(t) ?: return@forEachIndexed
            while (out.size <= i) out.add(ClassPeriod(m.groupValues[1], m.groupValues[2]))
            out[i] = ClassPeriod(m.groupValues[1], m.groupValues[2])
        }
        return out
    }
}

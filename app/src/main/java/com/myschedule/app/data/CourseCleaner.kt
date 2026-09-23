package com.myschedule.app.data

/**
 * 清洗"抓取时把整格文字当成课程名"留下的脏数据。
 *
 * 早期版本从教务系统抓到的一格长这样:
 *   计算机三维设计与仿真(1-4节)1-5周,7-13周,15周本部科B505赵静(2026-2027-1)-252014001-0325机械制造…讲课:32,实验:12122.5必修
 * 整串被当成了课程名。这里按特征把它还原成:课程名 / 节次 / 周次 / 地点 / 教师。
 */
object CourseCleaner {

    private val weekRe = Regex("[0-9]+(?:[-~][0-9]+)?周(?:[,，、][0-9]+(?:[-~][0-9]+)?周)*")

    /**
     * 找周次文本。
     * 课程名末尾的数字很容易被吞进来:"电工电子技术2" + "1-4周" 会被匹配成 "21-4周"。
     * 所以要求区间必须"起始≤结束且≤30",不合法就往后挪一个字符重试。
     */
    private fun findWeeks(text: String): Pair<Int, String>? {
        var from = 0
        while (from < text.length) {
            val m = weekRe.find(text, from) ?: return null
            if (validWeeks(m.value)) return m.range.first to m.value
            from = m.range.first + 1
        }
        return null
    }

    private fun validWeeks(s: String): Boolean {
        Regex("([0-9]+)\\s*[-~]\\s*([0-9]+)").findAll(s).forEach { r ->
            val a = r.groupValues[1].toIntOrNull() ?: return false
            val b = r.groupValues[2].toIntOrNull() ?: return false
            if (a > b || a > 30 || b > 30) return false
        }
        return true
    }
    private val periodRe = Regex("[（(]\\s*([0-9]+)\\s*[-~至]\\s*([0-9]+)\\s*节\\s*[)）]")
    private val singlePeriodRe = Regex("[（(]\\s*第?\\s*([0-9]+)\\s*节\\s*[)）]")
    private val campusRe = Regex("((?:本部|东区|西区|南区|北区|校区|南湖|阳光|嘉鱼)[\u4e00-\u9fa5A-Za-z0-9\\-]*)")
    private val tailNameRe = Regex("([\u4e00-\u9fa5]{2,3})[\\s,，、]*$")
    private val markerRe = Regex("[0-9]+\\s*[-~,，]?\\s*[0-9]*\\s*周|(?:本部|东区|西区|南区|北区|校区)|[（(]\\s*[0-9]{4}\\s*[-–—]")
    private val locationTailChar = Regex("[场楼室馆区科教阶部校院园心道路栋舍]")

    /** 名字脏不脏:过长 / 含周次 / 含教学班字样 */
    fun isDirty(name: String): Boolean =
        name.length > 18 ||
                name.contains("讲课") ||
                name.contains("教学班") ||
                weekRe.containsMatchIn(name)

    private fun trimTeacher(t: String): String {
        var s = t
        while (s.length > 2 && locationTailChar.containsMatchIn(s.substring(0, 1))) {
            s = s.substring(1)
        }
        return s
    }

    private fun cleanOne(raw: String, totalWeeks: Int): Course? {
        val full = raw
        val weeksHit = findWeeks(full)
        val weeksText = weeksHit?.second ?: ""

        var teacher = ""
        val beforeClassNo = full.split(Regex("[（(]\\s*[0-9]{4}\\s*[-–—]"))[0]
        tailNameRe.find(beforeClassNo)?.let { teacher = trimTeacher(it.groupValues[1]) }

        var location = ""
        campusRe.find(full)?.let { m ->
            var loc = m.groupValues[1]
            if (teacher.isNotEmpty() && loc.length > teacher.length && loc.endsWith(teacher)) {
                loc = loc.substring(0, loc.length - teacher.length)
            }
            location = loc
        }

        /* 课程名:截到第一个"字段特征"之前(周次只用校验通过的那个位置) */
        var name = full
        val cut = weeksHit?.first?.takeIf { it > 1 }
            ?: markerRe.find(full)?.range?.first?.takeIf { it > 1 }
            ?: -1
        if (cut > 1) {
            name = full.substring(0, cut).replace(periodRe, " ").replace(singlePeriodRe, " ")
                .replace(Regex("\\s+"), " ").trim()
        }
        name = name.replace(periodRe, " ").replace(singlePeriodRe, " ")
            .replace(Regex("[（(]\\s*[)）]"), " ").replace(Regex("\\s+"), " ").trim()
        if (name.length < 2) return null

        val pm = periodRe.find(full) ?: singlePeriodRe.find(full)
        val start = pm?.groupValues?.getOrNull(1)?.toIntOrNull()
        val end = pm?.groupValues?.getOrNull(2)?.toIntOrNull() ?: start

        return Course(
            id = 0L,
            name = name,
            teacher = teacher,
            location = location,
            note = "",
            colorIndex = 0,
            sessions = emptyList()
        ).let { base ->
            base.copy(
                sessions = listOf(
                    CourseSession(
                        dayOfWeek = 0,
                        startPeriod = start ?: 0,
                        endPeriod = end ?: 0,
                        weeks = if (weeksText.isNotEmpty()) TimetableImport.parseWeeks(weeksText, totalWeeks) else emptyList()
                    )
                )
            )
        }
    }

    /** 清洗全部课程;只动"看起来是脏数据"的那些 */
    fun sanitize(courses: List<Course>, totalWeeks: Int): List<Course> = courses.map { c ->
        if (!isDirty(c.name)) return@map c
        val cleaned = cleanOne(c.name, totalWeeks) ?: return@map c
        /* 保留原来每天每次课的时间段(脏数据里每格只生成一门课,一般就一段) */
        val sessions = c.sessions.mapIndexed { i, s ->
            val src = cleaned.sessions.getOrNull(i) ?: cleaned.sessions.firstOrNull()
            s.copy(
                startPeriod = src?.startPeriod?.takeIf { it > 0 } ?: s.startPeriod,
                endPeriod = src?.endPeriod?.takeIf { it > 0 } ?: s.endPeriod,
                weeks = src?.weeks?.takeIf { it.isNotEmpty() } ?: s.weeks
            )
        }.ifEmpty { c.sessions }
        c.copy(
            name = cleaned.name,
            teacher = cleaned.teacher.ifBlank { c.teacher },
            location = cleaned.location.ifBlank { c.location },
            sessions = sessions
        )
    }
}

package com.myschedule.app.data

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

/** 一节课的作息时间(如 08:00-08:45) */
@Serializable
data class ClassPeriod(val start: String = "08:00", val end: String = "08:45")

/** 学期信息:开学第一周的周一日期 + 总周数 */
@Serializable
data class Semester(
    val name: String = "我的学期",
    val startDate: String = "",
    val totalWeeks: Int = 20
)

/** 一段上课时间:周几 + 节次区间 + 上课周次列表 */
@Serializable
data class CourseSession(
    val dayOfWeek: Int = 1,     // 1=周一 ... 7=周日
    val startPeriod: Int = 1,
    val endPeriod: Int = 2,
    val weeks: List<Int> = emptyList()
)

@Serializable
data class Course(
    val id: Long = 0L,
    val name: String = "",
    val teacher: String = "",
    val location: String = "",
    val note: String = "",
    val colorIndex: Int = 0,
    val sessions: List<CourseSession> = emptyList()
)

@Serializable
data class TodoItem(
    val id: Long = 0L,
    val title: String = "",
    val note: String = "",
    val dueDate: String = "",   // yyyy-MM-dd,空串表示无截止日期
    val done: Boolean = false
)

@Serializable
data class ExamItem(
    val id: Long = 0L,
    val name: String = "",
    val date: String = "",      // yyyy-MM-dd
    val time: String = "",      // 如 14:00-16:00
    val location: String = "",
    val note: String = ""
)

@Serializable
data class GradeItem(
    val id: Long = 0L,
    val name: String = "",
    val credit: Double = 0.0,
    val score: Double = 0.0     // 百分制成绩
)

/** 应用全部数据,整体序列化为一个 JSON 文件 */
@Serializable
data class AppData(
    val semester: Semester = Semester(),
    val periods: List<ClassPeriod> = defaultPeriods(),
    val courses: List<Course> = emptyList(),
    val todos: List<TodoItem> = emptyList(),
    val exams: List<ExamItem> = emptyList(),
    val grades: List<GradeItem> = emptyList()
)

/** 默认作息时间表(12 大节,可在大约设置中修改) */
fun defaultPeriods(): List<ClassPeriod> = listOf(
    ClassPeriod("08:00", "08:45"), ClassPeriod("08:55", "09:40"),
    ClassPeriod("10:00", "10:45"), ClassPeriod("10:55", "11:40"),
    ClassPeriod("12:20", "13:05"), ClassPeriod("13:15", "14:00"),
    ClassPeriod("14:10", "14:55"), ClassPeriod("15:05", "15:50"),
    ClassPeriod("16:10", "16:55"), ClassPeriod("17:05", "17:50"),
    ClassPeriod("19:00", "19:45"), ClassPeriod("19:55", "20:40")
)

fun newId(): Long = System.currentTimeMillis() * 1000L + (0..999L).random()

/** 课程颜色池(底色 to 文字色) */
val CoursePalette: List<Pair<Color, Color>> = listOf(
    Color(0xFFBBDEFB) to Color(0xFF0D47A1),  // 蓝
    Color(0xFFFFCCBC) to Color(0xFFBF360C),  // 橘
    Color(0xFFC8E6C9) to Color(0xFF1B5E20),  // 绿
    Color(0xFFF8BBD0) to Color(0xFF880E4F),  // 粉
    Color(0xFFD1C4E9) to Color(0xFF4527A0),  // 紫
    Color(0xFFFFF9C4) to Color(0xFF827717),  // 黄
    Color(0xFFB2DFDB) to Color(0xFF004D40),  // 青
    Color(0xFFC5CAE9) to Color(0xFF283593),  // 靛
    Color(0xFFE1BEE7) to Color(0xFF6A1B9A),  // 品红
    Color(0xFFFFE0B2) to Color(0xFFE65100)   // 杏
)

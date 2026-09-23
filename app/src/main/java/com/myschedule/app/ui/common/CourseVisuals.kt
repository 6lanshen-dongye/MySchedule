package com.myschedule.app.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.myschedule.app.data.CoursePalette

/**
 * 一节课的三档配色(参考超级课程表色块):
 * - accent:顶部色条 / 时间轴圆点,用中等饱和度的颜色
 * - soft:色块浅底,几乎接近白色
 * - text:色块内文字,介于浅底与深色之间,保证可读
 */
data class CourseColors(val accent: Color, val soft: Color, val text: Color)

fun courseColors(colorIndex: Int): CourseColors {
    val size = CoursePalette.size
    val index = ((colorIndex % size) + size) % size
    val light = CoursePalette[index].first
    val dark = CoursePalette[index].second
    return CourseColors(
        accent = light,
        soft = lerp(light, Color.White, 0.66f),
        text = lerp(light, dark, 0.55f)
    )
}

/** 课表里的统一线色 */
val GridDashLine = Color(0xFFE4E8EF)
val GridSolidLine = Color(0xFFF1F3F7)

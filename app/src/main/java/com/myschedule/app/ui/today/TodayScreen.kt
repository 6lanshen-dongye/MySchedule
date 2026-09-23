package com.myschedule.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myschedule.app.data.AppData
import com.myschedule.app.data.AppStore
import com.myschedule.app.data.Course
import com.myschedule.app.data.CourseSession
import com.myschedule.app.data.ScheduleLogic
import com.myschedule.app.ui.common.DayStripRibbon
import com.myschedule.app.ui.common.QuickJumpChip
import com.myschedule.app.ui.common.QuickJumpStrip
import com.myschedule.app.ui.common.courseColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

private val DayNames = listOf("一", "二", "三", "四", "五", "六", "日")
private val InkBlack = Color(0xFF15171C)
/** 选中日期用柔和的炭灰,不用纯黑,看着不压抑 */
private val SoftInk = Color(0xFF3A3D44)
private val InkSoft = Color(0xFF23262C)
private val RailWidth = 68.dp

@Composable
fun TodayScreen(
    store: AppStore,
    focusDate: LocalDate? = null,
    focusTick: Int = 0,
    onNavigate: (Int) -> Unit = {}
) {
    val data by store.data.collectAsState()
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            tick++
        }
    }
    val now = remember(tick) { LocalDateTime.now() }
    val today = now.toLocalDate()

    var anchor by remember { mutableStateOf(focusDate ?: today) }
    LaunchedEffect(focusTick) { if (focusDate != null) anchor = focusDate }
    var showWeekPicker by remember { mutableStateOf(false) }
    var quickJumpOpen by remember { mutableStateOf(false) }
    /* 提交(点日期/点周卡片)时自增,用来把日期条拉过去对齐 */
    var alignTick by remember { mutableStateOf(0) }

    /* 可滑动的日期范围 = 整个学期;左右滑动切换前一天/后一天 */
    val semesterMonday = ScheduleLogic.semesterMonday(data.semester)
    val totalDays = (data.semester.totalWeeks.coerceAtLeast(1)) * 7
    fun indexOf(d: LocalDate): Int =
        if (semesterMonday == null) 0
        else ChronoUnit.DAYS.between(semesterMonday, d).toInt().coerceIn(0, totalDays - 1)

    val allDays = remember(data.semester) {
        if (semesterMonday == null) emptyList()
        else (0 until totalDays).map { semesterMonday.plusDays(it.toLong()) }
    }
    val dayPager = rememberPagerState(initialPage = indexOf(anchor), pageCount = { totalDays })
    val scope = rememberCoroutineScope()
    val totalWeeks = data.semester.totalWeeks.coerceAtLeast(1)
    /* 展开快速滑动时:日期条滑到哪一周就预览哪一周(顶部大组件跟着走),下面课程卡片不动,点了才切 */
    var previewWeek by remember { mutableStateOf<Int?>(null) }
    val commitWeek = ScheduleLogic.weekOfDate(data.semester, anchor) ?: 1
    val displayWeek = previewWeek ?: commitWeek
    /* 预览周的"同一天"(和当前查看的是星期几保持一致) */
    val pageDate = if (quickJumpOpen && previewWeek != null) {
        ScheduleLogic.dateOfWeek(data.semester, previewWeek!!, anchor.dayOfWeek.value) ?: anchor
    } else anchor

    LaunchedEffect(quickJumpOpen) {
        if (quickJumpOpen) previewWeek = commitWeek
    }
    var daySelfScroll by remember { mutableStateOf(false) }
    LaunchedEffect(anchor) {
        val idx = indexOf(anchor)
        if (dayPager.currentPage != idx) {
            daySelfScroll = true
            dayPager.animateScrollToPage(idx)
            daySelfScroll = false
        }
    }
    LaunchedEffect(dayPager) {
        snapshotFlow { dayPager.currentPage }.collect { p ->
            if (daySelfScroll || !dayPager.isScrollInProgress) return@collect
            val d = semesterMonday?.plusDays(p.toLong())
            if (d != null && d != anchor) anchor = d
        }
    }
    val monday = pageDate.minusDays((pageDate.dayOfWeek.value - 1).toLong())
    val week = ScheduleLogic.weekOfDate(data.semester, pageDate)
    val next = ScheduleLogic.nextClass(data, now)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── 顶部快捷入口 ──
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(vertical = 14.dp)
        ) {
            QuickEntry("待办事项", Icons.Filled.ListAlt) { onNavigate(2) }
            QuickEntry("考试倒计时", Icons.Filled.DateRange) { onNavigate(2) }
            QuickEntry("成绩 GPA", Icons.Filled.Star) { onNavigate(2) }
            QuickEntry("数据备份", Icons.Filled.Share) { onNavigate(3) }
        }

        // ── 日期头 ──
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${pageDate.monthValue}月${pageDate.dayOfMonth}日",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkBlack
                )
                Spacer(Modifier.width(7.dp))
                /* 点「第N周」直接跳周;展开快速滑动时隐去,把空间让给周条 */
                if (!quickJumpOpen) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showWeekPicker = true }
                    ) {
                        Text(
                            if (week != null) "第${week}周" else "不在学期内",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = InkBlack
                        )
                        Icon(
                            Icons.Filled.ArrowDropDown,
                            contentDescription = "选择周次",
                            tint = InkBlack,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    /* 展开挑周条时隐去中间的"| 周X",给它腾地方 */
                    Text(
                        " | 周${DayNames[pageDate.dayOfWeek.value - 1]}",
                        fontSize = 15.sp,
                        color = InkBlack
                    )
                }
                Spacer(Modifier.weight(1f))
                /* 右上角小组件:快速滑动(开关式) */
                QuickJumpChip(active = quickJumpOpen, onClick = { quickJumpOpen = !quickJumpOpen })
                /* 展开时把右侧文字让出去,免得被挤成竖排 */
                if (!quickJumpOpen) {
                    Spacer(Modifier.width(8.dp))
                    if (pageDate != today) {
                        Text(
                            "回到今天",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    alignTick++; scope.launch { dayPager.animateScrollToPage(indexOf(today)) }
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    } else {
                        Text(
                            data.semester.name,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            /* 快速滑动展开时:周条独占整行并放大 */
            if (quickJumpOpen) {
                QuickJumpStrip(
                    semester = data.semester,
                    currentWeek = displayWeek,
                    onPreview = { w -> previewWeek = w },
                    onPick = { w ->
                        previewWeek = w
                        ScheduleLogic.dateOfWeek(data.semester, w, anchor.dayOfWeek.value)
                            ?.let { anchor = it }
                        alignTick++
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 2.dp)
                )
            }
            // ── 日期条:整条可自由左右滑动(也能拖着翻日期);点某天=切到那天 ──
            Spacer(Modifier.height(10.dp))
            DayStripRibbon(
                days = allDays,
                /* 对齐到所在周的周一 */
                initialIndex = indexOf(anchor.minusDays((anchor.dayOfWeek.value - 1).toLong())),
                today = today,
                highlight = anchor,
                onPreview = { d ->
                    /* 拖动中只做预览:大组件跟着走,下面课程卡片不动 */
                    ScheduleLogic.weekOfDate(data.semester, d)?.let { previewWeek = it }
                },
                /* 松手停稳后:展开态保持预览,平时=切到那天 */
                onSettle = { d ->
                    ScheduleLogic.weekOfDate(data.semester, d)?.let { previewWeek = it }
                    if (!quickJumpOpen) {
                        /* 没用快速滑动时:一次滑动最多走一周(7天),前后一致 */
                        anchor = d.coerceIn(anchor.minusDays(7), anchor.plusDays(7))
                    }
                },
                onPick = { d ->
                    anchor = d
                    previewWeek = null
                    quickJumpOpen = false
                    alignTick++
                },
                showTodayDot = true,
                alignTick = alignTick,
                onTodayDot = { alignTick++; scope.launch { dayPager.animateScrollToPage(indexOf(today)) } }
            )

            Spacer(Modifier.height(10.dp))
        }

        // ── 下一节课(仅当查看的是今天) ──
        if (pageDate == today && next != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFEAF1FF))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    buildString {
                        append(if (next.inClass) "正在上 " else "下一节 ")
                        append(next.course.name)
                        /* 课程名后面显示下节课的教室(没有教室才回退到日期) */
                        if (next.course.location.isNotBlank()) {
                            append(" · ")
                            append(next.course.location)
                        }
                        if (next.date == today) {
                            val start = ScheduleLogic.periodStart(data, next.session.startPeriod)
                            if (start != null && !next.inClass) {
                                val mins = Duration.between(now, LocalDateTime.of(today, start)).toMinutes()
                                append(" · ")
                                append(
                                    when {
                                        mins >= 60 -> "还有 ${mins / 60} 小时 ${mins % 60} 分钟"
                                        mins >= 1 -> "还有 $mins 分钟"
                                        else -> "即将开始"
                                    }
                                )
                            }
                        } else if (next.course.location.isBlank()) {
                            append(" · ${next.date.monthValue}/${next.date.dayOfMonth}")
                        }
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // ── 当天课程时间轴:左右滑动 = 前一天/后一天 ──
        HorizontalPager(
            state = dayPager,
            modifier = Modifier.fillMaxWidth(),
            beyondViewportPageCount = 1,
            /* 展开快速滑动时锁住课程卡片:只有日期条/挑周条在动 */
            userScrollEnabled = !quickJumpOpen,
            flingBehavior = PagerDefaults.flingBehavior(
                state = dayPager,
                pagerSnapDistance = PagerSnapDistance.atMost(1)
            )
        ) { page ->
            val date = semesterMonday?.plusDays(page.toLong()) ?: pageDate
            val wk = ScheduleLogic.weekOfDate(data.semester, date)
            val list = if (wk != null) {
                ScheduleLogic.sessionsOnDay(data.courses, date.dayOfWeek.value, wk)
            } else emptyList()
            /* 每页占满剩余高度、内容自己滚动:否则课程少的那天会被垂直居中,上方留一大片空白 */
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 6.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.Top
            ) {
                if (list.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (wk == null) "这一天不在学期范围内" else "这天没有课 🎉",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    list.forEach { (course, session) ->
                        TimelineItem(data, course, session)
                    }
                }
            }
        }
    }

    /* 周次选择:跳到某一周(该周周一) */
    if (showWeekPicker) {
        val totalWeeks = data.semester.totalWeeks.coerceAtLeast(1)
        AlertDialog(
            onDismissRequest = { showWeekPicker = false },
            confirmButton = {
                TextButton(onClick = { showWeekPicker = false }) { Text("关闭") }
            },
            title = { Text("选择周次") },
            text = {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items((1..totalWeeks).toList()) { w ->
                        val mon = ScheduleLogic.dateOfWeek(data.semester, w, 1)
                        val sun = ScheduleLogic.dateOfWeek(data.semester, w, 7)
                        Text(
                            "第${w}周" + if (mon != null && sun != null)
                                "(${mon.monthValue}/${mon.dayOfMonth}-${sun.monthValue}/${sun.dayOfMonth})" else "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (w == week) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showWeekPicker = false
                                    mon?.let { target ->
                                        scope.launch { dayPager.animateScrollToPage(indexOf(target)) }
                                    }
                                }
                                .padding(vertical = 10.dp)
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun RowScope.QuickEntry(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = InkBlack,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TimelineItem(data: AppData, course: Course, session: CourseSession) {
    val colors = courseColors(course.colorIndex)
    val start = data.periods.getOrNull(session.startPeriod - 1)?.start ?: ""
    val end = data.periods.getOrNull(session.endPeriod - 1)?.end ?: ""

    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // 左侧节次 + 时间,右边一条虚线
        Box(
            Modifier
                .width(RailWidth)
                .fillMaxHeight()
                .drawBehind {
                    val x = size.width - 9.dp.toPx()
                    drawLine(
                        Color(0xFFD8DDE6),
                        Offset(x, 0f),
                        Offset(x, size.height),
                        2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 8f), 0f)
                    )
                }
        ) {
            Column(Modifier.padding(start = 16.dp, top = 14.dp)) {
                Text(
                    "${session.startPeriod}-${session.endPeriod}节",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkBlack
                )
                Spacer(Modifier.height(3.dp))
                if (start.isNotBlank()) {
                    Text(start, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (end.isNotBlank()) {
                    Text(end, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // 右侧课程卡片
        Column(
            Modifier
                .weight(1f)
                .padding(end = 16.dp, bottom = 12.dp)
                .shadow(2.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(colors.accent)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    course.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkBlack
                )
            }
            if (course.location.isNotBlank()) {
                LabeledLine("上课地点:", course.location)
            }
            if (course.teacher.isNotBlank()) {
                LabeledLine("上课老师:", course.teacher)
            }
            val weeks = ScheduleLogic.weeksText(session.weeks)
            if (weeks.isNotBlank()) {
                LabeledLine("上课周次:", weeks)
            }
        }
    }
}

@Composable
private fun LabeledLine(label: String, value: String) {
    Row {
        Text(
            label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            fontSize = 13.sp,
            color = InkBlack
        )
    }
}

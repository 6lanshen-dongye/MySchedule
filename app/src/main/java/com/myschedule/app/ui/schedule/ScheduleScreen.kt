package com.myschedule.app.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myschedule.app.data.AppStore
import com.myschedule.app.data.Course
import com.myschedule.app.data.CourseSession
import com.myschedule.app.data.ScheduleLogic
import com.myschedule.app.ui.common.DayStripRibbon
import com.myschedule.app.ui.common.GridDashLine
import com.myschedule.app.ui.common.GridSolidLine
import com.myschedule.app.ui.common.QuickJumpChip
import com.myschedule.app.ui.common.QuickJumpStrip
import com.myschedule.app.ui.common.courseColors
import java.time.LocalDate

private val DayNames = listOf("一", "二", "三", "四", "五", "六", "日")
private val RowHeight = 64.dp
private val LeftColumnWidth = 46.dp
private val BlockGap = 2.dp
private val InkBlack = Color(0xFF15171C)

private data class GridBlock(val course: Course, val start: Int, val span: Int)

private fun buildBlocks(courses: List<Course>, week: Int, day: Int): List<GridBlock> {
    val sessions = courses.flatMap { c ->
        c.sessions.filter { it.dayOfWeek == day && week in it.weeks }.map { c to it }
    }.sortedBy { it.second.startPeriod }
    val taken = mutableSetOf<Int>()
    val result = mutableListOf<GridBlock>()
    for ((c, s) in sessions) {
        val periods = (s.startPeriod..s.endPeriod).toList()
        if (periods.any { it in taken }) continue
        taken.addAll(periods)
        result.add(GridBlock(c, s.startPeriod, s.endPeriod - s.startPeriod + 1))
    }
    return result.sortedBy { it.start }
}

@Composable
fun ScheduleScreen(
    store: AppStore,
    onEditCourse: (Course?) -> Unit,
    onImportFromEdu: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val data by store.data.collectAsState()
    val today = remember { LocalDate.now() }
    val curWeek = ScheduleLogic.currentWeek(data.semester, today)
    var viewWeek by remember { mutableStateOf(curWeek) }
    LaunchedEffect(curWeek) { viewWeek = curWeek }
    val total = data.semester.totalWeeks.coerceAtLeast(1)
    val shownWeek = viewWeek.coerceIn(1, total)

    var showWeekPicker by remember { mutableStateOf(false) }
    var detailCourse by remember { mutableStateOf<Course?>(null) }
    var showManage by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Course?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var dayDetail by remember { mutableStateOf<LocalDate?>(null) }
    var quickJumpOpen by remember { mutableStateOf(false) }
    /* 只在"提交"时对齐日期条,平时滑动不回拉 */
    var alignTick by remember { mutableStateOf(0) }
    /* 展开快速滑动时:日期条/挑周条滑到的"预览周";课表网格只在点击后才跟着切 */
    var previewWeek by remember { mutableStateOf(shownWeek) }
    val displayWeek = if (quickJumpOpen) previewWeek.coerceIn(1, total) else shownWeek

    val periodCount = data.periods.size

    /* 刚展开时,预览周对齐到当前周 */
    LaunchedEffect(quickJumpOpen) {
        if (quickJumpOpen) previewWeek = shownWeek
    }

    /* 整个学期的每一天(日期条自由滑动用) */
    val allDays = remember(data.semester) {
        val start = ScheduleLogic.semesterMonday(data.semester)
        if (start == null) emptyList()
        else (0 until total * 7).map { start.plusDays(it.toLong()) }
    }
    val mondayIndex = allDays.indexOfFirst {
        /* 对齐用"已生效的周":滑动只是预览,不会把日期条又拉回去 */
        it == ScheduleLogic.dateOfWeek(data.semester, shownWeek, 1)
    }.coerceAtLeast(0)

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // ── 标题栏:第 N 周 + 学期名 + 操作图标 ──
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            /* 展开快速滑动时隐去「当周」标签,空间让给周条 */
            if (!quickJumpOpen) {
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { showWeekPicker = true }
                ) {
                    Text(
                        "第$shownWeek 周",
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                        color = InkBlack
                    )
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        contentDescription = "选择周次",
                        tint = InkBlack,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    data.semester.name,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            }
            /* 右上角小组件:快速滑动(开关式) */
            QuickJumpChip(active = quickJumpOpen, onClick = { quickJumpOpen = !quickJumpOpen })
            Spacer(Modifier.width(2.dp))
            IconButton(onClick = { viewWeek = curWeek; alignTick++ }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Today, contentDescription = "回到本周", tint = InkBlack)
            }
            IconButton(onClick = { showManage = true }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.ListAlt, contentDescription = "管理课程", tint = InkBlack)
            }
            Box(Modifier.padding(start = 2.dp)) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(InkBlack)
                        .clickable { menuOpen = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "添加课程",
                        tint = Color.White,
                        modifier = Modifier.size(21.dp)
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    modifier = Modifier.width(268.dp)
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                menuOpen = false
                                onOpenSettings()
                            }
                            .padding(horizontal = 18.dp, vertical = 12.dp)
                    ) {
                        Text("切换学期", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            data.semester.name,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MenuRow("教务系统导课", "从学校教务系统抓取本学期课表") {
                        menuOpen = false
                        onImportFromEdu()
                    }
                    MenuRow("快速滑动", "左右滑动挑周,一点跳到那一周") {
                        menuOpen = false
                        quickJumpOpen = true
                    }
                    MenuRow("手动添课", "自己填一门课") {
                        menuOpen = false
                        onEditCourse(null)
                    }
                    MenuRow("蹭课", "旁听课,备注里标记一下") {
                        menuOpen = false
                        onEditCourse(Course(id = 0L, note = "蹭课"))
                    }
                }
            }
        }

        /* 快速滑动展开时:周条独占整行并放大 */
        if (quickJumpOpen) {
            QuickJumpStrip(
                semester = data.semester,
                currentWeek = displayWeek,
                /* 滑动只预览高亮,不切课表;点周卡片才切 */
                onPreview = { w -> previewWeek = w.coerceIn(1, total) },
                onPick = { w ->
                    val committed = w.coerceIn(1, total)
                    viewWeek = committed
                    previewWeek = committed
                    alignTick++
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 2.dp)
            )
        }
        // ── 周日期条:左边月份,右边 一~日 + 日期;整条可左右滑动翻周 ──
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val monday = ScheduleLogic.dateOfWeek(data.semester, displayWeek, 1)
            Column(
                Modifier.width(LeftColumnWidth),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    monday?.monthValue?.toString() ?: "—",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkBlack
                )
                Text("月", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DayStripRibbon(
                days = allDays,
                initialIndex = mondayIndex,
                today = today,
                highlight = ScheduleLogic.dateOfWeek(data.semester, displayWeek, 1),
                /* 拖动中只做预览(不切课表) */
                onPreview = { d ->
                    ScheduleLogic.weekOfDate(data.semester, d)?.let { previewWeek = it }
                },
                /* 松手停稳后:展开态只预览,收起态=直接翻到那一周 */
                onSettle = { d ->
                    ScheduleLogic.weekOfDate(data.semester, d)?.let {
                        previewWeek = it
                        if (!quickJumpOpen) {
                            /* 没用快速滑动时:一次滑动最多翻一周,前后一致 */
                            val t = it.coerceIn(viewWeek - 1, viewWeek + 1).coerceIn(1, total)
                            if (t != viewWeek) viewWeek = t
                        }
                    }
                },
                alignTick = alignTick,
                /* 点某天:展开态=切到那一周并收起;平时=看那天的课程 */
                onPick = { d ->
                    if (quickJumpOpen) {
                        ScheduleLogic.weekOfDate(data.semester, d)?.let {
                            viewWeek = it
                            previewWeek = it
                        }
                        quickJumpOpen = false
                    } else if (d == today) {
                        quickJumpOpen = true
                    } else {
                        dayDetail = d
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
        // ── 网格 ──
        // 左右滑动 = 切换周次(整周平滑平移),节次时间列固定不动
        val pagerState = rememberPagerState(
            initialPage = (shownWeek - 1).coerceIn(0, total - 1),
            pageCount = { total }
        )
        var bodySelfScroll by remember { mutableStateOf(false) }
        LaunchedEffect(shownWeek) {
            val target = shownWeek - 1
            if (pagerState.currentPage != target) {
                bodySelfScroll = true
                pagerState.animateScrollToPage(target)
                bodySelfScroll = false
            }
        }
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }.collect { p ->
                if (bodySelfScroll || !pagerState.isScrollInProgress) return@collect
                val w = (p + 1).coerceIn(1, total)
                if (w != viewWeek) viewWeek = w
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .drawBehind {
                    val leftPx = LeftColumnWidth.toPx()
                    val rowPx = RowHeight.toPx()
                    val dayWidth = (size.width - leftPx) / 7f
                    // 竖向分隔线
                    for (j in 0..7) {
                        val x = leftPx + j * dayWidth
                        drawLine(
                            GridSolidLine,
                            Offset(x, 0f),
                            Offset(x, size.height),
                            1.5f
                        )
                    }
                    // 横向虚线
                    val dash = PathEffect.dashPathEffect(floatArrayOf(9f, 9f), 0f)
                    for (i in 1 until periodCount) {
                        val y = i * rowPx
                        drawLine(
                            GridDashLine,
                            Offset(leftPx, y),
                            Offset(size.width, y),
                            2f,
                            pathEffect = dash
                        )
                    }
                }
        ) {
            // 节次时间列
            Column(Modifier.width(LeftColumnWidth)) {
                for (i in 0 until periodCount) {
                    Column(
                        Modifier
                            .height(RowHeight)
                            .padding(start = 7.dp, top = 3.dp)
                    ) {
                        Text(
                            "${i + 1}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = InkBlack,
                            lineHeight = 15.sp
                        )
                        Text(
                            data.periods.getOrNull(i)?.start ?: "",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 11.sp
                        )
                        Text(
                            data.periods.getOrNull(i)?.end ?: "",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 11.sp
                        )
                    }
                }
            }
            // 7 天列:左右滑动切换周次
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                pageSpacing = 0.dp,
                beyondViewportPageCount = 1,
                /* 一次滑动只翻一周,避免快速划动跨好几周 */
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pagerState,
                    pagerSnapDistance = PagerSnapDistance.atMost(1)
                )
            ) { page ->
                val w = (page + 1).coerceIn(1, total)
                Row(Modifier.fillMaxWidth()) {
                    for (d in 1..7) {
                        DayColumn(
                            blocks = buildBlocks(data.courses, w, d),
                            periodCount = periodCount,
                            onCourseTap = { detailCourse = it },
                            onEmptyTap = { p ->
                                onEditCourse(
                                    Course(
                                        id = 0L,
                                        sessions = listOf(
                                            CourseSession(dayOfWeek = d, startPeriod = p, endPeriod = p)
                                        )
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    // ── 某天的课程清单(点日期条弹出) ──
    dayDetail?.let { d ->
        val w = ScheduleLogic.weekOfDate(data.semester, d) ?: shownWeek
        val list = ScheduleLogic.sessionsOnDay(data.courses, d.dayOfWeek.value, w)
        AlertDialog(
            onDismissRequest = { dayDetail = null },
            confirmButton = {
                TextButton(onClick = { dayDetail = null }) { Text("关闭") }
            },
            title = {
                Text(
                    "${d.monthValue}月${d.dayOfMonth}日 周${DayNames[d.dayOfWeek.value - 1]} · 第${w}周",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                if (list.isEmpty()) {
                    Text("这天没有课 🎉", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(list) { pair ->
                            val course = pair.first
                            val session = pair.second
                            val colors = courseColors(course.colorIndex)
                            val st = data.periods.getOrNull(session.startPeriod - 1)?.start ?: ""
                            val en = data.periods.getOrNull(session.endPeriod - 1)?.end ?: ""
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 7.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    Modifier
                                        .padding(top = 5.dp)
                                        .size(9.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(colors.accent)
                                )
                                Spacer(Modifier.width(9.dp))
                                Column {
                                    Text(course.name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        "第${session.startPeriod}-${session.endPeriod}节" +
                                                (if (st.isNotBlank() && en.isNotBlank()) " $st-$en" else "") +
                                                listOf(course.location, course.teacher)
                                                    .filter { it.isNotBlank() }
                                                    .joinToString(" · ", prefix = " · "),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    // ── 课程详情 ──
    detailCourse?.let { c ->
        AlertDialog(
            onDismissRequest = { detailCourse = null },
            confirmButton = {
                TextButton(onClick = {
                    detailCourse = null
                    onEditCourse(c)
                }) { Text("编辑") }
            },
            dismissButton = {
                TextButton(onClick = { detailCourse = null }) { Text("关闭") }
            },
            title = { Text(c.name, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    c.sessions.sortedBy { it.dayOfWeek }.forEach { s ->
                        Text(
                            "周${DayNames[s.dayOfWeek - 1]} 第${s.startPeriod}-${s.endPeriod}节 · ${ScheduleLogic.weeksText(s.weeks)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (c.teacher.isNotBlank()) Text("教师:${c.teacher}", style = MaterialTheme.typography.bodyMedium)
                    if (c.location.isNotBlank()) Text("教室:${c.location}", style = MaterialTheme.typography.bodyMedium)
                    if (c.note.isNotBlank()) {
                        Text(
                            "备注:${c.note}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        )
    }

    // ── 周次选择 ──
    if (showWeekPicker) {
        AlertDialog(
            onDismissRequest = { showWeekPicker = false },
            confirmButton = {
                TextButton(onClick = { showWeekPicker = false }) { Text("关闭") }
            },
            title = { Text("选择周次") },
            text = {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items((1..total).toList()) { w ->
                        val start = ScheduleLogic.dateOfWeek(data.semester, w, 1)
                        val end = ScheduleLogic.dateOfWeek(data.semester, w, 7)
                        Text(
                            "第${w}周" + if (start != null && end != null)
                                "(${start.monthValue}/${start.dayOfMonth}-${end.monthValue}/${end.dayOfMonth})" else "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (w == shownWeek) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewWeek = w
                                    showWeekPicker = false
                                }
                                .padding(vertical = 10.dp)
                        )
                    }
                }
            }
        )
    }

    // ── 管理课程 ──
    if (showManage) {
        AlertDialog(
            onDismissRequest = { showManage = false },
            confirmButton = {
                TextButton(onClick = { showManage = false }) { Text("关闭") }
            },
            title = { Text("管理课程(${data.courses.size})") },
            text = {
                if (data.courses.isEmpty()) {
                    Text("还没有课程,点击右上角 + 添加", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(data.courses, key = { it.id }) { c ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val colors = courseColors(c.colorIndex)
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(colors.accent)
                                )
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .clickable {
                                            showManage = false
                                            onEditCourse(c)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(c.name, style = MaterialTheme.typography.bodyLarge)
                                    if (c.teacher.isNotBlank() || c.location.isNotBlank()) {
                                        Text(
                                            listOf(c.teacher, c.location).filter { it.isNotBlank() }
                                                .joinToString(" · "),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(onClick = {
                                    onEditCourse(c)
                                    showManage = false
                                }) {
                                    Icon(
                                        Icons.Filled.Edit,
                                        contentDescription = "编辑",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(onClick = { deleteTarget = c }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "删除",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    // ── 删除确认 ──
    deleteTarget?.let { c ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除课程") },
            text = { Text("确定删除《${c.name}》吗?该操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    store.update { d -> d.copy(courses = d.courses.filter { it.id != c.id }) }
                    deleteTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun MenuRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp)
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = InkBlack)
        Text(
            subtitle,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DayColumn(
    blocks: List<GridBlock>,
    periodCount: Int,
    onCourseTap: (Course) -> Unit,
    onEmptyTap: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        var p = 1
        while (p <= periodCount) {
            val block = blocks.firstOrNull { it.start == p }
            if (block != null) {
                val colors = courseColors(block.course.colorIndex)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(RowHeight * block.span)
                        .padding(bottom = BlockGap)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.soft)
                        .clickable { onCourseTap(block.course) }
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(3.5.dp)
                            .background(colors.text)
                    )
                    Text(
                        text = if (block.course.location.isBlank()) block.course.name
                        else "${block.course.name}@${block.course.location}",
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.text,
                        maxLines = if (block.span <= 1) 3 else 12,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp)
                    )
                }
                p += block.span
            } else {
                val empty = p
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(RowHeight)
                        .clickable { onEmptyTap(empty) }
                )
                p += 1
            }
        }
    }
}

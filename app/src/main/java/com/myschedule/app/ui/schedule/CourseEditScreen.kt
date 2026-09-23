package com.myschedule.app.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myschedule.app.data.AppStore
import com.myschedule.app.data.Course
import com.myschedule.app.data.CoursePalette
import com.myschedule.app.data.CourseSession
import com.myschedule.app.data.ScheduleLogic
import com.myschedule.app.data.newId
import com.myschedule.app.ui.common.Stepper

private val DayNames = listOf("一", "二", "三", "四", "五", "六", "日")

/** 编辑中的一段上课时间(字段为 Compose 状态,界面即时刷新) */
class SessionDraft(
    day: Int,
    startP: Int,
    endP: Int,
    weekStart: Int,
    weekEnd: Int,
    parity: Int
) {
    var day by mutableStateOf(day)
    var startP by mutableStateOf(startP)
    var endP by mutableStateOf(endP)
    var weekStart by mutableStateOf(weekStart)
    var weekEnd by mutableStateOf(weekEnd)
    var parity by mutableStateOf(parity)   // 0每周 1单周 2双周
}

private fun weeksOfDraft(d: SessionDraft, totalWeeks: Int): List<Int> =
    (d.weekStart..d.weekEnd)
        .filter { d.parity == 0 || (d.parity == 1 && it % 2 == 1) || (d.parity == 2 && it % 2 == 0) }
        .filter { it in 1..totalWeeks }

private fun draftFrom(s: CourseSession, totalWeeks: Int): SessionDraft {
    val w = s.weeks.ifEmpty { (1..totalWeeks).toList() }
    val parity = when {
        w.size >= 2 && w.all { it % 2 == 1 } -> 1
        w.size >= 2 && w.all { it % 2 == 0 } -> 2
        else -> 0
    }
    return SessionDraft(s.dayOfWeek, s.startPeriod, s.endPeriod, w.min(), w.max(), parity)
}

@Composable
fun CourseEditScreen(store: AppStore, editing: Course?, onClose: () -> Unit) {
    val data by store.data.collectAsState()
    val total = data.semester.totalWeeks.coerceAtLeast(1)
    val periodCount = data.periods.size
    val isNew = editing == null || editing.id == 0L

    val name = remember { mutableStateOf(editing?.name ?: "") }
    val teacher = remember { mutableStateOf(editing?.teacher ?: "") }
    val location = remember { mutableStateOf(editing?.location ?: "") }
    val note = remember { mutableStateOf(editing?.note ?: "") }
    val colorIndex = remember {
        mutableStateOf(
            editing?.colorIndex ?: (data.courses.size % CoursePalette.size)
        )
    }
    val drafts = remember {
        mutableStateListOf<SessionDraft>().apply {
            val ss = editing?.sessions
            if (!ss.isNullOrEmpty()) {
                addAll(ss.map { draftFrom(it, total) })
            } else {
                val first = editing?.sessions?.firstOrNull()
                add(
                    SessionDraft(
                        first?.dayOfWeek ?: 1,
                        first?.startPeriod ?: 1,
                        first?.endPeriod ?: 2,
                        1,
                        total,
                        0
                    )
                )
            }
        }
    }

    var error by remember { mutableStateOf<String?>(null) }
    var conflictCourse by remember { mutableStateOf<Course?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun buildCourse(id: Long): Course {
        val valid = drafts.map { d ->
            CourseSession(d.day, d.startP, d.endP, weeksOfDraft(d, total))
        }.filter { it.weeks.isNotEmpty() }
        return Course(
            id = id,
            name = name.value.trim(),
            teacher = teacher.value.trim(),
            location = location.value.trim(),
            note = note.value.trim(),
            colorIndex = colorIndex.value,
            sessions = valid
        )
    }

    fun attemptSave(allowConflict: Boolean) {
        if (name.value.isBlank()) {
            error = "请填写课程名称"
            return
        }
        if (drafts.none { weeksOfDraft(it, total).isNotEmpty() }) {
            error = "请至少保留一段有效的上课时间"
            return
        }
        val newCourse = buildCourse(if (isNew) newId() else editing!!.id)
        if (!allowConflict) {
            val hit = data.courses.firstOrNull { c ->
                c.id != newCourse.id && c.sessions.any { t ->
                    newCourse.sessions.any { n -> ScheduleLogic.conflicts(n, t) }
                }
            }
            if (hit != null) {
                conflictCourse = hit
                return
            }
        }
        store.update { d ->
            if (isNew) {
                d.copy(courses = d.courses + newCourse)
            } else {
                d.copy(courses = d.courses.map { if (it.id == newCourse.id) newCourse else it })
            }
        }
        onClose()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶栏
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "关闭")
            }
            Text(
                if (isNew) "添加课程" else "编辑课程",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (!isNew) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除课程",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            OutlinedTextField(
                value = name.value,
                onValueChange = { name.value = it; error = null },
                label = { Text("课程名称 *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = teacher.value,
                    onValueChange = { teacher.value = it },
                    label = { Text("教师") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = location.value,
                    onValueChange = { location.value = it },
                    label = { Text("教室") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = note.value,
                onValueChange = { note.value = it },
                label = { Text("备注(可选)") },
                modifier = Modifier.fillMaxWidth()
            )

            // 颜色选择
            Text("颜色", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CoursePalette.forEachIndexed { i, pair ->
                    val selected = colorIndex.value == i
                    Box(
                        Modifier
                            .size(if (selected) 34.dp else 30.dp)
                            .clip(CircleShape)
                            .background(pair.first)
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) pair.second else Color(0x22000000),
                                shape = CircleShape
                            )
                            .clickable { colorIndex.value = i }
                    )
                }
            }

            // 上课时间
            Text("上课时间", style = MaterialTheme.typography.titleSmall)
            drafts.forEachIndexed { idx, d ->
                SessionCard(
                    draft = d,
                    index = idx,
                    periodCount = periodCount,
                    totalWeeks = total,
                    onRemove = { drafts.removeAt(idx) }
                )
            }
            TextButton(onClick = {
                drafts.add(SessionDraft(1, 1, 2, 1, total, 0))
            }) {
                Icon(Icons.Filled.Add, contentDescription = null, Modifier.size(16.dp))
                Text(" 添加一段上课时间")
            }

            Spacer(Modifier.padding(bottom = 8.dp))
        }

        // 底部保存
        Box(Modifier.padding(16.dp)) {
            Button(
                onClick = { attemptSave(false) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存", fontWeight = FontWeight.Bold)
            }
        }
    }

    // 冲突确认
    if (conflictCourse != null) {
        AlertDialog(
            onDismissRequest = { conflictCourse = null },
            title = { Text("时间冲突") },
            text = { Text("与课程《${conflictCourse!!.name}》的上课时间重叠,仍要保存吗?") },
            confirmButton = {
                TextButton(onClick = {
                    conflictCourse = null
                    attemptSave(true)
                }) { Text("仍要保存") }
            },
            dismissButton = {
                TextButton(onClick = { conflictCourse = null }) { Text("返回修改") }
            }
        )
    }

    // 删除课程确认
    if (confirmDelete && editing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除课程") },
            text = { Text("确定删除《${editing.name}》吗?") },
            confirmButton = {
                TextButton(onClick = {
                    store.update { d -> d.copy(courses = d.courses.filter { it.id != editing.id }) }
                    confirmDelete = false
                    onClose()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SessionCard(
    draft: SessionDraft,
    index: Int,
    periodCount: Int,
    totalWeeks: Int,
    onRemove: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "时间段 ${index + 1}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除时间段",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // 周几
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayNames.forEachIndexed { i, label ->
                    FilterChip(
                        selected = draft.day == i + 1,
                        onClick = { draft.day = i + 1 },
                        label = { Text(label) }
                    )
                }
            }

            // 节次
            Row(verticalAlignment = Alignment.CenterVertically) {
                Stepper(
                    value = draft.startP,
                    min = 1,
                    max = periodCount,
                    onChange = {
                        draft.startP = it
                        if (draft.endP < it) draft.endP = it
                    }
                )
                Text(" 节 至 ", style = MaterialTheme.typography.bodyMedium)
                Stepper(
                    value = draft.endP,
                    min = 1,
                    max = periodCount,
                    onChange = {
                        draft.endP = it
                        if (draft.startP > it) draft.startP = it
                    }
                )
                Text(" 节", style = MaterialTheme.typography.bodyMedium)
            }

            // 周次
            Row(verticalAlignment = Alignment.CenterVertically) {
                Stepper(
                    value = draft.weekStart,
                    min = 1,
                    max = totalWeeks,
                    onChange = {
                        draft.weekStart = it
                        if (draft.weekEnd < it) draft.weekEnd = it
                    },
                    suffix = ""
                )
                Text(" 周 至 ", style = MaterialTheme.typography.bodyMedium)
                Stepper(
                    value = draft.weekEnd,
                    min = 1,
                    max = totalWeeks,
                    onChange = {
                        draft.weekEnd = it
                        if (draft.weekStart > it) draft.weekStart = it
                    }
                )
                Text(" 周", style = MaterialTheme.typography.bodyMedium)
            }

            // 单双周
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("每周" to 0, "单周" to 1, "双周" to 2).forEach { (label, v) ->
                    FilterChip(
                        selected = draft.parity == v,
                        onClick = { draft.parity = v },
                        label = { Text(label) }
                    )
                }
            }

            Text(
                "预览:周${DayNames[draft.day - 1]} 第${draft.startP}-${draft.endP}节 · ${
                    ScheduleLogic.weeksText(weeksOfDraft(draft, totalWeeks))
                }",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

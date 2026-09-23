package com.myschedule.app.ui.tools

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myschedule.app.data.AppStore
import com.myschedule.app.data.ExamItem
import com.myschedule.app.data.GradeItem
import com.myschedule.app.data.ScheduleLogic
import com.myschedule.app.data.TodoItem
import com.myschedule.app.data.newId
import com.myschedule.app.ui.common.DatePickerDialogM3
import com.myschedule.app.ui.common.EmptyHint
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun ToolsScreen(store: AppStore) {
    var view by remember { mutableStateOf("hub") }
    when (view) {
        "hub" -> ToolsHub(store, onOpen = { view = it })
        "todo" -> TodoView(store, onBack = { view = "hub" })
        "exam" -> ExamView(store, onBack = { view = "hub" })
        "grade" -> GradeView(store, onBack = { view = "hub" })
    }
}

@Composable
private fun SubScreenBar(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}

@Composable
private fun ToolsHub(store: AppStore, onOpen: (String) -> Unit) {
    val data by store.data.collectAsState()
    val today = remember { LocalDate.now() }
    val undone = data.todos.count { !it.done }
    val upcomingExams = data.exams.count { it.date >= today.toString() }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "学习工具",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        HubCard(
            icon = Icons.Filled.Assignment,
            iconBg = Color(0xFF3B7CF0),
            title = "待办事项",
            desc = "记录要做的事,支持截止日期",
            badge = if (undone > 0) "$undone 项未完成" else "全部完成啦",
            onClick = { onOpen("todo") }
        )
        HubCard(
            icon = Icons.Filled.Event,
            iconBg = Color(0xFFE8590C),
            title = "考试与 DDL",
            desc = "考试安排倒计时",
            badge = if (upcomingExams > 0) "$upcomingExams 项进行中" else "暂无安排",
            onClick = { onOpen("exam") }
        )
        HubCard(
            icon = Icons.Filled.School,
            iconBg = Color(0xFF2F9E44),
            title = "成绩与 GPA",
            desc = "录入成绩,自动计算加权平均分和 GPA",
            badge = if (data.grades.isNotEmpty())
                "均分 " + String.format("%.1f", ScheduleLogic.weightedAverage(data.grades))
            else "暂无成绩",
            onClick = { onOpen("grade") }
        )
    }
}

@Composable
private fun HubCard(
    icon: ImageVector,
    iconBg: Color,
    title: String,
    desc: String,
    badge: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White)
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                badge,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ==================== 待办事项 ====================

@Composable
private fun TodoView(store: AppStore, onBack: () -> Unit) {
    val data by store.data.collectAsState()
    val input = remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf("") }
    var showDuePicker by remember { mutableStateOf(false) }

    val sorted = data.todos.sortedWith(
        compareBy<TodoItem> { it.done }.thenBy { it.dueDate.isBlank() }.thenBy { it.dueDate }
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SubScreenBar("待办事项", onBack)

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OutlinedTextField(
                value = input.value,
                onValueChange = { input.value = it },
                placeholder = { Text("添加待办…") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showDuePicker = true }) {
                Icon(
                    Icons.Filled.CalendarMonth,
                    contentDescription = "截止日期",
                    tint = if (dueDate.isNotBlank()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = {
                if (input.value.isNotBlank()) {
                    val t = TodoItem(
                        id = newId(),
                        title = input.value.trim(),
                        dueDate = dueDate
                    )
                    store.update { d -> d.copy(todos = d.todos + t) }
                    input.value = ""
                    dueDate = ""
                }
            }) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "添加",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        if (dueDate.isNotBlank()) {
            Text(
                "截止日期:$dueDate(点击日历图标修改)",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (sorted.isEmpty()) EmptyHint("暂无待办事项,在上面输入框记一笔吧")
            sorted.forEach { t ->
                val today = remember { LocalDate.now().toString() }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = t.done,
                        onCheckedChange = { checked ->
                            store.update { d ->
                                d.copy(todos = d.todos.map { if (it.id == t.id) it.copy(done = checked) else it })
                            }
                        }
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            t.title,
                            textDecoration = if (t.done) TextDecoration.LineThrough else null,
                            color = if (t.done) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (t.note.isNotBlank() || t.dueDate.isNotBlank()) {
                            val overdue = t.dueDate.isNotBlank() && t.dueDate < today && !t.done
                            Text(
                                listOfNotNull(
                                    t.dueDate.takeIf { it.isNotBlank() }?.let { "截止 $it" },
                                    t.note.takeIf { it.isNotBlank() }
                                ).joinToString(" · "),
                                fontSize = 12.sp,
                                color = if (overdue) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = {
                        store.update { d -> d.copy(todos = d.todos.filter { it.id != t.id }) }
                    }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.padding(bottom = 16.dp))
        }
    }

    if (showDuePicker) {
        DatePickerDialogM3(
            initial = null,
            onDismiss = { showDuePicker = false },
            onSelect = {
                dueDate = it.toString()
                showDuePicker = false
            }
        )
    }
}

// ==================== 考试安排 ====================

@Composable
private fun ExamView(store: AppStore, onBack: () -> Unit) {
    val data by store.data.collectAsState()
    val today = remember { LocalDate.now() }
    var showDialog by remember { mutableStateOf(false) }

    val sorted = data.exams.sortedBy { it.date }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SubScreenBar("考试与 DDL", onBack) {
            IconButton(onClick = { showDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "添加", tint = MaterialTheme.colorScheme.primary)
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (sorted.isEmpty()) EmptyHint("暂无考试安排,点右上角 + 添加")
            sorted.forEach { e ->
                val days = ChronoUnit.DAYS.between(today, LocalDate.parse(e.date))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(e.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            val detail = listOfNotNull(
                                e.date.takeIf { it.isNotBlank() },
                                e.time.takeIf { it.isNotBlank() },
                                e.location.takeIf { it.isNotBlank() }
                            ).joinToString(" · ")
                            if (detail.isNotBlank()) {
                                Text(
                                    detail,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (e.note.isNotBlank()) {
                                Text(e.note, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            val (label, color) = when {
                                days < 0 -> "已结束" to MaterialTheme.colorScheme.onSurfaceVariant
                                days == 0L -> "今天!" to MaterialTheme.colorScheme.error
                                else -> "还有${days}天" to MaterialTheme.colorScheme.primary
                            }
                            Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            IconButton(onClick = {
                                store.update { d -> d.copy(exams = d.exams.filter { it.id != e.id }) }
                            }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.padding(bottom = 16.dp))
        }
    }

    if (showDialog) {
        ExamEditDialog(
            onDismiss = { showDialog = false },
            onSave = { name, date, time, location, note ->
                store.update { d ->
                    d.copy(exams = d.exams + ExamItem(newId(), name, date, time, location, note))
                }
            }
        )
    }
}

@Composable
private fun ExamEditDialog(onDismiss: () -> Unit, onSave: (String, String, String, String, String) -> Unit) {
    val name = remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    val time = remember { mutableStateOf("") }
    val location = remember { mutableStateOf("") }
    val note = remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加考试 / DDL") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                OutlinedTextField(
                    value = name.value,
                    onValueChange = { name.value = it },
                    label = { Text("名称 *") },
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showDatePicker = true }) {
                        Text(if (date.isBlank()) "选择日期 *" else date)
                    }
                }
                OutlinedTextField(
                    value = time.value,
                    onValueChange = { time.value = it },
                    label = { Text("时间(如 14:00-16:00)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = location.value,
                    onValueChange = { location.value = it },
                    label = { Text("地点") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = note.value,
                    onValueChange = { note.value = it },
                    label = { Text("备注") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.value.isBlank()) {
                    error = "请填写名称"
                } else if (date.isBlank()) {
                    error = "请选择日期"
                } else {
                    onSave(name.value.trim(), date, time.value.trim(), location.value.trim(), note.value.trim())
                    onDismiss()
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )

    if (showDatePicker) {
        DatePickerDialogM3(
            initial = null,
            onDismiss = { showDatePicker = false },
            onSelect = {
                date = it.toString()
                showDatePicker = false
            }
        )
    }
}

// ==================== 成绩 GPA ====================

@Composable
private fun GradeView(store: AppStore, onBack: () -> Unit) {
    val data by store.data.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SubScreenBar("成绩与 GPA", onBack) {
            IconButton(onClick = { showDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "添加", tint = MaterialTheme.colorScheme.primary)
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatCell("加权平均分", if (data.grades.isEmpty()) "—" else String.format("%.1f", ScheduleLogic.weightedAverage(data.grades)), Modifier.weight(1f))
                    StatCell("GPA(4.0)", if (data.grades.isEmpty()) "—" else String.format("%.2f", ScheduleLogic.weightedGpa(data.grades)), Modifier.weight(1f))
                    StatCell("总学分", if (data.grades.isEmpty()) "—" else trimZero(data.grades.sumOf { it.credit }), Modifier.weight(1f))
                }
            }

            if (data.grades.isEmpty()) EmptyHint("暂无成绩,点右上角 + 录入")
            data.grades.forEach { g ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(g.name, fontWeight = FontWeight.Bold)
                        Text(
                            "学分 ${trimZero(g.credit)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        trimZero(g.score),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (g.score >= 60) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    IconButton(onClick = {
                        store.update { d -> d.copy(grades = d.grades.filter { it.id != g.id }) }
                    }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.padding(bottom = 16.dp))
        }
    }

    if (showDialog) {
        GradeEditDialog(
            onDismiss = { showDialog = false },
            onSave = { name, credit, score ->
                store.update { d ->
                    d.copy(grades = d.grades + GradeItem(newId(), name, credit, score))
                }
            }
        )
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, color = Color(0xCCFFFFFF), fontSize = 12.sp)
    }
}

private fun trimZero(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else String.format("%.1f", v)

@Composable
private fun GradeEditDialog(onDismiss: () -> Unit, onSave: (String, Double, Double) -> Unit) {
    val name = remember { mutableStateOf("") }
    val credit = remember { mutableStateOf("") }
    val score = remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("录入成绩") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                OutlinedTextField(
                    value = name.value,
                    onValueChange = { name.value = it },
                    label = { Text("课程名 *") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = credit.value,
                    onValueChange = { credit.value = it },
                    label = { Text("学分 *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = score.value,
                    onValueChange = { score.value = it },
                    label = { Text("成绩(百分制) *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val c = credit.value.toDoubleOrNull()
                val s = score.value.toDoubleOrNull()
                when {
                    name.value.isBlank() -> error = "请填写课程名"
                    c == null || c <= 0 -> error = "学分需为正数"
                    s == null || s !in 0.0..100.0 -> error = "成绩需在 0-100 之间"
                    else -> {
                        onSave(name.value.trim(), c, s)
                        onDismiss()
                    }
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

package com.myschedule.app.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myschedule.app.data.AppData
import com.myschedule.app.data.AppStore
import com.myschedule.app.data.ClassPeriod
import com.myschedule.app.data.Course
import com.myschedule.app.data.CourseSession
import com.myschedule.app.data.ScheduleLogic
import com.myschedule.app.data.newId
import com.myschedule.app.ui.common.ConfirmDialog
import com.myschedule.app.ui.common.DatePickerDialogM3
import com.myschedule.app.ui.common.SectionCard
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime

private val TimeRegex = Regex("""^([01]\d|2[0-3]):[0-5]\d$""")

@Composable
fun SettingsScreen(store: AppStore) {
    val data by store.data.collectAsState()
    val context = LocalContext.current

    var showStartPicker by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<AppData?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDemo by remember { mutableStateOf(false) }

    // 作息时间本地编辑缓冲
    val periodDrafts = remember(data.periods) {
        mutableStateListOf(*data.periods.map { Pair(it.start, it.end) }.toTypedArray())
    }
    var periodsError by remember { mutableStateOf<String?>(null) }
    fun savePeriods() {
        if (periodDrafts.any { !TimeRegex.matches(it.first) || !TimeRegex.matches(it.second) }) {
            periodsError = "有时间的格式不对,应为 HH:mm(如 08:00)"
            return
        }
        // 检查每节结束晚于开始
        for (i in periodDrafts.indices) {
            val s = LocalTime.parse(periodDrafts[i].first)
            val e = LocalTime.parse(periodDrafts[i].second)
            if (!e.isAfter(s)) {
                periodsError = "第${i + 1}节的结束时间需要晚于开始时间"
                return
            }
        }
        periodsError = null
        store.update { d -> d.copy(periods = periodDrafts.map { ClassPeriod(it.first, it.second) }) }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(store.exportJson().toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "备份已导出", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "导出失败:${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                if (text != null) {
                    pendingImport = Json { ignoreUnknownKeys = true }
                        .decodeFromString(AppData.serializer(), text)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "导入失败,文件格式不正确", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "设置",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // 学期设置
        SectionCard("学期设置") {
            OutlinedTextField(
                value = data.semester.name,
                onValueChange = { v ->
                    store.update { d -> d.copy(semester = d.semester.copy(name = v)) }
                },
                label = { Text("学期名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("开学日期(第一周周一)", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { showStartPicker = true }) {
                    Text(data.semester.startDate.ifBlank { "未设置" })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("总周数", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = {
                    if (data.semester.totalWeeks > 1) {
                        val w = data.semester.totalWeeks - 1
                        store.update { d -> d.copy(semester = d.semester.copy(totalWeeks = w)) }
                    }
                }) { Icon(Icons.Filled.Remove, contentDescription = "减少") }
                Text("${data.semester.totalWeeks}", fontWeight = FontWeight.Bold)
                IconButton(onClick = {
                    if (data.semester.totalWeeks < 30) {
                        val w = data.semester.totalWeeks + 1
                        store.update { d -> d.copy(semester = d.semester.copy(totalWeeks = w)) }
                    }
                }) { Icon(Icons.Filled.Add, contentDescription = "增加") }
            }
        }

        // 作息时间
        SectionCard("作息时间(每天 ${periodDrafts.size} 节)") {
            if (periodsError != null) {
                Text(periodsError!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
            periodDrafts.forEachIndexed { i, p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "第${i + 1}节",
                        Modifier.width(52.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = p.first,
                        onValueChange = { v -> if (v.length <= 5) periodDrafts[i] = Pair(v, p.second) },
                        singleLine = true,
                        isError = !TimeRegex.matches(p.first),
                        modifier = Modifier.weight(1f)
                    )
                    Text(" - ", Modifier.padding(horizontal = 4.dp))
                    OutlinedTextField(
                        value = p.second,
                        onValueChange = { v -> if (v.length <= 5) periodDrafts[i] = Pair(p.first, v) },
                        singleLine = true,
                        isError = !TimeRegex.matches(p.second),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val lastEnd = periodDrafts.lastOrNull()?.second?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
                        val start = lastEnd?.plusMinutes(15L)
                        val end = start?.plusMinutes(45L)
                        periodDrafts.add(
                            Pair(
                                start?.toString() ?: "08:00",
                                end?.toString() ?: "08:45"
                            )
                        )
                    },
                    enabled = periodDrafts.size < 20
                ) { Text("加一节") }
                OutlinedButton(
                    onClick = { if (periodDrafts.size > 4) periodDrafts.removeAt(periodDrafts.size - 1) },
                    enabled = periodDrafts.size > 4
                ) { Text("删末节") }
                Button(onClick = { savePeriods() }, modifier = Modifier.weight(1f)) {
                    Text("保存作息")
                }
            }
            Text(
                "时间格式 HH:mm;课表网格按这里的节次数显示",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 数据管理
        SectionCard("数据管理") {
            Button(onClick = { exportLauncher.launch("我的课程表备份.json") }, modifier = Modifier.fillMaxWidth()) {
                Text("导出备份(JSON)")
            }
            OutlinedButton(onClick = {
                importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            }, modifier = Modifier.fillMaxWidth()) {
                Text("从备份导入")
            }
            OutlinedButton(onClick = { confirmDemo = true }, modifier = Modifier.fillMaxWidth()) {
                Text("载入示例课表")
            }
            OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) {
                Text("清空全部数据", color = MaterialTheme.colorScheme.error)
            }
            Text(
                "所有数据仅保存在手机本机;换机或重装前建议先导出备份",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionCard("关于") {
            Text("我的课程表 v1.0", fontWeight = FontWeight.Bold)
            Text(
                "个人自用的课表应用:周课表、今日课程、待办、考试倒计时、GPA、备份。数据无联网,隐私安全。",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(8.dp))
    }

    if (showStartPicker) {
        DatePickerDialogM3(
            initial = ScheduleLogic.parseDate(data.semester.startDate),
            onDismiss = { showStartPicker = false },
            onSelect = {
                store.update { d -> d.copy(semester = d.semester.copy(startDate = it.toString())) }
                showStartPicker = false
            }
        )
    }

    pendingImport?.let { pd ->
        ConfirmDialog(
            title = "导入备份",
            text = "将导入:${pd.courses.size} 门课程、${pd.todos.size} 条待办、${pd.exams.size} 场考试、${pd.grades.size} 条成绩。\n当前数据将被覆盖,确定继续吗?",
            onConfirm = {
                val normalized = if (pd.semester.startDate.isBlank()) {
                    val today = LocalDate.now()
                    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
                    pd.copy(semester = pd.semester.copy(startDate = monday.toString()))
                } else pd
                store.replaceAll(normalized)
                pendingImport = null
                Toast.makeText(context, "导入成功", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { pendingImport = null }
        )
    }

    if (confirmDemo) {
        ConfirmDialog(
            title = "载入示例课表",
            text = "将用示例课程覆盖当前课表(待办/考试/成绩不变),确定吗?",
            onConfirm = {
                store.update { it.copy(courses = demoCourses()) }
                confirmDemo = false
            },
            onDismiss = { confirmDemo = false }
        )
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "清空全部数据",
            text = "所有课程、待办、考试、成绩都将被删除且无法恢复,确定吗?",
            danger = true,
            onConfirm = {
                val today = LocalDate.now()
                val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
                store.replaceAll(AppData(semester = com.myschedule.app.data.Semester(startDate = monday.toString())))
                confirmClear = false
                Toast.makeText(context, "已清空", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { confirmClear = false }
        )
    }
}

private fun weeks(range: IntRange, parity: Int = 0): List<Int> =
    range.filter { parity == 0 || (parity == 1 && it % 2 == 1) || (parity == 2 && it % 2 == 0) }

private fun course(
    name: String,
    teacher: String,
    location: String,
    color: Int,
    vararg sessions: CourseSession
): Course = Course(id = newId(), name = name, teacher = teacher, location = location, colorIndex = color, sessions = sessions.toList())

private fun demoCourses(): List<Course> = listOf(
    course(
        "高等数学(二)", "王老师", "教一 301", 0,
        CourseSession(1, 1, 2, weeks(1..16)),
        CourseSession(3, 1, 2, weeks(1..16))
    ),
    course(
        "大学英语(四)", "李老师", "外语楼 205", 3,
        CourseSession(2, 3, 4, weeks(1..16, 1))
    ),
    course(
        "数据结构", "张老师", "实验楼 408", 2,
        CourseSession(3, 3, 4, weeks(1..8)),
        CourseSession(5, 3, 4, weeks(9..16))
    ),
    course(
        "数据结构实验", "张老师", "实验楼 410", 4,
        CourseSession(3, 7, 8, weeks(2..16, 2))
    ),
    course(
        "大学物理", "刘老师", "教二 102", 9,
        CourseSession(4, 1, 2, weeks(1..16)),
        CourseSession(4, 3, 4, weeks(1..16, 1))
    ),
    course(
        "体育(篮球)", "陈老师", "东区操场", 5,
        CourseSession(5, 6, 7, weeks(1..16))
    ),
    course(
        "中国近现代史纲要", "赵老师", "教三 210", 7,
        CourseSession(5, 9, 10, weeks(1..13))
    )
)

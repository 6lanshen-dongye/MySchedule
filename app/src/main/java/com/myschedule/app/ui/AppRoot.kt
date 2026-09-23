package com.myschedule.app.ui

import androidx.activity.compose.BackHandler
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.myschedule.app.data.AppStore
import com.myschedule.app.data.Course
import com.myschedule.app.ui.imports.ImportActivity
import com.myschedule.app.ui.schedule.CourseEditScreen
import com.myschedule.app.ui.schedule.ScheduleScreen
import com.myschedule.app.ui.settings.SettingsScreen
import com.myschedule.app.ui.theme.MyScheduleTheme
import com.myschedule.app.ui.today.TodayScreen
import com.myschedule.app.ui.tools.ToolsScreen
import java.time.LocalDate

private data class Tab(val label: String, val icon: ImageVector)

private val Tabs = listOf(
    Tab("课表", Icons.Filled.CalendarMonth),
    Tab("今日", Icons.Filled.Today),
    Tab("工具", Icons.Filled.Apps),
    Tab("设置", Icons.Filled.Settings)
)

@Composable
fun MyScheduleApp(store: AppStore) {
    var tab by remember { mutableStateOf(0) }
    var editorTarget by remember { mutableStateOf<Course?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // 从课表页点某一天跳转到「今日」时携带的日期
    var focusDate by remember { mutableStateOf<LocalDate?>(null) }
    var focusTick by remember { mutableStateOf(0) }

    MyScheduleTheme {
        if (showEditor) {
            // 返回键应回到课表,而不是退出应用
            BackHandler {
                showEditor = false
                editorTarget = null
            }
            CourseEditScreen(
                store = store,
                editing = editorTarget,
                onClose = {
                    showEditor = false
                    editorTarget = null
                }
            )
        } else {
            Scaffold(
                containerColor = Color.White,
                bottomBar = {
                    Column {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                            Tabs.forEachIndexed { i, t ->
                                NavigationBarItem(
                                    selected = tab == i,
                                    onClick = { tab = i },
                                    icon = { Icon(t.icon, contentDescription = t.label) },
                                    label = { Text(t.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                )
                            }
                        }
                    }
                }
            ) { padding ->
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    when (tab) {
                        0 -> ScheduleScreen(
                            store = store,
                            onEditCourse = { course ->
                                editorTarget = course
                                showEditor = true
                            },
                            onImportFromEdu = {
                                context.startActivity(
                                    Intent(context, ImportActivity::class.java)
                                )
                            },
                            onOpenSettings = { tab = 3 }
                        )
                        1 -> TodayScreen(store = store, onNavigate = { tab = it })
                        2 -> ToolsScreen(store)
                        3 -> SettingsScreen(store)
                    }
                }
            }
        }
    }
}

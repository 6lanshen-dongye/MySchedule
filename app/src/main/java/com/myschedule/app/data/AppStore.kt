package com.myschedule.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

/** 数据仓库:内存 StateFlow + 私有目录 JSON 文件,每次修改即落盘 */
class AppStore private constructor(context: Context) {

    private val file = File(context.filesDir, "myschedule.json")

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _data = MutableStateFlow(load())
    val data: StateFlow<AppData> = _data

    fun update(transform: (AppData) -> AppData) {
        _data.value = transform(_data.value)
        persist()
    }

    fun replaceAll(newData: AppData) {
        _data.value = newData
        persist()
    }

    fun exportJson(): String = json.encodeToString(AppData.serializer(), _data.value)

    fun importJson(text: String): AppData {
        val parsed = json.decodeFromString(AppData.serializer(), text)
        _data.value = parsed
        persist()
        return parsed
    }

    private fun load(): AppData {
        val loaded = try {
            json.decodeFromString(AppData.serializer(), file.readText())
        } catch (e: Exception) {
            AppData()
        }
        // 首次启动:学期开始日期默认定为本周周一
        val base = if (loaded.semester.startDate.isBlank()) {
            val today = LocalDate.now()
            val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
            loaded.copy(semester = loaded.semester.copy(startDate = monday.toString()))
        } else {
            loaded
        }
        // 把早期版本抓取时留下的"整格文字当课程名"的脏数据洗干净
        val cleaned = base.copy(
            courses = CourseCleaner.sanitize(base.courses, base.semester.totalWeeks.coerceAtLeast(1))
        )
        if (cleaned != base) persistData(cleaned)
        return cleaned
    }

    private fun persist() = persistData(_data.value)

    private fun persistData(data: AppData) {
        try {
            file.writeText(json.encodeToString(AppData.serializer(), data))
        } catch (e: Exception) {
            // 写盘失败时保留内存数据,下次修改重试
        }
    }

    companion object {
        @Volatile
        private var instance: AppStore? = null

        /** 全局单例:主界面与「教务系统导课」页共用同一份内存数据 */
        fun get(context: Context): AppStore =
            instance ?: synchronized(this) {
                instance ?: AppStore(context.applicationContext).also { instance = it }
            }
    }
}

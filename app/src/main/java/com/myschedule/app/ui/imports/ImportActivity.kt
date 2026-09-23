package com.myschedule.app.ui.imports

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.myschedule.app.data.AppStore
import com.myschedule.app.data.ScheduleLogic
import com.myschedule.app.data.ScrapeResult
import com.myschedule.app.data.TimetableImport
import kotlinx.serialization.json.Json
import org.json.JSONTokener

/**
 * 「教务系统导课」独立页面。
 *
 * 刻意用原生 View 而不是 Compose:在 HyperOS 上 WebView 嵌在 Compose 里时,
 * 门户那种"加载完再渲染"的 SPA 不会上屏(白屏但 DOM 正常),拆成独立 Activity 就正常了。
 */
class ImportActivity : Activity() {

    private val store by lazy { AppStore.get(applicationContext) }
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private lateinit var web: WebView
    private lateinit var urlEdit: EditText
    private lateinit var hint: TextView
    private lateinit var desktopBtn: Button

    private var desktopMode = false
    private var mobileUa = ""
    private var scraping = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        // 记住上次停留的页面(登录态也在),下次直接回到课表查询那一页
        val last = getSharedPreferences("edu_import", MODE_PRIVATE).getString("lastUrl", null)
        val start = if (!last.isNullOrBlank()) last else DEFAULT_PORTAL
        urlEdit.setText(start)
        web.loadUrl(start)
    }

    override fun onPause() {
        super.onPause()
        val u = web.url
        if (!u.isNullOrBlank() && !u.startsWith("about:")) {
            getSharedPreferences("edu_import", MODE_PRIVATE).edit().putString("lastUrl", u).apply()
        }
    }

    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    // ────────────────────────── 界面 ──────────────────────────

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    private fun rounded(bg: Int, radiusDp: Int = 10) = android.graphics.drawable.GradientDrawable().apply {
        setColor(bg)
        cornerRadius = dp(radiusDp).toFloat()
    }

    /** 文字按钮(无底,蓝色/深色字) */
    private fun textButton(label: String, color: Int, sizeSp: Float = 14f, onClick: () -> Unit) =
        Button(this).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            setTextColor(color)
            background = null
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { onClick() }
        }

    /** 实心按钮 */
    private fun solidButton(label: String, bg: Int, fg: Int, sizeSp: Float = 14f, onClick: () -> Unit) =
        Button(this).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            setTextColor(fg)
            background = rounded(bg)
            stateListAnimator = null
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(18), dp(8), dp(18), dp(8))
            setOnClickListener { onClick() }
        }

    /** 浅底胶囊按钮 */
    private fun chipButton(label: String, onClick: () -> Unit) =
        Button(this).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(BRAND)
            background = rounded(0xFFF1F4F9.toInt(), 18)
            stateListAnimator = null
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(14), dp(6), dp(14), dp(6))
            setOnClickListener { onClick() }
        }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        // 标题栏
        val titleBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        titleBar.addView(textButton("✕", INK, 18f) { finish() })
        titleBar.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(this@ImportActivity).apply {
                text = "教务系统导课"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#15171C"))
            })
            addView(TextView(this@ImportActivity).apply {
                text = "登录后在「我的课表」页点抓取课表"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(Color.parseColor("#667085"))
            })
        })
        titleBar.addView(textButton("刷新", BRAND, 13f) { web.reload() })
        root.addView(titleBar)

        // 地址栏
        val urlRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), dp(8))
        }
        urlEdit = EditText(this).apply {
            hint = "学校教务系统网址"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            maxLines = 1
            imeOptions = EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO) {
                    load(urlEdit.text.toString()); true
                } else false
            }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        urlRow.addView(urlEdit)
        urlRow.addView(solidButton("打开", BRAND, Color.WHITE, 13f) {
            load(urlEdit.text.toString())
        })
        root.addView(urlRow)

        // 快捷入口
        val quickRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), 0, dp(12), dp(8))
        }
        quickRow.addView(chipButton("正方课表页(模板)") {
            urlEdit.setText(ZHENGFANG_KB_TEMPLATE)
            load(ZHENGFANG_KB_TEMPLATE)
            toast("把网址里的 example.edu.cn 换成你学校的域名")
        })
        quickRow.addView(chipButton("正方首页(模板)") {
            urlEdit.setText(ZHENGFANG_HOME_TEMPLATE)
            load(ZHENGFANG_HOME_TEMPLATE)
            toast("把网址里的 example.edu.cn 换成你学校的域名")
        })
        desktopBtn = chipButton("桌面版:关") {
            desktopMode = !desktopMode
            desktopBtn.text = if (desktopMode) "桌面版:开" else "桌面版:关"
            web.settings.userAgentString = if (desktopMode) DESKTOP_UA else mobileUa
            web.reload()
        }
        quickRow.addView(desktopBtn)
        root.addView(quickRow)

        // 浏览器
        web = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                useWideViewPort = true
                loadWithOverviewMode = true
                builtInZoomControls = true
                displayZoomControls = false
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(false)
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                // 去掉 "; wv" 标记,免得门户把内置浏览器当成"App 内嵌"走降级分支
                userAgentString = userAgentString.replace("; wv", "")
            }
            mobileUa = settings.userAgentString
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    hint.text = "正在打开…"
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    hint.text = url ?: ""
                    // 把登录态写盘:否则 App 一重启就得重新登录
                    CookieManager.getInstance().flush()
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    hint.text = "打不开:${error?.description ?: "网络错误"}"
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(msg: ConsoleMessage?): Boolean {
                    android.util.Log.d("EduImport", "${msg?.messageLevel()} ${msg?.message()}")
                    return true
                }
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        root.addView(web)

        // 底部
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        hint = TextView(this).apply {
            text = ""
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTextColor(Color.parseColor("#667085"))
            maxLines = 3
            setPadding(dp(12), dp(6), dp(12), 0)
        }
        bottom.addView(hint)
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(10))
        }
        actionRow.addView(solidButton("抓取课表", BRAND, Color.WHITE, 15f) { scrape() })
        bottom.addView(actionRow)
        root.addView(bottom)

        setContentView(root)
    }

    // ────────────────────────── 逻辑 ──────────────────────────

    private fun load(input: String) {
        var u = input.trim()
        if (u.isEmpty()) {
            toast("先填学校教务系统网址,或点上面的快捷入口")
            return
        }
        if (u.any { it.code in 0x3400..0x9FFF }) {
            toast("网址里不能有中文。请填英文域名,例如 jwglxt.xxx.edu.cn")
            return
        }
        u = u.replace(" ", "")
        if (!u.startsWith("http") && !u.startsWith("file:")) u = "https://$u"
        urlEdit.setText(u)
        hint.text = "正在打开…"
        web.loadUrl(u)
    }

    private fun scrape() {
        if (scraping) return
        scraping = true
        hint.text = "正在读取页面…"
        web.evaluateJavascript(SCRAPE_JS) { raw ->
            scraping = false
            try {
                val decoded = JSONTokener(raw ?: "null").nextValue()
                val text = when (decoded) {
                    null -> "null"
                    is String -> decoded
                    else -> decoded.toString()
                }
                val result = json.decodeFromString(ScrapeResult.serializer(), text)
                when {
                    !result.ok -> {
                        hint.text = result.msg
                        dumpPage(result.trace)
                    }
                    result.blocks.isEmpty() -> {
                        hint.text = result.msg.ifBlank { "这一页没有课程数据" }
                        dumpPage(result.trace)
                    }
                    else -> showPreview(result)
                }
            } catch (e: Exception) {
                hint.text = "解析失败:${e.message}"
            }
        }
    }

    /** 抓取失败时把页面里所有表格的结构 + 解析追踪存一份,方便排查 */
    private fun dumpPage(trace: String = "") {
        web.evaluateJavascript(DEBUG_JS) { raw ->
            try {
                val decoded = JSONTokener(raw ?: "null").nextValue()
                val text = when (decoded) {
                    is String -> decoded
                    null -> ""
                    else -> decoded.toString()
                }
                val dir = getExternalFilesDir(null) ?: filesDir
                val content = if (trace.isBlank()) text else (text + "\n\n=== 解析追踪 ===\n" + trace)
                java.io.File(dir, "scrape_debug.txt").writeText(content)
                android.util.Log.d("EduImport", "debug dumped to ${dir.absolutePath}/scrape_debug.txt")
            } catch (e: Exception) {
                android.util.Log.d("EduImport", "dump failed: ${e.message}")
            }
        }
    }

    private fun showPreview(result: ScrapeResult) {
        val totalWeeks = store.data.value.semester.totalWeeks.coerceAtLeast(1)
        val courses = TimetableImport.toCourses(result.blocks, totalWeeks)
        if (courses.isEmpty()) {
            hint.text = "解析到 ${result.blocks.size} 个课程块,但没能组成课程,请检查页面"
            return
        }

        val body = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Color.parseColor("#1B2032"))
            val sb = StringBuilder()
            courses.forEach { c ->
                sb.append("• ").append(c.name).append('\n')
                sb.append("    ")
                    .append(c.sessions.joinToString(" / ") { s ->
                        "周${DAY_NAMES[s.dayOfWeek - 1]} ${s.startPeriod}-${s.endPeriod}节"
                    })
                val extra = listOf(c.teacher, c.location).filter { it.isNotBlank() }
                if (extra.isNotEmpty()) sb.append(" · ").append(extra.joinToString(" · "))
                sb.append('\n')
                val w = c.sessions.firstOrNull()?.weeks?.let { ScheduleLogic.weeksText(it) } ?: ""
                if (w.isNotBlank()) sb.append("    ").append(w).append('\n')
            }
            if (result.times.any { it.contains(":") }) {
                sb.append("\n(检测到作息时间,将一并导入)")
            }
            text = sb.toString()
        }
        val scroll = ScrollView(this).apply {
            setPadding(dp(20), dp(8), dp(20), dp(8))
            addView(body)
        }

        AlertDialog.Builder(this)
            .setTitle("解析到 ${courses.size} 门课 / ${result.blocks.size} 节课")
            .setView(scroll)
            .setPositiveButton("覆盖导入") { _, _ ->
                applyImport(courses, result, append = false)
            }
            .setNeutralButton("追加") { _, _ ->
                applyImport(courses, result, append = true)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun applyImport(
        courses: List<com.myschedule.app.data.Course>,
        result: ScrapeResult,
        append: Boolean
    ) {
        store.update { d ->
            d.copy(
                courses = if (append) d.courses + courses else courses,
                periods = TimetableImport.applyTimes(d.periods, result.times)
            )
        }
        Toast.makeText(this, "已导入 ${courses.size} 门课", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun toast(msg: String) {
        hint.text = msg
    }

    companion object {
        private val DAY_NAMES = listOf("一", "二", "三", "四", "五", "六", "日")

        /**
         * 通用正方教务系统入口模板。
         * `example.edu.cn` 是 RFC 保留的示例域名,只是个占位 ——
         * 用的时候把它换成你学校的域名即可(填过一次会记住,下次直接打开)。
         */
        const val ZHENGFANG_KB_TEMPLATE =
            "https://jwglxt.example.edu.cn/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html" +
                    "?gnmkdm=N253508&layout=default"
        const val ZHENGFANG_HOME_TEMPLATE = "https://jwglxt.example.edu.cn/jwglxt/"
        const val DEFAULT_PORTAL = ZHENGFANG_KB_TEMPLATE

        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/126.0.0.0 Safari/537.36"

        private val BRAND = 0xFF3B7CF0.toInt()
        private val INK = 0xFF15171C.toInt()
    }
}

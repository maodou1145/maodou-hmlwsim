package com.maodou.hmlwrunner.runtime

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * 全局运行态：包列表 / 设备配置 / 解释器事件 / 原生硬件。
 * 与 WebView 生命周期解耦，页面切换不会重启正在解释执行的应用。
 */
class RuntimeController(app: Application) : AndroidViewModel(app) {

    private val appContext: Application = app
    private val prefs = Prefs(app)
    private val main = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var webReady = false
    private var configApplied = false
    private var pendingRun: PackageInfo? = null
    private var lastBatteryLevel = 0.85f
    private var lastBatteryCharging = false
    private var polling = true
    private val chunkBuffers = mutableMapOf<String, MutableMap<Int, String>>()
    private val chunkTargets = mutableMapOf<String, Pair<String, Int>>()

    // ---------------------------------------------------------------- 可观察状态
    var phase by mutableStateOf(Phase.IDLE); private set
    var packages by mutableStateOf(prefs.loadPackages()); private set
    var selected by mutableStateOf(packages.firstOrNull()); private set
    var config by mutableStateOf(prefs.loadConfig()); private set
    var logs by mutableStateOf(listOf<LogEntry>()); private set
    var errors by mutableStateOf(listOf<RunError>()); private set
    var wearMessages by mutableStateOf(listOf<WearMessage>()); private set
    var runtimeState by mutableStateOf<RuntimeState?>(null); private set
    var lastResult by mutableStateOf<PackageResult?>(null); private set
    var progress by mutableStateOf(""); private set
    var toast by mutableStateOf(""); private set
    var batteryText by mutableStateOf("—"); private set
    var feedbackConfig by mutableStateOf(prefs.loadFeedbackConfig()); private set
    var feedbackStatus by mutableStateOf(FeedbackStatus()); private set

    private val hardware = NativeHardware(
        app,
        onBattery = { level, charging ->
            lastBatteryLevel = level
            lastBatteryCharging = charging
            main.post {
                batteryText = String.format(Locale.getDefault(), "%.0f%%%s",
                    level * 100, if (charging) " ⚡" else "")
                dispatch("pushBattery", JSONObject().put("level", level.toDouble()).put("charging", charging))
            }
        },
        onSensor = { type, x, y, z ->
            main.post {
                dispatch("pushSensor", JSONObject()
                    .put("type", type)
                    .put("x", x.toDouble()).put("y", y.toDouble()).put("z", z.toDouble()))
            }
        },
    )


    init {
        hardware.start()
        feedbackStatus = feedbackStatus.copy(systemVolume = hardware.systemVolume())
        startPolling()
    }

    override fun onCleared() {
        super.onCleared()
        polling = false
        hardware.stop()
        try { webView?.destroy() } catch (e: Exception) { }
        webView = null
    }

    // ---------------------------------------------------------------- WebView
    fun getOrCreateWebView(context: Context): WebView {
        webView?.let { return it }
        val view = WebRuntimeView.create(context, this)
        webView = view
        return view
    }

    private var lastAttachRefresh = 0L

    fun onWebViewAttached(view: WebView) {
        webView = view
        if (webReady && !configApplied) {
            applyConfigInternal()
            configApplied = true
        }
        // WebView 在页签间 detach/attach 后，系统合成层有时不自动重绘 → 画面全黑。
        // 重新附加时主动 requestLayout + invalidate，并让网页按最新视口重算表盘缩放。
        val now = System.currentTimeMillis()
        if (now - lastAttachRefresh < 250L) return
        lastAttachRefresh = now
        view.post {
            try {
                view.requestLayout()
                view.invalidate()
                view.evaluateJavascript(
                    "if(window.HmlwMobile&&window.HmlwMobile.updateScale){window.HmlwMobile.updateScale();}",
                    null
                )
            } catch (e: Exception) { }
        }
        view.postDelayed({
            try { view.invalidate() } catch (e: Exception) { }
        }, 150L)
    }

    private fun dispatch(cmd: String, payload: JSONObject) {
        val view = webView ?: return
        val script = "if(window.HmlwMobile){HmlwMobile.dispatch(" +
            JSONObject.quote(cmd) + "," + JSONObject.quote(payload.toString()) + ");}"
        try {
            view.evaluateJavascript(script, null)
        } catch (e: Exception) {
            addError("JS_DISPATCH", "命令 $cmd 下发失败：${e.message}", "")
        }
    }

    private fun startPolling() {
        val task = object : Runnable {
            override fun run() {
                if (!polling) return
                if (webReady) {
                    dispatch("readState", JSONObject())
                    dispatch("pushBattery", JSONObject()
                        .put("level", lastBatteryLevel.toDouble())
                        .put("charging", lastBatteryCharging))
                }
                main.postDelayed(this, 2000L)
            }
        }
        main.postDelayed(task, 2000L)
    }

    // ---------------------------------------------------------------- 包导入
    fun importPackage(uri: Uri, displayName: String?) {
        phase = Phase.INSPECTING
        progress = "正在导入并识别目标包…"
        Thread {
            val result = PackageInspector.import(appContext, uri, displayName)
            main.post {
                val info = result.info
                if (info == null) {
                    phase = Phase.ERROR
                    progress = ""
                    addError(result.code, result.message, result.detail)
                    toast = result.message
                } else {
                    val next = (listOf(info) + packages.filter { it.id != info.id }).take(12)
                    packages = next
                    prefs.savePackages(next)
                    selected = info
                    phase = Phase.READY
                    progress = ""
                    if (info.kind == PackageKind.UNKNOWN || info.warning.isNotEmpty()) {
                        toast = "已导入 ${info.kind.label}，但存在提示：${info.warning.ifEmpty { "结构未识别" }}"
                        addLog("warn", info.warning.ifEmpty { "包结构未识别，运行时会给出明确错误" })
                    } else {
                        toast = "已导入 ${info.kind.label}：${info.bundleName}"
                    }
                    addLog("info", "导入 ${info.fileName} -> ${info.kind.label}${if (info.signed) "（含签名信息）" else ""}")
                }
            }
        }.start()
    }

    fun selectPackage(info: PackageInfo) {
        selected = info
        phase = Phase.READY
    }

    fun removePackage(info: PackageInfo) {
        try { File(appContext.filesDir, info.relativePath).delete() } catch (e: Exception) { }
        val next = packages.filter { it.id != info.id }
        packages = next
        prefs.savePackages(next)
        if (selected?.id == info.id) {
            selected = next.firstOrNull()
            if (next.isEmpty()) phase = Phase.IDLE
        }
        toast = "已移除 ${info.fileName}"
    }

    // ---------------------------------------------------------------- 运行
    fun runSelected() {
        val info = selected ?: run {
            toast = "请先导入并选择目标包"
            return
        }
        phase = Phase.LOADING
        lastResult = null
        progress = "准备运行 ${info.fileName}…"
        errors = emptyList()
        if (webReady) {
            dispatchLoad(info)
        } else {
            pendingRun = info
            addLog("info", "解释器就绪后将自动运行 ${info.fileName}")
        }
    }

    private fun dispatchLoad(info: PackageInfo) {
        dispatch("applyConfig", config.toJson())
        dispatch("loadPackage", JSONObject()
            .put("url", info.webUrl)
            .put("fileName", info.fileName)
            .put("typeHint", info.kind.name))
    }

    fun action(name: String, extras: Map<String, Any> = emptyMap()) {
        val o = JSONObject().put("name", name)
        extras.forEach { (k, v) -> o.put(k, v) }
        dispatch("runAction", o)
    }

    // ---------------------------------------------------------------- 配置
    fun updateConfig(next: DeviceConfig) {
        config = next
        prefs.saveConfig(next)
        if (webReady) dispatch("applyConfig", next.toJson())
    }

    fun choosePreset(key: String) {
        val p = DeviceConfig.preset(key) ?: return
        updateConfig(config.copy(width = p.width, height = p.height, screenShape = p.shape, activePreset = key))
        if (webReady) action("preset", mapOf("value" to key))
        toast = "已应用预设 ${p.label} (${p.width}×${p.height})"
    }

    private fun applyConfigInternal() {
        dispatch("applyConfig", config.toJson())
        dispatch("pushBattery", JSONObject()
            .put("level", lastBatteryLevel.toDouble())
            .put("charging", lastBatteryCharging))
    }

    fun requestState() {
        if (webReady) dispatch("readState", JSONObject())
    }

    // ------------------------------------------------------- 音频 / 震动反馈
    fun setFeedbackEnabled(sound: Boolean? = null, haptics: Boolean? = null) {
        val next = feedbackConfig.copy(
            soundEnabled = sound ?: feedbackConfig.soundEnabled,
            hapticsEnabled = haptics ?: feedbackConfig.hapticsEnabled,
        )
        feedbackConfig = next
        prefs.saveFeedbackConfig(next)
        pushFeedbackConfig()
        feedbackStatus = feedbackStatus.copy(systemVolume = hardware.systemVolume())
    }

    private fun pushFeedbackConfig() {
        if (!webReady) return
        dispatch("audioConfig", JSONObject()
            .put("systemVolume", hardware.systemVolume().toDouble())
            .put("soundEnabled", feedbackConfig.soundEnabled)
            .put("hapticsEnabled", feedbackConfig.hapticsEnabled))
    }

    /** 配置页测试入口：tone / short / long。 */
    fun testFeedback(kind: String) {
        feedbackStatus = feedbackStatus.copy(systemVolume = hardware.systemVolume())
        when (kind) {
            "tone" -> {
                if (!feedbackConfig.soundEnabled) {
                    toast = "真机声音反馈已关闭"
                    return
                }
                hardware.playTone()
                feedbackStatus = feedbackStatus.copy(lastToneAt = System.currentTimeMillis())
                addLog("info", "[音频] 测试提示音")
            }
            "short" -> doVibrate("short", 35L)
            "long" -> doVibrate("long", 400L)
        }
    }

    private fun doVibrate(mode: String, ms: Long) {
        if (!feedbackConfig.hapticsEnabled) {
            addLog("warn", "[触觉] 已关闭，忽略 $mode 震动")
            return
        }
        hardware.vibrate(ms)
        feedbackStatus = feedbackStatus.copy(
            vibrateMode = mode,
            vibrateCount = feedbackStatus.vibrateCount + 1,
            lastVibrateAt = System.currentTimeMillis(),
        )
        addLog("info", "[触觉] $mode · ${ms}ms")
    }

    // ---------------------------------------------------------------- 桥事件
    fun onBridgeEvent(type: String, raw: String) {
        val o = try { JSONObject(raw) } catch (e: Exception) { JSONObject() }
        when (type) {
            "chunk" -> handleChunk(o)
            "ready" -> {
                webReady = true
                val libs = o.optJSONObject("libs") ?: JSONObject()
                addLog("info", "解释器就绪：JSZip=${libs.optBoolean("jszip")} " +
                    "html2canvas=${libs.optBoolean("html2canvas")} qrcode=${libs.optBoolean("qrcode")}")
                applyConfigInternal()
                configApplied = true
                pushFeedbackConfig()
                // 页面每次重新就绪（冷启动 / 进程重建 / WebView 重载）都恢复当前选中的包，
                // 保证「打开预览就能看到应用」，而不是空表盘。
                val target = pendingRun ?: selected
                pendingRun = null
                if (target != null) {
                    phase = Phase.LOADING
                    dispatchLoad(target)
                }
            }
            "log" -> addLog(o.optString("level", "info"), o.optString("text", ""))
            "progress" -> {
                progress = o.optString("message", "")
                addLog("info", o.optString("message", ""))
            }
            "error" -> {
                addError(
                    o.optString("code", "RUNTIME"),
                    o.optString("message", "运行异常"),
                    o.optString("detail", ""),
                    o.optString("stack", ""),
                    o.optString("source", ""),
                )
            }
            "packageResult" -> {
                val ok = o.optBoolean("ok", false)
                val result = PackageResult(
                    ok = ok,
                    code = o.optString("code"),
                    message = o.optString("message"),
                    detail = o.optString("detail"),
                    stack = o.optString("stack"),
                    stage = o.optString("stage"),
                    info = o.optJSONObject("info"),
                )
                lastResult = result
                progress = ""
                if (ok) {
                    phase = Phase.RUNNING
                    toast = result.message
                    addLog("info", result.message)
                    val sandboxError = o.optString("sandboxError", "")
                    if (sandboxError.isNotEmpty()) {
                        addError("SANDBOX", "应用脚本执行报错：$sandboxError", "")
                    }
                } else {
                    phase = Phase.ERROR
                    toast = result.message
                    addError(result.code, result.message, result.detail, result.stack, result.stage)
                    addLog("error", result.message)
                }
            }
            "state" -> runtimeState = try { RuntimeState.parse(o) } catch (e: Exception) { runtimeState }
            "wearLog" -> {
                val msg = WearMessage(System.currentTimeMillis(),
                    o.optString("direction", "sent"), o.optString("text", ""))
                wearMessages = (wearMessages + msg).takeLast(100)
            }
            "vibrate" -> {
                val mode = o.optString("mode", "long")
                doVibrate(mode, o.optLong("durationMs", if (mode == "short") 35L else 400L))
            }
            "vibrateStop" -> {
                hardware.cancelVibrate()
                addLog("info", "[触觉] stop")
            }
            "audioState" -> {
                val event = o.optString("event", "")
                feedbackStatus = feedbackStatus.copy(
                    audioState = o.optString("state", "stop"),
                    audioSrc = o.optString("src", ""),
                    audioVolume = o.optDouble("volume", 0.0).toFloat(),
                    systemVolume = o.optDouble("systemVolume", 0.0).toFloat(),
                    lastEvent = event,
                )
                if (event != "timeupdate" && event != "config") {
                    addLog("info", "[音频] $event · state=${o.optString("state", "stop")}" +
                        (if (o.optString("src", "").isNotEmpty()) " · ${o.optString("src", "")}" else ""))
                }
            }
            "audioFeedback" -> {
                if (feedbackConfig.soundEnabled) {
                    hardware.playTone()
                    feedbackStatus = feedbackStatus.copy(lastToneAt = System.currentTimeMillis())
                    addLog("info", "[音频] 兜底提示音（${o.optString("reason", "")}）")
                }
            }
            "screenshot" -> handleScreenshot(o)
            "pong" -> Unit
            else -> addLog("info", "[$type] $raw".take(240))
        }
    }

    /** 重组 JS 侧分片的大事件（截图 dataURL） */
    private fun handleChunk(o: JSONObject) {
        val id = o.optString("id")
        val targetType = o.optString("type")
        val index = o.optInt("index", -1)
        val total = o.optInt("total", 0)
        val data = o.optString("data", "")
        if (id.isEmpty() || index < 0 || total <= 0) return
        val buffer = chunkBuffers.getOrPut(id) { mutableMapOf() }
        buffer[index] = data
        chunkTargets[id] = targetType to total
        if (buffer.size >= total) {
            val joined = (0 until total).joinToString("") { buffer[it] ?: "" }
            chunkBuffers.remove(id)
            chunkTargets.remove(id)
            onBridgeEvent(targetType, joined)
        }
    }

    fun onWebViewError(code: Int, description: String, failingUrl: String) {
        addError("WEBVIEW_$code", "页面资源加载失败：$description", failingUrl)
        if (failingUrl.contains("/assets/www/")) {
            phase = Phase.ERROR
            toast = "离线解释器页面加载失败，请检查 assets 完整性"
        }
    }

    // ---------------------------------------------------------------- 截图
    private fun handleScreenshot(o: JSONObject) {
        val action = o.optString("action", "save")
        val dataUrl = o.optString("dataUrl", "")
        val jsError = o.optString("error", "")
        Thread {
            val bitmap = if (dataUrl.isNotEmpty()) {
                ScreenshotHelper.decodeDataUrl(dataUrl)
            } else {
                // html2canvas 对 shadow DOM 可能失效，用 WebView.draw 兜底
                webView?.let { ScreenshotHelper.captureWebView(it) }
            }
            main.post {
                if (bitmap == null) {
                    addError("SCREENSHOT", "截图失败：${jsError.ifEmpty { "无法生成位图" }}", "")
                    toast = "截图失败"
                    return@post
                }
                if (action == "save") {
                    val where = ScreenshotHelper.saveToGallery(appContext, bitmap)
                    toast = "截图已保存：$where"
                    addLog("info", "截图已保存：$where")
                } else {
                    val file = ScreenshotHelper.writePng(appContext, bitmap)
                    val ok = ScreenshotHelper.copyToClipboard(appContext, file)
                    toast = if (ok) "截图已复制到剪贴板（图片 URI）" else "剪贴板写入失败，已存到 ${file.name}"
                    addLog("info", toast)
                }
            }
        }.start()
    }

    // ---------------------------------------------------------------- 日志
    private fun addLog(level: String, text: String) {
        if (text.isBlank()) return
        logs = (logs + LogEntry(System.currentTimeMillis(), level, text)).takeLast(400)
    }

    private fun addError(code: String, message: String, detail: String, stack: String = "", source: String = "") {
        errors = (errors + RunError(System.currentTimeMillis(), code, message, detail, stack, source)).takeLast(80)
    }

    fun clearLogs() {
        logs = emptyList()
        errors = emptyList()
        if (webReady) {
            action("clear_log")
            action("clear_wear_log")
        }
    }

    fun consumeToast() { toast = "" }
}

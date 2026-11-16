package com.maodou.hmlwrunner.runtime

import org.json.JSONObject
import java.util.Locale

/** 三类输入 + 兜底未知类型。signed.bin 与未签名 HAP 都按「APP 同等处理」执行。 */
enum class PackageKind(val label: String, val hint: String) {
    APP("APP 包", "外层 ZIP（含 .hap / pack.info），原版解包器原生支持"),
    SIGNED_BIN("signed.bin", "签名后的 HAP 单文件，剥离签名头后按 HAP 解析"),
    HAP("未签名 HAP", "直接含 config.json 的 HAP，按 APP 包同等处理"),
    UNKNOWN("未知格式", "无法识别，需要检查文件是否损坏或选错"),
}

/** 真机音频 / 震动反馈开关，device 配置页可调并持久化。 */
data class FeedbackConfig(
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("soundEnabled", soundEnabled)
        .put("hapticsEnabled", hapticsEnabled)

    companion object {
        fun fromJson(o: JSONObject) = FeedbackConfig(
            soundEnabled = o.optBoolean("soundEnabled", true),
            hapticsEnabled = o.optBoolean("hapticsEnabled", true),
        )
    }
}

/** 最近一次 @system.audio / @system.vibrator 调用的状态回读。 */
data class FeedbackStatus(
    val audioState: String = "stop",
    val audioSrc: String = "",
    val audioVolume: Float = 0f,
    val systemVolume: Float = 0f,
    val lastEvent: String = "",
    val vibrateMode: String = "—",
    val vibrateCount: Int = 0,
    val lastVibrateAt: Long = 0L,
    val lastToneAt: Long = 0L,
) {
    val volumeText: String
        get() = String.format(Locale.getDefault(), "%.0f%%", audioVolume * 100)
    val systemVolumeText: String
        get() = String.format(Locale.getDefault(), "%.0f%%", systemVolume * 100)
    val lastVibrateText: String
        get() = if (lastVibrateAt == 0L) "—" else
            "$vibrateMode · " + java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                .format(java.util.Date(lastVibrateAt))
}

data class PackageInfo(
    val id: String,
    val fileName: String,
    val relativePath: String,
    val sizeBytes: Long,
    val kind: PackageKind,
    val signed: Boolean,
    val bundleName: String,
    val version: String,
    val deviceType: String,
    val pages: List<String>,
    val entryCount: Int,
    val layers: List<String>,
    val importedAt: Long,
    val warning: String = "",
) {
    /** WebViewAssetLoader 的同源 URL，JS 侧 fetch() 直接拿到 ArrayBuffer */
    val webUrl: String
        get() = "https://appassets.androidplatform.net/files/$relativePath"

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("fileName", fileName)
        put("relativePath", relativePath)
        put("sizeBytes", sizeBytes)
        put("kind", kind.name)
        put("signed", signed)
        put("bundleName", bundleName)
        put("version", version)
        put("deviceType", deviceType)
        put("pages", org.json.JSONArray(pages))
        put("entryCount", entryCount)
        put("layers", org.json.JSONArray(layers))
        put("importedAt", importedAt)
        put("warning", warning)
    }

    companion object {
        fun fromJson(o: JSONObject): PackageInfo {
            val pagesArr = o.optJSONArray("pages") ?: org.json.JSONArray()
            val layersArr = o.optJSONArray("layers") ?: org.json.JSONArray()
            val kindName = o.optString("kind", PackageKind.UNKNOWN.name)
            return PackageInfo(
                id = o.optString("id"),
                fileName = o.optString("fileName"),
                relativePath = o.optString("relativePath"),
                sizeBytes = o.optLong("sizeBytes"),
                kind = runCatching { PackageKind.valueOf(kindName) }.getOrDefault(PackageKind.UNKNOWN),
                signed = o.optBoolean("signed"),
                bundleName = o.optString("bundleName", "unknown"),
                version = o.optString("version", "1.0.0"),
                deviceType = o.optString("deviceType", "liteWearable"),
                pages = (0 until pagesArr.length()).map { pagesArr.optString(it) },
                entryCount = o.optInt("entryCount"),
                layers = (0 until layersArr.length()).map { layersArr.optString(it) },
                importedAt = o.optLong("importedAt"),
                warning = o.optString("warning", ""),
            )
        }
    }
}

data class DevicePreset(
    val key: String,
    val label: String,
    val width: Int,
    val height: Int,
    val shape: String,
)

/** 与 hmlwsim 网页版 device 配置页 1:1 对应的全部可配置项 */
data class DeviceConfig(
    val screenShape: String = "circle",
    val width: Int = 466,
    val height: Int = 466,
    val activePreset: String = "gt3",
    val useSystemBattery: Boolean = true,
    val batteryPercent: Int = 85,
    val sensorReal: Map<String, Boolean> = mapOf(
        "accelerometer" to true,
        "gyroscope" to true,
        "compass" to true,
        "barometer" to false,
    ),
    val stepCount: Int = 0,
    val heartRate: Int = 72,
    val heartRateInvalid: Boolean = true,
    val onBody: Boolean = false,
    val invertScroll: Boolean = false,
    val crownSensitivity: Float = 1f,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("screenShape", screenShape)
        put("width", width)
        put("height", height)
        put("activePreset", activePreset)
        put("useSystemBattery", useSystemBattery)
        put("batteryPercent", batteryPercent)
        put("sensorReal", JSONObject().apply {
            sensorReal.forEach { (k, v) -> put(k, v) }
        })
        put("stepCount", stepCount)
        put("heartRate", heartRate)
        put("heartRateInvalid", heartRateInvalid)
        put("onBody", onBody)
        put("invertScroll", invertScroll)
        put("crownSensitivity", crownSensitivity.toDouble())
    }

    companion object {
        /** 与 js/sensor-panel.js 的 presets 完全一致 */
        val PRESETS = listOf(
            DevicePreset("fit2", "FIT2", 336, 480, "rect"),
            DevicePreset("gt3", "GT3+", 466, 466, "circle"),
            DevicePreset("fit3", "FIT3+", 408, 480, "rect"),
            DevicePreset("gt2-42", "GT2 (42mm)", 390, 390, "circle"),
            DevicePreset("gt2-46", "GT2 (46mm)", 454, 454, "circle"),
            DevicePreset("watchd", "WATCH D", 280, 456, "rect"),
        )

        fun fromJson(o: JSONObject): DeviceConfig {
            val real = o.optJSONObject("sensorReal") ?: JSONObject()
            return DeviceConfig(
                screenShape = o.optString("screenShape", "circle"),
                width = o.optInt("width", 466),
                height = o.optInt("height", 466),
                activePreset = o.optString("activePreset", "gt3"),
                useSystemBattery = o.optBoolean("useSystemBattery", true),
                batteryPercent = o.optInt("batteryPercent", 85),
                sensorReal = mapOf(
                    "accelerometer" to real.optBoolean("accelerometer", true),
                    "gyroscope" to real.optBoolean("gyroscope", true),
                    "compass" to real.optBoolean("compass", true),
                    "barometer" to real.optBoolean("barometer", false),
                ),
                stepCount = o.optInt("stepCount", 0),
                heartRate = o.optInt("heartRate", 72),
                heartRateInvalid = o.optBoolean("heartRateInvalid", true),
                onBody = o.optBoolean("onBody", false),
                invertScroll = o.optBoolean("invertScroll", false),
                crownSensitivity = o.optDouble("crownSensitivity", 1.0).toFloat(),
            )
        }

        fun preset(key: String): DevicePreset? = PRESETS.find { it.key == key }
    }
}

data class LogEntry(val time: Long, val level: String, val text: String)

data class RunError(
    val time: Long,
    val code: String,
    val message: String,
    val detail: String = "",
    val stack: String = "",
    val source: String = "",
)

data class WearMessage(val time: Long, val direction: String, val text: String)

data class PackageResult(
    val ok: Boolean,
    val code: String,
    val message: String,
    val detail: String = "",
    val stack: String = "",
    val stage: String = "",
    val info: JSONObject? = null,
)

enum class Phase { IDLE, INSPECTING, READY, LOADING, RUNNING, ERROR }

/** 运行期状态回读（来自 HmlwMobile.readState()），用于 device 配置页的状态展示 */
data class RuntimeState(
    val updatedAt: Long,
    val shape: String,
    val width: Int,
    val height: Int,
    val batteryPercent: Int,
    val batteryLevel: Float,
    val charging: Boolean,
    val useSystemBattery: Boolean,
    val stepCount: Int,
    val heartRate: Int,
    val heartRateInvalid: Boolean,
    val onBody: Boolean,
    val invertScroll: Boolean,
    val sensorReal: Map<String, Boolean>,
    val sensorLines: List<Pair<String, String>>,
    val deviceLines: List<Pair<String, String>>,
    val brightnessMode: Int,
    val page: String,
    val appName: String,
    val bundleName: String,
    val phoneConnected: Boolean,
    val peerName: String,
    val vfsBundle: String,
    val vfsCount: Int,
    val libs: Map<String, Boolean>,
) {
    companion object {
        private fun vec(o: JSONObject?): String {
            if (o == null) return "—"
            return String.format(
                Locale.getDefault(), "x %.2f  y %.2f  z %.2f",
                o.optDouble("x", 0.0), o.optDouble("y", 0.0), o.optDouble("z", 0.0)
            )
        }

        private fun scalar(o: JSONObject?, key: String, unit: String = ""): String {
            if (o == null) return "—"
            val v = o.optDouble(key, 0.0)
            return String.format(Locale.getDefault(), "%.1f%s", v, unit)
        }

        fun parse(json: JSONObject): RuntimeState {
            val dom = json.optJSONObject("dom") ?: JSONObject()
            val sensors = json.optJSONObject("sensors") ?: JSONObject()
            val device = json.optJSONObject("device") ?: JSONObject()
            val battery = json.optJSONObject("battery") ?: JSONObject()
            val phone = json.optJSONObject("phoneLink") ?: JSONObject()
            val peer = phone.optJSONObject("peer") ?: JSONObject()
            val app = json.optJSONObject("app") ?: JSONObject()
            val vfs = json.optJSONObject("vfs") ?: JSONObject()
            val libs = json.optJSONObject("libs") ?: JSONObject()
            val real = dom.optJSONObject("sensorReal") ?: JSONObject()

            val sensorLines = listOf(
                "线性加速度" to vec(sensors.optJSONObject("accelerometer")),
                "角速度" to vec(sensors.optJSONObject("gyroscope")),
                "磁场" to vec(sensors.optJSONObject("compass")),
                "气压" to scalar(sensors.optJSONObject("barometer"), "pressure", " hPa"),
                "计步" to scalar(sensors.optJSONObject("stepCounter"), "steps", " 步"),
                "心率" to scalar(sensors.optJSONObject("heartRate"), "heartRate", " bpm"),
                "佩戴状态" to (if ((sensors.optJSONObject("onBodyState") ?: JSONObject())
                        .optBoolean("value", false)) "已佩戴" else "未佩戴"),
            )

            val deviceLines = listOf(
                "屏幕形状" to device.optString("screenShape", "circle"),
                "窗口尺寸" to "${device.optInt("windowWidth", 466)} × ${device.optInt("windowHeight", 466)}",
                "屏幕密度" to "${device.optInt("screenDensity", 96)} dpi",
                "设备类型" to device.optString("deviceType", "liteWearable"),
                "品牌 / 型号" to "${device.optString("brand", "-")} / ${device.optString("model", "-")}",
                "语言 / 地区" to "${device.optString("language", "-")} / ${device.optString("region", "-")}",
                "API 版本" to (if (device.isNull("apiVersion")) "Infinity" else device.optString("apiVersion")),
            )

            return RuntimeState(
                updatedAt = json.optLong("time", System.currentTimeMillis()),
                shape = dom.optString("shape", "circle"),
                width = dom.optInt("width", 466),
                height = dom.optInt("height", 466),
                batteryPercent = dom.optInt("batteryPercent", 0),
                batteryLevel = battery.optDouble("level", 0.85).toFloat(),
                charging = battery.optBoolean("charging", false),
                useSystemBattery = dom.optBoolean("useSystemBattery", true),
                stepCount = dom.optInt("stepCount", 0),
                heartRate = dom.optInt("heartRate", 72),
                heartRateInvalid = dom.optBoolean("heartRateInvalid", true),
                onBody = dom.optBoolean("onBody", false),
                invertScroll = dom.optBoolean("invertScroll", false),
                sensorReal = mapOf(
                    "accelerometer" to real.optBoolean("accelerometer", true),
                    "gyroscope" to real.optBoolean("gyroscope", true),
                    "compass" to real.optBoolean("compass", true),
                    "barometer" to real.optBoolean("barometer", false),
                ),
                sensorLines = sensorLines,
                deviceLines = deviceLines,
                brightnessMode = json.optInt("brightnessMode", 1),
                page = json.optString("page", ""),
                appName = app.optString("appName", ""),
                bundleName = app.optString("bundleName", ""),
                phoneConnected = phone.optBoolean("connected", false),
                peerName = peer.optString("mDeviceName", ""),
                vfsBundle = vfs.optString("bundleName", ""),
                vfsCount = vfs.optInt("fileCount", 0),
                libs = mapOf(
                    "JSZip" to libs.optBoolean("jszip", false),
                    "html2canvas" to libs.optBoolean("html2canvas", false),
                    "qrcode" to libs.optBoolean("qrcode", false),
                ),
            )
        }
    }
}

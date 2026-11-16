package com.maodou.hmlwrunner.runtime

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 轻量持久化：设备配置 + 最近导入的包列表。避免引入 Room / DataStore 等额外依赖。 */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("hmlw_runner", Context.MODE_PRIVATE)

    fun loadConfig(): DeviceConfig {
        val raw = sp.getString(KEY_CONFIG, null) ?: return DeviceConfig()
        return try {
            DeviceConfig.fromJson(JSONObject(raw))
        } catch (e: Exception) {
            DeviceConfig()
        }
    }

    fun saveConfig(config: DeviceConfig) {
        sp.edit().putString(KEY_CONFIG, config.toJson().toString()).apply()
    }

    fun loadPackages(): List<PackageInfo> {
        val raw = sp.getString(KEY_PACKAGES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                runCatching { PackageInfo.fromJson(arr.optJSONObject(i) ?: JSONObject()) }.getOrNull()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun savePackages(list: List<PackageInfo>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        sp.edit().putString(KEY_PACKAGES, arr.toString()).apply()
    }

    fun loadFeedbackConfig(): FeedbackConfig {
        val raw = sp.getString(KEY_FEEDBACK, null) ?: return FeedbackConfig()
        return try {
            FeedbackConfig.fromJson(JSONObject(raw))
        } catch (e: Exception) {
            FeedbackConfig()
        }
    }

    fun saveFeedbackConfig(config: FeedbackConfig) {
        sp.edit().putString(KEY_FEEDBACK, config.toJson().toString()).apply()
    }

    private companion object {
        const val KEY_CONFIG = "device_config"
        const val KEY_PACKAGES = "packages"
        const val KEY_FEEDBACK = "feedback_config"
    }
}

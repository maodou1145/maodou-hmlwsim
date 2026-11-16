package com.maodou.hmlwrunner.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat

/**
 * Android 硬件 -> 网页版浏览器 API 的数据源。
 * 电池喂给 navigator.getBattery() polyfill；传感器喂给 devicemotion /
 * Magnetometer / PressureSensor polyfill；震动作为 system.vibrator 的附加反馈。
 */
class NativeHardware(
    private val context: Context,
    private val onBattery: (Float, Boolean) -> Unit,
    private val onSensor: (String, Float, Float, Float) -> Unit,
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val lastEmitAt = mutableMapOf<String, Long>()
    private var started = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            dispatchBattery(intent)
        }
    }

    fun start() {
        if (started) return
        started = true
        try {
            ContextCompat.registerReceiver(
                context,
                batteryReceiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        } catch (e: Exception) {
            // 部分 ROM 对 sticky 广播注册有限制，退化为一次性读取
        }
        readBatteryOnce()

        val linear = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val sensors = listOfNotNull(
            linear ?: accel,
            sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
            sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
            sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE),
        )
        sensors.forEach { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stop() {
        if (!started) return
        started = false
        try { context.unregisterReceiver(batteryReceiver) } catch (e: Exception) { }
        try { sensorManager?.unregisterListener(this) } catch (e: Exception) { }
    }

    private fun readBatteryOnce() {
        try {
            val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            dispatchBattery(sticky)
        } catch (e: Exception) {
            // 忽略：不影响解释器运行
        }
    }

    private fun dispatchBattery(intent: Intent?) {
        if (intent == null) return
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        if (level >= 0 && scale > 0) onBattery(level.toFloat() / scale, charging)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val type = when (event.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_ACCELEROMETER -> "accelerometer"
            Sensor.TYPE_GYROSCOPE -> "gyroscope"
            Sensor.TYPE_MAGNETIC_FIELD -> "compass"
            Sensor.TYPE_PRESSURE -> "barometer"
            else -> return
        }
        val now = System.currentTimeMillis()
        if (now - (lastEmitAt[type] ?: 0L) < 60L) return
        lastEmitAt[type] = now
        val v = event.values
        onSensor(type, v.getOrElse(0) { 0f }, v.getOrElse(1) { 0f }, v.getOrElse(2) { 0f })
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    @Suppress("DEPRECATION")
    private fun systemVibrator(): Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    } catch (e: Exception) {
        null
    }

    /**
     * @system.vibrator 的 short / long 模式：35ms / 400ms，可传任意时长。
     *
     * 关键：必须显式指定 VibrationAttributes.USAGE_ALARM。
     * 实测 vivo V2048A / Android 13：默认 Usage=UNKNOWN 的震动会被「系统触觉反馈总开关」
     * （settings haptic_feedback_enabled=0、vibrate_on=0，dumpsys 里 TOUCH=(OFF)）压成
     * scale=0.00 —— 系统日志显示震动已下发且 status=finished，但振幅被归零，用户无感。
     * ALARM 类不受该开关压制（dumpsys 里 ALARM=(MEDIUM)），能正常出感。
     */
    @Suppress("DEPRECATION")
    fun vibrate(durationMs: Long) {
        try {
            val vibrator = systemVibrator() ?: return
            val ms = durationMs.coerceIn(50L, 2000L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+：显式 usage=ALARM，绕开触觉反馈开关的振幅压制
                val effect = VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
                @Suppress("NewApi")
                val attrs = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM)
                vibrator.vibrate(effect, attrs)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10~12：用 AudioAttributes.USAGE_ALARM 表达同等语义
                val effect = VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
                val attrs = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .build()
                vibrator.vibrate(effect, attrs)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(ms)
            }
        } catch (e: Exception) {
            // 极端 ROM：退化为最朴素调用，至少尝试一次
            try { systemVibrator()?.vibrate(durationMs.coerceIn(50L, 2000L)) } catch (_: Exception) { }
        }
    }

    fun vibrate() = vibrate(60L)

    fun cancelVibrate() {
        try { systemVibrator()?.cancel() } catch (e: Exception) { }
    }

    /** 系统提示音：用于「调用反馈」兜底与配置页测试按钮（不需要额外音频资源）。 */
    fun playTone() {
        try {
            val generator = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            generator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            Handler(Looper.getMainLooper()).postDelayed({
                try { generator.release() } catch (e: Exception) { }
            }, 500L)
        } catch (e: Exception) {
            // 部分设备无 ToneGenerator：静默降级
        }
    }

    /** 当前系统媒体音量（0..1），作为 @system.audio.volume 的默认值。 */
    fun systemVolume(): Float = try {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max > 0) am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max else 0.6f
    } catch (e: Exception) {
        0.6f
    }
}

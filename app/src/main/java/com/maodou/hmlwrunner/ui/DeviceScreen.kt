package com.maodou.hmlwrunner.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.maodou.hmlwrunner.ui.theme.ExpressiveShapes
import com.maodou.hmlwrunner.runtime.DeviceConfig
import com.maodou.hmlwrunner.runtime.RuntimeController

/**
 * device 配置页：完整覆盖 hmlwsim 网页版左侧控制面板的全部可配置项、
 * 参数调整、状态展示与既有交互行为；控件值通过 JS 桥驱动原 DOM 控件并派发
 * 原生事件（change / input / click），因此行为与网页版一致。
 */
@Composable
fun DeviceScreen(controller: RuntimeController, modifier: Modifier = Modifier) {
    val cfg = controller.config
    val state = controller.runtimeState

    var widthText by remember { mutableStateOf(cfg.width.toString()) }
    var heightText by remember { mutableStateOf(cfg.height.toString()) }
    var stepText by remember { mutableStateOf(cfg.stepCount.toString()) }
    var wearInput by remember { mutableStateOf("") }
    LaunchedEffect(cfg.width) { widthText = cfg.width.toString() }
    LaunchedEffect(cfg.height) { heightText = cfg.height.toString() }
    LaunchedEffect(cfg.stepCount) { stepText = cfg.stepCount.toString() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ---------------------------------------------------------- 状态展示
        item {
            SectionCard(
                title = "设备状态",
                subtitle = "每 2 秒从解释器回读；亮度/设备信息来自原 DeviceFeatureDisplay",
                icon = Icons.Filled.Info,
            ) {
                if (state == null) {
                    Text(
                        "等待解释器状态回读…（进入一次「运行」页即会初始化）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    state.deviceLines.forEach { (k, v) -> InfoRow(k, v) }
                    InfoRow("电池", "${(state.batteryLevel * 100).toInt()}%" +
                        if (state.charging) "（充电中）" else "")
                    InfoRow("亮度模式", state.brightnessMode.toString())
                    InfoRow("当前页", state.page.ifEmpty { "—" })
                    InfoRow("应用", "${state.appName.ifEmpty { "—" }} / ${state.bundleName.ifEmpty { "—" }}")
                    InfoRow("手机连接", if (state.phoneConnected) "已连接 ${state.peerName}" else "未连接")
                    InfoRow("虚拟文件系统", "${state.vfsBundle.ifEmpty { "—" }} · ${state.vfsCount} 文件")
                    InfoRow("离线库", state.libs.entries.joinToString(" ") { "${it.key}=${if (it.value) "✓" else "✗"}" })
                }
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(onClick = { controller.requestState() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("刷新状态")
                }
            }
        }

        // ---------------------------------------------------------- Device
        item {
            SectionCard(title = "Device · 屏幕形状与分辨率", icon = Icons.Filled.Tune) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = cfg.screenShape == "circle",
                        onClick = { controller.updateConfig(cfg.copy(screenShape = "circle", activePreset = "")) },
                        label = { Text("圆形 Circle") },
                    )
                    FilterChip(
                        selected = cfg.screenShape == "rect",
                        onClick = { controller.updateConfig(cfg.copy(screenShape = "rect", activePreset = "")) },
                        label = { Text("方形 Rectangle") },
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = widthText,
                        onValueChange = { s ->
                            widthText = s
                            val n = s.toIntOrNull()
                            if (n != null && n in 100..1000) {
                                controller.updateConfig(cfg.copy(width = n, activePreset = ""))
                            }
                        },
                        label = { Text("宽度 100–1000") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = ExpressiveShapes.Panel,
                    )
                    OutlinedTextField(
                        value = heightText,
                        onValueChange = { s ->
                            heightText = s
                            val n = s.toIntOrNull()
                            if (n != null && n in 100..1000) {
                                controller.updateConfig(cfg.copy(height = n, activePreset = ""))
                            }
                        },
                        label = { Text("高度 100–1000") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = ExpressiveShapes.Panel,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text("Presets", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 使用 Column 嵌套两组 FilterChip，避免横向溢出
                    Column(Modifier.weight(1f)) {
                        DeviceConfig.PRESETS.take(3).forEach { p ->
                            FilterChip(
                                selected = cfg.activePreset == p.key,
                                onClick = { controller.choosePreset(p.key) },
                                label = { Text(p.label) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        DeviceConfig.PRESETS.drop(3).forEach { p ->
                            FilterChip(
                                selected = cfg.activePreset == p.key,
                                onClick = { controller.choosePreset(p.key) },
                                label = { Text(p.label) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                Text(
                    "预设与网页版一致：FIT2 336×480 / GT3+ 466×466 / FIT3+ 408×480 / " +
                        "GT2 42mm 390×390 / GT2 46mm 454×454 / WATCH D 280×456",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---------------------------------------------------------- Sensors
        item {
            SectionCard(
                title = "Sensors · 电池与传感器",
                subtitle = "「Show real values」打开时由 Android 真实传感器喂值",
                icon = Icons.Filled.Sensors,
            ) {
                SettingSwitch(
                    title = "Use system battery",
                    subtitle = "使用本机 BatteryManager 实时电量（开启后手动滑块锁定，行为同网页版）",
                    checked = cfg.useSystemBattery,
                    onChange = { controller.updateConfig(cfg.copy(useSystemBattery = it)) },
                )
                SliderRow(
                    label = "Battery",
                    value = cfg.batteryPercent.toFloat(),
                    range = 0f..100f,
                    display = "${cfg.batteryPercent}%",
                    enabled = !cfg.useSystemBattery,
                    onValueChange = { controller.updateConfig(cfg.copy(batteryPercent = it.toInt())) },
                )
                Spacer(Modifier.height(6.dp))
                Text("真实值开关", style = MaterialTheme.typography.labelLarge)
                listOf(
                    "accelerometer" to "Linear acceleration",
                    "gyroscope" to "Rotation rate",
                    "compass" to "Magnetic field",
                    "barometer" to "Barometer",
                ).forEach { (key, label) ->
                    SettingSwitch(
                        title = label,
                        subtitle = "Show real values",
                        checked = cfg.sensorReal[key] == true,
                        onChange = { v ->
                            val next = cfg.sensorReal.toMutableMap()
                            next[key] = v
                            controller.updateConfig(cfg.copy(sensorReal = next))
                        },
                    )
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = stepText,
                    onValueChange = { s ->
                        stepText = s
                        val n = s.toIntOrNull()
                        if (n != null && n in 0..999999) {
                            controller.updateConfig(cfg.copy(stepCount = n))
                        }
                    },
                    label = { Text("Step count 0–999999") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = ExpressiveShapes.Panel,
                )
                Spacer(Modifier.height(8.dp))
                SettingSwitch(
                    title = "Heart rate · Not detected",
                    subtitle = "勾选后上报 255（未检测到），心率滑块锁定",
                    checked = cfg.heartRateInvalid,
                    onChange = { controller.updateConfig(cfg.copy(heartRateInvalid = it)) },
                )
                SliderRow(
                    label = "Heart rate",
                    value = cfg.heartRate.toFloat(),
                    range = 30f..220f,
                    display = if (cfg.heartRateInvalid) "255（未检测到）" else "${cfg.heartRate} bpm",
                    enabled = !cfg.heartRateInvalid,
                    onValueChange = { controller.updateConfig(cfg.copy(heartRate = it.toInt())) },
                )
                SettingSwitch(
                    title = "Wearing state",
                    subtitle = "佩戴状态",
                    checked = cfg.onBody,
                    onChange = { controller.updateConfig(cfg.copy(onBody = it)) },
                )
                if (state != null) {
                    Spacer(Modifier.height(10.dp))
                    Text("传感器当前值", style = MaterialTheme.typography.labelLarge)
                    state.sensorLines.forEach { (k, v) -> InfoRow(k, v) }
                }
            }
        }

        // ---------------------------------------------------------- WearEngine
        item {
            SectionCard(
                title = "WearEngine · 模拟手机消息",
                subtitle = "连接状态：${if (state?.phoneConnected == true) "已连接 ${state.peerName}" else "等待连接"}",
                icon = Icons.Filled.Link,
            ) {
                if (controller.wearMessages.isEmpty()) {
                    Text(
                        "暂无消息。应用通过 system.wearengine 发来的消息也会显示在这里。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    controller.wearMessages.takeLast(10).forEach { m ->
                        Text(
                            (if (m.direction == "sent") "→ " else "← ") + m.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (m.direction == "sent") MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = wearInput,
                        onValueChange = { wearInput = it },
                        label = { Text("Type a message...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = ExpressiveShapes.Panel,
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val text = wearInput.trim()
                        if (text.isNotEmpty()) {
                            controller.action("wear_send", mapOf("text" to text))
                            wearInput = ""
                        }
                    }) {
                        Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Send")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { controller.action("wear_send_file", mapOf("text" to "photo.bin")) },
                        modifier = Modifier.weight(1f),
                    ) { Text("模拟文件消息") }
                    OutlinedButton(
                        onClick = { controller.action("clear_wear_log") },
                        modifier = Modifier.weight(1f),
                    ) { Text("清空消息记录") }
                }
            }
        }

        // ---------------------------------------------------------- Crown
        item {
            SectionCard(
                title = "Crown · 表冠",
                subtitle = "Invert Scroll 切换滚动方向，行为与网页版一致",
                icon = Icons.Filled.Speed,
            ) {
                SettingSwitch(
                    title = "Invert Scroll",
                    subtitle = "反转表冠滚动事件方向",
                    checked = cfg.invertScroll,
                    onChange = { controller.updateConfig(cfg.copy(invertScroll = it)) },
                )
                SliderRow(
                    label = "Sensitivity（源码 DeviceFeatureCrown.setSensitivity）",
                    value = cfg.crownSensitivity,
                    range = 0.2f..3f,
                    display = String.format(java.util.Locale.getDefault(), "%.1f×", cfg.crownSensitivity),
                    onValueChange = { controller.updateConfig(cfg.copy(crownSensitivity = it)) },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(
                        onClick = { controller.action("crown", mapOf("delta" to -1)) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("上旋")
                    }
                    FilledTonalButton(
                        onClick = { controller.action("crown", mapOf("delta" to 1)) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("下旋")
                    }
                }
            }
        }

        // ---------------------------------------------------------- 音频 / 震动反馈
        item {
            SectionCard(title = "Feedback · 音频与震动", icon = Icons.Filled.Sensors) {
                Text(
                    "按 Lite Wearable 官方 @system.audio / @system.vibrator 语义接入：" +
                        "应用调用时手机真实出声 / 震动，并在此回读状态。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                SettingSwitch(
                    title = "真机声音反馈",
                    subtitle = "@system.audio 播放、兜底提示音与测试提示音",
                    checked = controller.feedbackConfig.soundEnabled,
                    onChange = { controller.setFeedbackEnabled(sound = it) },
                )
                SettingSwitch(
                    title = "真机震动反馈",
                    subtitle = "short 35ms / long 400ms；关闭后仅保留网页描边反馈",
                    checked = controller.feedbackConfig.hapticsEnabled,
                    onChange = { controller.setFeedbackEnabled(haptics = it) },
                )
                Spacer(Modifier.height(6.dp))
                InfoRow("系统媒体音量", controller.feedbackStatus.systemVolumeText)
                InfoRow(
                    "播放状态",
                    controller.feedbackStatus.audioState +
                        (if (controller.feedbackStatus.lastEvent.isNotEmpty())
                            " · ${controller.feedbackStatus.lastEvent}" else ""),
                )
                InfoRow("应用音量", controller.feedbackStatus.volumeText)
                InfoRow("音频源", controller.feedbackStatus.audioSrc.ifEmpty { "（未设置）" })
                InfoRow("最近震动", controller.feedbackStatus.lastVibrateText)
                InfoRow("震动次数", controller.feedbackStatus.vibrateCount.toString())
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { controller.testFeedback("tone") },
                        modifier = Modifier.weight(1f),
                    ) { Text("提示音") }
                    FilledTonalButton(
                        onClick = { controller.testFeedback("short") },
                        modifier = Modifier.weight(1f),
                    ) { Text("短震") }
                    FilledTonalButton(
                        onClick = { controller.testFeedback("long") },
                        modifier = Modifier.weight(1f),
                    ) { Text("长震") }
                }
            }
        }

        // ---------------------------------------------------------- Tools / Lifecycle
        item {
            SectionCard(title = "Tools · 截图与日志", icon = Icons.Filled.PhotoCamera) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { controller.action("screenshot_save") },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("截图并下载")
                    }
                    FilledTonalButton(
                        onClick = { controller.action("screenshot_clipboard") },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("复制到剪贴板")
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { controller.clearLogs() }, modifier = Modifier.fillMaxWidth()) {
                    Text("清空 Log 面板")
                }
            }
        }

        item {
            SectionCard(title = "生命周期 / 配置", icon = Icons.Filled.BatteryFull) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { controller.action("restart") },
                        modifier = Modifier.weight(1f),
                    ) { Text("Restart app") }
                    FilledTonalButton(
                        onClick = { controller.action("reload") },
                        modifier = Modifier.weight(1f),
                    ) { Text("Reload app") }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Restart：销毁页面与 Sandbox 后重启（VFS 保留）；" +
                        "Reload：额外清空 VFS 并重新导入 HAP 文件。语义与网页版一致。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { controller.updateConfig(DeviceConfig()) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("恢复默认设备配置") }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

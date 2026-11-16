package com.maodou.hmlwrunner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.maodou.hmlwrunner.runtime.Phase
import com.maodou.hmlwrunner.runtime.RuntimeController
import com.maodou.hmlwrunner.runtime.RuntimeWebView

@Composable
fun RuntimeScreen(
    controller: RuntimeController,
    onOpenDevice: () -> Unit,
    onOpenLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // 运行状态条
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            controller.selected?.bundleName ?: "未选择目标包",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        val cfg = controller.config
                        val page = controller.runtimeState?.page
                        Text(
                            "${cfg.width}×${cfg.height} · ${if (cfg.screenShape == "circle") "圆屏" else "方屏"}" +
                                (if (!page.isNullOrEmpty()) " · $page" else "") +
                                " · 电池 ${controller.batteryText}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val phaseText = when (controller.phase) {
                        Phase.IDLE -> "待机"
                        Phase.INSPECTING -> "识别中"
                        Phase.READY -> "就绪"
                        Phase.LOADING -> "启动中"
                        Phase.RUNNING -> "运行中"
                        Phase.ERROR -> "异常"
                    }
                    AssistChip(
                        onClick = onOpenLogs,
                        label = { Text(phaseText) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (controller.phase == Phase.ERROR)
                                MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
                if (controller.phase == Phase.LOADING) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(
                        controller.progress.ifEmpty { "正在解包并启动…" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // 解释器视口
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            RuntimeWebView(controller, Modifier.fillMaxSize())

            // 原生表冠：向隐藏的 #crown 派发 wheel 事件，保留 Invert Scroll 等原交互
            Column(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FilledIconButton(onClick = { controller.action("crown", mapOf("delta" to -1)) }) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "表冠上旋")
                }
                Text("表冠", style = MaterialTheme.typography.labelSmall)
                FilledIconButton(onClick = { controller.action("crown", mapOf("delta" to 1)) }) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "表冠下旋")
                }
            }
        }

        // 运行异常 / 解析失败反馈
        val result = controller.lastResult
        if (result != null && !result.ok) {
            ErrorCard(
                com.maodou.hmlwrunner.runtime.RunError(
                    time = System.currentTimeMillis(),
                    code = result.code,
                    message = result.message,
                    detail = result.detail,
                    stack = result.stack,
                    source = result.stage,
                )
            )
        }

        // 操作栏
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { controller.action("restart") },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("重启")
                    }
                    FilledTonalButton(
                        onClick = { controller.action("reload") },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("重载")
                    }
                    FilledTonalButton(onClick = onOpenDevice, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("配置")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { controller.action("screenshot_save") },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("截图保存")
                    }
                    FilledTonalButton(
                        onClick = { controller.action("screenshot_clipboard") },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("截图复制")
                    }
                }
            }
        }
    }
}

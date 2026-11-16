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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.maodou.hmlwrunner.ui.theme.ExpressiveShapes
import com.maodou.hmlwrunner.runtime.PackageInfo
import com.maodou.hmlwrunner.runtime.PackageKind
import com.maodou.hmlwrunner.runtime.Phase
import com.maodou.hmlwrunner.runtime.RuntimeController
import java.util.Locale

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.getDefault(), "%.2f MB", bytes / 1048576f)
    bytes >= 1024L -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024f)
    else -> "$bytes B"
}

@Composable
fun HomeScreen(
    controller: RuntimeController,
    onPickFile: () -> Unit,
    onRun: () -> Unit,
    onDevice: () -> Unit,
    onLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = controller.selected
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { IntroCard(packageCount = controller.packages.size, selectedName = selected?.bundleName) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ExtendedFloatingActionButton(
                    onClick = onPickFile,
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
                    text = { Text("导入目标包") },
                )
                FilledTonalButton(onClick = onDevice, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("设备配置")
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onLogs, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Article, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("查看运行日志")
            }
        }

        if (controller.phase == Phase.INSPECTING || controller.phase == Phase.LOADING) {
            item {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    controller.progress.ifEmpty { "处理中…" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        controller.errors.takeLast(2).forEach { err ->
            item(key = "err_${err.time}") { ErrorCard(err) }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("已导入的包", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(8.dp))
                Badge(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Text(controller.packages.size.toString(), modifier = Modifier.padding(horizontal = 8.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "点击卡片选中",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (controller.packages.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Filled.Add,
                    title = "还没有目标包",
                    subtitle = "支持 APP 包 (.app) 与 HAP (.hap)，任意扩展名均可",
                )
            }
        } else {
            items(controller.packages, key = { it.id }) { pkg ->
                PackageCard(
                    info = pkg,
                    selected = pkg.id == selected?.id,
                    onClick = { controller.selectPackage(pkg) },
                    onDelete = { controller.removePackage(pkg) },
                )
            }
        }

        if (selected != null) {
            item { SelectedDetailCard(selected) }
            item {
                Button(
                    onClick = onRun,
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    shape = ExpressiveShapes.Hero,
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("运行 ${selected.bundleName}", style = MaterialTheme.typography.titleMedium)
                }
            }
            item {
                PhaseHint(controller)
            }
        }
    }
}

@Composable
private fun IntroCard(packageCount: Int, selectedName: String?) {
    Card(
        modifier = Modifier.fillMaxWidth().expressiveReveal(),
        shape = ExpressiveShapes.Card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("轻量 JS 运行器", style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "复用 hmlwsim 的 JS 解释器源码，在系统 WebView 里解释执行 lite wearable 应用代码；" +
                    "它不是完整设备模拟器，不模拟 ROM / 安装 / 签名校验。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.AssistChip(
                    onClick = { },
                    label = { Text("已导入 $packageCount 个包") },
                )
                if (!selectedName.isNullOrEmpty()) {
                    androidx.compose.material3.AssistChip(
                        onClick = { },
                        label = { Text("当前：$selectedName", maxLines = 1) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("① 导入 APP 包直接运行", style = MaterialTheme.typography.bodyMedium)
            Text("② HAP 自动适配为 APP 结构", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PackageCard(
    info: PackageInfo,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val (container, content) = kindColors(info.kind)
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.Panel,
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(info.kind.label, container, content)
                if (info.signed) {
                    Spacer(Modifier.width(8.dp))
                    StatusPill(
                        "含签名信息",
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "移除", modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(info.fileName, style = MaterialTheme.typography.titleMedium)
            Text(
                "${info.bundleName} · v${info.version} · ${formatSize(info.sizeBytes)} · ${info.entryCount} 项",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (info.warning.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    info.warning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun kindColors(kind: PackageKind): Pair<Color, Color> = when (kind) {
    PackageKind.APP -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    PackageKind.HAP -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    PackageKind.UNKNOWN -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
}

@Composable
private fun SelectedDetailCard(info: PackageInfo) {
    SectionCard(title = "识别结果", subtitle = info.kind.hint, icon = Icons.Filled.Info) {
        InfoRow("文件", info.fileName)
        InfoRow("包类型", info.kind.label)
        InfoRow("签名", if (info.signed) "检测到签名信息（不参与解释执行）" else "未检测到 / 未签名")
        InfoRow("大小", formatSize(info.sizeBytes))
        InfoRow("Bundle", info.bundleName)
        InfoRow("版本", info.version)
        InfoRow("设备类型", info.deviceType)
        InfoRow("条目数", info.entryCount.toString())
        InfoRow("页面", info.pages.take(6).joinToString("、").ifEmpty { "—" } +
            if (info.pages.size > 6) " …共 ${info.pages.size} 页" else "")
        InfoRow("解包层级", info.layers.joinToString(" → ").ifEmpty { "—" })
    }
}

@Composable
private fun PhaseHint(controller: RuntimeController) {
    val text = when (controller.phase) {
        Phase.IDLE -> "等待导入"
        Phase.INSPECTING -> "正在识别包结构"
        Phase.READY -> "已就绪，可运行"
        Phase.LOADING -> "正在解包并启动入口页"
        Phase.RUNNING -> "运行中：解释器已加载"
        Phase.ERROR -> "上次运行/解析出错，详见日志"
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (controller.phase == Phase.ERROR) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

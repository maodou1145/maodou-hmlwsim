package com.maodou.hmlwrunner.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.maodou.hmlwrunner.runtime.RuntimeController
import com.maodou.hmlwrunner.ui.theme.ExpressiveShapes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class Line(val time: Long, val level: String, val text: String)

@Composable
fun LogScreen(controller: RuntimeController, modifier: Modifier = Modifier) {
    var filter by remember { mutableStateOf("ALL") }
    val clipboard = LocalClipboardManager.current
    val formatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    val all = remember(controller.logs, controller.errors) {
        (controller.logs.map { Line(it.time, it.level, it.text) } +
            controller.errors.map { Line(it.time, "error", "[${it.code}] ${it.message}") })
            .sortedByDescending { it.time }
    }
    val shown = if (filter == "ALL") all else all.filter { it.level == filter }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        controller.lastResult?.let { result ->
            SectionCard(title = "最近一次运行结果", subtitle = "阶段 ${result.stage} · 代码 ${result.code}") {
                Text(
                    result.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (result.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                if (result.detail.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(result.detail, style = MaterialTheme.typography.bodySmall)
                }
                if (result.stack.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        result.stack.take(800),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("ALL" to "全部", "info" to "信息", "warn" to "警告", "error" to "错误")
                .forEach { (key, label) ->
                    FilterChip(
                        selected = filter == key,
                        onClick = { filter = key },
                        label = { Text(label) },
                    )
                }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(
                onClick = { controller.clearLogs() },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Text("清空")
            }
            FilledTonalButton(
                onClick = {
                    clipboard.setText(AnnotatedString(all.asReversed().joinToString("\n") {
                        "${formatter.format(Date(it.time))} [${it.level}] ${it.text}"
                    }))
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null)
                Text("复制全部")
            }
        }
        Spacer(Modifier.height(10.dp))

        if (shown.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Article,
                title = "暂无日志",
                subtitle = "运行一个包后，解包过程、console 输出与异常都会显示在这里",
            )
        } else {
            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = ExpressiveShapes.Card,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(shown, key = { "${it.time}_${it.level}_${it.text.hashCode()}" }) { line ->
                        val (icon, container, content) = when (line.level) {
                            "error" -> Triple(
                                Icons.Filled.Error,
                                MaterialTheme.colorScheme.errorContainer,
                                MaterialTheme.colorScheme.onErrorContainer,
                            )
                            "warn" -> Triple(
                                Icons.Filled.Warning,
                                MaterialTheme.colorScheme.tertiaryContainer,
                                MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            else -> Triple(
                                Icons.Filled.Info,
                                MaterialTheme.colorScheme.secondaryContainer,
                                MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                        ListItem(
                            leadingContent = {
                                Icon(icon, contentDescription = line.level, tint = content)
                            },
                            overlineContent = {
                                Text("${formatter.format(Date(line.time))} · ${line.level.uppercase()}")
                            },
                            headlineContent = {
                                Text(line.text, style = MaterialTheme.typography.bodyMedium)
                            },
                            colors = androidx.compose.material3.ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp, end = 16.dp),
                            color = container.copy(alpha = 0.75f),
                        )
                    }
                }
            }
        }
    }
}

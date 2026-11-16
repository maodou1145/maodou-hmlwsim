package com.maodou.hmlwrunner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.maodou.hmlwrunner.runtime.RuntimeController
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
            .sortedBy { it.time }
    }
    val shown = if (filter == "ALL") all else all.filter { it.level == filter }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        controller.errors.takeLast(3).reversed().forEach { err -> ErrorCard(err) }

        controller.lastResult?.let { r ->
            Spacer(Modifier.height(8.dp))
            SectionCard(title = "最近一次运行结果", subtitle = "阶段 ${r.stage} · 代码 ${r.code}") {
                Text(
                    r.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (r.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                if (r.detail.isNotEmpty()) {
                    Text(r.detail, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ALL" to "全部", "info" to "信息", "warn" to "警告", "error" to "错误").forEach { (key, label) ->
                FilterChip(
                    selected = filter == key,
                    onClick = { filter = key },
                    label = { Text(label) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row {
            OutlinedButton(onClick = { controller.clearLogs() }, modifier = Modifier.weight(1f)) {
                androidx.compose.material3.Icon(
                    Icons.Filled.Delete, contentDescription = null,
                    modifier = Modifier.padding(end = 4.dp),
                )
                Text("清空")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = {
                    clipboard.setText(AnnotatedString(all.joinToString("\n") {
                        "${formatter.format(Date(it.time))} [${it.level}] ${it.text}"
                    }))
                },
                modifier = Modifier.weight(1f),
            ) {
                androidx.compose.material3.Icon(
                    Icons.Filled.ContentCopy, contentDescription = null,
                    modifier = Modifier.padding(end = 4.dp),
                )
                Text("复制全部")
            }
        }

        Spacer(Modifier.height(12.dp))
        if (shown.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Article,
                title = "暂无日志",
                subtitle = "运行一个包后，解包过程、console 输出与异常都会显示在这里",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(shown) { line ->
                    val color = when (line.level) {
                        "error" -> MaterialTheme.colorScheme.error
                        "warn" -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Text(
                        "${formatter.format(Date(line.time))}  ${line.text}",
                        style = MaterialTheme.typography.bodySmall,
                        color = color,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

package com.maodou.hmlwrunner.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.maodou.hmlwrunner.runtime.Phase
import com.maodou.hmlwrunner.runtime.Prefs
import com.maodou.hmlwrunner.runtime.RuntimeController

private enum class Tab(val label: String, val icon: ImageVector) {
    PACKAGES("目标包", Icons.Filled.FolderOpen),
    RUNTIME("运行", Icons.Filled.PlayArrow),
    DEVICE("设备配置", Icons.Filled.Tune),
    LOGS("日志", Icons.Filled.Article),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(controller: RuntimeController) {
    var tab by remember { mutableStateOf(Tab.PACKAGES) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    // 首次启动欢迎弹窗：读一次持久化标记，同进程内用 state 控制显隐。
    var showWelcome by remember { mutableStateOf(!Prefs(context).hasSeenWelcome()) }

    if (showWelcome) {
        WelcomeDialog(
            onDismiss = {
                Prefs(context).markWelcomeSeen()
                showWelcome = false
            },
            onOpenUrl = { url -> runCatching { uriHandler.openUri(url) } },
        )
    }

    // SAF 文件选择：任意 MIME，扩展名由内容嗅探决定（signed.bin / .app / .hap 都接受）
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) controller.importPackage(uri, null)
    }

    LaunchedEffect(controller.toast) {
        val message = controller.toast
        if (message.isNotEmpty()) {
            snackbar.showSnackbar(message)
            controller.consumeToast()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (tab) {
                            Tab.PACKAGES -> "maodou-hmlwsim"
                            Tab.RUNTIME -> "解释执行"
                            Tab.DEVICE -> "Device 配置"
                            Tab.LOGS -> "运行日志"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                actions = {
                    IconButton(onClick = { controller.requestState() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新状态")
                    }
                    IconButton(onClick = { pickFile.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = "导入目标包")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = {
                            tab = item
                            // 底部页签本身也是运行入口：切到「运行」时若尚未启动，自动装载当前选中的包。
                            // 否则用户从主页直接点「运行」只会看到一个空白表盘（黑屏）。
                            if (item == Tab.RUNTIME &&
                                controller.selected != null &&
                                controller.phase != Phase.RUNNING &&
                                controller.phase != Phase.LOADING
                            ) {
                                controller.runSelected()
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        alwaysShowLabel = true,
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (tab) {
                Tab.PACKAGES -> HomeScreen(
                    controller = controller,
                    onPickFile = { pickFile.launch(arrayOf("*/*")) },
                    onRun = {
                        tab = Tab.RUNTIME
                        controller.runSelected()
                    },
                    onDevice = { tab = Tab.DEVICE },
                    onLogs = { tab = Tab.LOGS },
                )
                Tab.RUNTIME -> RuntimeScreen(
                    controller = controller,
                    onOpenDevice = { tab = Tab.DEVICE },
                    onOpenLogs = { tab = Tab.LOGS },
                )
                Tab.DEVICE -> DeviceScreen(controller)
                Tab.LOGS -> LogScreen(controller)
            }
        }
    }
}

/** 首次启动欢迎 / 免责声明弹窗。链接可点击跳转系统浏览器。 */
@Composable
private fun WelcomeDialog(
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("欢迎使用 maodou-hmlwsim for Android") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "应用是适用于 lite wearable 的应用包 JS 脚本解释器",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "无法替代真机！无法替代真机！",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "因为是 JS 代码解释器，不能运行所有代码！",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "若有 BUG 请反馈至 1409284216",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "原版作者：靠墙的钴-60",
                    style = MaterialTheme.typography.bodySmall,
                )
                LinkRow("GitHub 主页", "https://github.com/kqakqakqa", onOpenUrl)
                LinkRow("原版仓库", "https://github.com/kqakqakqa/hmlwsim", onOpenUrl)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("我知道了") }
        },
    )
}

@Composable
private fun LinkRow(label: String, url: String, onOpenUrl: (String) -> Unit) {
    Text(
        "$label：$url",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(vertical = 2.dp)
            .clickable { onOpenUrl(url) },
    )
}

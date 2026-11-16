package com.maodou.hmlwrunner.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlinx.coroutines.delay

private enum class Tab(val label: String, val icon: ImageVector) {
    PACKAGES("目标包", Icons.Filled.Home),
    RUNTIME("运行", Icons.Filled.PlayArrow),
    DEVICE("设备", Icons.Filled.Tune),
    LOGS("日志", Icons.Filled.Article),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(controller: RuntimeController) {
    var tab by remember { mutableStateOf(Tab.PACKAGES) }
    var pendingTab by remember { mutableStateOf<Tab?>(null) }
    var booting by remember { mutableStateOf(true) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var showWelcome by remember { mutableStateOf(!Prefs(context).hasSeenWelcome()) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) controller.importPackage(uri, null)
    }

    // 按压反馈先呈现，稍后统一切页；快速连点时只执行最后一个目标，避免导航竞态。
    LaunchedEffect(pendingTab) {
        val destination = pendingTab ?: return@LaunchedEffect
        delay(180L)
        tab = destination
        if (destination == Tab.RUNTIME && controller.selected != null &&
            controller.phase != Phase.RUNNING && controller.phase != Phase.LOADING
        ) {
            controller.runSelected()
        }
        pendingTab = null
    }

    // 这是 Compose 内的启动页：比空白窗口更早给用户明确反馈，且不依赖系统版本。
    LaunchedEffect(Unit) {
        delay(850L)
        booting = false
    }
    if (booting) {
        StartupLoadingScreen()
        return
    }

    if (showWelcome) {
        WelcomeDialog(
            onDismiss = {
                Prefs(context).markWelcomeSeen()
                showWelcome = false
            },
            onOpenUrl = { url -> runCatching { uriHandler.openUri(url) } },
        )
    }

    LaunchedEffect(controller.toast) {
        val message = controller.toast
        if (message.isNotEmpty()) {
            snackbar.showSnackbar(message)
            controller.consumeToast()
        }
    }

    val title = when (tab) {
        Tab.PACKAGES -> "目标包"
        Tab.RUNTIME -> "解释执行"
        Tab.DEVICE -> "设备配置"
        Tab.LOGS -> "运行日志"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, style = MaterialTheme.typography.titleLarge)
                        if (tab == Tab.PACKAGES) {
                            Text(
                                "maodou-hmlwsim",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
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
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
            ) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = {
                            if (tab != item) pendingTab = item
                        },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        alwaysShowLabel = false,
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (tab) {
                Tab.PACKAGES -> HomeScreen(
                    controller = controller,
                    onPickFile = { pickFile.launch(arrayOf("*/*")) },
                    onRun = { pendingTab = Tab.RUNTIME },
                    onDevice = { pendingTab = Tab.DEVICE },
                    onLogs = { pendingTab = Tab.LOGS },
                )
                Tab.RUNTIME -> RuntimeScreen(
                    controller = controller,
                    onOpenDevice = { tab = Tab.DEVICE },
                    onOpenLogs = { tab = Tab.LOGS },
                )
                Tab.DEVICE -> DeviceScreen(controller)
                Tab.LOGS -> LogScreen(controller)
            }

            if (controller.phase == Phase.INSPECTING || controller.phase == Phase.LOADING) {
                LoadingOverlay(
                    title = if (controller.phase == Phase.INSPECTING) "正在读取目标包" else "正在启动应用",
                    message = controller.progress.ifEmpty { "请稍候，解释器正在准备运行环境…" },
                    fileName = controller.selected?.fileName,
                )
            }
        }
    }
}

@Composable
private fun WelcomeDialog(
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("欢迎使用 maodou-hmlwsim") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("适用于 Lite Wearable 应用的 JS 脚本解释器", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Text(
                    "无法替代真机！无法替代真机！",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(10.dp))
                Text("因为是 JS 代码解释器，不能运行所有代码！", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Text("若有 BUG 请反馈至 1409284216", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text("原版作者：靠墙的钴-60", style = MaterialTheme.typography.bodySmall)
                LinkRow("GitHub 主页", "https://github.com/kqakqakqa", onOpenUrl)
                LinkRow("原版仓库", "https://github.com/kqakqakqa/hmlwsim", onOpenUrl)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("我知道了") } },
    )
}

@Composable
private fun LinkRow(label: String, url: String, onOpenUrl: (String) -> Unit) {
    Text(
        "$label：$url",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 3.dp).clickable { onOpenUrl(url) },
    )
}

package com.maodou.hmlwrunner.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Material 3 Expressive 视觉系统。
 *
 * 说明：material3 1.4.0（当前最新稳定版）中 `MaterialExpressiveTheme` 与
 * `Shapes(largeIncreased = ...)` 仍是 internal，跨模块调用会编译失败；
 * 因此这里用**全部公开稳定 API** 落地 M3E 设计语言：
 *  - 动态取色 / tonal surface 分层
 *  - ExpressiveShapes 大圆角形状系统（28–44dp，而非 M3 默认的 16dp）
 *  - 强调排版（更重字重、更紧的字距）
 *  - FilterChip / AssistChip / FilledTonalButton / ElevatedCard 等强调型组件
 *  - 弹簧动效（见 ui/Components.kt 的 press-scale）
 * 不引入 alpha / RC 依赖，也不要求额外的 Compose 编译器或构建环境。
 */
@Composable
fun HmlwTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }

    val typography = Typography(
        headlineSmall = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 32.sp,
            letterSpacing = (-0.2).sp,
        ),
        titleMedium = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.1.sp,
        ),
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content,
    )
}

/** M3E 形状令牌：比标准 M3 更饱满的容器圆角 */
object ExpressiveShapes {
    val Panel = RoundedCornerShape(20.dp)
    val Card = RoundedCornerShape(28.dp)
    val Hero = RoundedCornerShape(36.dp)
    val Floating = RoundedCornerShape(44.dp)
    val Pill = RoundedCornerShape(50)
}

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val WarmLightColors = lightColorScheme(
    primary = Color(0xFF9C2F1C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD2),
    onPrimaryContainer = Color(0xFF3D0601),
    secondary = Color(0xFF75574F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBD1),
    onSecondaryContainer = Color(0xFF2B1510),
    tertiary = Color(0xFF6B5D2E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF3E5A8),
    onTertiaryContainer = Color(0xFF211B00),
    surface = Color(0xFFFFF8F6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1ED),
    surfaceContainer = Color(0xFFF9EAE5),
    surfaceContainerHigh = Color(0xFFF3E4DF),
    surfaceContainerHighest = Color(0xFFEDE0DB),
)

private val WarmDarkColors = darkColorScheme(
    primary = Color(0xFFFFB4A4),
    onPrimary = Color(0xFF5F160A),
    primaryContainer = Color(0xFF7D2515),
    onPrimaryContainer = Color(0xFFFFDAD2),
    secondary = Color(0xFFE6BBB0),
    onSecondary = Color(0xFF432A24),
    secondaryContainer = Color(0xFF5C4038),
    onSecondaryContainer = Color(0xFFFFDBD1),
    tertiary = Color(0xFFD7C78D),
    onTertiary = Color(0xFF393106),
    tertiaryContainer = Color(0xFF51471B),
    onTertiaryContainer = Color(0xFFF3E5A8),
    surface = Color(0xFF191110),
    surfaceContainerLowest = Color(0xFF140C0B),
    surfaceContainerLow = Color(0xFF211918),
    surfaceContainer = Color(0xFF2A211F),
    surfaceContainerHigh = Color(0xFF352B29),
    surfaceContainerHighest = Color(0xFF403634),
)

@Composable
fun HmlwTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> WarmDarkColors
        else -> WarmLightColors
    }

    val typography = Typography(
        displaySmall = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 36.sp,
            lineHeight = 44.sp,
            letterSpacing = (-0.5).sp,
        ),
        headlineSmall = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            lineHeight = 34.sp,
            letterSpacing = (-0.3).sp,
        ),
        titleLarge = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
        ),
        titleMedium = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.1.sp,
        ),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
        labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = androidx.compose.material3.Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(36.dp),
        ),
        content = content,
    )
}

/** M3E 形状令牌：页面中的大容器使用统一的形状层级。 */
object ExpressiveShapes {
    val Panel = RoundedCornerShape(20.dp)
    val Card = RoundedCornerShape(28.dp)
    val Hero = RoundedCornerShape(36.dp)
    val Floating = RoundedCornerShape(44.dp)
    val Pill = RoundedCornerShape(50)
}

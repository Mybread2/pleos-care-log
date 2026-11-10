package com.carelog.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

private val Colors = darkColorScheme(
    primary = Color(0xFF6FC3FF),
    onPrimary = Color(0xFF00243A),
    secondary = Color(0xFF9ED8A6),
    tertiary = Color(0xFFFFC870),
    background = Color(0xFF0F1216),
    surface = Color(0xFF0F1216),
    surfaceContainer = Color(0xFF1A1F26),
    surfaceContainerHigh = Color(0xFF232A33),
    onSurface = Color(0xFFE8ECF1),
    onSurfaceVariant = Color(0xFFA9B4C0),
    error = Color(0xFFFF8A80),
)

/** 차량 디스플레이는 시청 거리가 멀어 기본보다 크게 (AAOS 가이드: 본문 24sp 이상 권장). */
private val CarTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontSize = 40.sp, lineHeight = 48.sp),
        titleLarge = titleLarge.copy(fontSize = 32.sp, lineHeight = 40.sp),
        titleMedium = titleMedium.copy(fontSize = 28.sp, lineHeight = 36.sp),
        bodyLarge = bodyLarge.copy(fontSize = 26.sp, lineHeight = 34.sp),
        bodyMedium = bodyMedium.copy(fontSize = 24.sp, lineHeight = 32.sp),
        labelLarge = labelLarge.copy(fontSize = 24.sp, lineHeight = 30.sp),
        labelMedium = labelMedium.copy(fontSize = 20.sp, lineHeight = 26.sp),
    )
}

@Composable
fun CareLogTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = CarTypography, content = content)
}

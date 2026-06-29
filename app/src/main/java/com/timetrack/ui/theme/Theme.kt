package com.timetrack.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Färgpalett hämtad från den tidigare appens tema. */
object TT {
    val background = Color(0xFF0A0A0B)
    val card = Color(0xFF161618)
    val cardElevated = Color(0xFF1F1F22)
    val field = Color(0xFF1C1C1F)
    val orange = Color(0xFFF26A0E)
    val orangeSoft = Color(0x1FF26A0E)
    val textPrimary = Color(0xFFFFFFFF)
    val textSecondary = Color(0xFF8E8E93)
    val textTertiary = Color(0xFF636366)
    val divider = Color(0xFF26262A)
    val pill = Color(0xFFF2F2F2)
    val pillText = Color(0xFF111111)
    val danger = Color(0xFFE5484D)
}

private val DarkColors = darkColorScheme(
    primary = TT.orange,
    onPrimary = Color.White,
    background = TT.background,
    onBackground = TT.textPrimary,
    surface = TT.card,
    onSurface = TT.textPrimary,
    surfaceVariant = TT.cardElevated,
    onSurfaceVariant = TT.textSecondary,
    outline = TT.divider,
    error = TT.danger,
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun TimeTrackTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme() // appen är alltid mörk, men undvik varning
    MaterialTheme(
        colorScheme = DarkColors,
        typography = AppTypography,
        content = content,
    )
}

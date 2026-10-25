package com.malagel.viewy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF245C49),
    onPrimary = Color.White,
    background = Color(0xFFFAFAF8),
    onBackground = Color(0xFF191D1A),
    surface = Color(0xFFFAFAF8),
    surfaceVariant = Color(0xFFE6E9E5),
    onSurfaceVariant = Color(0xFF444C46),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA0D0B8),
    onPrimary = Color(0xFF073828),
    background = Color(0xFF111412),
    onBackground = Color(0xFFE1E4DF),
    surface = Color(0xFF111412),
    surfaceVariant = Color(0xFF272D29),
    onSurfaceVariant = Color(0xFFBDC8BF),
)

@Composable
fun ViewyTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

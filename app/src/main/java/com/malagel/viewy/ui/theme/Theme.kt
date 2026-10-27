package com.malagel.viewy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Material 3's complete baseline dark scheme: neutral surfaces and restrained lavender accents.
// Keeping all roles together avoids mixing custom accents with unrelated default containers.
private val ViewyColors = darkColorScheme()

@Composable
fun ViewyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ViewyColors, content = content)
}

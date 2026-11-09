package com.programmersbox.claudemanager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.nucleusframework.darkmodedetector.isSystemInDarkMode

/** Status colors, kept apart from the brand accent (matches the mockup). */
object StatusColors {
    val working = Color(0xFF0E8F7E)
    val waiting = Color(0xFFB97800)
    val idle = Color(0xFF8A91A2)
    val error = Color(0xFFCF3F3F)
}

private val Light = lightColorScheme(
    primary = Color(0xFF5B4BDB),
    primaryContainer = Color(0xFFECE9FF),
    background = Color(0xFFEEF0F4),
    surface = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF6F7FA),
    outlineVariant = Color(0xFFDFE2EA),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9D92FF),
    primaryContainer = Color(0xFF2A2550),
    background = Color(0xFF0F1116),
    surface = Color(0xFF171A22),
    surfaceContainer = Color(0xFF1D212B),
    outlineVariant = Color(0xFF2A2F3C),
)

@Composable
fun ClaudeManagerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkMode()) Dark else Light, content = content)
}

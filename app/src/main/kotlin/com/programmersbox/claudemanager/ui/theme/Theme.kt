package com.programmersbox.claudemanager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.nucleusframework.darkmodedetector.isSystemInDarkMode

/** Status colors, kept apart from the blue accent so state never reads as "selected". */
object StatusColors {
    val working = Color(0xFF12A37F)
    val waiting = Color(0xFFD08A00)
    val idle = Color(0xFF8394AD)
    val error = Color(0xFFD94848)
}

/** Terminal-style panels (tool log, shell command): deep navy in both themes. */
object TerminalColors {
    val background = Color(0xFF0D1626)
    val text = Color(0xFFC8D6EC)
    val dim = Color(0xFF6A7E9C)
}

private val Light = lightColorScheme(
    primary = Color(0xFF1F6FEB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FD),
    onPrimaryContainer = Color(0xFF0B3A86),
    secondary = Color(0xFF3B82C4),
    background = Color(0xFFEDF2F9),
    onBackground = Color(0xFF0F1B2D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F1B2D),
    onSurfaceVariant = Color(0xFF55657D),
    surfaceContainer = Color(0xFFF2F6FC),
    surfaceContainerHigh = Color(0xFFE8EFF9),
    outline = Color(0xFFA9B8CF),
    outlineVariant = Color(0xFFD5DFEC),
    error = Color(0xFFD94848),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF6EA8FF),
    onPrimary = Color(0xFF06204A),
    primaryContainer = Color(0xFF173263),
    onPrimaryContainer = Color(0xFFD6E5FF),
    secondary = Color(0xFF7FB2E5),
    background = Color(0xFF0A111E),
    onBackground = Color(0xFFE4ECF8),
    surface = Color(0xFF101A2B),
    onSurface = Color(0xFFE4ECF8),
    onSurfaceVariant = Color(0xFF93A5C1),
    surfaceContainer = Color(0xFF152238),
    surfaceContainerHigh = Color(0xFF1B2A44),
    outline = Color(0xFF3B4D6B),
    outlineVariant = Color(0xFF22324D),
    error = Color(0xFFFF7A7A),
)

@Composable
fun ClaudeManagerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkMode()) Dark else Light, content = content)
}

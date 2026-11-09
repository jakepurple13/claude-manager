package com.programmersbox.claudemanager.ui.tray

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.programmersbox.claudemanager.data.SessionRepository
import com.programmersbox.claudemanager.data.cost.CostEstimate
import com.programmersbox.claudemanager.data.settings.SettingsRepository
import com.programmersbox.claudemanager.ui.components.formatCost
import com.programmersbox.claudemanager.data.registry.SessionStatus
import com.programmersbox.claudemanager.ui.components.Chip
import com.programmersbox.claudemanager.ui.components.StatusDot
import com.programmersbox.claudemanager.ui.components.label
import com.programmersbox.claudemanager.ui.motion.RollingText
import com.programmersbox.claudemanager.ui.navigation.Navigator
import com.programmersbox.claudemanager.ui.theme.StatusColors
import dev.nucleusframework.application.NucleusApplicationScope
import dev.nucleusframework.composenativetray.trayapp.TrayApp
import org.koin.compose.koinInject

@Composable
fun NucleusApplicationScope.ClaudeManagerTray(
    onOpenWindow: () -> Unit,
    onQuit: () -> Unit,
    repository: SessionRepository = koinInject(),
    navigator: Navigator = koinInject(),
    settingsRepository: SettingsRepository = koinInject(),
) {
    val state by repository.state.collectAsState()
    val settings by settingsRepository.settings.collectAsState()
    val sessions = state.sessions
    val waiting = sessions.count { it.live.status == SessionStatus.Waiting }

    TrayApp(
        icon = TrayGlyph,
        tooltip = buildString {
            append("Claude Manager · ${sessions.size} sessions")
            if (waiting > 0) append(" · $waiting waiting")
        },
        windowSize = DpSize(340.dp, 460.dp),
        menu = {
            Item(label = "Open Claude Manager") { onOpenWindow() }
            Item(label = "Settings…") {
                navigator.openSettings()
                onOpenWindow()
            }
            Divider()
            Item(label = "Quit") { onQuit() }
        },
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Count(sessions.size, "sessions", Modifier.weight(1f))
                    Count(waiting, "waiting", Modifier.weight(1f), highlight = waiting > 0)
                    Count(sessions.sumOf { s -> s.activity.subagents.count { it.running } }, "agents", Modifier.weight(1f))
                    Count(sessions.sumOf { it.shells.size }, "shells", Modifier.weight(1f))
                }
                Heading("Sessions")
                LazyColumn(Modifier.weight(1f)) {
                    items(sessions, key = { it.live.sessionId }) { s ->
                        val running = s.activity.subagents.count { it.running } + s.shells.size
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .animateItem()
                                .clickable {
                                    navigator.openSession(s.live.sessionId)
                                    onOpenWindow()
                                }
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            StatusDot(s.live.status)
                            Text(s.displayName, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.5.sp)
                            when {
                                s.live.status == SessionStatus.Waiting -> Chip("waiting")
                                running > 0 -> Chip("$running running")
                                else -> Text(s.live.status.label(), fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (state.cdmAvailable) {
                    Heading("Devices")
                    state.devices.forEach { d ->
                        val holder = sessions.firstOrNull { it.live.sessionId == d.holderSessionId }?.displayName
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 6.dp)) {
                            StatusDot(if (d.holderSessionId != null) SessionStatus.Working else SessionStatus.Idle)
                            Text(
                                if (d.holderSessionId != null) "${d.model} → ${holder ?: "another session"}" else "${d.model} · free",
                                Modifier.weight(1f),
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(d.platform, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${sessions.sumOf { it.activity.outputTokens } / 1000}k output tokens" +
                            if (settings.showCost) " · ${formatCost(sessions.fold(CostEstimate.Zero) { acc, it -> acc + it.activity.cost })}" else "",
                        Modifier.weight(1f),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(onClick = onOpenWindow) { Text("Open Manager") }
                }
            }
        }
    }
}

@Composable
private fun Count(value: Int, label: String, modifier: Modifier, highlight: Boolean = false) {
    Column(
        modifier.background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(7.dp)).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RollingText(
            "$value",
            TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = if (highlight) StatusColors.waiting else MaterialTheme.colorScheme.onSurface),
        )
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Heading(text: String) {
    Text(text.uppercase(), fontSize = 10.5.sp, letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Six-spoke asterisk drawn as a vector so no icon files are needed. */
private val TrayGlyph: ImageVector = ImageVector.Builder("tray", 24.dp, 24.dp, 24f, 24f).apply {
    path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.4f, fill = SolidColor(Color.Transparent)) {
        moveTo(12f, 3f); lineTo(12f, 21f)
        moveTo(4.2f, 7.5f); lineTo(19.8f, 16.5f)
        moveTo(4.2f, 16.5f); lineTo(19.8f, 7.5f)
    }
}.build()

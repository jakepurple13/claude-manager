package com.programmersbox.claudemanager.ui.tray

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.programmersbox.claudemanager.data.SessionRepository
import com.programmersbox.claudemanager.data.cost.CostEstimate
import com.programmersbox.claudemanager.data.registry.SessionStatus
import com.programmersbox.claudemanager.data.settings.SettingsRepository
import com.programmersbox.claudemanager.ui.components.color
import com.programmersbox.claudemanager.ui.components.formatCost
import com.programmersbox.claudemanager.ui.components.label
import com.programmersbox.claudemanager.ui.navigation.Navigator
import com.programmersbox.claudemanager.ui.theme.StatusColors
import dev.nucleusframework.composenativetray.tray.api.Tray
import org.koin.compose.koinInject

/**
 * The menu bar / system tray menu. A native menu, so the OS positions it on whichever screen holds
 * the icon. (Nucleus's TrayApp popup lands off-screen when the menu bar is on a monitor whose scale
 * differs from the primary one.) Labels avoid per-second values so the menu only rebuilds when
 * sessions actually change.
 */
@Composable
fun ClaudeManagerTray(
    onOpenWindow: () -> Unit,
    onQuit: () -> Unit,
    repository: SessionRepository = koinInject(),
    navigator: Navigator = koinInject(),
    settingsRepository: SettingsRepository = koinInject(),
) {
    val state by repository.state.collectAsState()
    val settings by settingsRepository.settings.collectAsState()
    val sessions = state.sessions
    val waiting = sessions.filter { it.live.status == SessionStatus.Waiting }
    val agents = sessions.sumOf { s -> s.activity.subagents.count { it.running } }
    val shells = sessions.sumOf { it.shells.size }

    fun open(sessionId: String, timeline: Boolean = false) {
        navigator.openSession(sessionId, timeline)
        onOpenWindow()
    }

    Tray(
        icon = TrayGlyph,
        tooltip = buildString {
            append("Claude Manager · ${sessions.size} session${if (sessions.size == 1) "" else "s"}")
            if (waiting.isNotEmpty()) append(" · ${waiting.size} waiting")
        },
    ) {
        Item(
            label = buildString {
                append("${sessions.size} session${if (sessions.size == 1) "" else "s"}")
                if (agents > 0) append(" · $agents agent${if (agents == 1) "" else "s"}")
                if (shells > 0) append(" · $shells shell${if (shells == 1) "" else "s"}")
                if (settings.showCost) append(" · ${formatCost(sessions.fold(CostEstimate.Zero) { acc, it -> acc + it.activity.cost })}")
            },
            isEnabled = false,
        )

        if (waiting.isNotEmpty()) {
            Divider()
            Item(label = "Waiting for you", isEnabled = false)
            waiting.forEach { s ->
                Item(s.displayName, Dot, StatusColors.waiting) { open(s.live.sessionId) }
            }
        }

        Divider()
        if (sessions.isEmpty()) {
            Item(label = "No sessions running", isEnabled = false)
        }
        sessions.forEach { s ->
            val running = s.activity.subagents.count { it.running } + s.shells.size
            val detail = when {
                running > 0 -> "${s.live.status.label()} · $running running"
                else -> s.live.status.label()
            }
            SubMenu("${s.displayName} — $detail", Dot, s.live.status.color()) {
                Item(label = "Open") { open(s.live.sessionId) }
                Item(label = "Open timeline") { open(s.live.sessionId, timeline = true) }
                s.activity.title?.let {
                    Divider()
                    Item(label = it, isEnabled = false)
                }
                if (s.devices.isNotEmpty()) {
                    Divider()
                    s.devices.forEach { d -> Item(label = "Holds ${d.model} (${d.serial})", isEnabled = false) }
                }
            }
        }

        if (state.cdmAvailable && state.devices.isNotEmpty()) {
            Divider()
            SubMenu(label = "Devices") {
                state.devices.forEach { d ->
                    val holder = sessions.firstOrNull { it.live.sessionId == d.holderSessionId }?.displayName
                    val label = when {
                        d.holderSessionId == null -> "${d.model} · free"
                        else -> "${d.model} → ${holder ?: "another session"}"
                    }
                    Item(label, Dot, if (d.holderSessionId != null) StatusColors.working else StatusColors.idle) {}
                }
            }
        }

        Divider()
        Item(label = "Open Claude Manager") { onOpenWindow() }
        Item(label = "Settings…") {
            navigator.openSettings()
            onOpenWindow()
        }
        Divider()
        Item(label = "Quit Claude Manager") { onQuit() }
    }
}

/** A filled circle, tinted per item to show status in the native menu. */
private val Dot: ImageVector = ImageVector.Builder("dot", 16.dp, 16.dp, 16f, 16f).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(8f, 3f)
        arcTo(5f, 5f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 8f, y1 = 13f)
        arcTo(5f, 5f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 8f, y1 = 3f)
        close()
    }
}.build()

/** Six-spoke asterisk drawn as a vector so no icon files are needed. */
private val TrayGlyph: ImageVector = ImageVector.Builder("tray", 24.dp, 24.dp, 24f, 24f).apply {
    path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.4f, fill = SolidColor(Color.Transparent)) {
        moveTo(12f, 3f); lineTo(12f, 21f)
        moveTo(4.2f, 7.5f); lineTo(19.8f, 16.5f)
        moveTo(4.2f, 16.5f); lineTo(19.8f, 7.5f)
    }
}.build()

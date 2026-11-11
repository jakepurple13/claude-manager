package com.programmersbox.claudemanager.ui.tray

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.programmersbox.claudemanager.ClaudePaths
import com.programmersbox.claudemanager.data.SessionRepository
import com.programmersbox.claudemanager.data.runningChildren
import com.programmersbox.claudemanager.data.runningTasks
import com.programmersbox.claudemanager.data.visibleShells
import com.programmersbox.claudemanager.data.cost.CostEstimate
import com.programmersbox.claudemanager.data.registry.SessionStatus
import com.programmersbox.claudemanager.data.settings.SettingsRepository
import com.programmersbox.claudemanager.ui.components.formatCost
import com.programmersbox.claudemanager.ui.components.label
import com.programmersbox.claudemanager.ui.navigation.Navigator

import org.koin.compose.koinInject

/**
 * The menu bar / system tray menu. A native menu, so the OS positions it on whichever screen holds
 * the icon. (Nucleus's TrayApp popup lands off-screen when the menu bar is on a monitor whose scale
 * differs from the primary one.) Labels avoid per-second values so the menu only rebuilds when
 * sessions actually change.
 *
 * The icon is a fixed file on disk rather than an ImageVector; see [TrayIconFiles] for the native
 * crash that avoids. Status dots are emoji in the labels, not item icons, because the library writes
 * every item icon to a new temp file on each menu rebuild.
 */
@Composable
fun ClaudeManagerTray(
    onOpenWindow: () -> Unit,
    onQuit: () -> Unit,
    repository: SessionRepository = koinInject(),
    navigator: Navigator = koinInject(),
    settingsRepository: SettingsRepository = koinInject(),
    paths: ClaudePaths = koinInject(),
) {
    val state by repository.state.collectAsState()
    val settings by settingsRepository.settings.collectAsState()
    val sessions = state.sessions
    val waiting = sessions.filter { it.live.status == SessionStatus.Waiting }
    val agents = sessions.sumOf { s -> s.activity.subagents.count { it.running } }
    val shells = sessions.sumOf { it.visibleShells.size }
    val tasks = sessions.sumOf { it.runningTasks().size }

    fun open(sessionId: String, timeline: Boolean = false) {
        navigator.openSession(sessionId, timeline)
        onOpenWindow()
    }

    val icon = remember { trayIconFiles(paths.appDataDir.resolve("tray")) }

    OffMainTray(
        iconPath = icon.png.toString(),
        windowsIconPath = icon.ico.toString(),
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
                if (tasks > 0) append(" · $tasks monitor${if (tasks == 1) "" else "s"}/workflow${if (tasks == 1) "" else "s"}")
                if (settings.showCost) append(" · ${formatCost(sessions.fold(CostEstimate.Zero) { acc, it -> acc + it.activity.cost })}")
            },
            isEnabled = false,
        )

        if (waiting.isNotEmpty()) {
            Divider()
            Item(label = "Waiting for you", isEnabled = false)
            waiting.forEach { s ->
                Item("${SessionStatus.Waiting.dot()} ${s.displayName}") { open(s.live.sessionId) }
            }
        }

        Divider()
        if (sessions.isEmpty()) {
            Item(label = "No sessions running", isEnabled = false)
        }
        sessions.forEach { s ->
            val running = s.runningChildren()
            val detail = when {
                running > 0 -> "${s.live.status.label()} · $running running"
                else -> s.live.status.label()
            }
            SubMenu("${s.live.status.dot()} ${s.displayName} — $detail") {
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
                    val dot = (if (d.holderSessionId != null) SessionStatus.Working else SessionStatus.Idle).dot()
                    Item("$dot $label") {}
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

/** Status dot as an emoji, so the native menu shows color without an item icon. */
private fun SessionStatus.dot(): String = when (this) {
    SessionStatus.Working -> "\uD83D\uDFE2" // 🟢
    SessionStatus.Waiting -> "\uD83D\uDFE0" // 🟠
    SessionStatus.Idle, SessionStatus.Unknown -> "\u26AA" // ⚪
}

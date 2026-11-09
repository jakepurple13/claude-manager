package com.programmersbox.claudemanager.data.notify

import com.programmersbox.claudemanager.AppScope
import com.programmersbox.claudemanager.AppWindow
import com.programmersbox.claudemanager.data.SessionRepository
import com.programmersbox.claudemanager.data.SessionsState
import com.programmersbox.claudemanager.data.registry.SessionStatus
import com.programmersbox.claudemanager.data.settings.AppSettings
import com.programmersbox.claudemanager.data.settings.SettingsRepository
import com.programmersbox.claudemanager.ui.navigation.Navigator
import com.programmersbox.claudemanager.util.firstLine
import dev.nucleusframework.notification.common.NotificationManager
import dev.nucleusframework.notification.common.NotificationResult
import dev.nucleusframework.notification.common.notification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** What the notifier tells the user about, given the previous and current state. */
sealed interface SessionEvent {
    data class Waiting(val sessionId: String, val name: String, val detail: String?) : SessionEvent
    data class FinishedTurn(val sessionId: String, val name: String, val workedMs: Long) : SessionEvent
    data class OrphanShell(val pid: Long, val command: String) : SessionEvent
}

object SessionEvents {
    /** Turns shorter than this don't notify; quick replies would be noise. */
    const val MIN_TURN_MS = 20_000L

    fun diff(
        previous: SessionsState,
        current: SessionsState,
        workingSince: Map<String, Long>,
        now: Long,
        minTurnMs: Long = MIN_TURN_MS,
    ): List<SessionEvent> {
        if (!previous.loaded) return emptyList()
        val before = previous.sessions.associateBy { it.live.sessionId }
        val events = mutableListOf<SessionEvent>()
        current.sessions.forEach { s ->
            val old = before[s.live.sessionId] ?: return@forEach
            val was = old.live.status
            val isNow = s.live.status
            if (was != SessionStatus.Waiting && isNow == SessionStatus.Waiting) {
                events += SessionEvent.Waiting(s.live.sessionId, s.displayName, s.activity.current?.summary)
            }
            if (was == SessionStatus.Working && isNow == SessionStatus.Idle) {
                val worked = now - (workingSince[s.live.sessionId] ?: now)
                if (worked >= minTurnMs) events += SessionEvent.FinishedTurn(s.live.sessionId, s.displayName, worked)
            }
        }
        val knownOrphans = previous.orphanShells.map { it.pid }.toSet()
        current.orphanShells.filter { it.pid !in knownOrphans }.forEach { events += SessionEvent.OrphanShell(it.pid, it.command) }
        return events
    }
}

/** Sends system notifications for [SessionEvent]s; clicking one opens the session. */
class SessionNotifier(
    private val repository: SessionRepository,
    private val navigator: Navigator,
    private val window: AppWindow,
    private val settings: SettingsRepository,
    private val scope: AppScope,
) {
    private var job: Job? = null
    private val workingSince = HashMap<String, Long>()

    fun start() {
        if (job != null) return
        job = scope.launch {
            runCatching { NotificationManager.initialize() }
            var previous = SessionsState()
            repository.state.collect { state ->
                val now = System.currentTimeMillis()
                val prefs = settings.settings.value
                SessionEvents.diff(previous, state, workingSince, now, prefs.minTurnSeconds * 1000L)
                    .filter { it.enabledIn(prefs) }
                    .forEach(::send)
                state.sessions.forEach { s ->
                    if (s.live.status == SessionStatus.Working) workingSince.putIfAbsent(s.live.sessionId, now)
                    else workingSince.remove(s.live.sessionId)
                }
                previous = state
            }
        }
    }

    private fun SessionEvent.enabledIn(prefs: AppSettings): Boolean = when (this) {
        is SessionEvent.Waiting -> prefs.notifyWaiting
        is SessionEvent.FinishedTurn -> prefs.notifyFinished
        is SessionEvent.OrphanShell -> prefs.notifyOrphanShells
    }

    private fun send(event: SessionEvent) {
        val (title, message, sessionId) = when (event) {
            is SessionEvent.Waiting -> Triple("${event.name} is waiting for you", event.detail ?: "Answer it in its terminal.", event.sessionId)
            is SessionEvent.FinishedTurn -> Triple("${event.name} finished its turn", "Worked for ${event.workedMs / 60_000}m ${event.workedMs / 1000 % 60}s.", event.sessionId)
            is SessionEvent.OrphanShell -> Triple("A shell outlived its session", "${event.command.firstLine(80)} (pid ${event.pid})", null)
        }
        val result = runCatching {
            notification(
                title = title,
                message = message,
                onActivated = { open(sessionId) },
            ).send()
        }.getOrNull()
        if (result is NotificationResult.Failure) System.err.println("Notification not shown: ${result.reason}")
    }

    /** Callbacks arrive off the UI thread; hop to Main before touching Compose state. */
    private fun open(sessionId: String?) {
        scope.launch(Dispatchers.Main) {
            window.show()
            sessionId?.let { navigator.openSession(it) }
        }
    }
}

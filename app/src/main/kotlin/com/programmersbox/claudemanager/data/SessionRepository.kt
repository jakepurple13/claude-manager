package com.programmersbox.claudemanager.data

import com.programmersbox.claudemanager.AppScope
import com.programmersbox.claudemanager.data.cdm.CdmClient
import com.programmersbox.claudemanager.data.cdm.CdmDevice
import com.programmersbox.claudemanager.data.db.SessionLabelDao
import com.programmersbox.claudemanager.data.settings.SettingsRepository
import com.programmersbox.claudemanager.data.db.SessionLabelEntity
import com.programmersbox.claudemanager.data.process.ProcessScanner
import com.programmersbox.claudemanager.data.process.ShellProcess
import com.programmersbox.claudemanager.data.registry.LiveSession
import com.programmersbox.claudemanager.data.registry.SessionRegistry
import com.programmersbox.claudemanager.data.transcript.SessionActivity
import com.programmersbox.claudemanager.data.transcript.TranscriptStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** A running shell, joined with what the transcript says about it. */
data class ShellInfo(
    val pid: Long,
    val command: String,
    val description: String?,
    val background: Boolean,
    val startedAt: Long,
    val cpuPercent: Float,
    val memoryBytes: Long,
    /** Tool-use id of the Monitor this shell runs, if any; shown under the monitor instead. */
    val monitorOf: String? = null,
)

data class SessionSummary(
    val live: LiveSession,
    val displayName: String,
    val pinned: Boolean,
    val activity: SessionActivity,
    val shells: List<ShellInfo>,
    /** Devices this session holds through Claude Device Manager. */
    val devices: List<CdmDevice> = emptyList(),
)

/** Shells that aren't a monitor's process (those show under their monitor). */
val SessionSummary.visibleShells: List<ShellInfo> get() = shells.filter { it.monitorOf == null }

fun SessionSummary.runningTasks(now: Long = System.currentTimeMillis()) = activity.tasks.filter { it.isRunning(now) }

/** Running agents, shells, monitors and workflows. */
fun SessionSummary.runningChildren(now: Long = System.currentTimeMillis()): Int =
    activity.subagents.count { it.running } + visibleShells.size + runningTasks(now).size

data class SessionsState(
    val sessions: List<SessionSummary> = emptyList(),
    val orphanShells: List<ShellProcess> = emptyList(),
    /** All cdm devices; empty when cdm isn't installed. */
    val devices: List<CdmDevice> = emptyList(),
    val cdmAvailable: Boolean = false,
    val loaded: Boolean = false,
)

class SessionRepository(
    registry: SessionRegistry,
    private val labels: SessionLabelDao,
    private val transcripts: TranscriptStore,
    private val processes: ProcessScanner,
    private val cdm: CdmClient,
    private val settings: SettingsRepository,
    scope: AppScope,
) {
    private val ticks: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(settings.settings.value.refreshSeconds.coerceIn(1, 60) * 1000L)
        }
    }

    /** One shared pipeline for the window and the tray, refreshed at the user's chosen interval. */
    val state: StateFlow<SessionsState> =
        combine(registry.sessions, labels.observeAll(), ticks) { live, labelList, _ -> live to labelList }
            .conflate()
            .map { (live, labelList) -> build(live, labelList.associateBy { it.sessionId }) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), SessionsState())

    val sessions: Flow<List<SessionSummary>> = state.map { it.sessions }

    private fun build(live: List<LiveSession>, labelsById: Map<String, SessionLabelEntity>): SessionsState {
        transcripts.retain(live.map { it.sessionId }.toSet())
        val snapshot = processes.scan(live.map { it.pid }.toSet())
        val devices = cdm.devices()
        val devicesByHolder = devices.filter { it.holderSessionId != null }.groupBy { it.holderSessionId!! }
        val sessions = live.map { s ->
            val label = labelsById[s.sessionId]
            val activity = runCatching { transcripts.activity(s.sessionId) }.getOrDefault(SessionActivity())
            val background = activity.backgroundCommands.associateBy { it.command.trim() }
            val now = System.currentTimeMillis()
            val monitors = activity.tasks
                .filter { it.kind == com.programmersbox.claudemanager.data.transcript.TaskKind.Monitor && it.isRunning(now) && it.detail != null }
                .associateBy { it.detail!!.trim() }
            SessionSummary(
                live = s,
                displayName = label?.customName?.takeIf { it.isNotBlank() } ?: s.name,
                pinned = label?.pinned == true,
                activity = activity,
                shells = snapshot.shellsByOwner[s.pid].orEmpty().map { p ->
                    val bg = background[p.command.trim()]
                    // A foreground shell is the Bash call in progress: label it with that call's description.
                    val foreground = activity.current?.takeIf { bg == null && it.name == "Bash" }?.summary?.removePrefix("Bash · ")
                    val monitor = monitors[p.command.trim()]
                    ShellInfo(
                        pid = p.pid,
                        command = p.command,
                        description = monitor?.label ?: bg?.description ?: foreground,
                        background = bg != null || monitor != null,
                        startedAt = p.startedAt,
                        cpuPercent = p.cpuPercent,
                        memoryBytes = p.memoryBytes,
                        monitorOf = monitor?.toolUseId,
                    )
                }.sortedBy { it.startedAt },
                devices = devicesByHolder[s.sessionId].orEmpty(),
            )
        }.sortedWith(compareByDescending<SessionSummary> { it.pinned }.thenBy { it.live.startedAt })
        return SessionsState(sessions, snapshot.orphans, devices, cdm.isAvailable, loaded = true)
    }

    suspend fun rename(sessionId: String, name: String?) {
        val current = labels.get(sessionId) ?: SessionLabelEntity(sessionId)
        labels.upsert(current.copy(customName = name?.trim()?.ifBlank { null }))
    }

    suspend fun setPinned(sessionId: String, pinned: Boolean) {
        val current = labels.get(sessionId) ?: SessionLabelEntity(sessionId)
        labels.upsert(current.copy(pinned = pinned))
    }

    /**
     * Asks the session's Claude process to exit (SIGTERM on macOS/Linux, terminate on Windows).
     * Returns false if the process is gone or refused the request.
     */
    fun stopSession(pid: Long): Boolean =
        ProcessHandle.of(pid).map { it.destroy() }.orElse(false)

    /** Ends a shell and the processes it started. */
    fun killShell(pid: Long): Boolean = processes.kill(pid)

}

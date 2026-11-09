package com.programmersbox.claudemanager.data

import com.programmersbox.claudemanager.AppScope
import com.programmersbox.claudemanager.data.db.HistoryDao
import com.programmersbox.claudemanager.data.db.SessionRecordEntity
import com.programmersbox.claudemanager.data.db.StatusSegmentEntity
import com.programmersbox.claudemanager.data.settings.SettingsRepository
import com.programmersbox.claudemanager.util.firstLine
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch

/**
 * Turns the live state into history: every change of a session's status, and every shell's
 * lifetime, becomes a [StatusSegmentEntity]. Runs for the app's whole life so history keeps
 * recording while the window is closed.
 */
class HistoryRecorder(
    private val repository: SessionRepository,
    private val history: HistoryDao,
    private val settings: SettingsRepository,
    private val scope: AppScope,
) {
    private data class Open(val id: Long, val status: String)
    private data class Desired(val sessionId: String, val subject: String, val label: String, val status: String)

    private val open = HashMap<Pair<String, String>, Open>()
    private val lock = Mutex()
    private val lastRecordWrite = HashMap<String, Long>()
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            val now = System.currentTimeMillis()
            history.closeDanglingSegments()
            val retentionMs = settings.current().historyDays * DAY_MS
            history.deleteSegmentsBefore(now - retentionMs)
            history.deleteSessionsBefore(now - retentionMs)
            repository.state.collect { state -> if (state.loaded) lock.withLock { record(state, System.currentTimeMillis()) } }
        }
    }

    /**
     * Deletes all recorded history (labels stay). Current statuses start fresh segments on the
     * next refresh because the recorder forgets what it had open.
     */
    suspend fun clearHistory() = lock.withLock {
        history.deleteAllSegments()
        history.deleteAllSessions()
        open.clear()
        lastRecordWrite.clear()
    }

    private suspend fun record(state: SessionsState, now: Long) {
        val desired = buildMap {
            state.sessions.forEach { s ->
                put(s.live.sessionId to MAIN, Desired(s.live.sessionId, MAIN, s.displayName, s.live.status.name))
                s.shells.forEach { shell ->
                    val subject = "shell:${shell.pid}"
                    put(s.live.sessionId to subject, Desired(s.live.sessionId, subject, shell.description ?: shell.command.firstLine(), "Working"))
                }
            }
        }

        // Close what disappeared; a vanished main subject means the session ended.
        (open.keys - desired.keys).forEach { key ->
            open.remove(key)?.let { history.closeSegment(it.id, now) }
            if (key.second == MAIN) {
                history.markEnded(key.first, now)
                lastRecordWrite.remove(key.first)
            }
        }

        desired.forEach { (key, want) ->
            val current = open[key]
            if (current?.status == want.status) return@forEach
            current?.let { history.closeSegment(it.id, now) }
            val id = history.insertSegment(
                StatusSegmentEntity(sessionId = want.sessionId, subject = want.subject, label = want.label, status = want.status, startAt = now),
            )
            open[key] = Open(id, want.status)
        }

        state.sessions.forEach { s ->
            val last = lastRecordWrite[s.live.sessionId]
            if (last == null || now - last >= RECORD_WRITE_MS) {
                history.upsertSession(
                    SessionRecordEntity(
                        sessionId = s.live.sessionId,
                        name = s.displayName,
                        cwd = s.live.cwd,
                        source = s.live.source.name,
                        pid = s.live.pid,
                        startedAt = s.live.startedAt,
                        lastSeenAt = now,
                    ),
                )
                lastRecordWrite[s.live.sessionId] = now
            }
        }
    }

    private companion object {
        const val MAIN = "main"
        const val RECORD_WRITE_MS = 15_000L
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

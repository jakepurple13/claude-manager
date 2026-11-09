package com.programmersbox.claudemanager.data.registry

import com.programmersbox.claudemanager.ClaudePaths
import dev.nucleusframework.fswatcher.FsWatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText

/** Reads Claude Code's live session registry and re-reads it whenever it changes. */
class SessionRegistry(private val paths: ClaudePaths) {

    private val json = Json { ignoreUnknownKeys = true }

    /** Current live sessions; emits on every registry change, plus a slow safety poll. */
    val sessions: Flow<List<LiveSession>> =
        merge(changes(), poll())
            .onStart { emit(Unit) }
            .conflate()
            .map { readAll() }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    fun readAll(): List<LiveSession> {
        val dir = paths.sessionsDir
        if (!dir.exists()) return emptyList()
        return dir.listDirectoryEntries()
            .filter { it.extension == "json" }
            .mapNotNull { file -> runCatching { json.decodeFromString<RegistryEntry>(file.readText()) }.getOrNull() }
            // A file can outlive a crashed process; only keep sessions whose process is alive.
            .filter { ProcessHandle.of(it.pid).map(ProcessHandle::isAlive).orElse(false) }
            .map { it.toLiveSession() }
            .sortedBy { it.startedAt }
    }

    private fun changes(): Flow<Unit> = callbackFlow {
        if (!FsWatchers.isSupported()) {
            awaitClose()
            return@callbackFlow
        }
        val watcher = FsWatchers.create()
        val job = launch { watcher.events.collect { trySend(Unit) } }
        runCatching { paths.sessionsDir.createDirectories() }
        val registration = watcher.watch(paths.sessionsDir, recursive = false)
        awaitClose {
            job.cancel()
            registration.close()
            watcher.close()
        }
    }

    private fun poll(): Flow<Unit> = flow {
        while (true) {
            delay(POLL_MS)
            emit(Unit)
        }
    }

    private companion object {
        const val POLL_MS = 5_000L
    }
}

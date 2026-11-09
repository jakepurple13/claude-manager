package com.programmersbox.claudemanager.data.transcript

import com.programmersbox.claudemanager.ClaudePaths
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * Reads session and subagent transcripts incrementally: each call only parses lines appended
 * since the previous call. Not thread-safe; the repository calls it from one coroutine.
 */
class TranscriptStore(private val paths: ClaudePaths) {

    private class Tail(val accumulator: TranscriptAccumulator = TranscriptAccumulator()) {
        var offset = 0L
        val partial = StringBuilder()
    }

    private val tails = HashMap<Path, Tail>()
    private val locations = HashMap<String, Path>()
    private val json = Json { ignoreUnknownKeys = true }

    fun activity(sessionId: String): SessionActivity {
        val transcript = locate(sessionId) ?: return SessionActivity()
        val main = read(transcript)
        return SessionActivity(
            title = main.title,
            model = main.model,
            gitBranch = main.gitBranch,
            contextTokens = main.contextTokens,
            outputTokens = main.outputTokens,
            toolCount = main.toolCount,
            current = main.current,
            recent = main.recent(8),
            lastText = main.lastText,
            lastActivityAt = main.lastActivityAt,
            subagents = subagents(transcript.parent.resolve(sessionId).resolve("subagents")),
            backgroundCommands = main.backgroundCommands,
            toolTimes = main.toolTimes,
        )
    }

    /** Drops cached state for sessions that are no longer live. */
    fun retain(sessionIds: Set<String>) {
        val dropped = locations.keys - sessionIds
        dropped.forEach { id ->
            val path = locations.remove(id) ?: return@forEach
            tails.keys.removeAll { it == path || it.startsWith(path.parent.resolve(id)) }
        }
    }

    private fun locate(sessionId: String): Path? {
        locations[sessionId]?.takeIf { it.exists() }?.let { return it }
        if (!paths.projectsDir.exists()) return null
        val found = paths.projectsDir.listDirectoryEntries()
            .asSequence()
            .filter { it.isDirectory() }
            .map { it.resolve("$sessionId.jsonl") }
            .firstOrNull { it.exists() }
        if (found != null) locations[sessionId] = found
        return found
    }

    private fun subagents(dir: Path): List<SubagentInfo> {
        if (!dir.isDirectory()) return emptyList()
        return dir.listDirectoryEntries("agent-*.jsonl").mapNotNull { file ->
            val id = file.name.removePrefix("agent-").removeSuffix(".jsonl")
            val meta = dir.resolve("agent-$id.meta.json").takeIf { it.exists() }
                ?.let { runCatching { json.parseToJsonElement(it.readText()).jsonObject }.getOrNull() }
            val acc = read(file)
            val created = runCatching { Files.readAttributes(file, "creationTime")["creationTime"].toString() }.getOrNull()
            SubagentInfo(
                id = id,
                type = meta?.str("agentType") ?: "agent",
                description = meta?.str("description") ?: acc.title ?: id,
                model = acc.model ?: meta?.str("model"),
                startedAt = created?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() },
                lastActivityAt = acc.lastActivityAt,
                running = meta?.bool("finished") != true,
                toolCount = acc.toolCount,
                current = acc.current,
                recent = acc.recent(6),
                lastText = acc.lastText,
                toolTimes = acc.toolTimes,
            )
        }.sortedBy { it.startedAt ?: 0 }
    }

    private fun read(file: Path): TranscriptAccumulator {
        val tail = tails.getOrPut(file) { Tail() }
        val size = runCatching { file.fileSize() }.getOrDefault(0)
        if (size < tail.offset) {
            // File was truncated or replaced; start over.
            tails[file] = Tail()
            return read(file)
        }
        if (size == tail.offset) return tail.accumulator
        RandomAccessFile(file.toFile(), "r").use { raf ->
            raf.seek(tail.offset)
            val bytes = ByteArray((size - tail.offset).toInt())
            raf.readFully(bytes)
            tail.offset = size
            tail.partial.append(bytes.decodeToString())
        }
        val text = tail.partial
        var start = 0
        while (true) {
            val newline = text.indexOf('\n', start)
            if (newline < 0) break
            tail.accumulator.accept(text.substring(start, newline))
            start = newline + 1
        }
        text.delete(0, start)
        return tail.accumulator
    }
}

package com.programmersbox.claudemanager.data.registry

import kotlinx.serialization.Serializable

/**
 * One entry of Claude Code's live session registry (`~/.claude/sessions/<pid>.json`).
 * Undocumented format: every field except the identifiers is optional, and unknown fields are ignored.
 * The registry's `.key` files and socket paths are deliberately not read.
 */
@Serializable
data class RegistryEntry(
    val pid: Long,
    val sessionId: String,
    val cwd: String = "",
    val startedAt: Long = 0,
    val version: String? = null,
    val kind: String? = null,
    val entrypoint: String? = null,
    val name: String? = null,
    val status: String? = null,
    val updatedAt: Long? = null,
    val statusUpdatedAt: Long? = null,
)

enum class SessionSource(val label: String) { Terminal("Terminal"), Ide("IDE"), Desktop("Desktop"), Other("Other") }

enum class SessionStatus { Working, Idle, Waiting, Unknown }

data class LiveSession(
    val pid: Long,
    val sessionId: String,
    val cwd: String,
    val name: String,
    val source: SessionSource,
    val status: SessionStatus,
    val startedAt: Long,
    val statusSince: Long?,
    val version: String?,
)

fun RegistryEntry.toLiveSession(): LiveSession = LiveSession(
    pid = pid,
    sessionId = sessionId,
    cwd = cwd,
    name = name ?: cwd.substringAfterLast('/').substringAfterLast('\\').ifBlank { sessionId.take(8) },
    source = when {
        entrypoint == null -> SessionSource.Other
        entrypoint == "cli" -> SessionSource.Terminal
        "desktop" in entrypoint -> SessionSource.Desktop
        listOf("vscode", "jetbrains", "ide", "cursor").any { it in entrypoint } -> SessionSource.Ide
        else -> SessionSource.Other
    },
    status = when (status) {
        "busy" -> SessionStatus.Working
        "idle" -> SessionStatus.Idle
        "waiting" -> SessionStatus.Waiting
        else -> SessionStatus.Unknown
    },
    startedAt = startedAt,
    statusSince = statusUpdatedAt,
    version = version,
)

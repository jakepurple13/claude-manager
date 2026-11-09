package com.programmersbox.claudemanager

import java.nio.file.Path
import kotlin.io.path.Path

/** Locations on disk that Claude Code writes and Claude Manager reads or owns. */
class ClaudePaths {
    /** Claude Code's config dir. Honors CLAUDE_CONFIG_DIR like the CLI does. */
    val claudeHome: Path = System.getenv("CLAUDE_CONFIG_DIR")?.let(::Path)
        ?: Path(System.getProperty("user.home"), ".claude")

    /** One `<pid>.json` per live session, maintained by Claude Code. */
    val sessionsDir: Path = claudeHome.resolve("sessions")

    /** Transcripts: `projects/<slug>/<sessionId>.jsonl`. */
    val projectsDir: Path = claudeHome.resolve("projects")

    /** Claude Manager's own data (Room database). */
    val appDataDir: Path = run {
        val home = System.getProperty("user.home")
        val os = System.getProperty("os.name").lowercase()
        when {
            os.contains("mac") -> Path(home, "Library", "Application Support", "ClaudeManager")
            os.contains("win") -> Path(System.getenv("APPDATA") ?: home, "ClaudeManager")
            else -> Path(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share", "claude-manager")
        }
    }
}

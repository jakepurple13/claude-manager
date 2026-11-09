package com.programmersbox.claudemanager.data.process

import dev.nucleusframework.systeminfo.SystemInfo

/** A shell Claude Code started for a Bash tool call, found in the process table. */
data class ShellProcess(
    val pid: Long,
    val parentPid: Long?,
    /** The command Claude asked to run, unwrapped from Claude Code's shell preamble. */
    val command: String,
    val startedAt: Long,
    val cpuPercent: Float,
    val memoryBytes: Long,
)

data class ProcessSnapshot(
    /** Shells by the PID of the Claude process that owns them. */
    val shellsByOwner: Map<Long, List<ShellProcess>>,
    /** Shells whose Claude process is gone. */
    val orphans: List<ShellProcess>,
)

/**
 * Finds Claude Code's Bash shells: processes whose command line sources a file in
 * `~/.claude/shell-snapshots/` and then runs `eval '<command>'`.
 */
class ProcessScanner {

    fun scan(livePids: Set<Long>): ProcessSnapshot {
        // ProcessHandle gives the full command line; SystemInfo truncates it for shells.
        val shells = ProcessHandle.allProcesses().toList().mapNotNull { handle ->
            val line = handle.info().commandLine().orElse(null) ?: return@mapNotNull null
            if (SIGNATURE !in line) return@mapNotNull null
            val details = if (SystemInfo.isAvailable()) SystemInfo.process(handle.pid()) else null
            ShellProcess(
                pid = handle.pid(),
                parentPid = handle.parent().map(ProcessHandle::pid).orElse(null),
                command = extractCommand(line),
                startedAt = handle.info().startInstant().map { it.toEpochMilli() }.orElse(0L),
                cpuPercent = details?.cpuUsage ?: 0f,
                memoryBytes = details?.memory ?: 0L,
            )
        }
        val shellPids = shells.map { it.pid }.toSet()
        return ProcessSnapshot(
            shellsByOwner = shells.filter { it.parentPid in livePids }.groupBy { it.parentPid!! },
            // Re-parented to init/launchd, or the parent has exited: nobody will clean these up.
            // A shell started by another shell is part of that shell's tree, not an orphan.
            orphans = shells.filter { p ->
                p.parentPid !in shellPids && (p.parentPid == null || p.parentPid == 1L || !isAlive(p.parentPid))
            },
        )
    }

    /** Ends a shell and everything it started. */
    fun kill(pid: Long): Boolean {
        val handle = ProcessHandle.of(pid).orElse(null) ?: return false
        handle.descendants().forEach { it.destroy() }
        return handle.destroy()
    }

    private fun isAlive(pid: Long): Boolean = ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)

    companion object {
        private const val SIGNATURE = "shell-snapshots/snapshot-"
        private val EVAL = Regex("""eval '(.*)' < /dev/null""", RegexOption.DOT_MATCHES_ALL)

        fun extractCommand(line: String): String =
            EVAL.find(line)?.groupValues?.get(1)?.replace("'\\''", "'")?.replace("'\"'\"'", "'")?.trim() ?: line.takeLast(120)
    }
}

package com.programmersbox.claudemanager.data.transcript

import com.programmersbox.claudemanager.data.cost.CostEstimate

/** One tool call seen in a transcript. */
data class ToolCall(
    val id: String,
    val name: String,
    val summary: String,
    val at: Long,
    val done: Boolean,
)

data class SubagentInfo(
    val id: String,
    val type: String,
    val description: String,
    val model: String?,
    val startedAt: Long?,
    val lastActivityAt: Long?,
    val running: Boolean,
    val toolCount: Int,
    val current: ToolCall?,
    val recent: List<ToolCall>,
    val lastText: String?,
    val toolTimes: List<Long> = emptyList(),
    /** API-equivalent cost of this subagent's own requests. */
    val cost: CostEstimate = CostEstimate.Zero,
)

/** A background Bash command the session started, as recorded in its transcript. */
data class BackgroundCommand(
    val taskId: String?,
    val command: String,
    val description: String?,
    val startedAt: Long,
    val finished: Boolean,
)

enum class TaskKind { Monitor, Workflow }

enum class TaskStatus { Running, Completed, Failed, Stopped }

/**
 * A Monitor or Workflow the session started. Both run in the background, report through
 * `<task-notification>` messages, and end with a status (or, for a monitor, its timeout).
 */
data class BackgroundTask(
    val toolUseId: String,
    val kind: TaskKind,
    val taskId: String? = null,
    /** The description (monitor) or workflow name. */
    val label: String,
    /** The watched command (monitor) or the workflow's own description. */
    val detail: String?,
    val startedAt: Long,
    val timeoutMs: Long? = null,
    val status: TaskStatus = TaskStatus.Running,
    val endedAt: Long? = null,
    val eventCount: Int = 0,
    val lastEvent: String? = null,
    val lastEventAt: Long? = null,
    /** Workflow phase titles from the script's `meta` block. */
    val phases: List<String> = emptyList(),
) {
    /** Running, unless it reported an end or a monitor has passed its timeout. */
    fun isRunning(now: Long): Boolean =
        status == TaskStatus.Running && (timeoutMs == null || now < startedAt + timeoutMs)

    /** When it stopped: its reported end, or a monitor's timeout. */
    fun endTime(now: Long): Long? = endedAt ?: timeoutMs?.let { startedAt + it }?.takeIf { it <= now }
}

/** Everything Claude Manager derives from a session's transcript. */
data class SessionActivity(
    val title: String? = null,
    val model: String? = null,
    val gitBranch: String? = null,
    /** Prompt size of the latest request: input + cache read + cache write tokens. */
    val contextTokens: Long = 0,
    val outputTokens: Long = 0,
    val toolCount: Int = 0,
    val current: ToolCall? = null,
    val recent: List<ToolCall> = emptyList(),
    val lastText: String? = null,
    val lastActivityAt: Long? = null,
    val subagents: List<SubagentInfo> = emptyList(),
    val backgroundCommands: List<BackgroundCommand> = emptyList(),
    val toolTimes: List<Long> = emptyList(),
    /** API-equivalent cost of the main thread plus all subagents. */
    val cost: CostEstimate = CostEstimate.Zero,
    val tasks: List<BackgroundTask> = emptyList(),
)

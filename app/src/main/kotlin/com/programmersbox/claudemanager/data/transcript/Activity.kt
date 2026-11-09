package com.programmersbox.claudemanager.data.transcript

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
)

/** A background Bash command the session started, as recorded in its transcript. */
data class BackgroundCommand(
    val taskId: String?,
    val command: String,
    val description: String?,
    val startedAt: Long,
    val finished: Boolean,
)

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
)

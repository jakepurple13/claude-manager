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
    /** Latest permission mode: "default", "auto", "plan", "acceptEdits", "bypassPermissions". */
    val permissionMode: String? = null,
    /** Reasoning effort of the latest request. */
    val effort: String? = null,
    val worktree: WorktreeInfo? = null,
    val pullRequest: PullRequestLink? = null,
    val lastPrompt: String? = null,
    /** Prompts the user has sent so far. */
    val prompts: Int = 0,
    /** Distinct files written by the session or its agents. */
    val filesEdited: Int = 0,
    /** Skills the main thread loaded, in first-use order. */
    val skills: List<String> = emptyList(),
    val compactions: Int = 0,
    val hookErrors: Int = 0,
) {
    /** Context window the latest request ran against: 1M once the prompt outgrows 200k or the model asks for it. */
    val contextWindow: Long
        get() = if (contextTokens > DEFAULT_CONTEXT_WINDOW || model?.contains("[1m]") == true) 1_000_000 else DEFAULT_CONTEXT_WINDOW

    val execution: Execution get() = Execution.of(this)

    companion object {
        const val DEFAULT_CONTEXT_WINDOW = 200_000L
    }
}

data class WorktreeInfo(val name: String?, val branch: String?, val originalCwd: String?)

data class PullRequestLink(val number: Long, val url: String?, val repository: String?)

enum class ExecutionMode(val label: String) { Native("Native"), SubagentDriven("Subagent-driven") }

/** Whether the main thread does the work itself or mostly hands it to agents, and why. */
data class Execution(
    val mode: ExecutionMode,
    val reason: String,
    val mainCalls: Int,
    val agentCalls: Int,
) {
    companion object {
        /** Skills whose whole purpose is dispatching agents. */
        private val DISPATCH_SKILLS = listOf("subagent-driven-development", "dispatching-parallel-agents")
        /** Agent types used for quick lookups; a few of these don't make a session agent-driven. */
        private val LOOKUP_AGENTS = setOf("explore", "claude-code-guide", "plan")

        fun of(activity: SessionActivity): Execution {
            val mainCalls = activity.toolCount
            val agentCalls = activity.subagents.sumOf { it.toolCount }
            val skill = activity.skills.firstOrNull { s -> DISPATCH_SKILLS.any { s.endsWith(it) } }
            val workers = activity.subagents.filter { it.type.substringAfterLast(':').lowercase() !in LOOKUP_AGENTS }
            val share = if (mainCalls + agentCalls == 0) 0 else agentCalls * 100 / (mainCalls + agentCalls)
            return when {
                skill != null -> Execution(ExecutionMode.SubagentDriven, "Loaded $skill.", mainCalls, agentCalls)
                workers.size >= 2 && share >= 50 ->
                    Execution(ExecutionMode.SubagentDriven, "$share% of tool calls ran inside ${workers.size} agents.", mainCalls, agentCalls)
                else -> Execution(
                    ExecutionMode.Native,
                    if (agentCalls == 0) "The main thread made every tool call." else "$share% of tool calls ran inside agents.",
                    mainCalls,
                    agentCalls,
                )
            }
        }
    }
}

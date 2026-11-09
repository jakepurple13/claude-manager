package com.programmersbox.claudemanager.data.transcript

import com.programmersbox.claudemanager.data.cost.CostEstimate
import com.programmersbox.claudemanager.data.cost.Usage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.time.Instant

/**
 * Folds transcript JSONL records, one line at a time, into running totals.
 * The format is undocumented: every lookup is defensive and unknown records are skipped.
 */
class TranscriptAccumulator {
    var title: String? = null; private set
    var model: String? = null; private set
    var gitBranch: String? = null; private set
    var contextTokens: Long = 0; private set
    var lastText: String? = null; private set
    var lastActivityAt: Long? = null; private set

    /** Output tokens per API message id; one message is split over several records. */
    private val outputByMessage = HashMap<String, Long>()
    /** Final usage per API message id, for cost estimates. */
    private val usageByMessage = HashMap<String, Usage>()
    private val tools = LinkedHashMap<String, ToolCall>()
    private val backgroundByToolId = LinkedHashMap<String, BackgroundCommand>()
    private val backgroundTaskToToolId = HashMap<String, String>()
    private val toolTimesList = ArrayDeque<Long>()
    private val tasksByToolId = LinkedHashMap<String, BackgroundTask>()
    private val taskIdToToolId = HashMap<String, String>()
    /** TaskStop calls waiting for their result: tool id of the stop -> task id it stops. */
    private val pendingStops = HashMap<String, String>()
    private var sawQueuedNotifications = false

    val outputTokens: Long get() = outputByMessage.values.sum()
    val toolCount: Int get() = tools.size
    val cost: CostEstimate get() = CostEstimate.of(usageByMessage.values)
    val current: ToolCall? get() = tools.values.lastOrNull()?.takeIf { !it.done }
    fun recent(n: Int): List<ToolCall> = tools.values.toList().takeLast(n).reversed()
    val backgroundCommands: List<BackgroundCommand> get() = backgroundByToolId.values.toList()

    val tasks: List<BackgroundTask> get() = tasksByToolId.values.toList()

    /** Start times of the most recent tool calls, oldest first (capped). */
    val toolTimes: List<Long> get() = toolTimesList.toList()

    fun accept(line: String) {
        if (line.isBlank()) return
        val record = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() ?: return
        record.str("gitBranch")?.takeIf { it.isNotBlank() }?.let { gitBranch = it }
        val at = record.str("timestamp")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
        when (record.str("type")) {
            "ai-title" -> record.str("aiTitle")?.let { title = it }
            "assistant" -> onAssistant(record, at)
            "user" -> onUser(record, at)
            // Background-task notifications are queued before they reach the model; the queue
            // has every one, while only some are also written as user messages.
            "queue-operation" -> if (record.str("operation") == "enqueue") {
                record.str("content")?.let { onNotification(it, at, fromQueue = true) }
            }
        }
    }

    private fun onAssistant(record: JsonObject, at: Long?) {
        val message = record.obj("message") ?: return
        at?.let { lastActivityAt = it }
        message.str("model")?.takeIf { !it.startsWith("<") }?.let { model = it }
        message.obj("usage")?.let { usage ->
            contextTokens = usage.long("input_tokens") + usage.long("cache_read_input_tokens") +
                usage.long("cache_creation_input_tokens")
            message.str("id")?.let { id ->
                outputByMessage[id] = maxOf(outputByMessage[id] ?: 0, usage.long("output_tokens"))
                val creation = usage.obj("cache_creation")
                val write1h = creation?.long("ephemeral_1h_input_tokens") ?: 0
                val write5m = creation?.long("ephemeral_5m_input_tokens") ?: (usage.long("cache_creation_input_tokens") - write1h)
                usageByMessage[id] = Usage(
                    model = message.str("model"),
                    input = usage.long("input_tokens"),
                    cacheWrite5m = write5m.coerceAtLeast(0),
                    cacheWrite1h = write1h,
                    cacheRead = usage.long("cache_read_input_tokens"),
                    output = outputByMessage.getValue(id),
                )
            }
        }
        message.arr("content")?.forEach { element ->
            val block = element as? JsonObject ?: return@forEach
            when (block.str("type")) {
                "text" -> block.str("text")?.trim()?.takeIf { it.isNotEmpty() }?.let { lastText = it }
                "tool_use" -> {
                    val id = block.str("id") ?: return@forEach
                    val name = block.str("name") ?: "tool"
                    val input = block.obj("input")
                    tools[id] = ToolCall(id, name, summarize(name, input), at ?: 0, done = false)
                    if (at != null) {
                        toolTimesList.addLast(at)
                        if (toolTimesList.size > MAX_TOOL_TIMES) toolTimesList.removeFirst()
                    }
                    when (name) {
                        "Monitor" -> tasksByToolId[id] = BackgroundTask(
                            toolUseId = id,
                            kind = TaskKind.Monitor,
                            label = input?.str("description") ?: input?.str("command")?.lineSequence()?.first() ?: "Monitor",
                            detail = input?.str("command") ?: input?.obj("ws")?.str("url"),
                            startedAt = at ?: 0,
                            timeoutMs = input?.let { (it["timeout_ms"] as? JsonPrimitive)?.longOrNull } ?: DEFAULT_MONITOR_TIMEOUT_MS,
                        )
                        "Workflow" -> {
                            val script = input?.str("script").orEmpty()
                            tasksByToolId[id] = BackgroundTask(
                                toolUseId = id,
                                kind = TaskKind.Workflow,
                                label = META_NAME.find(script)?.groupValues?.get(1) ?: input?.str("name") ?: "Workflow",
                                detail = META_DESCRIPTION.find(script)?.groupValues?.get(1),
                                startedAt = at ?: 0,
                                // The meta block is a pure literal at the top of the script, ending at the first unindented "}".
                                phases = PHASE_TITLE.findAll(script.substringAfter("meta", "").substringBefore("\n}").substringAfter("phases", ""))
                                    .map { it.groupValues[1] }.toList(),
                            )
                        }
                        "TaskStop" -> (input?.str("task_id") ?: input?.str("shell_id"))?.let { pendingStops[id] = it }
                    }
                    if (name == "Bash" && input?.bool("run_in_background") == true) {
                        backgroundByToolId[id] = BackgroundCommand(
                            taskId = null,
                            command = input.str("command").orEmpty(),
                            description = input.str("description"),
                            startedAt = at ?: 0,
                            finished = false,
                        )
                    }
                }
            }
        }
    }

    private fun onUser(record: JsonObject, at: Long?) {
        at?.let { lastActivityAt = it }
        val content = record.obj("message")?.get("content")
        (content as? JsonArray)?.forEach { element ->
            val block = element as? JsonObject ?: return@forEach
            if (block.str("type") == "tool_result") {
                val id = block.str("tool_use_id") ?: return@forEach
                tools[id]?.let { tools[id] = it.copy(done = true) }
                pendingStops.remove(id)?.let { taskId -> endTask(taskId, TaskStatus.Stopped, at) }
            }
            if (block.str("type") == "text") block.str("text")?.let { onNotification(it, at) }
        }
        (content as? JsonPrimitive)?.contentOrNull?.let { onNotification(it, at) }

        val result = record["toolUseResult"] as? JsonObject
        val taskId = result?.str("backgroundTaskId")
        val toolId = (content as? JsonArray)?.firstNotNullOfOrNull { (it as? JsonObject)?.str("tool_use_id") }
        if (taskId != null && toolId != null) {
            backgroundTaskToToolId[taskId] = toolId
            backgroundByToolId[toolId]?.let { backgroundByToolId[toolId] = it.copy(taskId = taskId) }
        }
        // Monitor and Workflow results carry the id their notifications use.
        if (toolId != null && tasksByToolId.containsKey(toolId)) {
            val id = result?.let { r -> listOf("taskId", "task_id", "runId", "run_id", "workflowRunId").firstNotNullOfOrNull { r.str(it) } }
            if (id != null) {
                taskIdToToolId[id] = toolId
                tasksByToolId[toolId] = tasksByToolId.getValue(toolId).copy(taskId = id)
            }
        }
    }

    private fun endTask(taskId: String, status: TaskStatus, at: Long?) {
        val toolId = taskIdToToolId[taskId] ?: return
        tasksByToolId[toolId]?.takeIf { it.status == TaskStatus.Running }?.let {
            tasksByToolId[toolId] = it.copy(status = status, endedAt = at ?: it.lastEventAt ?: it.startedAt)
        }
    }

    /**
     * `<task-notification>` messages carry monitor events and mark background work (shell
     * commands, monitors, workflows) as finished.
     */
    private fun onNotification(text: String, at: Long?, fromQueue: Boolean = false) {
        if ("<task-notification>" !in text) return
        if (fromQueue) sawQueuedNotifications = true
        // Count events from the queue when the transcript has one, else from user messages.
        val countEvents = fromQueue || !sawQueuedNotifications
        TASK_NOTIFICATION.findAll(text).forEach { match -> onTaskNotification(match.value, at, countEvents) }
    }

    private fun onTaskNotification(text: String, at: Long?, countEvents: Boolean) {
        val taskId = TASK_ID.find(text)?.groupValues?.get(1)?.trim() ?: return
        EVENT.find(text)?.groupValues?.get(1)?.takeIf { countEvents }?.let { event ->
            taskIdToToolId[taskId]?.let { toolId ->
                tasksByToolId[toolId]?.let {
                    tasksByToolId[toolId] = it.copy(eventCount = it.eventCount + 1, lastEvent = unescape(event).trim(), lastEventAt = at)
                }
            }
        }
        val status = STATUS.find(text)?.groupValues?.get(1)?.trim()?.lowercase() ?: return
        if (status == "running") return
        backgroundTaskToToolId[taskId]?.let { toolId ->
            backgroundByToolId[toolId]?.let { backgroundByToolId[toolId] = it.copy(finished = true) }
        }
        endTask(
            taskId,
            when {
                status.startsWith("complete") || status == "success" || status == "succeeded" -> TaskStatus.Completed
                status.startsWith("fail") || status == "error" -> TaskStatus.Failed
                else -> TaskStatus.Stopped
            },
            at,
        )
    }

    private fun unescape(s: String) = s.replace("&gt;", ">").replace("&lt;", "<").replace("&quot;", "\"").replace("&amp;", "&")

    companion object {
        private const val MAX_TOOL_TIMES = 2_000
        private val json = Json { ignoreUnknownKeys = true }
        private const val DEFAULT_MONITOR_TIMEOUT_MS = 300_000L
        private val TASK_NOTIFICATION = Regex("<task-notification>.*?</task-notification>|<task-notification>.*\\z", RegexOption.DOT_MATCHES_ALL)
        private val TASK_ID = Regex("<task-id>([^<]+)</task-id>")
        private val STATUS = Regex("<status>([^<]+)</status>")
        private val EVENT = Regex("<event>(.*?)</event>", RegexOption.DOT_MATCHES_ALL)
        private val META_NAME = Regex("""meta\s*=\s*\{[^}]*?name\s*:\s*['"`]([^'"`]+)['"`]""", RegexOption.DOT_MATCHES_ALL)
        private val META_DESCRIPTION = Regex("""meta\s*=\s*\{[^}]*?description\s*:\s*['"`]([^'"`]+)['"`]""", RegexOption.DOT_MATCHES_ALL)
        private val PHASE_TITLE = Regex("""title\s*:\s*['"`]([^'"`]+)['"`]""")

        fun summarize(name: String, input: JsonObject?): String {
            if (input == null) return name
            val detail = listOf("description", "command", "file_path", "pattern", "skill", "url", "query", "prompt")
                .firstNotNullOfOrNull { key -> input.str(key)?.takeIf { it.isNotBlank() }?.let { key to it } }
                ?: return name
            val text = when (detail.first) {
                "file_path" -> detail.second.substringAfterLast('/').substringAfterLast('\\')
                else -> detail.second.lineSequence().first()
            }
            return "$name · ${text.take(90)}"
        }
    }
}

internal fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
internal fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray
internal fun JsonObject.long(key: String): Long = (this[key] as? JsonPrimitive)?.longOrNull ?: 0
internal fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
internal fun JsonElement.asObject(): JsonObject? = this as? JsonObject

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

    val outputTokens: Long get() = outputByMessage.values.sum()
    val toolCount: Int get() = tools.size
    val cost: CostEstimate get() = CostEstimate.of(usageByMessage.values)
    val current: ToolCall? get() = tools.values.lastOrNull()?.takeIf { !it.done }
    fun recent(n: Int): List<ToolCall> = tools.values.toList().takeLast(n).reversed()
    val backgroundCommands: List<BackgroundCommand> get() = backgroundByToolId.values.toList()

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
            }
            if (block.str("type") == "text") block.str("text")?.let(::onNotification)
        }
        (content as? JsonPrimitive)?.contentOrNull?.let(::onNotification)

        val result = record["toolUseResult"] as? JsonObject
        val taskId = result?.str("backgroundTaskId")
        val toolId = (content as? JsonArray)?.firstNotNullOfOrNull { (it as? JsonObject)?.str("tool_use_id") }
        if (taskId != null && toolId != null) {
            backgroundTaskToToolId[taskId] = toolId
            backgroundByToolId[toolId]?.let { backgroundByToolId[toolId] = it.copy(taskId = taskId) }
        }
    }

    /** `<task-notification>` messages mark background work as finished. */
    private fun onNotification(text: String) {
        if ("<task-notification>" !in text) return
        val taskId = TASK_ID.find(text)?.groupValues?.get(1) ?: return
        val status = STATUS.find(text)?.groupValues?.get(1) ?: return
        if (status == "running") return
        val toolId = backgroundTaskToToolId[taskId] ?: return
        backgroundByToolId[toolId]?.let { backgroundByToolId[toolId] = it.copy(finished = true) }
    }

    companion object {
        private const val MAX_TOOL_TIMES = 2_000
        private val json = Json { ignoreUnknownKeys = true }
        private val TASK_ID = Regex("<task-id>([^<]+)</task-id>")
        private val STATUS = Regex("<status>([^<]+)</status>")

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

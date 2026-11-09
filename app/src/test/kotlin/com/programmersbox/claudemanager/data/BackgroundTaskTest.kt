package com.programmersbox.claudemanager.data

import com.programmersbox.claudemanager.data.transcript.TaskKind
import com.programmersbox.claudemanager.data.transcript.TaskStatus
import com.programmersbox.claudemanager.data.transcript.TranscriptAccumulator
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackgroundTaskTest {
    private fun ts(sec: Long) = Instant.ofEpochSecond(1_800_000_000 + sec).toString()
    private fun ms(sec: Long) = (1_800_000_000 + sec) * 1000

    private fun toolUse(id: String, name: String, input: String, sec: Long) =
        """{"type":"assistant","timestamp":"${ts(sec)}","message":{"id":"m$id","content":[{"type":"tool_use","id":"$id","name":"$name","input":$input}]}}"""

    private fun result(id: String, toolUseResult: String, sec: Long) =
        """{"type":"user","timestamp":"${ts(sec)}","toolUseResult":$toolUseResult,"message":{"content":[{"type":"tool_result","tool_use_id":"$id","content":"ok"}]}}"""

    private fun notification(body: String, sec: Long): String {
        val text = JsonPrimitive("<task-notification>\n$body\n</task-notification>").toString()
        return """{"type":"user","timestamp":"${ts(sec)}","message":{"content":$text}}"""
    }

    @Test
    fun monitorCountsEventsAndEndsOnStatus() {
        val acc = TranscriptAccumulator()
        acc.accept(toolUse("t1", "Monitor", """{"command":"tail -f run.log | grep ERROR","description":"errors in run.log","timeout_ms":1800000}""", 0))
        acc.accept(result("t1", """{"taskId":"bmon1","timeoutMs":1800000,"persistent":false}""", 1))
        acc.accept(notification("<task-id>bmon1</task-id>\n<summary>Monitor event: \"errors in run.log\"</summary>\n<event>ERROR a &gt; b</event>", 10))
        acc.accept(notification("<task-id>bmon1</task-id>\n<event>ERROR two</event>", 20))

        val running = acc.tasks.single()
        assertEquals(TaskKind.Monitor, running.kind)
        assertEquals("errors in run.log", running.label)
        assertEquals(2, running.eventCount)
        assertEquals("ERROR two", running.lastEvent)
        assertTrue(running.isRunning(ms(30)))

        acc.accept(notification("<task-id>bmon1</task-id>\n<status>stopped</status>", 40))
        val ended = acc.tasks.single()
        assertEquals(TaskStatus.Stopped, ended.status)
        assertEquals(ms(40), ended.endedAt)
        assertFalse(ended.isRunning(ms(41)))
    }

    private fun queued(body: String, sec: Long): String {
        val text = JsonPrimitive("<task-notification>\n$body\n</task-notification>").toString()
        return """{"type":"queue-operation","operation":"enqueue","timestamp":"${ts(sec)}","content":$text}"""
    }

    @Test
    fun queuedEventsCountOnceEvenWhenAlsoDelivered() {
        val acc = TranscriptAccumulator()
        acc.accept(toolUse("t1", "Monitor", """{"command":"tail -f x","description":"x"}""", 0))
        acc.accept(result("t1", """{"taskId":"bq"}""", 1))
        acc.accept(queued("<task-id>bq</task-id>\n<event>one</event>", 5))
        acc.accept(notification("<task-id>bq</task-id>\n<event>one</event>", 6)) // same event, delivered
        acc.accept(queued("<task-id>bq</task-id>\n<event>two</event>", 9))
        assertEquals(2, acc.tasks.single().eventCount)
        assertEquals("two", acc.tasks.single().lastEvent)
    }

    @Test
    fun monitorStopsOnTaskStopAndTimesOut() {
        val acc = TranscriptAccumulator()
        acc.accept(toolUse("t1", "Monitor", """{"command":"until done; do sleep 1; done","description":"wait","timeout_ms":60000}""", 0))
        acc.accept(result("t1", """{"taskId":"bmon2"}""", 1))
        assertTrue(acc.tasks.single().isRunning(ms(30)))
        assertFalse(acc.tasks.single().isRunning(ms(61)), "a monitor past its timeout is not running")

        acc.accept(toolUse("t2", "TaskStop", """{"task_id":"bmon2"}""", 5))
        acc.accept(result("t2", """{"message":"stopped"}""", 6))
        assertEquals(TaskStatus.Stopped, acc.tasks.single().status)
    }

    @Test
    fun workflowReadsMetaAndCompletes() {
        val script = "export const meta = {\n  name: 'review-changes',\n  description: 'Review changed files',\n  phases: [{ title: 'Review' }, { title: 'Verify' }],\n}\nconst x = { title: 'not a phase' }\n"
        val acc = TranscriptAccumulator()
        acc.accept(toolUse("w1", "Workflow", "{\"script\":${JsonPrimitive(script)}}", 0))
        acc.accept(result("w1", """{"taskId":"wf_abc123"}""", 1))

        val wf = acc.tasks.single()
        assertEquals(TaskKind.Workflow, wf.kind)
        assertEquals("review-changes", wf.label)
        assertEquals("Review changed files", wf.detail)
        assertEquals(listOf("Review", "Verify"), wf.phases)
        assertTrue(wf.isRunning(ms(100)), "workflows have no timeout")

        acc.accept(notification("<task-id>wf_abc123</task-id>\n<status>completed</status>\n<summary>Workflow finished</summary>", 300))
        assertEquals(TaskStatus.Completed, acc.tasks.single().status)
    }
}

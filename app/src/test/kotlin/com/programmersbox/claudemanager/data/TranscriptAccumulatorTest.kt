package com.programmersbox.claudemanager.data

import com.programmersbox.claudemanager.data.process.ProcessScanner
import com.programmersbox.claudemanager.data.transcript.TranscriptAccumulator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TranscriptAccumulatorTest {

    private fun assistant(id: String, content: String, output: Long = 10, ts: String = "2026-11-09T15:00:00Z") =
        """{"type":"assistant","timestamp":"$ts","gitBranch":"main","message":{"id":"$id","model":"claude-opus-5-5","usage":{"input_tokens":2,"cache_read_input_tokens":1000,"cache_creation_input_tokens":500,"output_tokens":$output},"content":[$content]}}"""

    @Test
    fun countsOutputOncePerMessageAndTracksContext() {
        val acc = TranscriptAccumulator()
        // One API message split across two records carries the same usage twice.
        acc.accept(assistant("m1", """{"type":"text","text":"hello"}""", output = 40))
        acc.accept(assistant("m1", """{"type":"tool_use","id":"t1","name":"Read","input":{"file_path":"/a/b/Main.kt"}}""", output = 40))
        acc.accept(assistant("m2", """{"type":"text","text":"done"}""", output = 5))

        assertEquals(45, acc.outputTokens)
        assertEquals(1502, acc.contextTokens)
        assertEquals("claude-opus-5-5", acc.model)
        assertEquals("main", acc.gitBranch)
        assertEquals("done", acc.lastText)
        assertEquals(1, acc.toolCount)
    }

    @Test
    fun toolBecomesDoneWhenResultArrives() {
        val acc = TranscriptAccumulator()
        acc.accept(assistant("m1", """{"type":"tool_use","id":"t1","name":"Bash","input":{"command":"ls -la"}}"""))
        assertEquals("Bash · ls -la", acc.current?.summary)

        acc.accept("""{"type":"user","message":{"content":[{"type":"tool_result","tool_use_id":"t1","content":"ok"}]}}""")
        assertNull(acc.current)
        assertTrue(acc.recent(5).single().done)
    }

    @Test
    fun backgroundCommandLifecycle() {
        val acc = TranscriptAccumulator()
        acc.accept(assistant("m1", """{"type":"tool_use","id":"t9","name":"Bash","input":{"command":"./gradlew run","description":"Run app","run_in_background":true}}"""))
        acc.accept("""{"type":"user","toolUseResult":{"backgroundTaskId":"bg1"},"message":{"content":[{"type":"tool_result","tool_use_id":"t9","content":"started"}]}}""")
        val started = acc.backgroundCommands.single()
        assertEquals("bg1", started.taskId)
        assertFalse(started.finished)

        acc.accept("""{"type":"user","message":{"content":"<task-notification>\n<task-id>bg1</task-id>\n<status>completed</status>\n</task-notification>"}}""")
        assertTrue(acc.backgroundCommands.single().finished)
    }

    @Test
    fun ignoresGarbageAndTitles() {
        val acc = TranscriptAccumulator()
        acc.accept("not json")
        acc.accept("""{"type":"ai-title","aiTitle":"Claude manager UI mockups"}""")
        assertEquals("Claude manager UI mockups", acc.title)
    }

    @Test
    fun extractsCommandFromClaudeShellLine() {
        val line = """/bin/zsh -c source /Users/x/.claude/shell-snapshots/snapshot-zsh-1.sh 2>/dev/null || true && eval 'echo '\''hi'\'' && ./gradlew run' < /dev/null && pwd -P >| /tmp/cwd"""
        assertEquals("echo 'hi' && ./gradlew run", ProcessScanner.extractCommand(line))
    }

    @Test
    fun unescapesDoubleQuoteStyleQuoting() {
        val line = "/bin/zsh -c source /x/.claude/shell-snapshots/snapshot-zsh-1.sh && eval 'pkill -f '\"'\"'[M]ainKt'\"'\"'' < /dev/null && pwd"
        assertEquals("pkill -f '[M]ainKt'", ProcessScanner.extractCommand(line))
    }
}

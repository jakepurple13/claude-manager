package com.programmersbox.claudemanager.data

import com.programmersbox.claudemanager.data.transcript.ExecutionMode
import com.programmersbox.claudemanager.data.transcript.SessionActivity
import com.programmersbox.claudemanager.data.transcript.SubagentInfo
import com.programmersbox.claudemanager.data.transcript.TranscriptAccumulator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionInfoTest {

    private fun tool(id: String, name: String, input: String) =
        """{"type":"assistant","effort":"medium","message":{"id":"m$id","content":[{"type":"tool_use","id":"$id","name":"$name","input":$input}]}}"""

    @Test
    fun readsSessionRecords() {
        val acc = TranscriptAccumulator()
        acc.accept("""{"type":"permission-mode","permissionMode":"auto"}""")
        acc.accept("""{"type":"user","permissionMode":"plan","turnPosition":{"promptIndex":3,"turnIndex":5},"message":{"content":"hi"}}""")
        acc.accept("""{"type":"last-prompt","lastPrompt":"Build it!"}""")
        acc.accept("""{"type":"worktree-state","worktreeSession":{"originalCwd":"/repo","worktreeName":"more-info","worktreeBranch":"worktree-more-info"}}""")
        acc.accept("""{"type":"pr-link","prNumber":7,"prUrl":"https://github.com/o/r/pull/7","prRepository":"o/r"}""")
        acc.accept("""{"type":"system","subtype":"compact_boundary"}""")
        acc.accept("""{"type":"attachment","attachment":{"type":"hook_non_blocking_error","hookName":"PreToolUse:Bash"}}""")
        acc.accept("""{"type":"attachment","attachment":{"type":"hook_success"}}""")
        acc.accept(tool("t1", "Edit", """{"file_path":"/repo/A.kt"}"""))
        acc.accept(tool("t2", "Write", """{"file_path":"/repo/A.kt"}"""))
        acc.accept(tool("t3", "Write", """{"file_path":"/repo/B.kt"}"""))
        acc.accept(tool("t4", "Skill", """{"skill":"superpowers:brainstorming"}"""))

        assertEquals("plan", acc.permissionMode)
        assertEquals(3, acc.prompts)
        assertEquals("Build it!", acc.lastPrompt)
        assertEquals("worktree-more-info", acc.worktree?.branch)
        assertEquals("/repo", acc.worktree?.originalCwd)
        assertEquals(7, acc.pullRequest?.number)
        assertEquals(1, acc.compactions)
        assertEquals(1, acc.hookErrors)
        assertEquals(setOf("/repo/A.kt", "/repo/B.kt"), acc.filesEdited)
        assertEquals(listOf("superpowers:brainstorming"), acc.skills)
        assertEquals("medium", acc.effort)
    }

    private fun agent(type: String, calls: Int) = SubagentInfo(
        id = type + calls, type = type, description = "", model = null, startedAt = null, lastActivityAt = null,
        running = false, toolCount = calls, current = null, recent = emptyList(), lastText = null,
    )

    @Test
    fun dispatchSkillMakesSessionSubagentDriven() {
        val activity = SessionActivity(toolCount = 40, skills = listOf("superpowers:subagent-driven-development"))
        assertEquals(ExecutionMode.SubagentDriven, activity.execution.mode)
    }

    @Test
    fun mostCallsInWorkerAgentsMakesSessionSubagentDriven() {
        val activity = SessionActivity(toolCount = 10, subagents = listOf(agent("implementer", 30), agent("reviewer", 10)))
        assertEquals(ExecutionMode.SubagentDriven, activity.execution.mode)
        assertTrue("80%" in activity.execution.reason)
    }

    @Test
    fun lookupAgentsStayNative() {
        val activity = SessionActivity(toolCount = 5, subagents = listOf(agent("Explore", 40), agent("Explore", 30)))
        assertEquals(ExecutionMode.Native, activity.execution.mode)
    }

    @Test
    fun contextWindowGrowsPast200k() {
        assertEquals(200_000, SessionActivity(contextTokens = 150_000).contextWindow)
        assertEquals(1_000_000, SessionActivity(contextTokens = 250_000).contextWindow)
    }
}

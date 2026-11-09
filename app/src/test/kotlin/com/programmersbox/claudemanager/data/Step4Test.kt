package com.programmersbox.claudemanager.data

import com.programmersbox.claudemanager.data.cdm.CdmClient
import com.programmersbox.claudemanager.data.notify.SessionEvent
import com.programmersbox.claudemanager.data.notify.SessionEvents
import com.programmersbox.claudemanager.data.process.ShellProcess
import com.programmersbox.claudemanager.data.registry.LiveSession
import com.programmersbox.claudemanager.data.registry.SessionSource
import com.programmersbox.claudemanager.data.registry.SessionStatus
import com.programmersbox.claudemanager.data.transcript.SessionActivity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Step4Test {

    @Test
    fun parsesCdmDevices() {
        val out = """
            MBX6P240401889  S6003L · Android real · phone · in use by be0ee6eb-2af4-4273-a637-211deac7c751:main
            R5CX12GDWPZ     SM-A156U1 · Android real · phone · free
            garbage line
        """.trimIndent()
        val devices = CdmClient.parse(out)
        assertEquals(2, devices.size)
        assertEquals("be0ee6eb-2af4-4273-a637-211deac7c751", devices[0].holderSessionId)
        assertEquals("S6003L", devices[0].model)
        assertTrue(devices[0].real)
        assertNull(devices[1].holderSessionId)
        assertEquals("Android", devices[1].platform)
    }

    private fun summary(id: String, status: SessionStatus) = SessionSummary(
        live = LiveSession(1, id, "/tmp", id, SessionSource.Terminal, status, 0, null, null),
        displayName = id,
        pinned = false,
        activity = SessionActivity(),
        shells = emptyList(),
    )

    private fun state(vararg s: SessionSummary, orphans: List<ShellProcess> = emptyList()) =
        SessionsState(sessions = s.toList(), orphanShells = orphans, loaded = true)

    @Test
    fun notifiesWaitingAndLongTurnsOnly() {
        val prev = state(summary("a", SessionStatus.Working), summary("b", SessionStatus.Working), summary("c", SessionStatus.Idle))
        val cur = state(summary("a", SessionStatus.Idle), summary("b", SessionStatus.Idle), summary("c", SessionStatus.Waiting))
        val since = mapOf("a" to 0L, "b" to 95_000L)
        val events = SessionEvents.diff(prev, cur, since, now = 100_000L)

        assertEquals(listOf("c"), events.filterIsInstance<SessionEvent.Waiting>().map { it.sessionId })
        // a worked 100s and notifies; b worked 5s and stays quiet.
        assertEquals(listOf("a"), events.filterIsInstance<SessionEvent.FinishedTurn>().map { it.sessionId })
    }

    @Test
    fun notifiesNewOrphansOnceAndNothingOnFirstLoad() {
        val orphan = ShellProcess(42, 1, "npm run dev", 0, 0f, 0)
        assertEquals(emptyList(), SessionEvents.diff(SessionsState(), state(orphans = listOf(orphan)), emptyMap(), 0))
        val first = SessionEvents.diff(state(), state(orphans = listOf(orphan)), emptyMap(), 0)
        assertEquals(listOf(42L), first.filterIsInstance<SessionEvent.OrphanShell>().map { it.pid })
        val again = SessionEvents.diff(state(orphans = listOf(orphan)), state(orphans = listOf(orphan)), emptyMap(), 0)
        assertTrue(again.isEmpty())
    }
}

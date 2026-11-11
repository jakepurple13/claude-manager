package com.programmersbox.claudemanager.ui

import com.programmersbox.claudemanager.ui.window.ScreenRect
import com.programmersbox.claudemanager.ui.window.SessionWindows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionWindowsTest {
    @Test
    fun openingTheSameSessionTwiceFocusesTheExistingWindow() {
        val windows = SessionWindows()
        windows.open("a")
        windows.open("a")
        assertEquals(1, windows.windows.size)
        assertEquals(1, windows.windows.single().focusRequests)
    }

    @Test
    fun focusReportsWhetherAWindowShowsTheSession() {
        val windows = SessionWindows()
        assertFalse(windows.focus("a"))
        windows.open("a")
        assertTrue(windows.focus("a"))
        assertTrue(windows.isOpen("a"))
        assertFalse(windows.isOpen("b"))
    }

    @Test
    fun closingRemovesOnlyThatWindow() {
        val windows = SessionWindows()
        windows.open("a")
        windows.open("b")
        windows.close(windows.windows[0])
        assertEquals(listOf("b"), windows.windows.map { it.sessionId })
        // Keys stay unique after a close, so Compose never reuses a closed window's state.
        windows.open("c")
        assertEquals(2, windows.windows.map { it.key }.toSet().size)
    }

    @Test
    fun requestCloseMarksTheWindowButKeepsItUntilClosed() {
        val windows = SessionWindows()
        windows.open("a")
        val window = windows.windows.single()
        windows.requestClose(window)
        assertTrue(window.closing)
        assertEquals(1, windows.windows.size)
        windows.close(window)
        assertTrue(windows.windows.isEmpty())
    }

    @Test
    fun cardLocatorsIgnoreStaleUnregistersAndHiddenCards() {
        val windows = SessionWindows()
        val first: () -> ScreenRect? = { ScreenRect(10f, 20f, 300f, 120f) }
        val second: () -> ScreenRect? = { ScreenRect(0f, 0f, 0f, 0f) }
        windows.registerCard("a", first)
        assertEquals(ScreenRect(10f, 20f, 300f, 120f), windows.cardOnScreen("a"))
        windows.registerCard("a", second)
        windows.unregisterCard("a", first)
        // A zero-size card is scrolled out of view: no target.
        assertNull(windows.cardOnScreen("a"))
        windows.unregisterCard("a", second)
        assertNull(windows.cardOnScreen("a"))
    }
}

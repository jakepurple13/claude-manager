package com.programmersbox.claudemanager.ui.window

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** A rectangle on screen in dp, top-left origin: where a new window grows from. */
data class ScreenRect(val x: Float, val y: Float, val width: Float, val height: Float)

/** One separate window showing a single session. */
@Stable
class SessionWindow internal constructor(
    val key: Long,
    val sessionId: String,
    /** The card the window was opened from, so it can grow out of it. Null opens it in place. */
    val origin: ScreenRect? = null,
) {
    /** Goes up each time something asks for this window to come to the front. */
    var focusRequests by mutableIntStateOf(0)
        private set

    /** True once a close was asked for: the window shrinks away, then [SessionWindows.close] removes it. */
    var closing by mutableStateOf(false)
        internal set

    internal fun requestFocus() { focusRequests++ }
}

/**
 * The session windows opened from the board. At most one window shows a given session.
 * They are not saved: the app always starts with just the main window.
 */
class SessionWindows {
    private var nextKey = 0L
    val windows = mutableStateListOf<SessionWindow>()

    fun isOpen(sessionId: String): Boolean = windows.any { it.sessionId == sessionId }

    /** Opens a window for [sessionId], or brings its existing window to the front. */
    fun open(sessionId: String, origin: ScreenRect? = null) {
        if (!focus(sessionId)) windows.add(SessionWindow(nextKey++, sessionId, origin))
    }

    /** Brings the window showing [sessionId] to the front. False when no window shows it. */
    fun focus(sessionId: String): Boolean {
        val window = windows.firstOrNull { it.sessionId == sessionId } ?: return false
        window.requestFocus()
        return true
    }

    /** Starts the closing animation; the window calls [close] when it has finished. */
    fun requestClose(window: SessionWindow) {
        window.closing = true
    }

    fun close(window: SessionWindow) {
        windows.remove(window)
    }

    // Board cards on screen right now, by session id, so a closing window can shrink back into its card.
    private val cardLocators = mutableMapOf<String, () -> ScreenRect?>()

    fun registerCard(sessionId: String, locate: () -> ScreenRect?) {
        cardLocators[sessionId] = locate
    }

    fun unregisterCard(sessionId: String, locate: () -> ScreenRect?) {
        if (cardLocators[sessionId] === locate) cardLocators.remove(sessionId)
    }

    /** Where [sessionId]'s board card is on screen, or null when the board doesn't show it. */
    fun cardOnScreen(sessionId: String): ScreenRect? =
        cardLocators[sessionId]?.invoke()?.takeIf { it.width > 1f && it.height > 1f }
}

package com.programmersbox.claudemanager

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Whether the main window is open; the tray and notifications can reopen it. */
class AppWindow {
    var visible by mutableStateOf(true)
        private set

    /** Goes up each time a session window asks for the board to come to the front. */
    var focusRequests by mutableIntStateOf(0)
        private set

    fun show() { visible = true }
    fun hide() { visible = false }

    /** Shows the main window and brings it in front of any session windows. */
    fun bringToFront() {
        visible = true
        focusRequests++
    }
}

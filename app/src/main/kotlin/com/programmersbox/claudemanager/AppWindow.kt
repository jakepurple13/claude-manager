package com.programmersbox.claudemanager

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Whether the main window is open; the tray and notifications can reopen it. */
class AppWindow {
    var visible by mutableStateOf(true)
        private set

    fun show() { visible = true }
    fun hide() { visible = false }
}

package com.programmersbox.claudemanager.ui.home

import com.programmersbox.claudemanager.ui.timeline.TimelineWindows
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The board's Board/Timeline choice and timeline range. Shared because the controls live in the
 * main window's title bar while the board reads them, and the two sit in different scopes.
 */
class BoardControls {
    private val _view = MutableStateFlow(HomeView.Board)
    val view: StateFlow<HomeView> = _view.asStateFlow()

    private val _windowMs = MutableStateFlow(TimelineWindows[1].first)
    val windowMs: StateFlow<Long> = _windowMs.asStateFlow()

    fun setView(view: HomeView) { _view.value = view }
    fun setWindow(ms: Long) { _windowMs.value = ms }
}

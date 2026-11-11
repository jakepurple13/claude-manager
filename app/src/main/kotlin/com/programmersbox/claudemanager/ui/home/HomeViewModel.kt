package com.programmersbox.claudemanager.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.programmersbox.claudemanager.data.SessionRepository
import com.programmersbox.claudemanager.data.SessionsState
import com.programmersbox.claudemanager.data.db.HistoryDao
import com.programmersbox.claudemanager.data.settings.SettingsRepository
import kotlinx.coroutines.flow.map
import com.programmersbox.claudemanager.ui.timeline.TimelineBuilder
import com.programmersbox.claudemanager.ui.timeline.TimelineLane
import com.programmersbox.claudemanager.ui.timeline.TimelineWindows
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HomeView { Board, Timeline }

class HomeViewModel(
    private val repository: SessionRepository,
    history: HistoryDao,
    settings: SettingsRepository,
    private val controls: BoardControls,
) : ViewModel() {
    val showCost: StateFlow<Boolean> =
        settings.settings.map { it.showCost }.stateIn(viewModelScope, SharingStarted.Eagerly, settings.settings.value.showCost)

    val state: StateFlow<SessionsState> = repository.state

    val view: StateFlow<HomeView> = controls.view
    val windowMs: StateFlow<Long> = controls.windowMs

    private val since = System.currentTimeMillis() - TimelineWindows.maxOf { it.first }

    val lanes: StateFlow<List<TimelineLane>> =
        combine(repository.state, history.observeSegmentsSince(since), history.observeSessionsSince(since)) { state, segments, records ->
            TimelineBuilder.globalLanes(state, segments, records)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setView(view: HomeView) = controls.setView(view)
    fun setWindow(ms: Long) = controls.setWindow(ms)
    fun killShell(pid: Long): Boolean = repository.killShell(pid)

    fun rename(sessionId: String, name: String) {
        viewModelScope.launch { repository.rename(sessionId, name) }
    }

    fun setPinned(sessionId: String, pinned: Boolean) {
        viewModelScope.launch { repository.setPinned(sessionId, pinned) }
    }

    fun stopSession(pid: Long): Boolean = repository.stopSession(pid)
}

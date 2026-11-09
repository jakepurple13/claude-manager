package com.programmersbox.claudemanager.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.programmersbox.claudemanager.data.SessionRepository
import com.programmersbox.claudemanager.data.SessionSummary
import com.programmersbox.claudemanager.data.db.HistoryDao
import com.programmersbox.claudemanager.ui.timeline.TimelineBuilder
import com.programmersbox.claudemanager.ui.timeline.TimelineLane
import com.programmersbox.claudemanager.ui.timeline.TimelineWindows
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.InjectedParam

enum class SessionTab { Tree, Timeline }

class SessionViewModel(
    @InjectedParam val sessionId: String,
    @InjectedParam startOnTimeline: Boolean,
    private val repository: SessionRepository,
    history: HistoryDao,
) : ViewModel() {

    /** Null once the session has ended and left the registry. */
    val session: StateFlow<SessionSummary?> =
        repository.sessions
            .map { list -> list.firstOrNull { it.live.sessionId == sessionId } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _tab = MutableStateFlow(if (startOnTimeline) SessionTab.Timeline else SessionTab.Tree)
    val tab: StateFlow<SessionTab> = _tab.asStateFlow()

    private val _windowMs = MutableStateFlow(TimelineWindows[1].first)
    val windowMs: StateFlow<Long> = _windowMs.asStateFlow()

    private val since = System.currentTimeMillis() - TimelineWindows.maxOf { it.first }

    val lanes: StateFlow<List<TimelineLane>> =
        combine(session, history.observeSessionSegments(sessionId, since)) { s, segments ->
            s?.let { TimelineBuilder.sessionLanes(it, segments) }.orEmpty()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Lane/row key shown in the details panel: `main`, `a:<agentId>` or `shell:<pid>`. */
    private val _selected = MutableStateFlow("main")
    val selected: StateFlow<String> = _selected.asStateFlow()

    fun select(key: String) { _selected.value = key }
    fun setTab(tab: SessionTab) { _tab.value = tab }
    fun setWindow(ms: Long) { _windowMs.value = ms }

    fun rename(name: String) {
        viewModelScope.launch { repository.rename(sessionId, name) }
    }

    fun setPinned(pinned: Boolean) {
        viewModelScope.launch { repository.setPinned(sessionId, pinned) }
    }

    fun killShell(pid: Long): Boolean = repository.killShell(pid)

    fun stop(): Boolean = session.value?.let { repository.stopSession(it.live.pid) } ?: false
}

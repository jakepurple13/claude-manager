package com.programmersbox.claudemanager.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.programmersbox.claudemanager.ClaudePaths
import com.programmersbox.claudemanager.data.HistoryRecorder
import com.programmersbox.claudemanager.data.SessionRepository
import com.programmersbox.claudemanager.data.SessionSummary
import com.programmersbox.claudemanager.data.cdm.CdmClient
import com.programmersbox.claudemanager.data.cdm.CdmConnection
import com.programmersbox.claudemanager.data.settings.AppSettings
import com.programmersbox.claudemanager.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.listDirectoryEntries

enum class SettingsCategory(val title: String, val glyph: String, val intro: String) {
    Display("Display", "◐", "How sessions and costs look."),
    Notifications("Notifications", "◔", "System notifications from the menu bar app."),
    History("History", "◷", "Timelines are recorded while Claude Manager runs, even with the window closed."),
    DataSources("Data sources", "⌁", "Where Claude Manager reads its data from."),
    About("About", "ⓘ", ""),
}

class SettingsViewModel(
    private val repository: SettingsRepository,
    sessions: SessionRepository,
    private val recorder: HistoryRecorder,
    cdm: CdmClient,
    paths: ClaudePaths,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = repository.settings

    private val _category = MutableStateFlow(SettingsCategory.Display)
    val category: StateFlow<SettingsCategory> = _category.asStateFlow()

    /** The first live session, shown in the card preview; null shows a sample. */
    val previewSession: StateFlow<SessionSummary?> =
        sessions.sessions.map { it.firstOrNull() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Re-read on every refresh so the status follows the daemon starting or stopping. */
    val cdmConnection: StateFlow<CdmConnection> =
        sessions.state.map { cdm.connection }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), cdm.connection)

    val claudeHome: String = paths.claudeHome.toString().replace(System.getProperty("user.home"), "~")
    val appDataDir: String = paths.appDataDir.toString().replace(System.getProperty("user.home"), "~")
    private val dataDir = paths.appDataDir

    private val _databaseBytes = MutableStateFlow(0L)
    val databaseBytes: StateFlow<Long> = _databaseBytes.asStateFlow()

    init {
        refreshDatabaseSize()
    }

    fun select(category: SettingsCategory) { _category.value = category }

    fun update(change: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.update(change) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            recorder.clearHistory()
            refreshDatabaseSize()
        }
    }

    private fun refreshDatabaseSize() {
        viewModelScope.launch {
            _databaseBytes.value = withContext(Dispatchers.IO) {
                if (!dataDir.exists()) 0L
                else dataDir.listDirectoryEntries("claude-manager.db*").sumOf { runCatching { it.fileSize() }.getOrDefault(0) }
            }
        }
    }
}

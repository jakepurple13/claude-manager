package com.programmersbox.claudemanager.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.programmersbox.claudemanager.data.settings.AppSettings
import com.programmersbox.claudemanager.data.settings.SettingsRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    val settings: StateFlow<AppSettings> = repository.settings

    fun update(change: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.update(change) }
    }
}

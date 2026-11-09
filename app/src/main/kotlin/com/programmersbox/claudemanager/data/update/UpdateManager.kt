package com.programmersbox.claudemanager.data.update

import com.programmersbox.claudemanager.AppScope
import com.programmersbox.claudemanager.data.settings.SettingsRepository
import dev.nucleusframework.core.runtime.NucleusApp
import dev.nucleusframework.notification.common.notification
import dev.nucleusframework.updater.NucleusUpdater
import dev.nucleusframework.updater.UpdateInfo
import dev.nucleusframework.updater.UpdateResult
import dev.nucleusframework.updater.provider.GenericProvider
import dev.nucleusframework.updater.provider.GitHubProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateState {
    /** Running from Gradle or an IDE: there is no installed package to replace. */
    data object Unsupported : UpdateState
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val checkedAt: Long) : UpdateState
    data class Available(val version: String) : UpdateState
    data class Downloading(val version: String, val percent: Double, val differential: Boolean) : UpdateState
    data class ReadyToInstall(val version: String, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * In-app updates through Nucleus: reads the `latest-*.yml` published with each GitHub release,
 * downloads the matching installer (differentially when it can), verifies its SHA-512, and installs.
 */
class UpdateManager(
    private val settings: SettingsRepository,
    private val scope: AppScope,
) {
    private val updater: NucleusUpdater by lazy {
        // CLAUDE_MANAGER_UPDATE_FEED points the updater at another feed (a folder holding
        // latest-*.yml and the installers) to test updates without publishing a release.
        // Must be https, or http on localhost.
        val feed = System.getenv(FEED_ENV)?.takeIf { it.isNotBlank() }
        NucleusUpdater {
            provider = if (feed != null) GenericProvider(feed) else GitHubProvider(owner = OWNER, repo = REPO)
        }
    }

    val currentVersion: String = NucleusApp.version ?: "dev"

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var pending: UpdateInfo? = null
    private var job: Job? = null

    fun start() {
        val supported = runCatching { updater.isUpdateSupported() }.getOrDefault(false)
        if (!supported) {
            _state.value = UpdateState.Unsupported
            return
        }
        runCatching { updater.consumeUpdateEvent() }.getOrNull()?.let { event ->
            runCatching {
                notification(title = "Claude Manager updated", message = "Now on ${event.newVersion} (was ${event.previousVersion}).").send()
            }
        }
        scope.launch {
            while (true) {
                if (settings.current().autoCheckUpdates) check(notifyIfAvailable = true)
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    fun checkNow() {
        scope.launch { check(notifyIfAvailable = false) }
    }

    private suspend fun check(notifyIfAvailable: Boolean) {
        if (_state.value is UpdateState.Unsupported || _state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Checking
        _state.value = when (val result = updater.checkForUpdates()) {
            is UpdateResult.Available -> {
                pending = result.info
                if (notifyIfAvailable) {
                    runCatching {
                        notification(title = "Claude Manager ${result.info.version} is available", message = "Open Settings › About to install it.").send()
                    }
                }
                UpdateState.Available(result.info.version)
            }
            UpdateResult.NotAvailable -> UpdateState.UpToDate(System.currentTimeMillis())
            is UpdateResult.Error -> UpdateState.Failed(result.exception.message ?: "Update check failed")
        }
    }

    fun download() {
        val info = pending ?: return
        if (job?.isActive == true) return
        job = scope.launch {
            runCatching {
                updater.downloadUpdate(info).collect { progress ->
                    val file = progress.file
                    _state.value = if (file != null) {
                        UpdateState.ReadyToInstall(info.version, file)
                    } else {
                        UpdateState.Downloading(info.version, progress.percent, progress.isDifferential)
                    }
                }
            }.onFailure { _state.value = UpdateState.Failed(it.message ?: "Download failed") }
        }
    }

    /** Quits, runs the installer, and relaunches the new version. */
    fun installAndRestart() {
        val ready = _state.value as? UpdateState.ReadyToInstall ?: return
        updater.installAndRestart(ready.file)
    }

    private companion object {
        const val OWNER = "jakepurple13"
        const val REPO = "claude-manager"
        const val FEED_ENV = "CLAUDE_MANAGER_UPDATE_FEED"
        const val CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000
    }
}

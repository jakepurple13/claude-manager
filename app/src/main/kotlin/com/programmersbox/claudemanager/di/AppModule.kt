package com.programmersbox.claudemanager.di

import com.programmersbox.claudemanager.AppScope
import com.programmersbox.claudemanager.AppWindow
import com.programmersbox.claudemanager.data.cdm.CdmClient
import com.programmersbox.claudemanager.data.notify.SessionNotifier
import com.programmersbox.claudemanager.data.update.UpdateManager
import com.programmersbox.claudemanager.ClaudePaths
import com.programmersbox.claudemanager.data.process.ProcessScanner
import com.programmersbox.claudemanager.data.transcript.TranscriptStore
import com.programmersbox.claudemanager.data.SessionRepository
import com.programmersbox.claudemanager.data.HistoryRecorder
import com.programmersbox.claudemanager.data.db.buildDatabase
import com.programmersbox.claudemanager.data.db.historyDao
import com.programmersbox.claudemanager.data.db.settingsDao
import com.programmersbox.claudemanager.data.settings.SettingsRepository
import com.programmersbox.claudemanager.ui.navigation.SettingsRoute
import com.programmersbox.claudemanager.ui.settings.SettingsScreen
import com.programmersbox.claudemanager.ui.settings.SettingsViewModel
import com.programmersbox.claudemanager.data.db.sessionLabelDao
import com.programmersbox.claudemanager.data.registry.SessionRegistry
import com.programmersbox.claudemanager.ui.home.HomeScreen
import com.programmersbox.claudemanager.ui.home.HomeViewModel
import com.programmersbox.claudemanager.ui.navigation.Home
import com.programmersbox.claudemanager.ui.navigation.Navigator
import com.programmersbox.claudemanager.ui.navigation.SessionRoute
import com.programmersbox.claudemanager.ui.session.SessionScreen
import com.programmersbox.claudemanager.ui.session.SessionViewModel
import com.programmersbox.claudemanager.ui.window.SessionWindows
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation
import org.koin.plugin.module.dsl.create
import org.koin.plugin.module.dsl.single
import org.koin.plugin.module.dsl.viewModel

@OptIn(KoinExperimentalAPI::class)
val appModule = module {
    single<ClaudePaths>()
    single { create(::buildDatabase) }
    single { create(::sessionLabelDao) }
    single { create(::historyDao) }
    single { create(::settingsDao) }
    single<SettingsRepository>()
    single<AppScope>()
    single<SessionRegistry>()
    single<TranscriptStore>()
    single<ProcessScanner>()
    single<CdmClient>()
    single<AppWindow>()
    single<SessionRepository>()
    single<HistoryRecorder>()
    single<SessionNotifier>()
    single<UpdateManager>()
    single<SessionWindows>()
    single<Navigator>()

    viewModel<HomeViewModel>()
    viewModel<SessionViewModel>()
    viewModel<SettingsViewModel>()

    navigation<Home> { HomeScreen() }
    navigation<SettingsRoute> { SettingsScreen() }
    navigation<SessionRoute> { route -> SessionScreen(vm = koinViewModel { parametersOf(route.id, route.timeline) }) }
}

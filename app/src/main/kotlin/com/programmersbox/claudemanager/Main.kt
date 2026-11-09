package com.programmersbox.claudemanager

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberWindowState
import com.programmersbox.claudemanager.data.HistoryRecorder
import com.programmersbox.claudemanager.data.notify.SessionNotifier
import com.programmersbox.claudemanager.data.update.UpdateManager
import com.programmersbox.claudemanager.data.settings.SettingsRepository
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.programmersbox.claudemanager.di.appModule
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.programmersbox.claudemanager.ui.navigation.AppNavigation
import com.programmersbox.claudemanager.ui.navigation.Navigator
import com.programmersbox.claudemanager.ui.theme.ClaudeManagerTheme
import com.programmersbox.claudemanager.ui.tray.ClaudeManagerTray
import dev.nucleusframework.application.nucleusApplication
import dev.nucleusframework.window.material.MaterialDecoratedWindow
import dev.nucleusframework.window.material.MaterialTitleBar
import org.koin.core.context.startKoin

fun main(args: Array<String>) {
    val koin = startKoin { modules(appModule) }.koin
    // Record history for the app's whole life, including while the window is closed.
    koin.get<HistoryRecorder>().start()
    koin.get<SessionNotifier>().start()
    koin.get<UpdateManager>().start()
    val appWindow = koin.get<AppWindow>()
    val settings = koin.get<SettingsRepository>()

    // dockIconFollowsWindows: on macOS the Dock icon shows only while the main window is open,
    // so closing the window leaves the app running in the menu bar.
    nucleusApplication(args, dockIconFollowsWindows = true) {
        val windowState = rememberWindowState(size = DpSize(1180.dp, 760.dp))
        val navigator = remember { koin.get<Navigator>() }

        val prefs by settings.settings.collectAsState()
        ClaudeManagerTheme(prefs.themeMode) {
            if (appWindow.visible) {
                MaterialDecoratedWindow(
                    onCloseRequest = appWindow::hide,
                    state = windowState,
                    title = "Claude Manager",
                    minimumSize = DpSize(900.dp, 560.dp),
                    onPreviewKeyEvent = { event ->
                        if (event.key == Key.Escape && event.type == KeyEventType.KeyDown && navigator.canGoBack) {
                            navigator.back()
                            true
                        } else {
                            false
                        }
                    },
                ) {
                    MaterialTitleBar { Text("Claude Manager") }
                    Surface(Modifier.fillMaxSize()) { AppNavigation() }
                }
            }

            ClaudeManagerTray(
                onOpenWindow = appWindow::show,
                onQuit = ::exitApplication,
            )
        }
    }
}

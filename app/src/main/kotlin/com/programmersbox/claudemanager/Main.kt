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
import com.programmersbox.claudemanager.ui.theme.LocalThemeRipple
import com.programmersbox.claudemanager.ui.theme.ThemeRipple
import com.programmersbox.claudemanager.ui.theme.ThemeRippleHost
import androidx.compose.runtime.CompositionLocalProvider
import com.programmersbox.claudemanager.ui.tray.ClaudeManagerTray
import com.programmersbox.claudemanager.ui.components.BackgroundIndicator
import com.programmersbox.claudemanager.ui.components.LocalStatusDotStyle
import com.programmersbox.claudemanager.ui.components.StatusDotStyle
import dev.nucleusframework.application.nucleusApplication
import dev.nucleusframework.window.material.MaterialDecoratedWindow
import dev.nucleusframework.window.material.MaterialTitleBar
import org.koin.core.context.startKoin
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import com.programmersbox.claudemanager.ui.window.SessionWindowHost
import com.programmersbox.claudemanager.ui.window.SessionWindows
import dev.nucleusframework.application.NucleusWindow

fun main(args: Array<String>) {
    val koin = startKoin { modules(appModule) }.koin
    // Record history for the app's whole life, including while the window is closed.
    koin.get<HistoryRecorder>().start()
    koin.get<SessionNotifier>().start()
    koin.get<UpdateManager>().start()
    val appWindow = koin.get<AppWindow>()
    val sessionWindows = koin.get<SessionWindows>()
    val settings = koin.get<SettingsRepository>()

    // dockIconFollowsWindows: on macOS the Dock icon shows only while the main window is open.
    // Closing the window quits the app; the tray's Quit item does the same.
    nucleusApplication(args, dockIconFollowsWindows = true) {
        val windowState = rememberWindowState(size = DpSize(1180.dp, 760.dp))
        val navigator = remember { koin.get<Navigator>() }

        val prefs by settings.settings.collectAsState()
        val themeRipple = remember { ThemeRipple() }
        // The main window's handle, so session windows can open cascaded from it.
        val mainWindow = remember { arrayOfNulls<NucleusWindow>(1) }
        ClaudeManagerTheme(prefs.themeMode, prefs.themePalette) {
          val statusDotStyle = StatusDotStyle(BackgroundIndicator.fromId(prefs.backgroundIndicator), prefs.statusDotSizeDp.dp)
          CompositionLocalProvider(
              LocalThemeRipple provides themeRipple,
              LocalStatusDotStyle provides statusDotStyle,
          ) {
            if (appWindow.visible) {
                MaterialDecoratedWindow(
                    onCloseRequest = ::exitApplication,
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
                    mainWindow[0] = nucleusWindow
                    LaunchedEffect(appWindow.focusRequests) {
                        if (appWindow.focusRequests > 0) {
                            nucleusWindow.toFront()
                            nucleusWindow.requestFocus()
                        }
                    }
                    MaterialTitleBar { Text("Claude Manager") }
                    ThemeRippleHost(themeRipple, Modifier.fillMaxSize()) {
                        Surface(Modifier.fillMaxSize()) { AppNavigation() }
                    }
                }
            }

            // Session windows opened from the board. Closing the main window quits, which closes these too.
            sessionWindows.windows.forEachIndexed { index, window ->
                key(window.key) {
                    SessionWindowHost(
                        window = window,
                        index = index,
                        mainBounds = { mainWindow[0]?.boundsOnScreen() },
                        statusDotStyle = statusDotStyle,
                    )
                }
            }

            ClaudeManagerTray(
                onOpenWindow = appWindow::show,
                onQuit = ::exitApplication,
            )
          }
        }
    }
}

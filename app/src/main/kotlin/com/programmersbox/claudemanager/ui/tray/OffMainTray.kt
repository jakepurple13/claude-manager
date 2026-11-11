package com.programmersbox.claudemanager.ui.tray

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import dev.nucleusframework.composenativetray.menu.api.TrayMenuBuilder
import dev.nucleusframework.composenativetray.tray.api.NativeTray
import dev.nucleusframework.composenativetray.utils.MenuContentHash
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * The one thread that talks to the native tray. A single thread keeps updates in order.
 */
private val trayExecutor = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "claude-manager-tray").apply { isDaemon = true }
}
private val trayDispatcher = trayExecutor.asCoroutineDispatcher()

/**
 * Same as the library's `Tray(iconPath, windowsIconPath, tooltip, menuContent = ...)`, but every
 * call into [NativeTray] runs on [trayDispatcher] instead of the main thread.
 *
 * Why: on macOS, `MacTrayInitializer.initialize` and `update` are both `synchronized`. The first
 * update starts initialization on a background thread, which holds that lock while the native code
 * waits for the AppKit main thread. The library's `Tray` sends later updates from the main thread,
 * so a menu change during startup (sessions loading) blocks the main thread on the lock and the app
 * hangs with no window. Keeping the main thread out of the tray code removes that deadlock.
 */
@Composable
fun OffMainTray(
    iconPath: String,
    windowsIconPath: String,
    tooltip: String,
    menuContent: TrayMenuBuilder.() -> Unit,
) {
    val tray = remember { NativeTray() }
    // The library's own change key for menu content: a lambda is new on every recomposition.
    val menuHash = MenuContentHash.calculateMenuHash(menuContent)

    LaunchedEffect(tray, iconPath, windowsIconPath, tooltip, menuHash) {
        withContext(trayDispatcher) {
            tray.update(iconPath, windowsIconPath, tooltip, null, menuContent, null)
        }
    }

    DisposableEffect(tray) {
        onDispose { trayExecutor.execute { tray.dispose() } }
    }
}
